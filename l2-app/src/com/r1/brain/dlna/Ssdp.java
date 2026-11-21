package com.r1.brain.dlna;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * SSDP 发现：应答手机的 M-SEARCH，并周期性发 NOTIFY alive / byebye。
 *
 * <p>移植自 MIT 许可的 mokso/uvp-dlna-renderer。这里的行为是「手机在投屏列表里能不能看到设备」
 * 的唯一开关：只要 {@code 1900/UDP} 上有人回 200 OK，手机就会把设备列出来。
 *
 * <p>注意：本机会占用 UDP 1900，原厂播放器的 DLNA 接收端本来就没在跑，两边不会打架；
 * 如果哪天原厂也起来了，后绑的那个会失败——那时以谁先起为准。
 *
 * @author xudong.hua,claude
 * @since 2026-11-08 14:10 星期日
 */
final class Ssdp {
    private static final String TAG = "R1BRAIN-DLNA";
    private static final String GROUP = "239.255.255.250";
    private static final int PORT = 1900;
    private static final int MAX_AGE = 1800;
    private static final long ALIVE_INTERVAL_MS = 300000L;

    private final String uuid;
    private final String location;
    private final String[] nts;
    private final MulticastSocket sock;
    private final InetAddress group;
    private volatile boolean running = true;

    Ssdp(String uuid, InetAddress ip) throws IOException {
        this.uuid = uuid;
        this.location = "http://" + ip.getHostAddress() + ":" + DlnaHttpServer.PORT + "/desc.xml";
        this.nts = new String[]{"upnp:rootdevice", "uuid:" + uuid, Upnp.DEVICE_TYPE, Upnp.AVT, Upnp.RC, Upnp.CM};
        this.group = InetAddress.getByName(GROUP);
        this.sock = new MulticastSocket(PORT);
        NetworkInterface nif = NetworkInterface.getByInetAddress(ip);
        if (nif != null) sock.setNetworkInterface(nif);
        sock.setTimeToLive(2);
        sock.joinGroup(new InetSocketAddress(group, PORT), nif);
    }

    /** 启动监听与周期广播两个线程。 */
    void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                listen();
            }
        }, "dlna-ssdp-listen").start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                announce();
            }
        }, "dlna-ssdp-alive").start();
    }

    /** 关掉：先发 byebye，让手机立刻把设备从列表里去掉。 */
    void close() {
        running = false;
        for (String nt : nts) {
            send(notify(nt, "ssdp:byebye"), group, PORT);
        }
        sock.close();
    }

    private void listen() {
        byte[] buf = new byte[2048];
        while (running) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                sock.receive(p);
            } catch (IOException e) {
                if (running) Log.w(TAG, "ssdp 接收失败: " + e);
                continue;
            }
            String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.ISO_8859_1);
            if (!msg.startsWith("M-SEARCH")) continue;
            String st = header(msg, "ST");
            if (st == null) continue;
            for (String nt : nts) {
                if (st.equals("ssdp:all") || st.equals(nt)) {
                    send(response(nt), p.getAddress(), p.getPort());
                }
            }
        }
    }

    private void announce() {
        while (running) {
            for (String nt : nts) {
                send(notify(nt, "ssdp:alive"), group, PORT);
            }
            try {
                Thread.sleep(ALIVE_INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private String usn(String nt) {
        return nt.equals("uuid:" + uuid) ? nt : "uuid:" + uuid + "::" + nt;
    }

    private String response(String st) {
        return "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=" + MAX_AGE + "\r\nEXT:\r\nLOCATION: " + location
                + "\r\nSERVER: Android/5.1 UPnP/1.0 R1BrainDMR/1.0\r\nST: " + st + "\r\nUSN: " + usn(st) + "\r\n\r\n";
    }

    private String notify(String nt, String nts) {
        return "NOTIFY * HTTP/1.1\r\nHOST: " + GROUP + ":" + PORT + "\r\nCACHE-CONTROL: max-age=" + MAX_AGE
                + "\r\nLOCATION: " + location + "\r\nNT: " + nt + "\r\nNTS: " + nts
                + "\r\nSERVER: Android/5.1 UPnP/1.0 R1BrainDMR/1.0\r\nUSN: " + usn(nt) + "\r\n\r\n";
    }

    private void send(String msg, InetAddress to, int port) {
        byte[] b = msg.getBytes(StandardCharsets.ISO_8859_1);
        try {
            sock.send(new DatagramPacket(b, b.length, to, port));
        } catch (IOException e) {
            Log.d(TAG, "ssdp 发送失败: " + e);
        }
    }

    /** 从一段 SSDP 文本里取某个头。 */
    static String header(String msg, String name) {
        for (String line : msg.split("\r?\n")) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().toUpperCase(Locale.US).equals(name)) {
                return line.substring(c + 1).trim();
            }
        }
        return null;
    }
}
