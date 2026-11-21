package com.r1.playprobe;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * V2 探针：用 AlarmManager.setExact 设一个「N 秒后」的精确闹钟，
 * 验证这台机器上精确定时准不准（误差多少）。
 *
 * 用法：
 *   am start -n com.r1.playprobe/.AlarmProbe --ei sec 60
 *
 * 到点由 AlarmReceiver 接手：打日志（含应响时刻 / 实际时刻 / 误差）+ 播一段音，
 * 这样人不在跟前也能从日志算误差，在跟前还能听见。
 */
public class AlarmProbe extends Activity {

    static final String TAG = "R1ALARM";
    static final String ACTION_RING = "com.r1.playprobe.RING";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        int sec = getIntent().getIntExtra("sec", 60);
        long now = System.currentTimeMillis();
        long at = now + sec * 1000L;

        Intent i = new Intent(ACTION_RING);
        i.setClass(this, AlarmReceiver.class);
        i.putExtra("scheduledAt", at);
        i.putExtra("requestedSec", sec);
        PendingIntent pi = PendingIntent.getBroadcast(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        String how;
        try {
            am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            how = "setExact";
        } catch (Throwable t) {
            // 极端情况下（厂商改过 API）退到 set()
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
            how = "set(fallback) " + t.getClass().getSimpleName();
        }

        Log.i(TAG, "SCHEDULED " + how + " in " + sec + "s at=" + at
                + " now=" + now
                + " nextAlarmClock=" + (am.getNextAlarmClock() == null ? "null" : String.valueOf(am.getNextAlarmClock().getTriggerTime())));
        finish();
    }
}
