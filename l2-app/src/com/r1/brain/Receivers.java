package com.r1.brain;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.r1.brain.dlna.DlnaRenderer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;

/**
 * 接收端开关的唯一实现：DLNA、AirPlay、蓝牙（含进入可发现/配对）。
 *
 * <p>这是<strong>开关</strong>，不是推流发送端。手机端的系统投屏 / AirPlay / 蓝牙音频
 * 仍然由系统或第三方 App 发起；音箱只负责把接收服务打开或关掉。
 * HTTP（控制台、小程序）、语音本地意图、大模型工具都走这里，避免三套实现分叉。
 *
 * @author xudong.hua,claude
 * @since 2026-11-11 17:20 星期三
 */
public final class Receivers {

    private static final String TAG = Brain.TAG;

    /** 打开蓝牙后进入可发现的默认秒数。 */
    public static final int DISCOVERABLE_SECONDS = 120;

    /**
     * 原厂 Shairport/RAOP 默认端口（{@code ShairportConfiguration} 里是 0x138a）。
     * 不是常见文档里的 7000。
     */
    public static final int AIRPLAY_PORT = 5002;
    /** 若将来固件改回标准端口，也探测一下，状态里单独给出。 */
    public static final int AIRPLAY_PORT_LEGACY = 7000;
    /** 原厂等待外部连接约 90 秒无客户端会自动退出，偏好仍开时按这个间隔重发 start。 */
    private static final long AIRPLAY_KEEP_MS = 20000L;

    private static final Object AIRPLAY_LOCK = new Object();
    /** 最近一次激活/关闭 AirPlay 的尝试记录（给状态接口看，不落盘）。 */
    private static JSONArray lastAirplayAttempts = new JSONArray();
    private static String lastAirplayError = "";
    private static String lastAirplayL1 = "";
    private static String lastBtError = "";
    private static Handler airplayKeepHandler;
    private static Runnable airplayKeepRunnable;
    private static volatile Context airplayKeepCtx;

    private Receivers() {
    }

    /** L1 反射结果回来时记下，下次 {@link #status} 会带上。 */
    public static void onL1AirplayResult(String detail, boolean ok) {
        lastAirplayL1 = detail == null ? "" : detail;
        Log.i(TAG, "L1 AirPlay 回执 ok=" + ok + " " + lastAirplayL1);
    }

