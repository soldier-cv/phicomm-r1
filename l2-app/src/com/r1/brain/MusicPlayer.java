package com.r1.brain;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 音箱本机音乐播放器：使用 MediaPlayer 通过 STREAM_MUSIC 直接从音箱扬声器播放。
 * 支持音箱本地存储 (/sdcard/Music、/sdcard/Download) 与 HTTP(S) 网络音频流。
 *
 * 线程约定：
 *   - MediaPlayer 的创建、prepare、start、pause、release 统一在主线程执行；
 *   - 目录扫描等文件 IO 在单独的 io 线程执行，避免 onCompletion 回调里扫盘卡住主线程。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 13:52 星期五
 */
public class MusicPlayer {

    private static final String TAG = Brain.TAG;
    private static final String[] MUSIC_DIRS = {"/sdcard/Music", "/sdcard/Download", "/storage/emulated/0/Music"};
    private static final int MAX_SCAN_DEPTH = 2;
    private static final int MAX_TRACKS = 1000;

    private static MusicPlayer instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Random random = new Random();

    private MediaPlayer mp;
    private volatile boolean prepared;
    private volatile String source = "";
    private volatile String title = "";
    private volatile String error = "";
    private volatile LoopMode loop = LoopMode.LIST;
    private volatile long sleepDeadline = 0;
    private volatile boolean closed = false;
    /** 状态版本号：每次「命令已执行 / 异步状态变化」自增，供 HTTP 层等待一次操作落地。 */
    private volatile int stateVersion = 0;
    /**
     * 这首歌是不是被「唤醒让路」暂停的。
     *
     * <p>唤醒时必须先停音乐（播放中麦克风拿到的是数字静音），但这一轮结束后要接着放，
     * 否则问一句「几点了」音乐就再也不响了。任何显式的播放控制（暂停/停止/切歌/点歌/续播）
     * 都会清掉它——那时用户已经表达了意图，不该再自作主张恢复。
     */
    private volatile boolean voicePaused = false;

    private final Runnable sleepStop = new Runnable() {
        @Override
        public void run() {
            sleepDeadline = 0;
            voicePaused = false;
            releaseOnMain();
            source = "";
            title = "";
            stateVersion++;
            Log.i(TAG, "MusicPlayer: 睡眠定时到点，已自动停止播放");
        }
    };

    private MusicPlayer() {
    }

    public static synchronized MusicPlayer get() {
        if (instance == null) {
            instance = new MusicPlayer();
        }
        return instance;
    }

    /** 服务销毁时释放单例，下一次 onCreate 会得到全新的播放器。 */
    public static synchronized void destroy() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    /** 扫描音箱本地存储中的音频文件（最多两层目录），按文件名排序并按绝对路径去重。 */
    public static List<File> scan() {
        List<File> out = new ArrayList<File>();
        Set<String> seen = new HashSet<String>();
        for (String d : MUSIC_DIRS) {
            collect(new File(d), 0, out, seen);
            if (out.size() >= MAX_TRACKS) break;
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return out;
    }

    private static void collect(File folder, int depth, List<File> out, Set<String> seen) {
        if (out.size() >= MAX_TRACKS || folder == null || !folder.isDirectory()) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (out.size() >= MAX_TRACKS) return;
            if (f.isDirectory()) {
                if (depth + 1 < MAX_SCAN_DEPTH) collect(f, depth + 1, out, seen);
            } else if (f.isFile() && isAudioName(f.getName())) {
                String key;
                try {
                    key = f.getCanonicalPath();
                } catch (Throwable t) {
                    key = f.getAbsolutePath();
                }
                if (seen.add(key)) out.add(f);
            }
        }
    }

