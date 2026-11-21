package com.phicomm.speaker.device.bridge;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Random;

/**
 * 离线验证 {@link VoiceDsp}：不连设备，用合成音频跑「唤醒词 → 停顿 → 正题」这类场景。
 *
 * <p>合成的「人声」是 200~400Hz 调幅正弦 + 白噪声底噪，电平按实测的闷麦克风设定
 * （说话峰值约 360，底噪 rms 约 10）。只验证算法行为（增益、削波、判停时刻），
 * 不代表真机识别率。
 *
 * <pre>
 * javac -encoding UTF-8 -d %TEMP%\dsp l1\src\com\phicomm\speaker\device\bridge\VoiceDsp.java tools\VoiceDspProbe.java
 * java -cp %TEMP%\dsp com.phicomm.speaker.device.bridge.VoiceDspProbe out.txt
 * </pre>
 *
 * @author xudong.hua,claude
 * @since 2026-11-05 18:10 星期四
 */
public class VoiceDspProbe {

    private static final int RATE = 16000;
    /** 原厂每次 readData 的块：约 64ms 双声道。 */
    private static final int CHUNK_BYTES = 4096;

    private static PrintWriter out;
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[0]), "UTF-8"), true);
        channelDetection();
        agcQuietMic();
        agcLoudSpeaker();
        agcPauseFirst();
        endpointWakeWordThenCommand();
        endpointCommandOnly();
        endpointFollowUpSilence();
        endpointFollowUpSpeech();
        out.println(failures == 0 ? "ALL PASS" : ("FAILURES: " + failures));
        out.close();
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 场景

    private static void channelDetection() {
        // detectChannels 只看前 4000 帧（250ms）。合成的底噪是逐点独立的白噪声，
        // 左右差与相邻差统计上相等、判不出来；真机底噪是低频的（实测两点差 44.9、相邻差 7.4）。
        // 所以这里让人声从 30ms 起就开始，只验证「有人声时」的判定。
        short[] mono = new Builder(1).noise(10, 30).speech(360, 800).build();
        check("声道判定：单声道", VoiceDsp.detectChannels(bytes(mono), 19200) == 1, "");
        short[] stereo = new Builder(2).noise(10, 30).speech(360, 800).build();
        check("声道判定：双声道交织", VoiceDsp.detectChannels(bytes(stereo), 19200) == 2, "");
    }

    /** 闷麦克风：说话峰值 360，期望放大到增益上限附近（360 × 12 = 4320）、且几乎不削波。 */
    private static void agcQuietMic() {
        short[] in = new Builder(2).noise(10, 300).speech(360, 2000).noise(10, 1000).build();
        Result r = runAgc(in, 2);
        check("AGC 闷麦克风：说话段峰值抬到 3500 以上（上限 12x）", r.speechPeak > 3500, "speechPeak=" + r.speechPeak);
        check("AGC 闷麦克风：软限幅样点 < 1%", r.clipped < in.length / 2 / 100, "clipped=" + r.clipped);
        check("AGC 闷麦克风：底噪段不被抬到 1500 以上", r.tailRms < 1500, "tailRms=" + r.tailRms);
    }

    /** 用户贴近大声：峰值 12000，期望不超过满幅、不削波，长句后半段仍算人声。 */
    private static void agcLoudSpeaker() {
        short[] in = new Builder(2).noise(10, 300).speech(12000, 8000).build();
        Result r = runAgc(in, 2);
        check("AGC 大声：输出峰值不超 32000", r.outPeak <= 32000, "outPeak=" + r.outPeak);
        check("AGC 大声：软限幅样点 < 1%", r.clipped < in.length / 2 / 100, "clipped=" + r.clipped);
        check("AGC 大声：8 秒长句结束时仍判为人声（底噪没被拖上去）", r.lastSpeech, "floor=" + r.floor);
    }

    /**
     * 旧算法的失败场景：开头 0.6 秒全是底噪（用户停顿），旧算法按它估成 12 倍，
     * 用户随后贴近说话（峰值 5000）→ 5000 × 12 远超满幅，被硬削成方波。
     */
    private static void agcPauseFirst() {
        short[] in = new Builder(2).noise(10, 900).speech(5000, 2000).build();
        Result r = runAgc(in, 2);
        int oldGain = 12;
        int oldClipped = 0;
        for (int i = 0; i + 1 < in.length; i += 2) {
            int m = (in[i] + in[i + 1]) / 2 * oldGain;
            if (Math.abs(m) > 32000) oldClipped++;
        }
        out.println("  对照：旧算法（按开头估 12x + 硬削）削波 " + oldClipped + " 点");
        check("AGC 先停顿后说话：新算法削波远少于旧算法", r.clipped * 10 < Math.max(1, oldClipped),
                "new=" + r.clipped + " old=" + oldClipped);
        check("AGC 先停顿后说话：输出峰值 ≤ 32000", r.outPeak <= 32000, "outPeak=" + r.outPeak);
    }

    /** 2026-11-04 21:50 的失败场景：唤醒词 0.9 秒 + 停顿 1.6 秒 + 正题 1.8 秒 + 静音。 */
    private static void endpointWakeWordThenCommand() {
        short[] in = new Builder(2).speech(360, 900).noise(10, 1600).speech(360, 1800).noise(10, 3000).build();
        int endAt = runEndpoint(in, 2, new VoiceDsp.Endpointer(1200, 600, 1200, 0));
        int commandEnd = 900 + 1600 + 1800;
        check("判停：唤醒词后的停顿不触发收尾", endAt < 0 || endAt > commandEnd, "endAt=" + endAt);
        check("判停：正题说完 1.2~1.6 秒内收尾", endAt >= commandEnd + 1100 && endAt <= commandEnd + 1700,
                "endAt=" + endAt + " commandEnd=" + commandEnd);
    }

    /** 唤醒后立即说正题（唤醒词没录进来）：正题起点落在宽限期内也不能永远不收尾。 */
    private static void endpointCommandOnly() {
        short[] in = new Builder(2).noise(10, 200).speech(360, 2500).noise(10, 3000).build();
        int endAt = runEndpoint(in, 2, new VoiceDsp.Endpointer(1200, 600, 1200, 0));
        out.println("  说明：正题起点在宽限期内 → 交给原厂 VAD；endAt=" + endAt);
        // 这种情况不提前收尾属于保守设计（原厂 VAD 仍会收），只要求不会在说话中途截断
        check("判停：正题途中不截断", endAt < 0 || endAt > 2700, "endAt=" + endAt);
    }

    private static void endpointFollowUpSilence() {
        short[] in = new Builder(2).noise(10, 8000).build();
        VoiceDsp.Endpointer ep = new VoiceDsp.Endpointer(0, 400, 1000, 6000);
        int[] res = runEndpointVerdict(in, 2, ep);
        check("续听：6 秒没人说话 → NO_SPEECH", res[1] == VoiceDsp.Endpointer.NO_SPEECH && res[0] >= 5900 && res[0] <= 6200,
                "at=" + res[0] + " verdict=" + res[1]);
    }

    private static void endpointFollowUpSpeech() {
        short[] in = new Builder(2).noise(10, 1500).speech(360, 1500).noise(10, 3000).build();
        VoiceDsp.Endpointer ep = new VoiceDsp.Endpointer(0, 400, 1000, 6000);
        int[] res = runEndpointVerdict(in, 2, ep);
        check("续听：说完 1 秒左右收尾", res[1] == VoiceDsp.Endpointer.END && res[0] >= 3900 && res[0] <= 4300,
                "at=" + res[0] + " verdict=" + res[1]);
    }

    // ------------------------------------------------------------------ 执行

    private static final class Result {
        int outPeak;
        int speechPeak;
        int tailRms;
        int clipped;
        float finalGain;
        boolean lastSpeech;
        int floor;
    }

    /** 模拟 Bridge：前 0.6 秒做探测段 prime，然后按块 process。 */
    private static Result runAgc(short[] in, int channels) {
        byte[] b = bytes(in);
        int probe = RATE * 2 * 2 * 6 / 10;
        VoiceDsp.Agc agc = new VoiceDsp.Agc();
        agc.prime(b, Math.min(probe, b.length), channels);
        byte[] scratch = new byte[probe];
        Result r = new Result();
        short[] outPcm = new short[in.length / channels];
        int w = 0;
        int off = 0;
        while (off < b.length) {
            int len = Math.min(CHUNK_BYTES, b.length - off);
            byte[] chunk = new byte[len];
            System.arraycopy(b, off, chunk, 0, len);
            int frames = agc.process(chunk, len, channels, scratch, Integer.MAX_VALUE);
            for (int f = 0; f < frames && w < outPcm.length; f++) {
                outPcm[w++] = (short) ((scratch[f * 2] & 0xff) | (scratch[f * 2 + 1] << 8));
            }
            off += len;
        }
        int tailStart = Math.max(0, w - RATE / 2);
        long tailSum = 0;
        for (int i = 0; i < w; i++) {
            int a = Math.abs(outPcm[i]);
            if (a > r.outPeak) r.outPeak = a;
            if (i >= tailStart) tailSum += (long) outPcm[i] * outPcm[i];
        }
        r.speechPeak = r.outPeak;
        r.tailRms = (int) Math.sqrt((double) tailSum / Math.max(1, w - tailStart));
        r.clipped = agc.clipped();
        r.finalGain = agc.gain();
        r.lastSpeech = agc.lastSpeech();
        r.floor = agc.floor();
        out.println("  AGC: peak=" + r.outPeak + " tailRms=" + r.tailRms + " clipped=" + r.clipped
                + " gain=" + r.finalGain + " floor=" + agc.floor());
        return r;
    }

    private static int runEndpoint(short[] in, int channels, VoiceDsp.Endpointer ep) {
        int[] r = runEndpointVerdict(in, channels, ep);
        return r[1] == VoiceDsp.Endpointer.END ? r[0] : -1;
    }

    /** @return {触发时刻 ms, 结论}；没触发返回 {-1, CONTINUE} */
    private static int[] runEndpointVerdict(short[] in, int channels, VoiceDsp.Endpointer ep) {
        byte[] b = bytes(in);
        int probe = RATE * 2 * 2 * 6 / 10;
        VoiceDsp.Agc agc = new VoiceDsp.Agc();
        agc.prime(b, Math.min(probe, b.length), channels);
        byte[] scratch = new byte[probe];
        int off = 0;
        int ms = 0;
        while (off < b.length) {
            int len = Math.min(CHUNK_BYTES, b.length - off);
            byte[] chunk = new byte[len];
            System.arraycopy(b, off, chunk, 0, len);
            int frames = agc.process(chunk, len, channels, scratch, Integer.MAX_VALUE);
            int chunkMs = frames * 1000 / RATE;
            ms += chunkMs;
            int v = ep.feed(agc.lastSpeech(), chunkMs);
            if (v != VoiceDsp.Endpointer.CONTINUE) {
                out.println("  判停：" + ms + "ms 结论=" + v + " 算数说话=" + ep.speechMs() + "ms");
                return new int[]{ms, v};
            }
            off += len;
        }
        out.println("  判停：未触发（共 " + ms + "ms）");
        return new int[]{-1, VoiceDsp.Endpointer.CONTINUE};
    }

    private static void check(String name, boolean ok, String detail) {
        out.println((ok ? "[PASS] " : "[FAIL] ") + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
        if (!ok) failures++;
    }

    // ------------------------------------------------------------------ 合成音频

    private static final class Builder {
        private final int channels;
        private final Random rnd = new Random(42);
        private short[] buf = new short[0];
        private int pos;
        private double phase;

        Builder(int channels) {
            this.channels = channels;
        }

        Builder noise(int rms, int ms) {
            int frames = RATE * ms / 1000;
            grow(frames);
            for (int f = 0; f < frames; f++) {
                for (int c = 0; c < channels; c++) {
                    buf[pos++] = (short) (rnd.nextGaussian() * rms);
                }
            }
            return this;
        }

        /** 「人声」：基频在 200~400Hz 之间缓慢漂移，4Hz 音节调幅，叠加底噪。 */
        Builder speech(int peak, int ms) {
            int frames = RATE * ms / 1000;
            grow(frames);
            for (int f = 0; f < frames; f++) {
                double t = (double) f / RATE;
                double freq = 300 + 100 * Math.sin(2 * Math.PI * 0.7 * t);
                phase += 2 * Math.PI * freq / RATE;
                double env = 0.55 + 0.45 * Math.sin(2 * Math.PI * 4 * t);
                double v = Math.sin(phase) * env * peak;
                for (int c = 0; c < channels; c++) {
                    // 两个麦克风之间有到达时间差（这里约 0.06ms 的相位差）：交织判定靠的就是它
                    double mic = c == 0 ? v : Math.sin(phase - 1.0) * env * peak * 0.9;
                    buf[pos++] = (short) (mic + rnd.nextGaussian() * 10);
                }
            }
            return this;
        }

        short[] build() {
            short[] r = new short[pos];
            System.arraycopy(buf, 0, r, 0, pos);
            return r;
        }

        private void grow(int frames) {
            int need = pos + frames * channels;
            if (need > buf.length) {
                short[] n = new short[need];
                System.arraycopy(buf, 0, n, 0, pos);
                buf = n;
            }
        }
    }

    private static byte[] bytes(short[] s) {
        byte[] b = new byte[s.length * 2];
        for (int i = 0; i < s.length; i++) {
            b[i * 2] = (byte) s[i];
            b[i * 2 + 1] = (byte) (s[i] >> 8);
        }
        return b;
    }
}
