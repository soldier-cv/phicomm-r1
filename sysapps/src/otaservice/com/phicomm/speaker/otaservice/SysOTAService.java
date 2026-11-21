package com.phicomm.speaker.otaservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * OTA 服务的空壳实现。
 *
 * <p>原实现会向已下线的 home.phicomm.com 轮询固件更新，并具备下载安装整包的能力，
 * 存在把我们自己的 L1 包覆盖回原厂的风险。此处替换为空壳：组件依然存在（调用方不会
 * 拿到 NameNotFoundException），但什么都不做。
 *
 * <p>每次生命周期回调都打日志，方便在 logcat 里排查是谁把它拉起来、被调用了什么。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 00:50 星期六
 */
public class SysOTAService extends Service {

    private static final String TAG = "R1STUB/OTA";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "SysOTAService.onCreate 空壳已加载（不做任何事）");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "SysOTAService.onStartCommand action="
                + (intent == null ? "null" : intent.getAction())
                + " startId=" + startId + " -> 忽略");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "SysOTAService.onBind action="
                + (intent == null ? "null" : intent.getAction()) + " -> 返回 null");
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "SysOTAService.onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "SysOTAService.onDestroy");
        super.onDestroy();
    }
}
