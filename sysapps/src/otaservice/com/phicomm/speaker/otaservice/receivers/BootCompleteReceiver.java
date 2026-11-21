package com.phicomm.speaker.otaservice.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机广播接收器的空壳实现。
 *
 * <p>原实现会在开机后启动 OTA 服务去连斐讯的升级服务器。空壳只打日志，不启动任何东西。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 00:50 星期六
 */
public class BootCompleteReceiver extends BroadcastReceiver {

    private static final String TAG = "R1STUB/OTA";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "BootCompleteReceiver.onReceive action="
                + (intent == null ? "null" : intent.getAction()) + " -> 忽略（不启动 OTA 服务）");
    }
}
