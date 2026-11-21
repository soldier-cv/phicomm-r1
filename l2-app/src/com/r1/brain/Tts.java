package com.r1.brain;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;

/**
 * 系统原生 TTS 引擎封装（作为 HTTP TTS 未配置时的兜底通道）。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 13:52 星期五
 */
public class Tts implements TextToSpeech.OnInitListener {

    private static final String TAG = Brain.TAG;

    private TextToSpeech tts;
    private volatile boolean ready = false;
    private volatile boolean failed = false;
    private volatile String failReason = "";

    public void init(Context ctx) {
        try {
            tts = new TextToSpeech(ctx.getApplicationContext(), this);
        } catch (Throwable t) {
            failed = true;
            failReason = t.toString();
            Log.e(TAG, "TTS 创建失败: " + failReason);
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true;
            failReason = "onInit status=" + status;
            Log.e(TAG, "TTS 初始化失败: " + failReason);
            return;
        }
        try {
            int r = tts.setLanguage(Locale.CHINA);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS 不支持中文，setLanguage 返回 " + r);
            }
            int engines = 0;
            try {
                engines = tts.getEngines().size();
            } catch (Throwable ignored) {
            }
            ready = true;
            Log.i(TAG, "TTS 可用，引擎数=" + engines + " 语言=" + Locale.getDefault());
        } catch (Throwable t) {
            failed = true;
            failReason = t.toString();
            Log.e(TAG, "TTS 配置失败: " + failReason);
        }
    }

    public boolean isReady() {
        return ready;
    }

    public String status() {
        if (ready) return "ready";
        if (failed) return "failed(" + failReason + ")";
        return "initing";
    }

    public boolean speak(String text) {
        if (!ready) {
            Log.w(TAG, "TTS 不可用（" + status() + "），跳过播报: " + text);
            return false;
        }
        try {
            HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "r1brain");
            int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            Log.i(TAG, "TTS speak 返回 " + r + ": " + text);
            return r == TextToSpeech.SUCCESS;
        } catch (Throwable t) {
            Log.e(TAG, "TTS speak 异常: " + t);
            return false;
        }
    }

    public void shutdown() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Throwable ignored) {
        }
    }
}
