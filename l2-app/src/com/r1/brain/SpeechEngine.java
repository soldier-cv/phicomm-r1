package com.r1.brain;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * HTTP TTS 语音合成与播报引擎：请求兼容 /v1/audio/speech 音频流 → 本地缓存 → 分句有序播放。
 *
 * <p>长文本处理（对应 docs/05-voice.md §4）：
 * <ol>
 *   <li>按 。！？；换行 分句，单句超过 60 字再按 ，、 切；</li>
 *   <li>首句立刻合成，其余最多 3 路并发；</li>
 *   <li>严格按原顺序播放，播第 N 句时第 N+1 句已经在合成。</li>
 * </ol>
 *
 * <p>并发语义：同一时刻只播一路，新的播报会打断旧的；排队中的旧请求发现 generation
 * 已变化会直接放弃，避免延时播报积压。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-30 13:52 星期五
 */
public class SpeechEngine {

    private static final String TAG = Brain.TAG;

    /** 缓存条数上限。 */
    private static final int CACHE_MAX = 40;
    /** 缓存总大小上限：8MB。这台机器存储和内存都紧，条数够用不代表体积安全（长故事句子多）。 */
    private static final long CACHE_MAX_BYTES = 8 * 1024 * 1024L;
    /** 单次播报允许的总字数上限（整段），超出直接截断。 */
    private static final int MAX_TEXT = 2000;
    /** 单句上限，超过按逗号再切。 */
    private static final int MAX_SENTENCE = 60;
    /** 合成并发度。实测服务端 3 并发约 1.6 倍收益，再多会互相争抢。 */
    private static final int SYNTH_CONCURRENCY = 3;
    /** 单句播放最长等待，防止某句卡住整个队列。 */
    private static final int PLAY_TIMEOUT_SEC = 20;
    /**
     * {@link #stop()} 打断时投给回调的 {@code onError} 文案（协议值，不是给人看的）。
     *
     * <p>以前被打断的播报回调就此消失，调用方等不到收尾；现在一定会收到它，
     * 调用方据此区分「被停」和「失败」——被停不能去走原厂 TTS 兜底。
     */
    public static final String STOPPED = "stopped";

    /**
     * 语音播报状态回调接口。
     *
     * @author xudong.hua,gemini
     * @since 2026-10-30 13:52 星期五
     */
    public interface Callback {
        void onStarted(int firstAudioMs, int durationMs);
        void onDone();
        void onError(String message);
    }

    private final Context ctx;
    private final File cacheDir;
    /** 播放编排：单线程，保证顺序。 */
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    /** 合成：固定并发。 */
    private final ExecutorService synthPool = Executors.newFixedThreadPool(SYNTH_CONCURRENCY);
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile MediaPlayer player;
    private volatile boolean playing = false;
    private volatile String lastText = "";
    private volatile int lastFirstAudioMs = -1;
    private volatile String lastError = "";
    private volatile int generation = 0;
    private volatile boolean closed = false;
    /** 正在播（或排队中）的那一句的回调；结束、出错、被顶掉时清空。 */
    private volatile Callback current;

