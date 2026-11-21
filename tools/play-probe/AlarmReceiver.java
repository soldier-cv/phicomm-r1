package com.r1.playprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * 闹钟到点：打日志（算误差）+ 播一段音，让人能听见。
 * 不申请音频焦点、不动音量 —— 用系统当前的音量播。
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static MediaPlayer mp;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final long fired = System.currentTimeMillis();
        long scheduled = intent.getLongExtra("scheduledAt", 0L);
        int requested = intent.getIntExtra("requestedSec", -1);

        Log.i(AlarmReceiver.TAG, "FIRED at=" + fired
                + " scheduled=" + scheduled
                + " drift=" + (scheduled > 0 ? (fired - scheduled) : -1) + "ms"
                + " requestedSec=" + requested);

        try {
            if (mp != null) { try { mp.release(); } catch (Throwable ignored) { } }
            mp = new MediaPlayer();
            AssetFileDescriptor afd = ctx.getAssets().openFd("probe.wav");
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) {
                    Log.i(AlarmReceiver.TAG, "RING DONE at=" + System.currentTimeMillis());
                    try { m.release(); } catch (Throwable ignored) { }
                    mp = null;
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer m, int what, int extra) {
                    Log.i(AlarmReceiver.TAG, "RING ERROR what=" + what + " extra=" + extra);
                    try { m.release(); } catch (Throwable ignored) { }
                    mp = null;
                    return true;
                }
            });
            mp.prepare();
            Log.i(AlarmReceiver.TAG, "RING START duration=" + mp.getDuration());
            mp.start();
        } catch (Throwable t) {
            Log.i(AlarmReceiver.TAG, "RING EXCEPTION " + t.getClass().getName() + ": " + t.getMessage());
        }
    }

    static final String TAG = AlarmProbe.TAG;
}
