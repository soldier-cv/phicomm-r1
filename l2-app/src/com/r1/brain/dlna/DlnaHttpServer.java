package com.r1.brain.dlna;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 投屏用的极简 HTTP/1.1 服务：设备描述、SOAP 控制、GENA 订阅。
 *
 * <p>移植自 MIT 许可的 mokso/uvp-dlna-renderer。单连接单请求，不做 keep-alive——
 * 控制点的请求都很小，简单比高效重要。
 *
 * <p>为什么单独开端口（49494）而不是复用 8686：8686 有访问令牌校验，
 * 手机侧的 DLNA 控制点不会带我们的令牌；而且 1900/UDP 的 SSDP 应答里写的
 * LOCATION 必须是不带鉴权就能取的地址。
 *
 * @author xudong.hua,claude
 * @since 2026-11-08 14:10 星期日
 */
final class DlnaHttpServer implements Runnable {
    private static final String TAG = "R1BRAIN-DLNA";
    static final int PORT = 49494;
    private static final int MAX_BODY = 256 * 1024;

    private final ServerSocket server;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final CastPlayer player;
    private final Events events;
    private final String deviceXml;

    DlnaHttpServer(CastPlayer player, Events events, String deviceXml) throws IOException {
        this.server = new ServerSocket(PORT);
        this.player = player;
        this.events = events;
        this.deviceXml = deviceXml;
    }

