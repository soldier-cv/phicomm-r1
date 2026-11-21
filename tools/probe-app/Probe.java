package com.r1.probe;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;

/**
 * 只做一件事：用普通 App 的方式（AudioRecord）录 1 秒，报告结果。
 * 结果全部走 logcat，tag = R1PROBE。
 * 不写任何文件，不改任何设置，跑完自己 finish。
 */
public class Probe extends Activity {

    private static final String TAG = "R1PROBE";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        new Thread(new Runnable() {
            public void run() {
                probe(MediaRecorder.AudioSource.MIC, "MIC");
                probe(MediaRecorder.AudioSource.VOICE_RECOGNITION, "VOICE_RECOGNITION");
                Log.i(TAG, "DONE");
                runOnUiThread(new Runnable() {
                    public void run() { finish(); }
                });
            }
        }).start();
    }

    private void probe(int source, String name) {
        int rate = 16000;
        int chCfg = AudioFormat.CHANNEL_IN_MONO;
        int fmt = AudioFormat.ENCODING_PCM_16BIT;
        int minBuf = AudioRecord.getMinBufferSize(rate, chCfg, fmt);
        Log.i(TAG, "--- " + name + " start, minBuf=" + minBuf);

        AudioRecord rec = null;
        try {
            rec = new AudioRecord(source, rate, chCfg, fmt,
                    Math.max(minBuf, rate * 2));
            Log.i(TAG, name + " state=" + rec.getState()
                    + " (1=INITIALIZED)");

            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, name + " RESULT=INIT_FAIL");
                return;
            }

            rec.startRecording();
            Log.i(TAG, name + " recordingState=" + rec.getRecordingState()
                    + " (3=RECORDING)");

            short[] buf = new short[1600];   // 100ms
            long t0 = System.currentTimeMillis();
            int total = 0;
            int maxAbs = 0;
            int nonZero = 0;
            int reads = 0;

            while (System.currentTimeMillis() - t0 < 2000) {
                int n = rec.read(buf, 0, buf.length);
                reads++;
                if (n < 0) {
                    Log.i(TAG, name + " read error n=" + n);
                    break;
                }
                if (n == 0) continue;
                total += n;
                for (int i = 0; i < n; i++) {
                    int v = Math.abs(buf[i]);
                    if (v > maxAbs) maxAbs = v;
                    if (buf[i] != 0) nonZero++;
                }
                if (total >= rate) break;   // 够 1 秒就停
            }

            long cost = System.currentTimeMillis() - t0;
            Log.i(TAG, name + " RESULT samples=" + total
                    + " reads=" + reads
                    + " maxAbs=" + maxAbs
                    + " nonZero=" + nonZero
                    + " costMs=" + cost
                    + " -> " + (total > 0 && maxAbs > 0 ? "GOT_AUDIO" : "NO_AUDIO"));

            rec.stop();
        } catch (Throwable t) {
            Log.i(TAG, name + " EXCEPTION " + t.getClass().getName() + ": " + t.getMessage());
        } finally {
            if (rec != null) {
                try { rec.release(); } catch (Throwable ignored) { }
            }
        }
    }
}
