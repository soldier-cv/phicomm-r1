package com.r1.brain;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启广播接收器。
 * 音箱常年插电，断电重插后自动拉起智脑主服务 (MainService)。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 17:52 星期五
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = Brain.TAG;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Log.i(TAG, "BOOT_COMPLETED -> 启动 MainService");
        try {
            context.startService(new Intent(context, MainService.class));
        } catch (Throwable t) {
            Log.e(TAG, "开机启动失败: " + t);
        }
    }
}
