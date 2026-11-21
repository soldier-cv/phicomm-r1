package com.phicomm.speaker.device.bridge;

/**
 * 录音写盘前的纯计算：判声道、下混、自适应增益 + 软限幅、句尾判停。
 *
 * <p>为什么单独成类：这里不碰任何 {@code android.*}，可以在电脑上直接用 JDK 跑
 * （见 {@code tools/VoiceDspProbe.java}），不用为了调一个阈值去重装 31MB 的 L1。
 *
 * <p>所有方法都跑在原厂录音线程上：只做整数/浮点运算，不分配大对象、不抛异常。
 *
 * @author xudong.hua,claude
 * @since 2026-11-05 17:40 星期四
 */
final class VoiceDsp {

    /** 写盘采样率（16k 单声道，ASR 网关只收这个）。 */
    static final int RATE = 16000;

    private VoiceDsp() {
    }

    /** 读一个小端 16 位有符号样点；越界返回 0。 */
    static int s16(byte[] b, int off) {
        if (off < 0 || off + 1 >= b.length) {
            return 0;
        }
        return (short) ((b[off] & 0xff) | (b[off + 1] << 8));
    }

    /**
     * 判输入是单声道还是双声道交织。
     *
     * <p>依据：双声道交织时「同一帧左右两点」来自两个麦克风，差值明显大；
     * 「同一声道相邻两帧」是连续的波形，差值很小。实测 2026-11-03 那段录音两点差 44.9、相邻差 7.4。
     *
     * @return 2 = 双声道交织，1 = 单声道
     */
    static int detectChannels(byte[] b, int len) {
        long between = 0;
        long within = 0;
        int pairs = 0;
        for (int i = 0; i + 5 < len && pairs < 4000; i += 4) {
            int l = s16(b, i);
            int r = s16(b, i + 2);
            int next = s16(b, i + 4);
            between += Math.abs(l - r);
            within += Math.abs(l - next);
            pairs++;
        }
        if (pairs == 0) {
            return 0;
        }
        long betweenAvg = between / pairs;
        long withinAvg = within / pairs;
        // 太安静（底噪或数字静音）时左右声道没有差异，判不出——返回 0 让调用方沿用上一轮的结论，
        // 不要在这里猜单声道：猜错会把双声道交织按单声道写盘，ASR 只能拿到噪声。
        if (betweenAvg < 20 && withinAvg < 20) {
            return 0;
        }
        return betweenAvg > withinAvg * 3 ? 2 : 1;
    }

    /** 一段 PCM 的均方根（只看第一个声道，够用）。 */
    static int rms(byte[] b, int len, int channels) {
        int step = channels == 2 ? 4 : 2;
        long sum = 0;
        int n = 0;
        for (int i = 0; i + 1 < len; i += step) {
            int v = s16(b, i);
            sum += (long) v * v;
            n++;
        }
        if (n == 0) {
            return 0;
        }
        return (int) Math.sqrt((double) sum / n);
    }

    /**
     * 自适应增益：下混成单声道、逐块调增益、软限幅后写进输出缓冲。
     *
     * <p>为什么不再「按开头 0.6 秒估一次」：唤醒后用户通常停顿一下，开头那段多半是底噪，
     * 按它估出来就是 12 倍，正式说话时整段削成方波。现在：
     * <ul>
     *   <li>增益只在「像说话」的块上往上调，静音块保持不动——不会把底噪抬到满幅；</li>
     *   <li>块峰值放大后会超标就立刻降（快攻慢放），块内线性过渡，避免咔哒声；</li>
     *   <li>超过拐点的样点用 tanh 软压缩，不再硬截到 ±32000。</li>
     * </ul>
     */
    static final class Agc {

        /** 目标峰值（约 -11 dBFS，接口文档建议 -20 ~ -6 dBFS）。 */
        static final int TARGET_PEAK = 9000;
        static final float MAX_GAIN = 12f;
        /** 开头没听到人声时的起始增益：麦克风很闷（实测说话峰值 362），1 倍偏小。 */
        static final float START_GAIN = 2f;
        /** 说话块上每块向目标增益靠近的比例。 */
        static final float RELEASE = 0.25f;
        /** 软限幅拐点，超过它才压缩。 */
        static final int KNEE = 20000;
        static final int CEIL = 32000;
        /** 比底噪高这么多倍才算人声。 */
        static final int SPEECH_RATIO = 3;
        static final int MIN_FLOOR = 4;

