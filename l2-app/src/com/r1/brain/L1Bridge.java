package com.r1.brain;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * L2 → L1 的指令通道：灯环、原厂 TTS、续听、判停开关、压制原厂播报、引擎软恢复。
 *
 * <p>为什么必须经 L1：灯环控制在原厂包的 {@code PhicommLightController} 里，
 * 它通过 IPC 交给下层守护进程；普通应用既拿不到那些类，也没有对应权限。
 * L2 只能发一条定向广播，由 L1 里的桥代劳。
 *
 * <p>L1 会用同样的 action 回一条 {@code LIGHT_RESULT}，这里记录最后一次结果，
 * 供网页显示「到底成没成」，而不是点了没反应还不知道。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 14:25 星期六
 */
public final class L1Bridge {

    private static final String TAG = Brain.TAG;

    /** 最近一次灯环指令与结果。 */
    private static volatile String lastMode = "";
    private static volatile boolean lastOk = false;
    private static volatile long lastTs = 0;

    private L1Bridge() {
    }

    /**
     * 给 L1 发一条灯环指令。
     *
     * @param ctx    上下文
     * @param effect 效果
     * @return 是否成功发出（不代表 L1 执行成功，结果看回执）
     */
    public static boolean sendLight(Context ctx, LightEffect effect) {
        if (ctx == null) return false;
        LightEffect e = effect == null ? LightEffect.OFF : effect;
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_LIGHT);
            it.putExtra(BridgeProtocol.EXTRA_MODE, e.value());
            ctx.sendBroadcast(it);
            Log.i(TAG, "灯环指令 -> L1: " + e.value());
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "灯环指令发送失败: " + t);
            return false;
        }
    }

    /** 记录最近一次「压制原厂播报」指令的时间，定期重发（L1 重启后会丢状态）。 */
    private static volatile long lastMuteAt = 0;

    /**
     * 告诉 L1 是否压制原厂的播报（L2 配好 TTS 后打开，避免双播）。
     *
     * @param ctx 上下文
     * @param on  true 表示让 L1 压掉原厂 TTS
     * @return 是否成功发出
     */
    public static boolean sendMuteOriginal(Context ctx, boolean on) {
        if (ctx == null) return false;
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_MUTE);
            it.putExtra("on", on);
            ctx.sendBroadcast(it);
            lastMuteAt = System.currentTimeMillis();
            // 每 2 分钟都会重发一次心跳式的开关；只在值变化时记日志，别把 logcat 刷满
            if (on != lastMuteOn) {
                Log.i(TAG, "压制原厂播报 -> L1: " + on);
                lastMuteOn = on;
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "压制指令发送失败: " + t);
            return false;
        }
    }

    /** 上一次下发的压制开关，用于抑制重复日志。 */
    private static volatile boolean lastMuteOn = false;

    /**
     * 超过 2 分钟没发就重发一次（L1 若重启会忘掉这个开关）。
     *
     * @param ctx 上下文
     * @param on  true 表示压制
     * @return true 表示这次确实重发了（调用方可顺带重发其它开关）
     */
    public static boolean resendMuteIfStale(Context ctx, boolean on) {
        if (System.currentTimeMillis() - lastMuteAt > 120000) {
            return sendMuteOriginal(ctx, on);
        }
        return false;
    }

    /**
     * 让 L1 续听一句（连续对话用）。
     *
     * <p>v3 起 L1 不再调 {@code enterASR}（实测录到静音），而是从唤醒态的麦克风流里直接录、自己判停；
     * 6 秒没人说话会回 {@code ASR_CANCEL(follow-silence)}。
     *
     * @param ctx 上下文
     * @return 是否成功发出
     */
    public static boolean sendListen(Context ctx) {
        if (ctx == null) return false;
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_LISTEN);
            ctx.sendBroadcast(it);
            Log.i(TAG, "继续听 -> L1");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "继续听指令发送失败: " + t);
            return false;
        }
    }

    /**
     * 让 L1 用原厂 TTS 播一句话（选了原厂语音 / HTTP TTS 失败时的兜底）。
     *
     * @param ctx        上下文
     * @param text       要播的文本
     * @param thenListen true 表示念完由 L1 自己续听（只给不认识 {@code id} 的旧 L1 用；新 L1 靠 TTS_DONE）
     * @param id         播报序号，L1 v3 念完会在 {@code TTS_DONE} 里带回；0 表示不需要回执
     * @return 是否成功发出
     */
    public static boolean speak(Context ctx, String text, boolean thenListen, int id) {
        if (ctx == null || text == null || text.trim().isEmpty()) return false;
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_SPEAK);
            it.putExtra(BridgeProtocol.EXTRA_TEXT, text);
            it.putExtra(BridgeProtocol.EXTRA_LISTEN, thenListen);
            it.putExtra(BridgeProtocol.EXTRA_ID, id);
            ctx.sendBroadcast(it);
            Log.i(TAG, "原厂 TTS 播报: " + (text.length() > 30 ? text.substring(0, 30) + "…" : text));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "原厂 TTS 指令发送失败: " + t);
            return false;
        }
    }

    /**
     * 告诉 L1 唤醒轮要不要自己判停（v3 起；旧 L1 收到会忽略）。
     *
     * @param ctx 上下文
     * @param on  true 表示说完静音 1.2 秒就收尾，不等原厂 VAD
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    public static void sendEndpoint(Context ctx, boolean on) {
        if (ctx == null) return;
        // 每 2 分钟心跳都会重发一次；只在值变化时记日志，避免把 logcat 刷满
        if (on != sLastEndpointSent) {
            Log.i(TAG, "提前判停 -> L1: " + on);
            sLastEndpointSent = on;
        }
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_ENDPOINT);
            it.putExtra("on", on);
            ctx.sendBroadcast(it);
        } catch (Throwable t) {
            Log.w(TAG, "判停开关发送失败: " + t);
        }
    }

    /** 上一次下发的判停开关，用于抑制重复日志。 */
    private static volatile boolean sLastEndpointSent = false;

    /** 立刻停掉原厂 TTS（语音“别说了”）。 */
    public static boolean stopSpeak(Context ctx) {
        if (ctx == null) return false;
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_STOP);
            ctx.sendBroadcast(it);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "停止原厂 TTS 失败: " + t);
            return false;
        }
    }

    /**
     * 请求 L1 重新进入唤醒模式。
     *
     * <p>用于原厂识别引擎停摆后的软恢复：实测引擎异常后会停在 {@code engine_stop}
     * 不再 {@code engine_start}，设备彻底听不见，只能重启。这里通过 L1 的反射通道
     * 调一次 {@code enterWakeup}，比重启进程温和得多。
     *
     * @param ctx 上下文
     * @return 是否成功发出
     */
    public static boolean restartWake(Context ctx) {
        if (ctx == null) return false;
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_ENGINE);
            it.putExtra(BridgeProtocol.EXTRA_METHOD, "enterWakeup");
            ctx.sendBroadcast(it);
            Log.i(TAG, "请求 L1 重进唤醒模式");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "重进唤醒模式失败: " + t);
            return false;
        }
    }

    /**
     * 让 L1 恢复被唤醒暂停的原厂播放（投屏/蓝牙）。
     *
     * <p>唤醒时 L1 会绑定原厂 {@code MusicService} 暂停它；一轮结束后由这里恢复。
     *
     * @param ctx 上下文
     */
    public static void resumeExternalMedia(Context ctx) {
        if (ctx == null) return;
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_MEDIA);
            it.putExtra("action", "resume");
            ctx.sendBroadcast(it);
        } catch (Throwable t) {
            Log.w(TAG, "恢复外部播放指令失败: " + t);
        }
    }

    /**
     * 让 L1 尝试启动投屏接收端（DLNA/AirPlay）。
     *
     * <p>原厂播放器只在「播放停止 → 等待投屏」的状态迁移里 {@code startServer()}，
     * 开机不会走到，所以局域网里搜不到设备。L1 会把原厂音乐播放器 {@code stop()} 一次，
     * 促成那次迁移；能不能成由 L2 再查一次端口确认。
     *
     * @param ctx 上下文
     * @return 是否成功发出
     */
    public static boolean startCast(Context ctx) {
        if (ctx == null) return false;
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_CAST);
            ctx.sendBroadcast(it);
            Log.i(TAG, "启动投屏 -> L1");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "启动投屏指令发送失败: " + t);
            return false;
        }
    }

    /**
     * 让 L1 通过原厂 {@code msgcenter} 消息打开或关闭 AirPlay 接收端。
     *
     * <p>L1 发 type=0x40、sub=1/2（与 {@code PhicommXController.open/closeBlueToothStatus} 相同），
     * 并把结果通过 {@link BridgeProtocol#TYPE_AIRPLAY_RESULT} 回执回来。
     * 成不成以 L2 再探 TCP 5002 为准。
     *
     * @param ctx 上下文
     * @param on  true=尝试激活，false=尝试关闭
     * @return 是否成功发出指令
     */
    public static boolean controlAirPlay(Context ctx, boolean on) {
        if (ctx == null) return false;
        try {
            Intent it = new Intent(BridgeProtocol.ACTION_CMD);
            it.setPackage(BridgeProtocol.PACKAGE_L1);
            it.putExtra("cmd", BridgeProtocol.CMD_AIRPLAY);
            it.putExtra("on", on);
            ctx.sendBroadcast(it);
            Log.i(TAG, "AirPlay " + (on ? "打开" : "关闭") + " -> L1");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "AirPlay 指令发送失败: " + t);
            return false;
        }
    }

    /** 记录 L1 回来的灯环回执。 */
    public static void onLightResult(String mode, boolean ok) {
        lastMode = mode == null ? "" : mode;
        lastOk = ok;
        lastTs = System.currentTimeMillis();
        Log.i(TAG, "灯环回执: " + lastMode + " ok=" + ok);
    }

    /** 最近一次灯环状态的 JSON 片段。 */
    public static String stateJson() {
        return "{\"mode\":\"" + lastMode + "\",\"ok\":" + lastOk + ",\"ts\":" + lastTs + "}";
    }
}