    @Override
    public void run() {
        while (!server.isClosed()) {
            try {
                final Socket s = server.accept();
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        handle(s);
                    }
                });
            } catch (IOException e) {
                if (!server.isClosed()) Log.w(TAG, "accept 失败: " + e);
            }
        }
    }

    void close() {
        try {
            server.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    private void handle(Socket s) {
        Socket sock = s;
        try {
            sock.setSoTimeout(5000);
            InputStream in = sock.getInputStream();
            String line = readLine(in);
            if (line == null) return;
            String[] request = line.split(" ");
            if (request.length < 2) return;
            Map<String, String> headers = new HashMap<String, String>();
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                int c = h.indexOf(':');
                if (c > 0) {
                    headers.put(h.substring(0, c).trim().toLowerCase(Locale.US), h.substring(c + 1).trim());
                }
            }
            int len = 0;
            try {
                String cl = headers.get("content-length");
                len = cl == null ? 0 : Integer.parseInt(cl);
            } catch (NumberFormatException ignored) {
            }
            if (len < 0 || len > MAX_BODY) {
                respond(sock, 413, null, "", null);
                return;
            }
            byte[] body = new byte[len];
            for (int off = 0; off < len; ) {
                int n = in.read(body, off, len - off);
                if (n < 0) return;
                off += n;
            }
            route(sock, request[0], request[1], headers, new String(body, StandardCharsets.UTF_8));
        } catch (Throwable e) {
            Log.d(TAG, "请求处理失败: " + e);
        } finally {
            try {
                sock.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void route(Socket sock, String method, String path, Map<String, String> h, String body)
            throws IOException {
        if (method.equals("GET") && path.equals("/desc.xml")) {
            respond(sock, 200, "text/xml; charset=\"utf-8\"", deviceXml, null);
            return;
        }
        if (method.equals("GET") && path.endsWith(".xml")) {
            String type = Upnp.typeOf(path.substring(1, path.length() - 4));
            if (type != null) {
                respond(sock, 200, "text/xml; charset=\"utf-8\"", Upnp.scpd(type), null);
                return;
            }
        }
        if (method.equals("POST") && path.startsWith("/ctl/")) {
            String type = Upnp.typeOf(path.substring(5));
            if (type != null) {
                soap(sock, type, h.get("soapaction"), body);
                return;
            }
        }
        if (path.startsWith("/evt/") && Upnp.typeOf(path.substring(5)) != null) {
            String type = Upnp.typeOf(path.substring(5));
            if (method.equals("SUBSCRIBE")) {
                String sid = events.subscribe(type, h.get("callback"), h.get("sid"));
                if (sid == null) {
                    respond(sock, 412, null, "", null);
                    return;
                }
                respond(sock, 200, null, "", "SID: " + sid + "\r\nTIMEOUT: Second-" + Events.TIMEOUT_S + "\r\n");
                if (h.get("sid") == null) events.sendInitial(sid);
                return;
            }
            if (method.equals("UNSUBSCRIBE")) {
                respond(sock, events.unsubscribe(h.get("sid")) ? 200 : 412, null, "", null);
                return;
            }
        }
        respond(sock, 404, null, "", null);
    }

    private void soap(Socket sock, String type, String soapAction, String body) throws IOException {
        String action = soapAction == null ? "" : soapAction.replace("\"", "");
        int hash = action.indexOf('#');
        action = hash < 0 ? action : action.substring(hash + 1);
        String resp;
        try {
            resp = dispatch(type, action, body);
        } catch (RuntimeException e) {
            Log.w(TAG, "动作 " + action + " 失败: " + e);
            respond(sock, 500, "text/xml; charset=\"utf-8\"", Upnp.soapFault(501, "Action Failed"), null);
            return;
        }
        if (resp == null) {
            respond(sock, 500, "text/xml; charset=\"utf-8\"", Upnp.soapFault(401, "Invalid Action"), null);
        } else {
            respond(sock, 200, "text/xml; charset=\"utf-8\"", resp, null);
        }
    }

    /** 返回 SOAP 响应；不认识的动作返回 null。 */
    private String dispatch(String type, String action, String body) {
        if (type.equals(Upnp.AVT)) {
            if (action.equals("SetAVTransportURI")) {
                player.setUri(Upnp.arg(body, "CurrentURI").trim(), Upnp.arg(body, "CurrentURIMetaData"));
                return Upnp.soapResponse(type, action);
            }
            if (action.equals("SetNextAVTransportURI")) {
                player.setNextUri(Upnp.arg(body, "NextURI").trim(), Upnp.arg(body, "NextURIMetaData"));
                return Upnp.soapResponse(type, action);
            }
            if (action.equals("Play")) {
                player.play();
                return Upnp.soapResponse(type, action);
            }
            if (action.equals("Pause")) {
                player.pause();
                return Upnp.soapResponse(type, action);
            }
            if (action.equals("Stop")) {
                player.stop();
                return Upnp.soapResponse(type, action);
            }
            if (action.equals("Seek")) {
                player.seek(Upnp.parseTime(Upnp.arg(body, "Target")));
                return Upnp.soapResponse(type, action);
            }
            if (action.equals("Next") || action.equals("Previous")) {
                return Upnp.soapResponse(type, action);
            }
            if (action.equals("GetTransportInfo")) {
                CastPlayer.Snapshot s = player.snapshot();
                return Upnp.soapResponse(type, action, "CurrentTransportState", s.state,
                        "CurrentTransportStatus", "OK", "CurrentSpeed", "1");
            }
            if (action.equals("GetPositionInfo")) {
                CastPlayer.Snapshot s = player.snapshot();
                String pos = Upnp.time(s.positionMs);
                return Upnp.soapResponse(type, action, "Track", s.uri.isEmpty() ? "0" : "1",
                        "TrackDuration", Upnp.time(s.durationMs), "TrackMetaData", s.meta, "TrackURI", s.uri,
                        "RelTime", pos, "AbsTime", pos, "RelCount", "2147483647", "AbsCount", "2147483647");
            }
            if (action.equals("GetMediaInfo")) {
                CastPlayer.Snapshot s = player.snapshot();
                return Upnp.soapResponse(type, action, "NrTracks", s.uri.isEmpty() ? "0" : "1",
                        "MediaDuration", Upnp.time(s.durationMs), "CurrentURI", s.uri, "CurrentURIMetaData", s.meta,
                        "NextURI", s.nextUri, "NextURIMetaData", "", "PlayMedium", "NETWORK",
                        "RecordMedium", "NOT_IMPLEMENTED", "WriteStatus", "NOT_IMPLEMENTED");
            }
            if (action.equals("GetDeviceCapabilities")) {
                return Upnp.soapResponse(type, action, "PlayMedia", "NETWORK",
                        "RecMedia", "NOT_IMPLEMENTED", "RecQualityModes", "NOT_IMPLEMENTED");
            }
            if (action.equals("GetTransportSettings")) {
                return Upnp.soapResponse(type, action, "PlayMode", "NORMAL", "RecQualityMode", "NOT_IMPLEMENTED");
            }
            if (action.equals("GetCurrentTransportActions")) {
                return Upnp.soapResponse(type, action, "Actions", player.snapshot().actions());
            }
            return null;
        }
        if (type.equals(Upnp.RC)) {
            if (action.equals("GetVolume")) {
                return Upnp.soapResponse(type, action, "CurrentVolume", String.valueOf(player.volume()));
            }
            if (action.equals("SetVolume")) {
                try {
                    player.setVolume(Integer.parseInt(Upnp.arg(body, "DesiredVolume").trim()));
                } catch (NumberFormatException ignored) {
                }
                return Upnp.soapResponse(type, action);
            }
            if (action.equals("GetMute")) {
                return Upnp.soapResponse(type, action, "CurrentMute", player.muted() ? "1" : "0");
            }
            if (action.equals("SetMute")) {
                String m = Upnp.arg(body, "DesiredMute").trim();
                player.setMuted(m.equals("1") || m.equalsIgnoreCase("true"));
                return Upnp.soapResponse(type, action);
            }
            if (action.equals("ListPresets")) {
                return Upnp.soapResponse(type, action, "CurrentPresetNameList", "FactoryDefaults");
            }
            if (action.equals("SelectPreset")) {
                return Upnp.soapResponse(type, action);
            }
            return null;
        }
        if (action.equals("GetProtocolInfo")) {
            return Upnp.soapResponse(type, action, "Source", "", "Sink", Upnp.SINK_PROTOCOLS);
        }
        if (action.equals("GetCurrentConnectionIDs")) {
            return Upnp.soapResponse(type, action, "ConnectionIDs", "0");
        }
        if (action.equals("GetCurrentConnectionInfo")) {
            return Upnp.soapResponse(type, action, "RcsID", "0", "AVTransportID", "0", "ProtocolInfo", "",
                    "PeerConnectionManager", "", "PeerConnectionID", "-1", "Direction", "Input", "Status", "OK");
        }
        return null;
    }

    private static void respond(Socket sock, int code, String contentType, String body, String extraHeaders)
            throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder h = new StringBuilder("HTTP/1.1 ").append(code)
                .append(code == 200 ? " OK" : " Error").append("\r\n");
        if (contentType != null) h.append("Content-Type: ").append(contentType).append("\r\n");
        if (extraHeaders != null) h.append(extraHeaders);
        h.append("Server: Android/5.1 UPnP/1.0 R1BrainDMR/1.0\r\n")
                .append("Content-Length: ").append(b.length).append("\r\nConnection: close\r\n\r\n");
        OutputStream out = sock.getOutputStream();
        out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(b);
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (int c = in.read(); c >= 0 && c != '\n'; c = in.read()) {
            if (c != '\r') buf.write(c);
            if (buf.size() > 8192) throw new IOException("头太长");
        }
        return new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
    }
}