        private float gain;
        private int floor;
        private int lastRms;
        private int lastPeak;
        private boolean lastSpeech;
        private int clipped;

        Agc() {
            gain = START_GAIN;
        }

        /**
         * 用开头的探测段初始化：底噪取最安静的 100ms，若探测段里已经有人声就直接按它定增益。
         *
         * @param b        探测段 PCM（原始输入格式）
         * @param len      有效字节数
         * @param channels 输入声道数
         */
        void prime(byte[] b, int len, int channels) {
            int frameBytes = channels == 2 ? 4 : 2;
            int win = RATE / 10 * frameBytes;
            int quiet = Integer.MAX_VALUE;
            int peak = 0;
            for (int off = 0; off + win <= len; off += win) {
                long sum = 0;
                int n = 0;
                for (int i = off; i + 1 < off + win; i += frameBytes) {
                    int v = mono(b, i, channels);
                    sum += (long) v * v;
                    if (Math.abs(v) > peak) peak = Math.abs(v);
                    n++;
                }
                int r = n == 0 ? 0 : (int) Math.sqrt((double) sum / n);
                if (r < quiet) quiet = r;
            }
            floor = Math.max(MIN_FLOOR, quiet == Integer.MAX_VALUE ? 0 : quiet);
            if (peak > floor * SPEECH_RATIO * 2) {
                gain = clamp((float) TARGET_PEAK / peak);
            }
        }

        /**
         * 处理一块 PCM。
         *
         * @param in        原始输入
         * @param len       有效字节数
         * @param channels  输入声道数（1 或 2）
         * @param out       输出缓冲（单声道 16bit）
         * @param maxFrames 最多写多少帧
         * @return 写出的帧数
         */
        int process(byte[] in, int len, int channels, byte[] out, int maxFrames) {
            int frameBytes = channels == 2 ? 4 : 2;
            int frames = Math.min(len / frameBytes, Math.min(maxFrames, out.length / 2));
            if (frames <= 0) {
                return 0;
            }
            long sum = 0;
            int peak = 0;
            for (int f = 0; f < frames; f++) {
                int v = mono(in, f * frameBytes, channels);
                sum += (long) v * v;
                if (Math.abs(v) > peak) peak = Math.abs(v);
            }
            int r = (int) Math.sqrt((double) sum / frames);
            lastRms = r;
            lastPeak = peak;
            trackFloor(r);
            lastSpeech = r > floor * SPEECH_RATIO;

            float g0 = gain;
            float g1 = g0;
            float desired = peak > 0 ? clamp((float) TARGET_PEAK / peak) : g0;
            if (desired < g0) {
                g1 = desired;
            } else if (lastSpeech) {
                g1 = g0 + (desired - g0) * RELEASE;
            }
            gain = g1;

            for (int f = 0; f < frames; f++) {
                float g = g0 + (g1 - g0) * f / frames;
                int m = soft((int) (mono(in, f * frameBytes, channels) * g));
                out[f * 2] = (byte) m;
                out[f * 2 + 1] = (byte) (m >> 8);
            }
            return frames;
        }

        /**
         * 底噪跟踪：往下快；像底噪的块慢慢往上（比如电视打开了）；像人声的块几乎不动，
         * 否则一句长话会把底噪拖上去，后半句就不算「在说话」了。
         */
        private void trackFloor(int r) {
            if (floor <= 0) {
                floor = Math.max(MIN_FLOOR, r);
            } else if (r < floor) {
                floor = Math.max(MIN_FLOOR, (floor * 7 + r * 3) / 10);
            } else if (r <= floor * SPEECH_RATIO) {
                floor = Math.max(MIN_FLOOR, (floor * 49 + r) / 50);
            } else {
                floor = Math.max(MIN_FLOOR, (floor * 999 + r) / 1000);
            }
        }