    public static boolean isAudioName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav")
                || lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".aac");
    }

    /** 校验播放源合法性：仅允许 HTTP(S) 音频流或音箱存储卡内真实存在的音频文件。 */
    public static boolean isAllowedSource(String src) {
        if (src == null || src.length() == 0 || src.contains("..") || src.contains("\0")
                || src.indexOf('\\') >= 0) return false;
        if (src.startsWith("http://") || src.startsWith("https://")) return true;
        if (!(src.startsWith("/sdcard/") || src.startsWith("/storage/"))) return false;
        File f = new File(src);
        return f.isFile() && isAudioName(f.getName());
    }

    /** 当前状态版本号；HTTP 层用它等待一次播放控制真正落地。 */
    public int stateVersion() {
        return stateVersion;
    }

    /**
     * 是否有当前曲目（暂停中也算）。
     *
     * <p>「继续播放」要区分两件事：续播刚才那首，还是从曲库里挑一首新的。
     * 靠文案判断是非法的（改文案就改坏逻辑），所以这里给一个显式状态。
     *
     * @return true 表示播放器手里还握着一首（可能处于暂停）
     */
    public boolean hasTrack() {
        // 必须连 prepared 一起判断：stop() 只释放 MediaPlayer 并不会清空 source，
        // 只看 source 的话，「停止后再喊播放音乐」会被当成续播，实际却什么也放不出来。
        return prepared && source != null && source.length() > 0;
    }

    public static String titleOf(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    public void play(final String src, final String name) {
        if (!isAllowedSource(src)) {
            error = "无效的播放源";
            Log.w(TAG, "MusicPlayer: 拒绝无效播放源 " + src);
            return;
        }
        voicePaused = false;
        main.post(new Runnable() {
            @Override
            public void run() {
                startOnMain(src, name);
                stateVersion++;
            }
        });
    }

    /** 无曲目（或播放器已失效）时从头播放；正在播放时暂停，暂停时继续。 */
    public String toggle() {
        voicePaused = false;
        if (source.length() == 0 || mp == null || !prepared) {
            List<File> list = scan();
            if (list.isEmpty()) return "曲库为空，请先把音乐放到音箱 /sdcard/Music 目录。";
            File f = pickFirst(list);
            play(f.getAbsolutePath(), titleOf(f));
            return "开始播放《" + titleOf(f) + "》。";
        }
        final boolean wasPlaying;
        try {
            wasPlaying = mp.isPlaying();
        } catch (Throwable t) {
            return "播放器状态异常，请重新选择曲目。";
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                if (mp == null || !prepared) { stateVersion++; return; }
                try {
                    if (wasPlaying) mp.pause(); else mp.start();
                } catch (Throwable t) {
                    error = String.valueOf(t);
                    Log.w(TAG, "MusicPlayer toggle: " + error);
                }
                stateVersion++;
            }
        });
        return wasPlaying ? "已暂停播放。" : "继续播放《" + title + "》。";
    }

    public void pause() {
        voicePaused = false;
        main.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mp != null && prepared && mp.isPlaying()) mp.pause();
                } catch (Throwable t) {
                    Log.w(TAG, "MusicPlayer pause: " + t);
                }
                stateVersion++;
            }
        });
    }

    /**
     * 唤醒让路：正在放就暂停，并记下「是语音让路停的」，这一轮结束后由 {@link #resumeAfterVoice()} 接着放。
     *
     * @return true 表示这次确实暂停了一首正在播放的歌
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    public boolean pauseForVoice() {
        if (!isPlaying()) return false;
        voicePaused = true;
        main.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mp != null && prepared && mp.isPlaying()) mp.pause();
                } catch (Throwable t) {
                    Log.w(TAG, "MusicPlayer pauseForVoice: " + t);
                }
                stateVersion++;
            }
        });
        return true;
    }

    /**
     * 一轮语音结束：如果音乐是被唤醒让路停的、期间也没有别的播放控制，就接着放。
     *
     * @return true 表示已恢复播放
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    public boolean resumeAfterVoice() {
        if (!voicePaused) return false;
        voicePaused = false;
        if (!hasTrack()) return false;
        main.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mp != null && prepared && !mp.isPlaying()) mp.start();
                } catch (Throwable t) {
                    Log.w(TAG, "MusicPlayer resumeAfterVoice: " + t);
                }
                stateVersion++;
            }
        });
        return true;
    }

    /**
     * 当前是不是「被唤醒让路暂停、等着恢复」的状态（「暂停」意图要认它）。
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    public boolean isVoicePaused() {
        return voicePaused && hasTrack();
    }

    public void stop() {
        voicePaused = false;
        main.post(new Runnable() {
            @Override
            public void run() {
                main.removeCallbacks(sleepStop);
                sleepDeadline = 0;
                releaseOnMain();
                source = "";
                title = "";
                stateVersion++;
            }
        });
    }

    public void seekTo(final int ms) {
        main.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mp != null && prepared) mp.seekTo(Math.max(0, ms));
                } catch (Throwable t) {
                    Log.w(TAG, "MusicPlayer seek: " + t);
                }
                stateVersion++;
            }
        });
    }

    /**
     * 设置循环模式。
     *
     * @param mode 模式；null 按列表循环处理
     */
    public void setLoop(LoopMode mode) {
        loop = mode == null ? LoopMode.LIST : mode;
        stateVersion++;
    }

    /** 设置睡眠定时（分钟），minutes <= 0 表示关闭。 */
    public void setSleep(final int minutes) {
        final int mins = Math.max(0, Math.min(24 * 60, minutes));
        main.post(new Runnable() {
            @Override
            public void run() {
                main.removeCallbacks(sleepStop);
                if (mins > 0) {
                    sleepDeadline = SystemClock.elapsedRealtime() + mins * 60000L;
                    main.postDelayed(sleepStop, mins * 60000L);
                    Log.i(TAG, "MusicPlayer: 睡眠定时 " + mins + " 分钟");
                } else {
                    sleepDeadline = 0;
                    Log.i(TAG, "MusicPlayer: 已关闭睡眠定时");
                }
                stateVersion++;
            }
        });
    }

    /** 切换上一首/下一首（针对音箱本地曲库，扫盘在 io 线程执行）。 */
    public boolean step(final int delta) {
        List<File> list = scan();
        if (list.isEmpty()) return false;
        int cur = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getAbsolutePath().equals(source)) {
                cur = i;
                break;
            }
        }
        int next;
        if (loop == LoopMode.SHUFFLE && list.size() > 1) {
            do {
                next = random.nextInt(list.size());
            } while (next == cur);
        } else {
            next = cur < 0 ? 0 : (cur + delta + list.size()) % list.size();
        }
        File f = list.get(next);
        play(f.getAbsolutePath(), titleOf(f));
        return true;
    }

    public boolean isPlaying() {
        try {
            return mp != null && prepared && mp.isPlaying();
        } catch (Throwable t) {
            return false;
        }
    }

    public String statusJson() {
        JSONObject o = new JSONObject();
        try {
            int pos = 0;
            int dur = 0;
            if (mp != null && prepared) {
                try {
                    pos = mp.getCurrentPosition();
                    dur = mp.getDuration();
                } catch (Throwable ignored) {
                }
            }
            long remain = sleepDeadline > 0 ? Math.max(0, (sleepDeadline - SystemClock.elapsedRealtime()) / 1000) : 0;
            o.put("ok", true);
            o.put("hasTrack", source.length() > 0);
            o.put("source", source);
            o.put("title", title);
            o.put("playing", isPlaying());
            o.put("prepared", prepared);
            o.put("positionMs", pos);
            o.put("durationMs", dur);
            o.put("loop", loop.value());
            o.put("sleepRemainSec", remain);
            o.put("error", error);
        } catch (Throwable t) {
            Log.w(TAG, "MusicPlayer status: " + t);
        }
        return o.toString();
    }

    public String summary() {
        if (source.length() == 0) return "未在播放";
        return (isPlaying() ? "正在播放《" : "已暂停《") + title + "》";
    }

    /** 服务销毁时释放播放器与线程池。 */
    public void shutdown() {
        closed = true;
        main.removeCallbacks(sleepStop);
        main.post(new Runnable() {
            @Override
            public void run() {
                releaseOnMain();
            }
        });
        io.shutdownNow();
        Log.i(TAG, "MusicPlayer: shutdown");
    }

    private File pickFirst(List<File> list) {
        if (loop == LoopMode.SHUFFLE && list.size() > 1) {
            return list.get(random.nextInt(list.size()));
        }
        return list.get(0);
    }

    private void startOnMain(String src, String name) {
        if (closed) return;
        releaseOnMain();
        source = src;
        title = (name == null || name.length() == 0) ? src : name;
        error = "";
        prepared = false;
        MediaPlayer p = null;
        try {
            p = new MediaPlayer();
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            // 远端地址一律走本机中转：mediaserver 的 HTTP 栈在这台机器上拉不动网易云 CDN
            // （实测 "source returned error -1" 重试 10 次，见 MusicRelay 类注释）
            p.setDataSource(MusicRelay.localUrlFor(src));
            p.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer m) {
                    prepared = true;
                    try {
                        m.start();
                    } catch (Throwable t) {
                        error = String.valueOf(t);
                        Log.w(TAG, "MusicPlayer start: " + error);
                    }
                    stateVersion++;
                }
            });
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    if (loop == LoopMode.SINGLE) {
                        try {
                            m.seekTo(0);
                            m.start();
                        } catch (Throwable ignored) {
                        }
                        stateVersion++;
                        return;
                    }
                    if (!source.startsWith("/")) {
                        releaseOnMain();
                        source = "";
                        title = "";
                        stateVersion++;
                        return;
                    }
                    // 扫盘/选下一首放到 io 线程，避免在 MediaPlayer 回调里做文件 IO 卡住主线程
                    io.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (closed || !step(1)) {
                                main.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        releaseOnMain();
                                        source = "";
                                        title = "";
                                        stateVersion++;
                                    }
                                });
                            }
                        }
                    });
                }
            });
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    error = "播放失败 (" + what + "/" + extra + ")";
                    prepared = false;
                    stateVersion++;
                    Log.w(TAG, "MusicPlayer: " + error + " src=" + source);
                    try { m.reset(); } catch (Throwable ignored) { }
                    return true;
                }
            });
            mp = p;
            p.prepareAsync();
            Log.i(TAG, "MusicPlayer: 开始播放 " + src);
        } catch (Throwable t) {
            error = "无法打开音频: " + t.getMessage();
            Log.w(TAG, "MusicPlayer: " + error + " src=" + src);
            if (p != null) {
                try { p.release(); } catch (Throwable ignored) { }
            }
            mp = null;
            prepared = false;
        }
    }

    private void releaseOnMain() {
        prepared = false;
        if (mp != null) {
            try {
                mp.reset();
                mp.release();
            } catch (Throwable ignored) {
            }
            mp = null;
        }
    }
}
