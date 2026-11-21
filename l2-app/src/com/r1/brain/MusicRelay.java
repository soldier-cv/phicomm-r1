package com.r1.brain;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * 在线音乐的本机中转。
 *
 * <p><b>为什么需要它</b>：这台机器上 {@code MediaPlayer} 播远端 http 地址必定失败——
 * 实测 2026-11-03 22:49 / 22:50 / 22:51 三次，日志都是
 * {@code NuCachedSource2: source returned error -1} 连着重试 10 次（每 3 秒一次，
 * 总共 31 秒）然后 {@code (1/-2147483648)}；期间 {@code /proc/net/tcp} 里
 * 根本没有到 CDN 的连接，说明 mediaserver 那套老的 HTTP 栈连名字都解析不出去。
 * 同一个 URL 用 L2 自己的 {@code Http} 去拉是好的（能读到 {@code ID3} 头），
 * 所以把「取字节」这件事搬到 L2 来做，mediaserver 只连 127.0.0.1。
 *
 * <p>实现是一个最小 HTTP 服务：单连接、一次一个请求、支持 Range 透传
 * （MediaPlayer 拖动进度会发 {@code Range: bytes=N-}），用完即断。
 *
 * <p>内存约束：全程 8KB 缓冲流式转发，绝不把整首歌读进内存。
 *
 * @author xudong.hua,deepseek
 * @since 2026-11-03 23:05 星期二
 */
public final class MusicRelay {

    private static final String TAG = Brain.TAG;
    private static final int CONNECT_MS = 8000;
    private static final int READ_MS = 20000;
    /** 网易云 CDN 对裸请求也放行，但带上这两个头更稳（部分节点会挑 UA）。 */
    private static final String UA = "Mozilla/5.0 (Linux; Android 5.1.1) AppleWebKit/537.36";
    private static final String REFERER = "https://music.163.com/";

    private static ServerSocket sServer;
    private static int sPort;

    private MusicRelay() {
    }

    /**
     * 把远端音频地址换成回环地址；本地文件或中转没起来时原样返回。
     *
     * @param remote 原始地址（http/https）
     * @return MediaPlayer 可以直接用的地址
     */
    public static String localUrlFor(String remote) {
        if (remote == null) {
            return remote;
        }
        if (!remote.startsWith("http://") && !remote.startsWith("https://")) {
            return remote;
        }
        ensureStarted();
        if (sPort <= 0) {
            return remote;
        }
        try {
            return "http://127.0.0.1:" + sPort + "/audio.mp3?u=" + URLEncoder.encode(remote, "UTF-8");
        } catch (Throwable t) {
            Log.w(TAG, "MusicRelay: 地址编码失败 " + t);
            return remote;
        }
    }

    /** 起中转服务（幂等）。只监听回环，局域网其它机器连不进来。 */
    private static synchronized void ensureStarted() {
        if (sPort > 0 || sServer != null) {
            return;
        }
        try {
            ServerSocket ss = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
            sServer = ss;
            sPort = ss.getLocalPort();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    acceptLoop();
                }
            }, "r1-music-relay");
            t.setDaemon(true);
            t.start();
            Log.i(TAG, "MusicRelay: listening 127.0.0.1:" + sPort);
        } catch (Throwable t) {
            Log.w(TAG, "MusicRelay: 启动失败 " + t);
            sPort = 0;
        }
    }

    private static void acceptLoop() {
        while (true) {
            Socket s = null;
            try {
                s = sServer.accept();
                final Socket client = s;
                Thread h = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        serve(client);
                    }
                }, "r1-relay-conn");
                h.setDaemon(true);
                h.start();
            } catch (Throwable t) {
                // 服务被关闭或内存吃紧时退出，下次播放会重新拉起
                Log.w(TAG, "MusicRelay: accept 结束 " + t);
                return;
            }
        }
    }

    /** 处理一次请求：读请求行 + Range，然后从远端流式转发。 */
    private static void serve(Socket client) {
        InputStream cin = null;
        OutputStream cout = null;
        HttpURLConnection conn = null;
        try {
            client.setSoTimeout(READ_MS);
            cin = new BufferedInputStream(client.getInputStream());
            cout = client.getOutputStream();

            String requestLine = readLine(cin);
            if (requestLine == null) {
                return;
            }
            String range = null;
            String line;
            int guard = 0;
            while ((line = readLine(cin)) != null && line.length() > 0 && guard++ < 40) {
                int idx = line.indexOf(':');
                if (idx > 0 && "range".equalsIgnoreCase(line.substring(0, idx).trim())) {
                    range = line.substring(idx + 1).trim();
                }
            }

            String remote = remoteFrom(requestLine);
            if (remote == null) {
                writeSimple(cout, 400, "Bad Request");
                return;
            }

            conn = (HttpURLConnection) new URL(remote).openConnection();
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Referer", REFERER);
            if (range != null) {
                conn.setRequestProperty("Range", range);
            }
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) {
                Log.w(TAG, "MusicRelay: 远端 HTTP " + code);
                writeSimple(cout, 502, "Bad Gateway");
                return;
            }

            StringBuilder head = new StringBuilder();
            head.append("HTTP/1.1 ").append(code).append(code == 206 ? " Partial Content" : " OK").append("\r\n");
            head.append("Content-Type: ").append(typeOf(conn.getContentType())).append("\r\n");
            head.append("Accept-Ranges: bytes\r\n");
            head.append("Connection: close\r\n");
            String cr = conn.getHeaderField("Content-Range");
            if (cr != null) {
                head.append("Content-Range: ").append(cr).append("\r\n");
            }
            int len = conn.getContentLength();
            if (len >= 0) {
                head.append("Content-Length: ").append(len).append("\r\n");
            }
            head.append("\r\n");
            cout.write(head.toString().getBytes("UTF-8"));

            InputStream in = conn.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                cout.write(buf, 0, n);
            }
            cout.flush();
        } catch (Throwable t) {
            // 客户端提前断开（换歌、拖动进度）在这里是正常现象
            Log.i(TAG, "MusicRelay: 连接结束 " + t.getMessage());
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) { }
            }
            if (cin != null) {
                try { cin.close(); } catch (Throwable ignored) { }
            }
            if (cout != null) {
                try { cout.close(); } catch (Throwable ignored) { }
            }
            try { client.close(); } catch (Throwable ignored) { }
        }
    }

    /** 从 "GET /audio.mp3?u=xxx HTTP/1.1" 里取回远端地址。 */
    private static String remoteFrom(String requestLine) {
        try {
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return null;
            }
            String path = parts[1];
            int q = path.indexOf("?u=");
            if (q < 0) {
                return null;
            }
            String enc = path.substring(q + 3);
            String url = URLDecoder.decode(enc, "UTF-8");
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return null;
            }
            return url;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String typeOf(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return "audio/mpeg";
        }
        return contentType;
    }

    private static void writeSimple(OutputStream out, int code, String msg) {
        try {
            String text = "HTTP/1.1 " + code + " " + msg + "\r\nConnection: close\r\nContent-Length: 0\r\n\r\n";
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } catch (Throwable ignored) {
        }
    }

    /** 读一行（到 \r\n），超过 2KB 直接截断，避免异常请求把内存吃光。 */
    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                int len = sb.length();
                if (len > 0 && sb.charAt(len - 1) == '\r') {
                    sb.setLength(len - 1);
                }
                return sb.toString();
            }
            if (sb.length() < 2048) {
                sb.append((char) c);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