        private int soft(int x) {
            int a = Math.abs(x);
            if (a <= KNEE) {
                return x;
            }
            clipped++;
            double room = CEIL - KNEE;
            int y = (int) (KNEE + room * Math.tanh((a - KNEE) / room));
            return x < 0 ? -y : y;
        }

        private static float clamp(float g) {
            if (g < 1f) return 1f;
            return g > MAX_GAIN ? MAX_GAIN : g;
        }

        private static int mono(byte[] b, int off, int channels) {
            if (channels == 2) {
                return (s16(b, off) + s16(b, off + 2)) / 2;
            }
            return s16(b, off);
        }

        float gain() {
            return gain;
        }

        int floor() {
            return floor;
        }

        int lastRms() {
            return lastRms;
        }

        int lastPeak() {
            return lastPeak;
        }

        /** 最近一块是否像人声（比底噪高 {@link #SPEECH_RATIO} 倍）。 */
        boolean lastSpeech() {
            return lastSpeech;
        }

        /** 本轮进过软限幅的样点数（日志用）。 */
        int clipped() {
            return clipped;
        }

        /** 续听轮沿用上一轮学到的增益。 */
        void setGain(float g) {
            gain = clamp(g);
        }
    }

    /**
     * 句尾判停：说过话 + 静音够久就收尾。
     *
     * <p>2026-11-04 21:50 关掉旧判停的原因：录音开头带着唤醒词「小讯小讯」，
     * 它本身就满足「说满 0.8 秒 + 静音 1.5 秒」，正题还没开口就被截断。
     * 这里加一条：<b>起点落在宽限期内的那一段声音不算数</b>（那是唤醒词的尾巴），
     * 只有宽限期之后才开口的那段话，后面的静音才能触发收尾。
     */
    static final class Endpointer {

        static final int CONTINUE = 0;
        /** 说完了，可以收尾。 */
        static final int END = 1;
        /** 等了这么久都没人开口（续听用）。 */
        static final int NO_SPEECH = 2;

        /** 一段声音之间的静音短于它，视为同一段（换气、字间停顿）。 */
        private static final int GAP_MS = 250;

        private final int graceMs;
        private final int minSpeechMs;
        private final int silenceMs;
        private final int noSpeechMs;

        private int elapsed;
        private int countedSpeechMs;
        private int silentMs;
        private boolean inSegment;
        private boolean segmentCounts;

        /**
         * @param graceMs     起点早于它的声音段不算（唤醒词尾巴）；续听传 0
         * @param minSpeechMs 至少说满这么久才允许收尾
         * @param silenceMs   说完后静音这么久就收尾
         * @param noSpeechMs  这么久还没说满 minSpeechMs 就放弃；0 表示不放弃（交给原厂 VAD）
         */
        Endpointer(int graceMs, int minSpeechMs, int silenceMs, int noSpeechMs) {
            this.graceMs = graceMs;
            this.minSpeechMs = minSpeechMs;
            this.silenceMs = silenceMs;
            this.noSpeechMs = noSpeechMs;
        }

        /**
         * 喂一块的判定结果。
         *
         * @param speech  这一块是否像人声
         * @param chunkMs 这一块的时长
         * @return {@link #CONTINUE} / {@link #END} / {@link #NO_SPEECH}
         */
        int feed(boolean speech, int chunkMs) {
            if (speech) {
                if (!inSegment) {
                    inSegment = true;
                    segmentCounts = elapsed >= graceMs;
                }
                silentMs = 0;
                if (segmentCounts) {
                    countedSpeechMs += chunkMs;
                }
            } else {
                silentMs += chunkMs;
                if (silentMs >= GAP_MS) {
                    inSegment = false;
                }
            }
            elapsed += chunkMs;
            if (countedSpeechMs >= minSpeechMs && silentMs >= silenceMs) {
                return END;
            }
            if (noSpeechMs > 0 && countedSpeechMs < minSpeechMs && elapsed >= noSpeechMs) {
                return NO_SPEECH;
            }
            return CONTINUE;
        }

        /** 已经算数的说话时长（日志用）。 */
        int speechMs() {
            return countedSpeechMs;
        }

        int elapsedMs() {
            return elapsed;
        }
    }
}
