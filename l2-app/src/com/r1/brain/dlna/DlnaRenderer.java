package com.r1.brain.dlna;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.UUID;

/**
 * 自建 DLNA 接收端的生命周期（开关、线程、锁）。
 *
 * <p>为什么要自己做一个：原厂播放器（{@code com.phicomm.speaker.player}）的接收端只在
 * 「播放停止 → 等待投屏」的状态迁移里启动，开机永远到不了那里，而且它是系统签名的包我们改不了。
 * 本类用一个 MIT 许可的开源实现（mokso/uvp-dlna-renderer）把接收端放进我们自己的进程，
 * 想开就开、想关就关。
 *
 * <p>覆盖范围：DLNA（安卓/Windows/多数视频 App 的「投屏」）。AirPlay 是闭源协议，这里不做。
 *
 * @author xudong.hua,claude
 * @since 2026-11-08 14:10 星期日
 */
public final class DlnaRenderer {

    private static final String TAG = "R1BRAIN-DLNA";
    /** 手机上显示的名字。 */
    private static final String DEVICE_NAME = "R1 音箱";

    private static final Object LOCK = new Object();
    private static boolean running;
    private static String lastError = "";

    private static CastPlayer player;
    private static Events events;
    private static DlnaHttpServer http;
    private static Ssdp ssdp;
    private static WifiManager.MulticastLock multicastLock;
    private static InetAddress boundIp;

    private DlnaRenderer() {
    }

    /**
     * 打开投屏接收端（幂等）。
     *
     * @param ctx 上下文
     * @return true 表示已经在跑（或这次成功启动）
     */
    public static boolean start(final Context ctx) {
        synchronized (LOCK) {
            if (running) return true;
            lastError = "";
            final Context app = ctx.getApplicationContext();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    boot(app);
                }
            }, "dlna-boot");
            t.setDaemon(true);
            t.start();
            return true;
        }
    }

    /** 真正的启动工作（拿 IP 可能要等网络，所以放后台线程）。 */
    private static void boot(Context ctx) {
        InetAddress ip = findIp();
        for (int i = 0; ip == null && i < 20; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            ip = findIp();
        }
        if (ip == null) {
            lastError = "没有局域网地址";
            Log.w(TAG, "投屏启动失败：没有局域网地址");
            return;
        }
        synchronized (LOCK) {
            if (running) return;
            try {
                if (multicastLock == null) {
                    WifiManager wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
                    if (wifi != null) {
                        multicastLock = wifi.createMulticastLock("r1dlna");
                        multicastLock.setReferenceCounted(false);
                        multicastLock.acquire();
                    }
                }
                player = new CastPlayer(ctx, new Runnable() {
                    @Override
                    public void run() {
                        Events e = events;
                        if (e != null) e.changed();
                    }
                });
                events = new Events(player);
                http = new DlnaHttpServer(player, events, Upnp.deviceXml(uuid(ctx), DEVICE_NAME));
                Thread ht = new Thread(http, "dlna-http");
                ht.setDaemon(true);
                ht.start();
                ssdp = new Ssdp(uuid(ctx), ip);
                ssdp.start();
                boundIp = ip;
                running = true;
                Log.i(TAG, "投屏已启动: http://" + ip.getHostAddress() + ":" + DlnaHttpServer.PORT + "/desc.xml");
            } catch (Throwable e) {
                lastError = String.valueOf(e);
                Log.w(TAG, "投屏启动失败: " + e);
                shutdownQuietly();
            }
        }
    }

    /** 关闭投屏接收端（幂等）。 */
    public static void stop() {
        synchronized (LOCK) {
            if (!running && http == null && ssdp == null) return;
            Log.i(TAG, "投屏关闭中");
            final Ssdp s = ssdp;
            if (s != null) {
                // byebye 要联网，不能占着调用方线程
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        s.close();
                    }
                }, "dlna-byebye");
                t.setDaemon(true);
                t.start();
            }
            shutdownQuietly();
        }
    }

    private static void shutdownQuietly() {
        if (http != null) {
            try {
                http.close();
            } catch (Throwable ignored) {
            }
            http = null;
        }
        if (events != null) {
            try {
                events.shutdown();
            } catch (Throwable ignored) {
            }
            events = null;
        }
        if (player != null) {
            try {
                player.shutdown();
            } catch (Throwable ignored) {
            }
            player = null;
        }
        if (multicastLock != null) {
            try {
                multicastLock.release();
            } catch (Throwable ignored) {
            }
            multicastLock = null;
        }
        ssdp = null;
        running = false;
    }

    /** 唤醒让路：暂停正在播放的投屏（没有在放则返回 false）。 */
    public static boolean pauseForVoice() {
        CastPlayer p = player;
        return p != null && p.pauseForVoice();
    }

    /** 一轮语音结束：恢复被唤醒让路停的投屏。 */
    public static boolean resumeAfterVoice() {
        CastPlayer p = player;
        return p != null && p.resumeAfterVoice();
    }

    /** 现在有没有在对外广播。 */
    public static boolean isRunning() {
        return running;
    }

    /** 给网页/日志用的一行状态（含最近一次播放失败原因，手机推了坏地址时能查）。 */
    public static String status() {
        if (running) {
            String pe = "";
            CastPlayer p = player;
            if (p != null) {
                String e = p.lastError();
                if (e != null && !e.isEmpty()) pe = ",\"playError\":\"" + esc(e) + "\"";
            }
            return "{\"on\":true,\"ip\":\"" + (boundIp == null ? "" : boundIp.getHostAddress())
                    + "\",\"port\":" + DlnaHttpServer.PORT + ",\"name\":\"" + DEVICE_NAME + "\"" + pe + "}";
        }
        return "{\"on\":false,\"error\":\"" + esc(lastError == null ? "" : lastError) + "\"}";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    /** 设备 UUID：第一次生成后固定，手机靠它区分「同一台设备」。 */
    private static String uuid(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("r1dlna", Context.MODE_PRIVATE);
        String id = sp.getString("uuid", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            sp.edit().putString("uuid", id).apply();
        }
        return id;
    }

    /** 取本机的站点内 IPv4。 */
    private static InetAddress findIp() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a;
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "扫描网卡失败: " + e);
        }
        return null;
    }
}
