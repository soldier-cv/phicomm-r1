package com.r1.brain.dlna;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GENA 事件订阅：SUBSCRIBE / UNSUBSCRIBE 记账，状态变了主动 NOTIFY 给手机。
 *
 * <p>移植自 MIT 许可的 mokso/uvp-dlna-renderer。没有它也能投，但手机上的播放状态
 * 会一直停在「正在连接」，进度条不动——控制点只能靠轮询。
 *
 * @author xudong.hua,claude
 * @since 2026-11-08 14:10 星期日
 */
final class Events {
    private static final String TAG = "R1BRAIN-DLNA";
    static final int TIMEOUT_S = 1800;

    private static final class Sub {
        String sid;
        String service;
        URL callback;
        long expires;
        int seq;
    }

    private final Map<String, Sub> subs = new ConcurrentHashMap<String, Sub>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final CastPlayer player;

    Events(CastPlayer player) {
        this.player = player;
    }

    /**
     * 新订阅（给了 callback）或续订（给了 sid）。
     *
     * @param service        服务类型
     * @param callbackHeader CALLBACK 头
     * @param sidHeader      SID 头
     * @return SID；无效返回 null
     */
    String subscribe(String service, String callbackHeader, String sidHeader) {
        long expires = System.currentTimeMillis() + TIMEOUT_S * 1000L;
        if (sidHeader != null) {
            Sub s = subs.get(sidHeader);
            if (s == null) return null;
            s.expires = expires;
            return s.sid;
        }
        if (callbackHeader == null) return null;
        int a = callbackHeader.indexOf('<');
        int b = callbackHeader.indexOf('>');
        if (a < 0 || b < a) return null;
        Sub s = new Sub();
        try {
            s.callback = new URL(callbackHeader.substring(a + 1, b));
        } catch (Exception e) {
            return null;
        }
        s.sid = "uuid:" + UUID.randomUUID();
        s.service = service;
        s.expires = expires;
        subs.put(s.sid, s);
        return s.sid;
    }

    boolean unsubscribe(String sid) {
        return sid != null && subs.remove(sid) != null;
    }

    /** 新订阅的第一次事件（在 SUBSCRIBE 响应之后补发）。 */
    void sendInitial(final String sid) {
        final Sub s = subs.get(sid);
        if (s != null) {
            sender.execute(new Runnable() {
                @Override
                public void run() {
                    send(s);
                }
            });
        }
    }

    /** 状态变了：给 AVTransport / RenderingControl 的订阅者都推一次。 */
    void changed() {
        sender.execute(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Sub s : subs.values()) {
                    if (s.expires < now) {
                        subs.remove(s.sid);
                    } else if (!s.service.equals(Upnp.CM)) {
                        send(s);
                    }
                }
            }
        });
    }

    void shutdown() {
        subs.clear();
        sender.shutdownNow();
    }

    // 只在 sender 线程跑。HttpURLConnection 不让发 NOTIFY，所以手写 HTTP。
    private void send(Sub s) {
        byte[] body = Upnp.propertySet(s.service, player).getBytes(StandardCharsets.UTF_8);
        int port = s.callback.getPort() > 0 ? s.callback.getPort() : 80;
        String path = s.callback.getFile().isEmpty() ? "/" : s.callback.getFile();
        String head = "NOTIFY " + path + " HTTP/1.1\r\n"
                + "HOST: " + s.callback.getHost() + ":" + port + "\r\n"
                + "CONTENT-TYPE: text/xml; charset=\"utf-8\"\r\n"
                + "NT: upnp:event\r\nNTS: upnp:propchange\r\n"
                + "SID: " + s.sid + "\r\nSEQ: " + s.seq++ + "\r\n"
                + "CONTENT-LENGTH: " + body.length + "\r\nCONNECTION: close\r\n\r\n";
        Socket sock = null;
        try {
            sock = new Socket();
            sock.connect(new InetSocketAddress(s.callback.getHost(), port), 3000);
            sock.setSoTimeout(3000);
            OutputStream out = sock.getOutputStream();
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
            out.flush();
            String status = new BufferedReader(
                    new InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1)).readLine();
            // 412：控制点已经不认识这个 SID 了
            if (status != null && status.contains(" 412 ")) subs.remove(s.sid);
        } catch (Exception e) {
            // 死掉的订阅者会把后面所有 NOTIFY 堵在这一个线程上；控制点续订时会自己重新订阅
            Log.d(TAG, "NOTIFY 失败，丢弃 " + s.sid + ": " + e);
            subs.remove(s.sid);
        } finally {
            if (sock != null) {
                try {
                    sock.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
