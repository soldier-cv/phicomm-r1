package com.r1.brain;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Random;

import android.util.Base64;

/**
 * 极简 WebSocket 客户端（只做 ASR 流式要用的那部分）。
 *
 * <p>为什么不引库：本项目的硬约束是「不引进第三方大库」（AGENTS.md），
 * 而 Android 5.1 自带的 okhttp 版本也不一定支持 WS。这里只实现 RFC 6455 里
 * 握手 + 文本帧 + 二进制帧 + ping/pong + 分片重组，够 ASR 用。
 *
 * <p>用法：{@code new WsClient(url, listener).connect()}，然后
 * {@link #sendText(String)} / {@link #sendBinary(byte[])}，最后 {@link #close()}。
 *
 * @author xudong.hua,deepseek
 * @since 2026-11-04 01:20 星期三
 */
public final class WsClient {

    private static final String TAG = Brain.TAG;
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int CONNECT_MS = 4000;
    private static final int READ_MS = 20000;
    /** 单帧上限。超过就关连接：截断读取会让剩余字节被当成下一帧头，后续解析全乱。 */
    private static final int MAX_FRAME = 256 * 1024;

    /** 收到一条文本消息 / 连接结束。 */
    public interface Listener {
        void onText(String text);

        void onClosed(String reason);
    }

    private final String host;
    private final int port;
    private final String path;
    private final Listener listener;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private final Object writeLock = new Object();
    private volatile boolean closed = false;
    private Thread reader;

    public WsClient(String url, Listener listener) throws IOException {
        String u = url == null ? "" : url.trim();
        if (u.startsWith("ws://")) {
            u = u.substring(5);
        } else if (u.startsWith("wss://")) {
            throw new IOException("服务端不支持 wss");
        }
        int slash = u.indexOf('/');
        String hostPort = slash < 0 ? u : u.substring(0, slash);
        this.path = slash < 0 ? "/" : u.substring(slash);
        int colon = hostPort.indexOf(':');
        this.host = colon < 0 ? hostPort : hostPort.substring(0, colon);
        this.port = colon < 0 ? 80 : Integer.parseInt(hostPort.substring(colon + 1));
        this.listener = listener;
    }

    /** 建立连接并按帧异步读取。 */
    public void connect() throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), CONNECT_MS);
        s.setSoTimeout(READ_MS);
        socket = s;
        in = new BufferedInputStream(s.getInputStream());
        out = s.getOutputStream();

        String key = newKey();
        String req = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";
        out.write(req.getBytes("UTF-8"));
        out.flush();

        String head = readHandshake();
        if (!head.contains(" 101 ")) {
            throw new IOException("握手失败: " + firstLine(head));
        }
        String expect = base64(sha1(key + GUID));
        if (!head.contains(expect)) {
            throw new IOException("Sec-WebSocket-Accept 不匹配");
        }

        reader = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "r1-ws-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** 发一帧文本。 */
    public void sendText(String text) throws IOException {
        sendFrame(0x1, text.getBytes("UTF-8"));
    }

    /** 发一帧二进制。 */
    public void sendBinary(byte[] data) throws IOException {
        sendFrame(0x2, data);
    }

    /** 主动关闭（先发 close 帧，尽量让服务端收尾）。 */
    public void close() {
        if (closed) return;
        closed = true;
        try {
            sendFrame(0x8, new byte[0]);
        } catch (Throwable ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Throwable ignored) {
        }
    }

    public boolean isClosed() {
        return closed;
    }

    // ------------------------------------------------------------------ 内部

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        synchronized (writeLock) {
            if (socket == null || socket.isClosed()) throw new IOException("连接已关闭");
            int n = payload.length;
            ByteArrayOutputStream bos = new ByteArrayOutputStream(n + 14);
            bos.write(0x80 | opcode);
            if (n < 126) {
                bos.write(0x80 | n);
            } else if (n < 65536) {
                bos.write(0x80 | 126);
                bos.write((n >> 8) & 0xff);
                bos.write(n & 0xff);
            } else {
                bos.write(0x80 | 127);
                for (int i = 7; i >= 0; i--) {
                    bos.write((int) (((long) n >> (8 * i)) & 0xff));
                }
            }
            byte[] mask = new byte[4];
            new Random().nextBytes(mask);
            bos.write(mask, 0, 4);
            for (int i = 0; i < n; i++) {
                bos.write(payload[i] ^ mask[i % 4]);
            }
            out.write(bos.toByteArray());
            out.flush();
        }
    }

    private void readLoop() {
        ByteArrayOutputStream frag = null;
        int fragOpcode = 0;
        try {
            while (!closed) {
                int b0 = in.read();
                if (b0 < 0) break;
                int b1 = in.read();
                if (b1 < 0) break;
                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0f;
                long len = b1 & 0x7f;
                if (len == 126) {
                    len = ((in.read() & 0xff) << 8) | (in.read() & 0xff);
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) {
                        len = (len << 8) | (in.read() & 0xff);
                    }
                }
                byte[] mask = null;
                if ((b1 & 0x80) != 0) {
                    mask = new byte[4];
                    readFully(mask, 4);
                }
                if (len < 0 || len > MAX_FRAME) {
                    throw new IOException("WebSocket 帧过大: " + len);
                }
                byte[] payload = new byte[(int) len];
                readFully(payload, payload.length);
                if (mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= mask[i % 4];
                    }
                }

                if (opcode == 0x9) {                 // ping -> pong
                    sendFrame(0xA, payload);
                    continue;
                }
                if (opcode == 0xA) {                 // pong
                    continue;
                }
                if (opcode == 0x8) {                 // close
                    break;
                }
                if (opcode == 0x0) {                 // 续帧
                    if (frag != null) {
                        frag.write(payload);
                        if (fin) {
                            deliver(fragOpcode, frag.toByteArray());
                            frag = null;
                        }
                    }
                    continue;
                }
                if (!fin) {
                    fragOpcode = opcode;
                    frag = new ByteArrayOutputStream();
                    frag.write(payload);
                    continue;
                }
                deliver(opcode, payload);
            }
        } catch (Throwable t) {
            Log.i(TAG, "WsClient: 读取结束 " + t);
        } finally {
            closed = true;
            try {
                if (socket != null) socket.close();
            } catch (Throwable ignored) {
            }
            if (listener != null) listener.onClosed("closed");
        }
    }

    private void deliver(int opcode, byte[] payload) {
        if (opcode == 0x1 && listener != null) {
            try {
                listener.onText(new String(payload, "UTF-8"));
            } catch (Throwable t) {
                Log.w(TAG, "WsClient: 分发失败 " + t);
            }
        }
    }

    private String readHandshake() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int c = in.read();
            if (c < 0) throw new IOException("握手时连接断开");
            bos.write(c);
            if ((state == 0 || state == 2) && c == '\r') state++;
            else if ((state == 1 || state == 3) && c == '\n') state++;
            else state = 0;
            if (bos.size() > 8192) throw new IOException("握手头过长");
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    private void readFully(byte[] dst, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int n = in.read(dst, off, len - off);
            if (n < 0) throw new IOException("连接断开");
            off += n;
        }
    }

    private static String firstLine(String head) {
        int i = head.indexOf("\r\n");
        return i < 0 ? head : head.substring(0, i);
    }

    private static String newKey() {
        byte[] b = new byte[16];
        new Random().nextBytes(b);
        return base64(b);
    }

    private static String base64(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    private static byte[] sha1(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return md.digest(s.getBytes("UTF-8"));
    }
}
