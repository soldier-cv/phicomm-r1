package com.r1.brain;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 接收 L1（原厂包内的桥）发来的事件：语音识别结果、心跳、灯环回执。
 *
 * <p>L1 在 {@code NLUDispatcher.onASRResult*} 里把识别文本用
 * {@link BridgeProtocol#ACTION_EVENT} 广播发出来，这里收下后交给
 * {@link MainService#say(String)} 走正常的对话链路。
 *
 * <p>为什么用动态注册：L1 侧的 AndroidManifest 在签名 zip 里不能改，
 * 但本类注册在 L2 自己这一侧，随 MainService 生命周期注册/注销即可。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-30 22:10 星期五
 */
public class BridgeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String type = intent.getStringExtra(BridgeProtocol.EXTRA_TYPE);

        // 任何来自 L1 的消息都算一次心跳，供 /status 判断 L1 是否在线
        MainService svc = MainService.get();
        if (svc != null) {
            svc.noteL1(intent.getIntExtra(BridgeProtocol.EXTRA_VER, 0),
                    BridgeProtocol.TYPE_L1_HELLO.equals(type));
        }

        if (BridgeProtocol.TYPE_TTS_DONE.equals(type)) {
            if (svc != null) {
                svc.onNativeTtsDone(intent.getIntExtra(BridgeProtocol.EXTRA_ID, 0),
                        intent.getBooleanExtra(BridgeProtocol.EXTRA_OK, true));
            }
            return;
        }
        if (BridgeProtocol.TYPE_LIGHT_RESULT.equals(type)) {
            L1Bridge.onLightResult(intent.getStringExtra(BridgeProtocol.EXTRA_MODE),
                    intent.getBooleanExtra(BridgeProtocol.EXTRA_OK, false));
            return;
        }
        if (BridgeProtocol.TYPE_AIRPLAY_RESULT.equals(type)) {
            Receivers.onL1AirplayResult(intent.getStringExtra(BridgeProtocol.EXTRA_TEXT),
                    intent.getBooleanExtra(BridgeProtocol.EXTRA_OK, false));
            return;
        }
        if (BridgeProtocol.TYPE_WAKE.equals(type)) {
            // 唤醒词刚命中：立刻把音乐/TTS 停下来，给接下来的录音让出音频通路。
            if (svc != null) svc.onWake();
            return;
        }
        if (BridgeProtocol.TYPE_ASR_CANCEL.equals(type)) {
            // 这一轮不成立（原厂取消 / 太短 / TTS 尾音），必须主动释放流式会话，
            // 否则要等 30 秒超时才恢复，期间再唤醒没人接。
            String reason = intent.getStringExtra(BridgeProtocol.EXTRA_REASON);
            if (svc != null) svc.onVoiceCancel(reason);
            return;
        }
        if (BridgeProtocol.TYPE_ASR_AUDIO.equals(type)) {
            String path = intent.getStringExtra(BridgeProtocol.EXTRA_PATH);
            Log.i(Brain.TAG, "=== 收到录音文件: " + path);
            if (svc != null && path != null) svc.onVoiceAudio(path);
            return;
        }
        if (BridgeProtocol.TYPE_ASR_START.equals(type)) {
            String path = intent.getStringExtra(BridgeProtocol.EXTRA_PATH);
            if (svc != null && path != null) svc.onVoiceStart(path);
            return;
        }
        // 用自建 ASR 时，原厂的文本结果与本地硬猜都不采信，否则两套结果会打架。
        // 见 docs/24-self-hosted-asr.md
        if ((BridgeProtocol.TYPE_ASR_RESULT.equals(type) || BridgeProtocol.TYPE_ASR_WEAK.equals(type))
                && usesSelfAsr(context)) {
            return;
        }
        if (BridgeProtocol.TYPE_ASR_WEAK.equals(type)) {
            // 原厂本地语法没听清，硬套了一个命令；不要执行，直接请用户再说一次
            Log.i(Brain.TAG, "=== ASR 可信度太低，请重说: " + intent.getStringExtra(BridgeProtocol.EXTRA_TEXT));
            if (svc != null) svc.onHeardNothing();
            return;
        }
        if (!BridgeProtocol.TYPE_ASR_RESULT.equals(type)) {
            // L1_HELLO 与未来新增的类型都只算心跳，不再刷日志
            return;
        }

        String text = intent.getStringExtra(BridgeProtocol.EXTRA_TEXT);
        if (text == null || text.trim().isEmpty()) {
            Log.w(Brain.TAG, "桥消息里没有 text，忽略");
            return;
        }
        if (svc == null) {
            Log.w(Brain.TAG, "MainService 未就绪，丢弃: " + text);
            return;
        }
        // 注意：这个分支只在 asrEngine=native 时才会走到（自建 ASR 时上面直接 return）
        Log.i(Brain.TAG, "=== L1 ASR -> " + text);
        svc.say(text, true);
    }

    /**
     * 语音识别是否已切到自建 ASR（设置里 {@code asrEngine=self} 且填了地址）。
     *
     * @param context 应用上下文
     * @return true 表示原厂的文本结果一律不采信
     * @author xudong.hua,deepseek
     * @since 2026-11-01 19:20 星期日
     */
    private static boolean usesSelfAsr(Context context) {
        try {
            Config c = Config.load(context);
            return "self".equals(c.asrEngine) && c.asrUrl != null && !c.asrUrl.trim().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }
}
