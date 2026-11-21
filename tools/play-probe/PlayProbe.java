package com.r1.playprobe;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;

/**
 * V1 探针：普通 App（非 system、非 device 包）用 MediaPlayer 播一段 wav，
 * 验证「这台机器上 MediaPlayer 到底能不能出声」。
 *
 * 结果全部走 logcat，tag = R1PLAY。播完自己 finish，不写文件、不改设置。
 * 唯一主动改的是音量：进 Activity 先把 STREAM_MUSIC 拉满，避免"其实播了但音量 0"。
 */
public class PlayProbe extends Activity {

    private static final String TAG = "R1PLAY";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // 音量：默认不改（避免再次把人震到）。要改就传 --ei vol <0..15>；
        // 不播只调音量就传 --ez play false。
        int wantVol = getIntent().getIntExtra("vol", -1);
        boolean doPlay = getIntent().getBooleanExtra("play", true);

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            Log.i(TAG, "volume before=" + cur + " max=" + max + " want=" + wantVol);
            if (wantVol >= 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.min(wantVol, max), 0);
                Log.i(TAG, "volume after=" + am.getStreamVolume(AudioManager.STREAM_MUSIC));
            }
        } catch (Throwable t) {
            Log.i(TAG, "volume set failed: " + t);
        }

        if (!doPlay) {
            Log.i(TAG, "play disabled, DONE");
            finish();
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                play();
            }
        }).start();
    }

    private void play() {
        MediaPlayer mp = new MediaPlayer();
        try {
            AssetFileDescriptor afd = getAssets().openFd("probe.wav");
            Log.i(TAG, "asset len=" + afd.getLength());
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();

            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer m) {
                    Log.i(TAG, "PREPARED duration=" + m.getDuration() + "ms");
                    long t0 = System.currentTimeMillis();
                    m.start();
                    Log.i(TAG, "START at " + t0);
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) {
                    Log.i(TAG, "COMPLETED at " + System.currentTimeMillis());
                    m.release();
                    close();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer m, int what, int extra) {
                    Log.i(TAG, "ERROR what=" + what + " extra=" + extra);
                    m.release();
                    close();
                    return true;
                }
            });
            Log.i(TAG, "prepareAsync");
            mp.prepareAsync();
        } catch (Throwable t) {
            Log.i(TAG, "EXCEPTION " + t.getClass().getName() + ": " + t.getMessage());
            try { mp.release(); } catch (Throwable ignored) { }
            close();
        }
    }

    private void close() {
        Log.i(TAG, "DONE");
        runOnUiThread(new Runnable() {
            public void run() { finish(); }
        });
    }
}
