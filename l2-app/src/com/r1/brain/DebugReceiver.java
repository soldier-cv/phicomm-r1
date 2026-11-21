package com.r1.brain;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 广播调试与服务拉起入口（给 adb 用，不需要打开网页）。
 *
 * <p>用法：
 * <pre>
 *   adb shell am broadcast -a com.r1.debug.SAY --es text "你好"
 *   adb shell am broadcast -a com.r1.debug.PING
 *   adb shell am broadcast -a com.r1.debug.CONFIG --es apiKey xxx --es model GLM-4.5-Flash
 *   adb shell am broadcast -a com.r1.debug.START
 * </pre>
 *
 * <p><b>安全边界</b>：这个 receiver 必须 {@code exported="true"} 才能被 adb（shell uid）投递，
 * 于是同机的其它应用也能发。所以只要用户在设置页设过访问令牌，
 * 改配置与让音箱说话这两类动作都必须带上 <b>同一个令牌</b>：
 * <pre>
 *   adb shell am broadcast -a com.r1.debug.SAY --es token &lt;令牌&gt; --es text "你好"
 * </pre>
 * 没设令牌时保持开放 —— 与 HTTP 接口「首次开放、设置后强制校验」的策略一致。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 13:52 星期五
 */
public class DebugReceiver extends BroadcastReceiver {

    private static final String TAG = Brain.TAG;

    public static final String ACTION_SAY = "com.r1.debug.SAY";
    public static final String ACTION_PING = "com.r1.debug.PING";
    public static final String ACTION_CONFIG = "com.r1.debug.CONFIG";
    public static final String ACTION_START = "com.r1.debug.START";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "broadcast: " + action);

        if (ACTION_START.equals(action)) {
            try {
                Intent svc = new Intent(context, MainService.class);
                context.startService(svc);
                Log.i(TAG, "已请求启动 MainService");
            } catch (Throwable t) {
                Log.e(TAG, "启动 MainService 失败: " + t);
            }
            return;
        }

        if (ACTION_PING.equals(action)) {
            MainService s = MainService.get();
            Log.i(TAG, "PING -> " + (s == null ? "服务未运行" : s.state()));
            return;
        }

        if (ACTION_CONFIG.equals(action)) {
            if (!authorized(context, intent, action)) return;
            Config.update(context, new Config.Mutator() {
                @Override
                public void apply(Config c) {
                    if (intent.hasExtra("apiKey")) c.apiKey = intent.getStringExtra("apiKey");
                    if (intent.hasExtra("baseUrl")) c.baseUrl = intent.getStringExtra("baseUrl");
                    if (intent.hasExtra("model")) c.model = intent.getStringExtra("model");
                    if (intent.hasExtra("maxTokens")) c.maxTokens = intent.getIntExtra("maxTokens", c.maxTokens);
                    if (intent.hasExtra("ttsUrl")) c.ttsUrl = intent.getStringExtra("ttsUrl");
                    if (intent.hasExtra("ttsKey")) c.ttsKey = intent.getStringExtra("ttsKey");
                    if (intent.hasExtra("ttsVoice")) c.ttsVoice = intent.getStringExtra("ttsVoice");
                    if (intent.hasExtra("autoSpeak")) c.autoSpeak = intent.getBooleanExtra("autoSpeak", c.autoSpeak);
                }
            });
            Log.i(TAG, "CONFIG 已保存 -> " + Config.load(context));
            return;
        }

        if (ACTION_SAY.equals(action)) {
            if (!authorized(context, intent, action)) return;
            MainService s = MainService.get();
            if (s == null) {
                Log.w(TAG, "服务尚未运行，先拉起服务");
                try {
                    context.startService(new Intent(context, MainService.class));
                } catch (Throwable t) {
                    Log.e(TAG, "拉起 MainService 失败: " + t);
                }
                return;
            }
            String text = intent.getStringExtra("text");
            if (text == null) text = "你好";
            s.say(text);
            return;
        }

        Log.w(TAG, "未知 action: " + action);
    }

    /**
     * 校验调试广播的令牌。
     *
     * <p>规则与 HTTP 接口一致：用户没设过访问令牌时放行；设过就必须带同一个令牌。
     *
     * @param context 上下文
     * @param intent  广播
     * @param action  动作名（仅用于日志）
     * @return true 表示允许执行
     */
    private static boolean authorized(Context context, Intent intent, String action) {
        Config c = Config.load(context);
        if (!c.tokenUserSet) return true;
        String given = intent.getStringExtra("token");
        if (given != null && given.equals(c.token)) return true;
        Log.w(TAG, "调试广播被拒绝（未带正确令牌）: " + action);
        return false;
    }
}
