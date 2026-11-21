package org.eclipse.paho.android.sample.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * MQTT OTA 客户端服务的空壳实现。
 *
 * <p>原实现负责接收云端下发的升级指令并触发下载安装，服务器已下线。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 00:50 星期六
 */
public class MqttOtaClientService extends Service {

    private static final String TAG = "R1STUB/OTA";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "MqttOtaClientService.onCreate 空壳（不建连）");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "MqttOtaClientService.onStartCommand startId=" + startId + " -> 忽略");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "MqttOtaClientService.onBind -> 返回 null");
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "MqttOtaClientService.onDestroy");
        super.onDestroy();
    }
}
