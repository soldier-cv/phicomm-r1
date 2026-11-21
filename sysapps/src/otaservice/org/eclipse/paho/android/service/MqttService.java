package org.eclipse.paho.android.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Paho MQTT 服务的空壳实现。
 *
 * <p>原实现为 OTA 通道维持一条到 ssl://home.phicomm.com:8883 的长连接（服务器已下线，
 * 会无限重连）。空壳不建立任何连接。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 00:50 星期六
 */
public class MqttService extends Service {

    private static final String TAG = "R1STUB/OTA";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "paho MqttService.onCreate 空壳（不建连）");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "paho MqttService.onStartCommand startId=" + startId + " -> 忽略");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "paho MqttService.onBind -> 返回 null");
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "paho MqttService.onDestroy");
        super.onDestroy();
    }
}
