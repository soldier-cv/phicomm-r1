package com.r1.brain;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * 定时闹钟调度器（严格基于北京时间 GMT+08:00）。
 * 每 15 秒检查一次控制台配置中已启用的闹钟，到点自动通过音箱喇叭语音播报闹钟备注。
 *
 * <p>同一分钟内多个闹钟会合并成一条播报；每个闹钟每分钟只触发一次（用 id:time 去重）。
 * 去重状态会落盘（独立 SP），进程被系统回收后同一分钟内重启也不会重复播。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 13:52 星期五
 */
public class AlarmScheduler {

    private static final String TAG = Brain.TAG;
    private static final long TICK_MS = 15000L;
    private static final TimeZone CN_TZ = TimeZone.getTimeZone("GMT+08:00");
    /** 去重状态落盘用的独立 SP，避免污染主配置。 */
    private static final String SP_STATE = "r1alarm_state";

    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> firedThisMinute = new HashSet<String>();
    private String lastMinute = "";
    private boolean running;
    /** 时钟未同步的告警只打一次，避免每 15 秒刷一条。 */
    private boolean warnedClock;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            check();
            if (running) handler.postDelayed(this, TICK_MS);
        }
    };

    public AlarmScheduler(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void start() {
        if (running) return;
        running = true;
        handler.postDelayed(tick, TICK_MS);
        Log.i(TAG, "AlarmScheduler: started, tick=" + TICK_MS + "ms");
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
        Log.i(TAG, "AlarmScheduler: stopped");
    }

    /** 时钟是否可信用：这台机器断电后 RTC 从 2018-01-21 起跳，NTP 同步前不可信。 */
    private static boolean clockSane() {
        return new Date().getTime() > SANE_EPOCH_MS;
    }

    /** 2024-01-01 00:00 UTC；早于它的系统时钟一律当作「还没同步」。 */
    private static final long SANE_EPOCH_MS = 1704067200000L;

    /**
     * 时间跳变后清掉去重状态。
     *
     * <p>NTP 校准会从 2018 直接跳到 2026；如果不清理，`lastMinute` 还停在那一年，
     * 或者「这一分钟已触发」集合里留着旧键，可能同一条闹钟重复播。
     */
    public void onTimeChanged() {
        lastMinute = "";
        firedThisMinute.clear();
        clearSavedState();
        Log.i(TAG, "AlarmScheduler: 检测到时间跳变，已重置去重状态");
    }

    private void check() {
        try {
            // 断电重启后 RTC 从 2018 起跳，NTP 同步前"现在几点"是假的：
            // 这时候按 HH:mm 匹配会误触发（时钟从 00:01 跳到 07:56 的那一刻正好撞上闹钟）。
            // 宁可这段时间不响，也不要半夜乱响。
            if (!clockSane()) {
                if (!warnedClock) {
                    warnedClock = true;
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                    sdf.setTimeZone(CN_TZ);
                    Log.w(TAG, "AlarmScheduler: 系统时间尚未同步（当前 "
                            + sdf.format(new Date()) + "），闹钟暂不生效");
                }
                return;
            }
            warnedClock = false;
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.CHINA);
            sdf.setTimeZone(CN_TZ);
            String now = sdf.format(new Date());
            restoreMinuteState(now);

            Config cfg = Config.load(ctx);
            JSONArray arr = new JSONArray(cfg.alarms);
            List<String> labels = new ArrayList<String>();
            boolean wantWeather = false;
            boolean changed = false;
            boolean stateChanged = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject a = arr.optJSONObject(i);
                if (a == null) continue;
                if (!a.optBoolean("enabled", false) || !now.equals(a.optString("time", ""))) continue;
                String key = a.optString("id", String.valueOf(i)) + ":" + now;
                if (!firedThisMinute.add(key)) continue;
                stateChanged = true;
                String label = a.optString("label", "定时提醒");
                labels.add(label);
                if ("weather".equals(a.optString("action", "speak"))) {
                    wantWeather = true;
                }
                // 一次性闹钟（"20 分钟后提醒我"）响过就自动关闭，避免明天同一时刻再响
                if (a.optBoolean("once", false)) {
                    a.put("enabled", false);
                    changed = true;
                }
                Log.i(TAG, "AlarmScheduler: 北京时间闹钟触发 " + now
                        + " action=" + a.optString("action", "speak") + " " + label);
            }
            if (changed) {
                final String updated = arr.toString();
                Config.update(ctx, new Config.Mutator() {
                    @Override
                    public void apply(Config c) {
                        c.alarms = updated;
                    }
                });
                Log.i(TAG, "AlarmScheduler: 一次性闹钟已自动关闭");
            }
            if (stateChanged) persistMinuteState();
            if (labels.isEmpty()) return;

            // 注意：普通提醒和天气提醒必须在同一句话里，不能因为查天气就把 labels 吞掉。
            StringBuilder sb = new StringBuilder("北京时间 " + now + "，闹钟时间到。");
            for (int i = 0; i < labels.size(); i++) {
                if (i > 0) sb.append("；");
                sb.append(labels.get(i));
            }
            if (wantWeather) {
                // 天气要走网络，而 check() 跑在 Handler 的主线程上——
                // 直接在这里调 WeatherService 会抛 android.os.NetworkOnMainThreadException
                // （异常 message 是 null，日志里只看到「失败：null」，实测 2026-11-03 22:58）。
                // 所以整段丢到后台线程，取到结果再播。
                final String head = sb.toString();
                final String city = WeatherService.extractCity("", cfg.weatherCity);
                final Config cfgSnap = cfg;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String report = WeatherService.query(cfgSnap, city);
                        MainService svc = MainService.get();
                        if (svc != null) {
                            svc.speak(head + "。" + report);
                        } else {
                            Log.w(TAG, "AlarmScheduler: 服务未运行，天气闹钟未播报: " + report);
                        }
                    }
                }, "r1-alarm-weather").start();
                return;
            }
            MainService svc = MainService.get();
            if (svc != null) {
                svc.speak(sb.toString());
            } else {
                Log.w(TAG, "AlarmScheduler: 服务未运行，闹钟未播报: " + sb);
            }
        } catch (Throwable t) {
            Log.w(TAG, "AlarmScheduler: " + t);
        }
    }

    // ------------------------------------------------------------------ 去重状态落盘

    private SharedPreferences stateSp() {
        return ctx.getSharedPreferences(SP_STATE, Context.MODE_PRIVATE);
    }

    /**
     * 进入新的一分钟时恢复去重集合。
     *
     * <p>进程在「同一分钟内」被回收再拉起时，内存集合是空的，若不恢复会把同一条
     * 闹钟再播一遍。落盘的内容很小（一分钟内最多几条），只在跨分钟时写。
     */
    private void restoreMinuteState(String now) {
        if (now.equals(lastMinute)) return;
        lastMinute = now;
        firedThisMinute.clear();
        SharedPreferences sp = stateSp();
        String savedMinute = sp.getString("minute", "");
        if (now.equals(savedMinute)) {
            String keys = sp.getString("keys", "");
            for (String k : keys.split("\n")) {
                if (!k.isEmpty()) firedThisMinute.add(k);
            }
        } else {
            sp.edit().putString("minute", now).putString("keys", "").apply();
        }
    }

    private void persistMinuteState() {
        StringBuilder sb = new StringBuilder();
        for (String k : firedThisMinute) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(k);
        }
        stateSp().edit().putString("minute", lastMinute).putString("keys", sb.toString()).apply();
    }

    private void clearSavedState() {
        stateSp().edit().remove("minute").remove("keys").apply();
    }
}