    public SpeechEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.cacheDir = new File(ctx.getCacheDir(), "tts");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.w(TAG, "TTS 缓存目录创建失败: " + cacheDir);
        }
        purgeCache();
    }

    public boolean isConfigured() {
        Config c = Config.load(ctx);
        return c.ttsUrl != null && !c.ttsUrl.trim().isEmpty();
    }

    public String status() {
        if (!isConfigured()) return "unconfigured";
        return playing ? "playing" : "idle";
    }

    /**
     * 异步播报文本语音。
     *
     * <p>只要 {@code cb} 非空，就一定会收到 {@code onDone} 或 {@code onError} 之一（被新播报打断的除外），
     * 调用方靠它做 TTS 失败兜底与一轮收尾。
     *
     * @return false 表示没有受理（未配置 / 已关闭 / 空文本），此时 {@code onError} 已投递
     */
    public boolean speak(final String text, final Callback cb) {
        if (closed) {
            postError(cb, "TTS 引擎已关闭");
            return false;
        }
        if (text == null || text.trim().isEmpty()) {
            postError(cb, "空文本");
            return false;
        }
        final String full = text.trim().length() > MAX_TEXT ? text.trim().substring(0, MAX_TEXT) : text.trim();
        if (full.length() < text.trim().length()) {
            Log.w(TAG, "TTS 文本超过 " + MAX_TEXT + " 字，已截断");
        }
        if (!isConfigured()) {
            lastError = "TTS 未配置";
            postError(cb, lastError);
            return false;
        }
        lastText = full;
        final List<String> parts = split(full);
        final int gen = ++generation;
        // 新播报顶掉旧播报：旧的回调也要收到「被停」
        Callback old = current;
        current = cb;
        if (old != null && old != cb) {
            postError(old, STOPPED);
        }
        stopPlayerOnly();
        try {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        playSequence(parts, cb, gen);
                    } catch (Throwable t) {
                        if (gen != generation) return;
                        if (current == cb) current = null;
                        final String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
                        Log.e(TAG, "TTS 播放失败: " + msg);
                        lastError = msg;
                        postError(cb, msg);
                    }
                }
            });
        } catch (Throwable t) {
            lastError = "TTS 线程池不可用: " + t.getMessage();
            Log.e(TAG, lastError);
            if (current == cb) current = null;
            postError(cb, lastError);
            return false;
        }
        return true;
    }

    /**
     * 静默停止：只为「唤醒让路」用。
     *
     * <p>不给正在播的回调投 {@link #STOPPED}：否则唤醒时的暂停会被当成「用户打断」，
     * 提前把这一轮收尾、把刚暂停的音乐又恢复出来（实测会导致录音期间音乐响）。
     * 用户主动「别说了」/网页停止仍走 {@link #stop()}，那条要正常收尾。
     */
    public void stopQuietly() {
        generation++;
        playing = false;
        current = null;
        final MediaPlayer p = player;
        player = null;
        if (p != null) {
            try { p.stop(); } catch (Throwable ignored) { }
            try { p.release(); } catch (Throwable ignored) { }
        }
    }

    /** 停止当前正在播放的语音，并让排队中的请求失效；正在播的那句的回调会收到 {@link #STOPPED}。 */
    public void stop() {
        generation++;
        playing = false;
        Callback old = current;
        current = null;
        if (old != null) {
            postError(old, STOPPED);
        }
        final MediaPlayer p = player;
        player = null;
        if (p != null) {
            try { p.stop(); } catch (Throwable ignored) { }
            try { p.release(); } catch (Throwable ignored) { }
        }
    }

    public void shutdown() {
        closed = true;
        stop();
        pool.shutdownNow();
        synthPool.shutdownNow();
        Log.i(TAG, "SpeechEngine: shutdown");
    }

    // ---------------------------------------------------------------- 分句

    /**
     * 把一段文本切成适合播报的句子。
     *
     * @param text 原始文本
     * @return 句子列表，至少一句
     */
    static List<String> split(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        // 1) 先按句末标点切（不在这一遍按长度硬切，否则「超长句按逗号切」永远没机会执行）
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            cur.append(c);
            boolean hard = (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n' || c == '!' || c == '?' || c == ';');
            if (hard) {
                addTrimmed(out, cur);
                cur.setLength(0);
            }
        }
        addTrimmed(out, cur);

        // 2) 超长句按逗号切；连逗号都没有的极端情况再按长度硬切
        List<String> fin = new ArrayList<String>();
        for (String s : out) {
            if (s.length() <= MAX_SENTENCE) {
                fin.add(s);
                continue;
            }
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                buf.append(c);
                if ((c == '，' || c == '、' || c == ',') && buf.length() >= MAX_SENTENCE / 2) {
                    addTrimmed(fin, buf);
                    buf.setLength(0);
                } else if (buf.length() >= MAX_SENTENCE) {
                    addTrimmed(fin, buf);
                    buf.setLength(0);
                }
            }
            addTrimmed(fin, buf);
        }
        if (fin.isEmpty()) fin.add(text.trim());
        return fin;
    }

    private static void addTrimmed(List<String> out, StringBuilder sb) {
        String s = sb.toString().trim();
        if (!s.isEmpty()) out.add(s);
    }

    // ---------------------------------------------------------------- 播放编排

    /** 首句先合成，其余并发；严格按顺序播。 */
    private void playSequence(List<String> parts, final Callback cb, final int gen) throws Exception {
        Config cfg = Config.load(ctx);
        if (!isConfigured(cfg)) throw new IllegalStateException("TTS 未配置");

        final long t0 = System.currentTimeMillis();
        List<Future<File>> futures = new ArrayList<Future<File>>();
        for (final String p : parts) {
            futures.add(synthPool.submit(new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return synth(p);
                }
            }));
        }

        boolean first = true;
        int totalDuration = 0;
        int played = 0;
        String lastError = null;
        for (int i = 0; i < parts.size(); i++) {
            if (gen != generation) return;
            File f;
            try {
                f = futures.get(i).get(60, TimeUnit.SECONDS);
            } catch (Throwable e) {
                // 单句合成失败（网关偶发 0 字节 / 超时）不应该中断整段播报，
                // 跳过这句继续下一句，否则长故事会突然“没声了”。
                lastError = (e.getCause() == null ? e : e.getCause()).getMessage();
                Log.w(TAG, "TTS 第 " + (i + 1) + "/" + parts.size() + " 句合成失败，跳过: " + lastError);
                continue;
            }
            if (gen != generation) return;

            int[] dur = playFile(f, gen);
            if (gen != generation) return;
            if (dur == null) continue;

            played++;
            totalDuration += dur[0];
            if (first) {
                first = false;
                lastFirstAudioMs = (int) (System.currentTimeMillis() - t0);
                if (cb != null) {
                    final int fa = lastFirstAudioMs;
                    final int d = totalDuration;
                    main.post(new Runnable() {
                        @Override
                        public void run() { cb.onStarted(fa, d); }
                    });
                }
            }
            Log.i(TAG, "TTS 第 " + (i + 1) + "/" + parts.size() + " 句播完（" + dur[0] + "ms）");
        }

        if (gen != generation) return;
        if (current == cb) current = null;
        if (played == 0) {
            final String msg = lastError == null ? "全部句子合成失败" : lastError;
            lastError = msg;
            if (cb != null) {
                main.post(new Runnable() {
                    @Override
                    public void run() { cb.onError(msg); }
                });
            }
            return;
        }
        if (cb != null) {
            main.post(new Runnable() {
                @Override
                public void run() { cb.onDone(); }
            });
        }
    }

    /** 阻塞播放一个文件，返回 {时长ms}；被打断返回 null。 */
    private int[] playFile(File f, final int gen) throws Exception {
        final MediaPlayer p = new MediaPlayer();
        final CountDownLatch done = new CountDownLatch(1);
        try {
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            p.setDataSource(f.getAbsolutePath());
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) { done.countDown(); }
            });
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    Log.w(TAG, "MediaPlayer onError what=" + what + " extra=" + extra);
                    done.countDown();
                    return true;
                }
            });
            p.prepare();
        } catch (Throwable t) {
            try { p.release(); } catch (Throwable ignored) { }
            throw t;
        }
        if (gen != generation) {
            try { p.release(); } catch (Throwable ignored) { }
            return null;
        }
        int duration = 0;
        try { duration = p.getDuration(); } catch (Throwable ignored) { }
        player = p;
        playing = true;
        p.start();
        try { f.setLastModified(System.currentTimeMillis()); } catch (Throwable ignored) { }
        try {
            done.await(PLAY_TIMEOUT_SEC, TimeUnit.SECONDS);
        } finally {
            playing = false;
            if (player == p) player = null;
            try { p.release(); } catch (Throwable ignored) { }
        }
        return new int[]{duration};
    }

    // ---------------------------------------------------------------- 合成与缓存

    /** 取一句的音频文件：命中缓存直接用，否则请求并落盘。 */
    private File synth(String sentence) throws Exception {
        Config cfg = Config.load(ctx);
        File mp3 = fileFor(cfg, sentence);
        if (mp3.exists() && mp3.length() > 0) {
            return mp3;
        }
        fetch(cfg, sentence, mp3);
        return mp3;
    }

    /** 请求 OpenAI 兼容 /v1/audio/speech 接口并将音频流落盘。 */
    private void fetch(Config cfg, String text, File out) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", cfg.ttsModel);
        body.put("voice", cfg.ttsVoice);
        body.put("input", text);
        body.put("speed", cfg.ttsSpeed);
        body.put("response_format", "mp3");

        byte[] payload = body.toString().getBytes("UTF-8");
        HttpURLConnection conn = null;
        String contentType = null;
        // 临时文件名带线程号：文本里出现重复句子时，3 路合成会同时写同一个缓存目标，
        // 共用固定 ".part" 会把文件写坏。每个线程写自己的临时文件，最后原子 rename 覆盖。
        File tmp = new File(out.getAbsolutePath() + "." + Thread.currentThread().getId() + ".part");
        try {
            conn = (HttpURLConnection) new URL(cfg.ttsUrl).openConnection();
            conn.setRequestMethod("POST");
            // 和 Http 一样：老系统的根库不认新证书，TTS 走 HTTPS 时也要补额外根
            if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                javax.net.ssl.SSLSocketFactory sf = Tls.socketFactory();
                if (sf != null) ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(sf);
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (cfg.ttsKey != null && !cfg.ttsKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + cfg.ttsKey.trim());
            }
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            contentType = conn.getContentType();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            try {
                if (code < 200 || code >= 300) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    if (is != null) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                    }
                    String err = new String(bos.toByteArray(), "UTF-8");
                    throw new IllegalStateException("TTS HTTP " + code + " "
                            + (err.length() > 60 ? err.substring(0, 60) : err));
                }

                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(tmp);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                    fos.flush();
                } finally {
                    if (fos != null) {
                        try { fos.close(); } catch (Throwable ignored) { }
                    }
                }
            } finally {
                if (is != null) {
                    try { is.close(); } catch (Throwable ignored) { }
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
        if (tmp.length() < 200) {
            long len = tmp.length();
            tmp.delete();
            throw new IllegalStateException("TTS 返回内容过小（" + len + " 字节）");
        }
        // 只判大小不够：网关可能用 200 + 一段 JSON 错误体返回。那种内容一旦按句子 MD5
        // 落盘成 .mp3，这句以后会永远命中坏缓存、永远不出声。这里校验 Content-Type 与音频魔数。
        if (!looksLikeAudio(contentType, tmp)) {
            tmp.delete();
            throw new IllegalStateException("TTS 返回的不是音频（Content-Type="
                    + (contentType == null ? "未知" : contentType) + "）");
        }
        if (!tmp.renameTo(out)) {
            tmp.delete();
            throw new IllegalStateException("缓存写入失败");
        }
        purgeCache();
    }

    /** 判断下载结果是不是音频：优先看魔数，其次看明确声明为 audio/* 的 Content-Type。 */
    private static boolean looksLikeAudio(String contentType, File f) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.US).trim();
        if (ct.startsWith("application/json") || ct.startsWith("text/")) return false;
        byte[] head = new byte[4];
        int n = 0;
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            n = in.read(head);
        } catch (Throwable ignored) {
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable ignored) { }
            }
        }
        if (n >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') return true;
        if (n >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) return true;
        return ct.startsWith("audio/");
    }

    private File fileFor(Config cfg, String text) {        String key = cfg.ttsUrl + "|" + cfg.ttsModel + "|" + cfg.ttsVoice + "|" + cfg.ttsSpeed + "|" + text;
        String name;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(key.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < d.length; i++) sb.append(String.format("%02x", d[i]));
            name = sb.toString() + ".mp3";
        } catch (Throwable t) {
            name = Integer.toHexString(key.hashCode()) + ".mp3";
        }
        return new File(cacheDir, name);
    }

    /** LRU 缓存清理：超出 CACHE_MAX 数量时清理最早的文件。 */
    /**
     * LRU 缓存清理：条数超过 {@link #CACHE_MAX}、或总量超过 {@link #CACHE_MAX_BYTES} 时按最旧文件清理。
     *
     * <p>为什么要加体积上限：条数一样多时，长故事切出来的句子文件更大，光限条数挡不住空间增长。
     * 顺手清掉合成中断留下的 {@code *.part}，它们不是缓存，只会白占地方。
     */
    private synchronized void purgeCache() {
        try {
            File[] files = cacheDir.listFiles();
            if (files == null) return;
            long now = System.currentTimeMillis();
            long total = 0;
            ArrayList<File> list = new ArrayList<File>();
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(".part")) {
                    if (now - f.lastModified() > 3600000L) f.delete();
                    continue;
                }
                total += f.length();
                list.add(f);
            }
            if (list.size() <= CACHE_MAX && total <= CACHE_MAX_BYTES) return;
            Collections.sort(list, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.valueOf(a.lastModified()).compareTo(b.lastModified());
                }
            });
            for (int i = 0; i < list.size(); i++) {
                boolean tooMany = list.size() - i > CACHE_MAX;
                boolean tooBig = total > CACHE_MAX_BYTES;
                if (!tooMany && !tooBig) break;
                File f = list.get(i);
                total -= f.length();
                f.delete();
            }
        } catch (Throwable t) {
            Log.w(TAG, "purgeCache: " + t);
        }
    }

    private void stopPlayerOnly() {
        playing = false;
        final MediaPlayer p = player;
        player = null;
        if (p != null) {
            try { p.stop(); } catch (Throwable ignored) { }
            try { p.release(); } catch (Throwable ignored) { }
        }
    }

    private void postError(final Callback cb, final String msg) {
        if (cb == null) return;
        main.post(new Runnable() {
            @Override
            public void run() { cb.onError(msg); }
        });
    }

    private boolean isConfigured(Config c) {
        return c.ttsUrl != null && !c.ttsUrl.trim().isEmpty();
    }

    public String stateJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("configured", isConfigured());
            o.put("state", status());
            o.put("lastFirstAudioMs", lastFirstAudioMs);
            o.put("lastError", lastError);
            String t = lastText == null ? "" : lastText;
            o.put("lastText", t.length() > 40 ? t.substring(0, 40) + "…" : t);
            return o.toString();
        } catch (Throwable t) {
            return "{}";
        }
    }
}
