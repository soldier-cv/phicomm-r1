package com.r1.brain.dlna;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * 投屏用的播放器：一个 MediaPlayer，由 AVTransport / RenderingControl 动作驱动。
 *
 * <p>移植自 MIT 许可的 mokso/uvp-dlna-renderer。和原版的两点差别：
 * <ul>
 *   <li>开始播放前先让本机的音乐播放器让路（同一个喇叭，两条流一起出声只会互相打架）；</li>
 *   <li>音量为 0 时不再调 {@code setStreamMute}（这台机器上它会把媒体音量栈压坏，
 *       原版注释里也提到了 4.4 的同一个坑）。</li>
 * </ul>
 *
 * @author xudong.hua,claude
 * @since 2026-11-08 14:10 星期日
 */
public final class CastPlayer implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "R1BRAIN-DLNA";

    /** 投屏要出声之前，先让本机的音乐停下。 */
    public interface MusicYielder {
        void yieldForCast();
    }

    private static volatile MusicYielder yielder;

    /** 由 MainService 注入：投屏开始放之前调用，停掉本地音乐。 */
    public static void setMusicYielder(MusicYielder y) {
        yielder = y;
    }

    static final class Snapshot {
        String state;
        String uri;
        String meta;
        String nextUri;
        long durationMs;
        long positionMs;

        String actions() {
            if (state.equals("PLAYING")) return "Pause,Stop,Seek";
            if (state.equals("NO_MEDIA_PRESENT")) return "";
            return "Play,Stop,Seek";
        }
    }

    private final AudioManager audio;
    private final Runnable onChange;
    private MediaPlayer mp;
    private boolean prepared;
    private boolean playWhenReady;
    private boolean muted;
    private String state = "NO_MEDIA_PRESENT";
    private String uri = "";
    private String meta = "";
    private String nextUri = "";
    private String nextMeta = "";
    private long durationMs;
    /** 最近一次失败原因（给 /api/v1/cast 与日志用；成功播放后清空）。 */
    private String lastError = "";
    /** 这一轮是被唤醒让路停的，结束后由 resumeAfterVoice 接着放。 */
    private boolean voicePaused;

    CastPlayer(Context ctx, Runnable onChange) {
        this.audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        this.onChange = onChange;
    }

    synchronized Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.state = state;
        s.uri = uri;
        s.meta = meta;
        s.nextUri = nextUri;
        s.durationMs = durationMs;
        s.positionMs = prepared && mp != null ? mp.getCurrentPosition() : 0;
        return s;
    }

    synchronized void setUri(String newUri, String newMeta) {
        voicePaused = false;
        release();
        uri = newUri == null ? "" : newUri;
        meta = newMeta == null ? "" : newMeta;
        state = uri.isEmpty() ? "NO_MEDIA_PRESENT" : "STOPPED";
        onChange.run();
    }

    synchronized void setNextUri(String newUri, String newMeta) {
        nextUri = newUri == null ? "" : newUri;
        nextMeta = newMeta == null ? "" : newMeta;
        onChange.run();
    }

    synchronized void play() {
        if (uri.isEmpty()) return;
        playWhenReady = true;
        if (mp == null) open();
        if (prepared) {
            start();
        } else {
            state = "TRANSITIONING";
        }
        onChange.run();
    }

    synchronized void pause() {
        playWhenReady = false;
        if (prepared && mp != null && mp.isPlaying()) {
            mp.pause();
            state = "PAUSED_PLAYBACK";
            onChange.run();
        }
    }

    synchronized void stop() {
        voicePaused = false;
        release();
        state = uri.isEmpty() ? "NO_MEDIA_PRESENT" : "STOPPED";
        onChange.run();
    }

    synchronized void seek(long ms) {
        if (prepared && mp != null && ms >= 0) {
            try {
                mp.seekTo((int) ms);
            } catch (Throwable ignored) {
            }
        }
    }

    int volume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
    }

    void setVolume(int percent) {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int v = Math.max(0, Math.min(100, percent));
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max / 100f), 0);
        onChange.run();
    }

    /** 最近一次失败原因（空串表示没出错）。 */
    synchronized String lastError() {
        return lastError;
    }

    synchronized boolean muted() {
        return muted;
    }

    synchronized void setMuted(boolean m) {
        if (m == muted) return;
        muted = m;
        try {
            audio.setStreamMute(AudioManager.STREAM_MUSIC, m);
        } catch (Throwable t) {
            Log.w(TAG, "静音设置失败: " + t);
        }
        onChange.run();
    }


    /**
     * 唤醒让路：正在投屏播放就暂停，并记下「是语音让路停的」。
     *
     * @return true 表示这次确实暂停了正在播放的投屏
     */
    synchronized boolean pauseForVoice() {
        if (mp == null || !prepared) return false;
        try {
            if (!mp.isPlaying()) return false;
            voicePaused = true;
            playWhenReady = false;
            mp.pause();
            state = "PAUSED_PLAYBACK";
            onChange.run();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "CastPlayer pauseForVoice: " + t);
            return false;
        }
    }

    /**
     * 一轮语音结束：如果投屏是被唤醒让路停的、期间也没有别的播放控制，就接着放。
     *
     * @return true 表示已恢复播放
     */
    synchronized boolean resumeAfterVoice() {
        if (!voicePaused) return false;
        voicePaused = false;
        if (mp == null || !prepared) return false;
        try {
            if (!mp.isPlaying()) {
                start();
                onChange.run();
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "CastPlayer resumeAfterVoice: " + t);
        }
        return false;
    }

    /** 彻底收摊（关闭投屏时调用）。 */
    synchronized void shutdown() {
        voicePaused = false;
        release();
        if (muted) {
            try {
                audio.setStreamMute(AudioManager.STREAM_MUSIC, false);
            } catch (Throwable ignored) {
            }
        }
        state = "NO_MEDIA_PRESENT";
        uri = "";
    }

    // 调用方持有锁
    private void open() {
        mp = new MediaPlayer();
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mp.setOnPreparedListener(this);
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);
        prepared = false;
        durationMs = 0;
        try {
            mp.setDataSource(uri);
            mp.prepareAsync();
        } catch (Throwable e) {
            Log.w(TAG, "投屏地址打不开: " + uri + " " + e);
            lastError = "打不开地址: " + e;
            release();
            state = "STOPPED";
        }
    }

    private void start() {
        try {
            audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Throwable ignored) {
        }
        MusicYielder y = yielder;
        if (y != null) {
            try {
                y.yieldForCast();
            } catch (Throwable t) {
                Log.w(TAG, "让路失败: " + t);
            }
        }
        mp.start();
        lastError = "";
        state = "PLAYING";
    }

    private void release() {
        if (mp != null) {
            try {
                mp.reset();
            } catch (Throwable ignored) {
            }
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
            mp = null;
        }
        prepared = false;
        playWhenReady = false;
        durationMs = 0;
        try {
            audio.abandonAudioFocus(this);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public synchronized void onPrepared(MediaPlayer p) {
        if (p != mp) return;
        prepared = true;
        durationMs = Math.max(0, p.getDuration());
        if (playWhenReady) start();
        onChange.run();
    }

    @Override
    public synchronized void onCompletion(MediaPlayer p) {
        if (p != mp) return;
        release();
        if (!nextUri.isEmpty()) {
            uri = nextUri;
            meta = nextMeta;
            nextUri = "";
            nextMeta = "";
            play();
            return;
        }
        state = "STOPPED";
        onChange.run();
    }

    @Override
    public synchronized boolean onError(MediaPlayer p, int what, int extra) {
        if (p != mp) return true;
        Log.w(TAG, "投屏播放出错 " + what + "/" + extra + " : " + uri);
        lastError = "播放出错 " + what + "/" + extra;
        release();
        state = "STOPPED";
        onChange.run();
        return true;
    }

    @Override
    public void onAudioFocusChange(int change) {
        // 有别的应用（来电、闹钟）要声音：先让出来
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            pause();
        }
    }
}