    /**
     * 三项开关的完整状态。
     *
     * @param ctx 上下文
     * @return JSON（ok / dlna / airplay / bluetooth）
     */
    public static JSONObject status(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            Config c = Config.load(ctx);
            o.put("ok", true);
            o.put("dlna", dlnaStatus(c));
            o.put("airplay", airplayStatus(c));
            o.put("bluetooth", bluetoothStatus(c));
        } catch (Throwable t) {
            try {
                o.put("ok", false);
                o.put("error", String.valueOf(t));
            } catch (Throwable ignored) { }
        }
        return o;
    }

    /**
     * 按请求里出现的字段开关（未出现的保持不变）。
     *
     * <p>字段：{@code dlna}、{@code airplay}、{@code bluetooth}、{@code discoverable}，都是布尔。
     *
     * @param ctx 上下文
     * @param want 请求 JSON，可为 null
     * @return 应用后的完整状态
     */
    public static JSONObject apply(Context ctx, JSONObject want) {
        if (want != null) {
            if (want.has("dlna")) setDlna(ctx, want.optBoolean("dlna", false));
            if (want.has("airplay")) setAirPlay(ctx, want.optBoolean("airplay", false));
            if (want.has("bluetooth")) {
                Boolean disc = want.has("discoverable") ? Boolean.valueOf(want.optBoolean("discoverable")) : null;
                setBluetooth(ctx, want.optBoolean("bluetooth", false), disc);
            } else if (want.has("discoverable")) {
                setBluetooth(ctx, true, want.optBoolean("discoverable", false));
            }
        }
        return status(ctx);
    }

    /**
     * 语音/工具入口：改一项并返回播报文本。
     *
     * @param ctx    上下文
     * @param target dlna / airplay / bluetooth / discoverable
     * @param on     打开还是关闭
     * @return 播报文本
     */
    public static String applyVoice(Context ctx, String target, boolean on) {
        String t = target == null ? "" : target.toLowerCase();
        if ("dlna".equals(t) || "cast".equals(t)) {
            boolean ok = setDlna(ctx, on);
            if (on) return ok ? "投屏已打开。" : "投屏没打开。";
            return "投屏已关闭。";
        }
        if ("airplay".equals(t)) {
            boolean ok = setAirPlay(ctx, on);
            if (on) {
                return ok ? "隔空播放已打开。"
                        : ("隔空播放没能激活。" + (lastAirplayError.isEmpty() ? "" : lastAirplayError));
            }
            return ok ? "隔空播放已关闭。" : ("隔空播放关闭指令已发出，但端口还在听。"
                    + (lastAirplayError.isEmpty() ? "" : lastAirplayError));
        }
        if ("bluetooth".equals(t)) {
            boolean ok = setBluetooth(ctx, on, on ? Boolean.TRUE : Boolean.FALSE);
            if (on) {
                return ok ? "蓝牙已打开，正在等待配对。"
                        : ("蓝牙没打开。" + (lastBtError.isEmpty() ? "" : lastBtError));
            }
            return ok ? "蓝牙已关闭。" : ("蓝牙没关上。" + (lastBtError.isEmpty() ? "" : lastBtError));
        }
        if ("discoverable".equals(t) || "pair".equals(t) || "pairing".equals(t)) {
            boolean ok = setBluetooth(ctx, true, on);
            if (on) {
                return ok ? "已进入蓝牙配对，两分钟内可被发现。"
                        : ("没能进入配对模式。" + (lastBtError.isEmpty() ? "" : lastBtError));
            }
            return ok ? "已退出配对模式。" : "退出配对没成功。";
        }
        return "不会这项开关。";
    }

    /** 三项开关的口头状态。 */
    public static String statusSpeech(Context ctx) {
        try {
            JSONObject s = status(ctx);
            JSONObject d = s.optJSONObject("dlna");
            JSONObject a = s.optJSONObject("airplay");
            JSONObject b = s.optJSONObject("bluetooth");
            StringBuilder sb = new StringBuilder();
            sb.append(d != null && d.optBoolean("on") ? "投屏开着" : "投屏关着");
            sb.append("，");
            sb.append(a != null && a.optBoolean("listening") ? "隔空播放端口在听" : "隔空播放没在听");
            if (a != null && a.optBoolean("wanted") && !a.optBoolean("listening")) {
                sb.append("（想开但没激活）");
            }
            sb.append("，");
            if (b == null || !b.optBoolean("supported")) {
                sb.append("这台设备没有蓝牙。");
            } else {
                sb.append(b.optBoolean("on") ? "蓝牙开着" : "蓝牙关着");
                if (b.optBoolean("discoverable")) sb.append("，正在可发现");
                sb.append("。");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "状态没查到。";
        }
    }

    /**
     * 打开或关闭自建 DLNA 接收端，并记住偏好。
     *
     * @return 打开时是否已经在跑；关闭时恒为 true
     */
    public static boolean setDlna(Context ctx, final boolean on) {
        Config.update(ctx, new Config.Mutator() {
            @Override
            public void apply(Config c) {
                c.castEnabled = on;
            }
        });
        if (on) {
            DlnaRenderer.start(ctx);
            for (int i = 0; i < 10 && !DlnaRenderer.isRunning(); i++) {
                sleepQuiet(300);
            }
            return DlnaRenderer.isRunning();
        }
        DlnaRenderer.stop();
        return true;
    }

    /**
     * 尝试打开或关闭原厂 AirPlay 接收端。
     *
     * <p>原厂 {@code AirPlayerService} 是 {@code exported=false}，只在播放器
     * 「等待外部连接」时 {@code bootAllExternalConnectPlayer()} → {@code AirPortPlayer.startServer()}。
     * L1 发原厂 {@code msgcenter} 消息 type=0x40。成功与否以 TCP 5002 是否在听为准。
     *
     * @return 打开时 5002（或旧端口 7000）已在听；关闭时已不在听
     */
    public static boolean setAirPlay(Context ctx, final boolean on) {
        Config.update(ctx, new Config.Mutator() {
            @Override
            public void apply(Config c) {
                c.airplayEnabled = on;
            }
        });
        boolean ok = on ? activateAirPlay(ctx) : deactivateAirPlay(ctx);
        // 原厂 start/stopWaiting 会改蓝牙扫描模式：打开时进可发现，关闭后变成 SCAN_MODE_NONE。
        restoreBluetoothAfterAirPlay(ctx);
        return ok;
    }

    /** 按已保存的蓝牙偏好把扫描模式拉回「开着但不可发现」，避免 AirPlay 开关把适配器弄成无法连接。 */
    private static void restoreBluetoothAfterAirPlay(Context ctx) {
        try {
            Config c = Config.load(ctx);
            if (c.bluetoothEnabled) {
                setBluetooth(ctx, Boolean.TRUE, Boolean.FALSE);
            }
        } catch (Throwable t) {
            Log.w(TAG, "AirPlay 后恢复蓝牙偏好失败: " + t);
        }
    }

    /**
     * 开关蓝牙适配器，可选进入/退出可发现。
     *
     * @param enable       适配器开关；null 表示不改
     * @param discoverable 可发现；null 表示不改。打开蓝牙且未指定时，默认进入可发现。
     * @return 目标状态是否达到
     */
    public static boolean setBluetooth(Context ctx, final Boolean enable, final Boolean discoverable) {
        lastBtError = "";
        BluetoothAdapter bt = adapter();
        if (bt == null) {
            lastBtError = "该设备不支持蓝牙。";
            return false;
        }
        boolean wantOn = enable == null ? bt.isEnabled() : enable.booleanValue();
        // 只要显式打开蓝牙（无论是否单独请求配对），都记住偏好，开机可恢复
        if (enable != null) {
            Config.update(ctx, new Config.Mutator() {
                @Override
                public void apply(Config c) {
                    c.bluetoothEnabled = enable.booleanValue();
                }
            });
        }
        try {
            if (!wantOn) {
                setScanMode(bt, BluetoothAdapter.SCAN_MODE_NONE, 0);
                boolean ok = bt.disable();
                if (!ok && bt.isEnabled()) lastBtError = "系统拒绝关闭蓝牙。";
                return !bt.isEnabled() || ok;
            }
            if (!bt.isEnabled()) {
                if (!bt.enable()) {
                    lastBtError = "系统拒绝打开蓝牙。";
                    return false;
                }
                for (int i = 0; i < 16 && !bt.isEnabled(); i++) sleepQuiet(500);
                if (!bt.isEnabled()) {
                    lastBtError = "蓝牙打开超时。";
                    return false;
                }
            }
            boolean wantDisc = discoverable == null ? true : discoverable.booleanValue();
            if (wantDisc) {
                if (!setScanMode(bt, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, DISCOVERABLE_SECONDS)) {
                    lastBtError = "适配器已打开，但没能进入可发现（setScanMode 被拒绝）。请用手机再试一次，或看系统是否弹出了确认。";
                    return bt.isEnabled();
                }
            } else if (discoverable != null) {
                setScanMode(bt, BluetoothAdapter.SCAN_MODE_CONNECTABLE, 0);
            }
            return true;
        } catch (Throwable t) {
            lastBtError = "蓝牙操作失败: " + t;
            Log.w(TAG, lastBtError);
            return false;
        }
    }

    // ---------------------------------------------------------------- status fragments

    private static JSONObject dlnaStatus(Config c) throws Exception {
        JSONObject d = new JSONObject(DlnaRenderer.status());
        d.put("wanted", c.castEnabled);
        d.put("listeningSsdp", portListening(1900, true));
        d.put("listeningHttp", portListening(49494, false));
        return d;
    }

    private static JSONObject airplayStatus(Config c) throws Exception {
        boolean listening = portListening(AIRPLAY_PORT, false);
        boolean listeningLegacy = portListening(AIRPLAY_PORT_LEGACY, false);
        JSONObject a = new JSONObject();
        a.put("on", listening || listeningLegacy);
        a.put("wanted", c.airplayEnabled);
        a.put("listening", listening || listeningLegacy);
        a.put("port", AIRPLAY_PORT);
        a.put("portLegacy", AIRPLAY_PORT_LEGACY);
        a.put("listening5002", listening);
        a.put("listening7000", listeningLegacy);
        a.put("error", lastAirplayError == null ? "" : lastAirplayError);
        a.put("l1", lastAirplayL1 == null ? "" : lastAirplayL1);
        a.put("attempts", lastAirplayAttempts);
        a.put("note", "原厂入口是 msgcenter type=0x40（与「进入蓝牙状态」同一条），"
                + "Shairport 听 TCP 5002 并广播 _raop._tcp。打开后约 90 秒无客户端会自动退出，"
                + "L2 会按偏好续期。等待期间原厂灯环会切到效果 0xcc。"
                + "同时会拉起原厂 DLNA 与蓝牙 A2DP 接收，不改变我们自建的 DLNA 开关。");
        return a;
    }

    private static JSONObject bluetoothStatus(Config c) throws Exception {
        JSONObject b = new JSONObject();
        BluetoothAdapter bt = adapter();
        b.put("supported", bt != null);
        boolean on = false;
        String name = "";
        int scan = 0;
        boolean disc = false;
        if (bt != null) {
            try {
                on = bt.isEnabled();
                if (on) {
                    name = String.valueOf(bt.getName());
                    scan = bt.getScanMode();
                    disc = scan == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
                }
            } catch (Throwable ignored) { }
        }
        b.put("on", on);
        b.put("wanted", c.bluetoothEnabled);
        b.put("name", name);
        b.put("scanMode", scan);
        b.put("discoverable", disc);
        b.put("error", lastBtError == null ? "" : lastBtError);
        return b;
    }

    // ---------------------------------------------------------------- AirPlay activation

    private static boolean airplayListening() {
        return portListening(AIRPLAY_PORT, false) || portListening(AIRPLAY_PORT_LEGACY, false);
    }

    private static boolean activateAirPlay(Context ctx) {
        synchronized (AIRPLAY_LOCK) {
            JSONArray attempts = new JSONArray();
            lastAirplayError = "";
            if (airplayListening()) {
                putAttempt(attempts, "already_listening", true,
                        "TCP " + AIRPLAY_PORT + " 已经在听，无需再激活");
                lastAirplayAttempts = attempts;
                scheduleAirplayKeepAlive(ctx);
                return true;
            }

            // 只走原厂官方入口：msgcenter 0x40/1。不要再发 PLAYER/stop，也不要 am startservice
            // （AirPlayerService 是 exported=false；PLAYER/stop 会把刚进入的等待态关掉）。
            boolean sent = L1Bridge.controlAirPlay(ctx, true);
            boolean up = waitForPort(true, 8, 300);
            putAttempt(attempts, "l1_msg_0x40_start",
                    sent && up,
                    "L1 CMD_AIRPLAY → msgcenter type=0x40 sub=1 startWaitingExternalConnect，探测 TCP "
                            + AIRPLAY_PORT);
            lastAirplayAttempts = attempts;
            if (up) {
                scheduleAirplayKeepAlive(ctx);
                return true;
            }
            if (sent) {
                // 指令已发出，端口可能还在起来；按偏好续期，状态保持诚实。
                scheduleAirplayKeepAlive(ctx);
                lastAirplayError = "已向原厂播放器发出 startWaiting 指令，TCP "
                        + AIRPLAY_PORT + " 尚未监听。请稍后刷新；若一直没有，看 l1 回执。";
            } else {
                lastAirplayError = "L1 指令没发出（桥可能不在线）。请确认已安装含 msgcenter 0x40 的新 L1。";
            }
            Log.w(TAG, lastAirplayError);
            return false;
        }
    }

    private static boolean deactivateAirPlay(Context ctx) {
        synchronized (AIRPLAY_LOCK) {
            stopAirplayKeepAlive();
            JSONArray attempts = new JSONArray();
            lastAirplayError = "";
            if (!airplayListening()) {
                putAttempt(attempts, "already_off", true,
                        "TCP " + AIRPLAY_PORT + " 本来就没在听");
                lastAirplayAttempts = attempts;
                return true;
            }
            boolean sent = L1Bridge.controlAirPlay(ctx, false);
            boolean down = waitForPort(false, 8, 300);
            putAttempt(attempts, "l1_msg_0x40_stop",
                    sent && down,
                    "L1 CMD_AIRPLAY → msgcenter type=0x40 sub=2 stopWaitingExternalConnect");
            lastAirplayAttempts = attempts;
            if (down) return true;
            lastAirplayError = sent
                    ? ("已发出关闭指令，但 TCP " + AIRPLAY_PORT + " 还在听。")
                    : "L1 关闭指令没发出。";
            Log.w(TAG, lastAirplayError);
            return false;
        }
    }

    /** 最多约 2.4 秒，避免 HTTP / 语音线程被旧逻辑拖过 20 秒。 */
    private static boolean waitForPort(boolean wantUp, int rounds, long stepMs) {
        for (int i = 0; i < rounds; i++) {
            if (wantUp && airplayListening()) return true;
            if (!wantUp && !airplayListening()) return true;
            sleepQuiet(stepMs);
        }
        return wantUp ? airplayListening() : !airplayListening();
    }

    /** 原厂等待态约 90 秒超时；偏好仍为打开且端口掉了就重发 start，不在播放中乱发 restart。 */
    private static void scheduleAirplayKeepAlive(Context ctx) {
        if (ctx == null) return;
        airplayKeepCtx = ctx.getApplicationContext();
        if (airplayKeepHandler == null) {
            airplayKeepHandler = new Handler(Looper.getMainLooper());
        }
        if (airplayKeepRunnable == null) {
            airplayKeepRunnable = new Runnable() {
                @Override
                public void run() {
                    final Context app = airplayKeepCtx;
                    if (app == null) return;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean again = false;
                            try {
                                Config c = Config.load(app);
                                if (!c.airplayEnabled) return;
                                again = true;
                                if (!airplayListening()) {
                                    Log.i(TAG, "AirPlay 保活：TCP " + AIRPLAY_PORT + " 未在听，重发 startWaiting");
                                    L1Bridge.controlAirPlay(app, true);
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "AirPlay 保活失败: " + t);
                                again = true;
                            } finally {
                                if (again && airplayKeepHandler != null && airplayKeepRunnable != null) {
                                    airplayKeepHandler.postDelayed(airplayKeepRunnable, AIRPLAY_KEEP_MS);
                                }
                            }
                        }
                    }, "r1-airplay-keep").start();
                }
            };
        }
        airplayKeepHandler.removeCallbacks(airplayKeepRunnable);
        airplayKeepHandler.postDelayed(airplayKeepRunnable, AIRPLAY_KEEP_MS);
    }

    private static void stopAirplayKeepAlive() {
        if (airplayKeepHandler != null && airplayKeepRunnable != null) {
            airplayKeepHandler.removeCallbacks(airplayKeepRunnable);
        }
    }

    private static void putAttempt(JSONArray arr, String name, boolean ok, String detail) {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("ok", ok);
            o.put("detail", detail == null ? "" : detail);
            arr.put(o);
        } catch (Throwable ignored) { }
        Log.i(TAG, "AirPlay 尝试 " + name + " ok=" + ok + " " + detail);
    }

    // ---------------------------------------------------------------- Bluetooth helpers

    private static BluetoothAdapter adapter() {
        try {
            return BluetoothAdapter.getDefaultAdapter();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 无弹窗设置扫描模式（隐藏 API）。Android 5.1 上 {@code setScanMode(int,int)} 需要 BLUETOOTH_ADMIN。
     *
     * @return 调用成功（并不保证系统最终给了可发现）
     */
    private static boolean setScanMode(BluetoothAdapter bt, int mode, int seconds) {
        try {
            Method m = BluetoothAdapter.class.getMethod("setScanMode", int.class, int.class);
            Object r = m.invoke(bt, mode, seconds);
            if (r instanceof Boolean) return ((Boolean) r).booleanValue();
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            Log.w(TAG, "setScanMode(int,int) 失败: " + t);
        }
        try {
            Method m = BluetoothAdapter.class.getMethod("setScanMode", int.class);
            Object r = m.invoke(bt, mode);
            if (r instanceof Boolean) return ((Boolean) r).booleanValue();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "setScanMode(int) 失败: " + t);
            return false;
        }
    }

    // ---------------------------------------------------------------- port probe（与 HttpServer 同一口径：读 /proc/net）

    /** 是否在监听某个端口（读 /proc/net，不需要 root）。IPv6 套接字在 tcp6/udp6。 */
    public static boolean portListening(int port, boolean udp) {
        String hex = Integer.toHexString(port).toUpperCase();
        while (hex.length() < 4) hex = "0" + hex;
        String[] files = udp
                ? new String[] { "/proc/net/udp", "/proc/net/udp6" }
                : new String[] { "/proc/net/tcp", "/proc/net/tcp6" };
        for (int f = 0; f < files.length; f++) {
            if (portFileHas(files[f], hex)) return true;
        }
        return false;
    }

    private static boolean portFileHas(String file, String hex) {
        java.io.BufferedReader br = null;
        try {
            br = new java.io.BufferedReader(new java.io.FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                // 行形如 "  0: 00000000:15B3 ..." 或 tcp6 的 32 位十六进制地址。
                // 第一个冒号在槽号后面，后面紧跟空格；要取 local_address 的端口，不能用第一处空格截断。
                int i = line.indexOf(':');
                if (i < 0) continue;
                String rest = line.substring(i + 1).trim();
                int sp = rest.indexOf(' ');
                if (sp < 0) continue;
                String local = rest.substring(0, sp).trim();
                int colon = local.lastIndexOf(':');
                if (colon < 0) continue;
                if (local.substring(colon + 1).equalsIgnoreCase(hex)) return true;
            }
        } catch (Throwable ignored) {
        } finally {
            if (br != null) {
                try { br.close(); } catch (Throwable ignored) { }
            }
        }
        return false;
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
