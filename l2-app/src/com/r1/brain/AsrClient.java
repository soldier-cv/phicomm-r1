package com.r1.brain;

import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

/**
 * 整合 ASR 网关的客户端（替代旧的自建 SenseVoice 网关）。
 *
 * <p>对端契约（见网关 INTEGRATION.md）：
 * <ul>
 *   <li>{@code POST /v1/asr/transcribe}，multipart 字段 {@code file}，请求头
 *       {@code X-API-Key: <令牌>}，返回 {@code {text, duration_seconds, ...}}；</li>
 *   <li>{@code GET /usage}，同样带 X-API-Key，返回配额快照，给设置页「测试/用量」用；</li>
 *   <li>错误体 {@code {error:{code,message}}}；{@code QUOTA_EXCEEDED} 为 HTTP 429，
 *       表示本月免费额度已用尽，必须立刻停止调用并播报明确提示。</li>
 * </ul>
 *
 * <p>按次数计费下不用流式：流式会话从 ready 帧就计一次、反复开关更烧次数，
 * 且网关的 WS 协议与旧的 FunASR 不同；整段上传每句话只计一次，最省额度。
 *
 * @author xudong.hua,deepseek
 * @since 2026-11-01 15:40 星期日
 */
public final class AsrClient {

    private static final String TAG = Brain.TAG;
    /** 文档规定：单次音频 1~10 秒。 */
    public static final int MAX_SECONDS = 10;
    /** 首包要等模型加载，健康检查放宽一点。 */
    private static final int CONNECT_MS = 3000;
    private static final int READ_MS = 30000;

    private AsrClient() {
    }

    /**
     * 一次转写的结果。
     *
     * @author xudong.hua,deepseek
     * @since 2026-11-01 15:40 星期日
     */
    public static final class Result {
        /** 识别文本；失败时为空串。 */
        public final String text;
        /** 音频时长（毫秒）；网关返回 duration_seconds 时换算，没返回时为 0。 */
        public final int durationMs;
        /** 实际使用的引擎名（provider_type / provider）；没返回时为空串。 */
        public final String engine;
        /** 走的是哪个接口，排查用。 */
        public final String endpoint;

        Result(String text, int durationMs, String engine, String endpoint) {
            this.text = text;
            this.durationMs = durationMs;
            this.engine = engine;
            this.endpoint = endpoint;
        }
    }

    /** 额度用尽异常：网关返回 QUOTA_EXCEEDED（429），上层要播报明确提示而非「没听清」。 */
    public static final class QuotaExceededException extends Exception {
        public QuotaExceededException(String message) {
            super(message);
        }
    }

    /** 是否配置了整合 ASR 网关（self + 地址非空 + 令牌非空，缺令牌会 401）。 */
    public static boolean isConfigured(Config cfg) {
        return cfg != null && "self".equals(cfg.asrEngine)
                && cfg.asrUrl != null && !cfg.asrUrl.trim().isEmpty()
                && cfg.asrToken != null && !cfg.asrToken.trim().isEmpty();
    }

    /**
     * 把一段音频转成文字。
     *
     * @param cfg   配置（取 asrUrl）
     * @param audio WAV（带 RIFF 头）或裸 PCM（16k/16bit/单声道）
     * @return 结果；成功时 text 非空
     * @throws Exception 网络错误或服务端返回错误
     */
    public static Result transcribe(Config cfg, byte[] audio) throws Exception {
        if (audio == null || audio.length == 0) throw new IllegalArgumentException("音频为空");
        String base = normalize(cfg.asrUrl);
        if (base.isEmpty()) throw new IllegalStateException("没有填写 ASR 网关地址");
        byte[] wav = audio.length > 4 && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F'
                ? audio : wavHeader(audio.length, 16000, 1, 16);

        // 整合网关统一走 /v1/asr/transcribe：multipart 的 file 字段 + X-API-Key。
        // 不再先试裸 WAV /asr —— 那是旧 SenseVoice 网关的私有接口，新网关没有。
        byte[] multipart = multipart(wav);
        String token = cfg.asrToken == null ? "" : cfg.asrToken.trim();
        Http.Response res = Http.request("POST", base + "/v1/asr/transcribe", multipart,
                "multipart/form-data; boundary=" + BOUNDARY,
                Http.headers("X-API-Key", token, "Accept", "application/json"),
                CONNECT_MS, READ_MS, 256 * 1024);

        // 额度用尽（429）单独抛出，让上层播报明确提示；不重试、不换账号（网关也不切换）。
        if (res.code == 429) {
            throw new QuotaExceededException(quotaMessage(res.text()));
        }
        if (!res.ok()) {
            throw new IllegalStateException("HTTP " + res.code + " " + errorHint(res.text()));
        }

        JSONObject o = new JSONObject(res.text());
        // 业务错误也可能藏在 200 的 body 里（网关规范：程序按 error.code 分支）。
        if (o.has("error")) {
            JSONObject err = o.optJSONObject("error");
            String code = err == null ? "" : err.optString("code", "");
            if ("QUOTA_EXCEEDED".equals(code)) {
                throw new QuotaExceededException(err.optString("message", "本月免费额度已用完。"));
            }
            throw new IllegalStateException(errorHint(res.text()));
        }
        String text = o.optString("text", "").trim();
        // duration_seconds 是秒（保留 3 位）；转成毫秒，没返回就 0。
        double sec = o.optDouble("duration_seconds", 0);
        if (Double.isNaN(sec) || sec < 0) sec = 0;
        int durationMs = (int) Math.round(sec * 1000);
        // provider_type 当引擎名展示（tencent / local 等），没有就空串。
        String engine = o.optString("provider_type", o.optString("provider", ""));
        // 整合网关不返回句级置信度，不再保留 confidence 字段。
        return new Result(text, durationMs, engine, "/v1/asr/transcribe");
    }

    /**
     * 用量/健康检查：整合网关的 {@code GET /usage}（带 X-API-Key）。
     *
     * <p>这个请求不消耗月度次数。返回原始 JSON（含 billing_month / 令牌 used/remaining），
     * 页面直接展示。正因为“读用量不算次数”，大动作前先看剩余量是安全的。
     *
     * @param baseUrl 网关地址（可带结尾斜杠）
     * @param token   调用方令牌明文
     * @return 服务端返回的 JSON
     * @throws Exception 连不上或非 2xx
     */
    public static JSONObject health(String baseUrl, String token) throws Exception {
        String base = normalize(baseUrl);
        if (base.isEmpty()) throw new IllegalStateException("没有填写 ASR 网关地址");
        String tk = token == null ? "" : token.trim();
        Http.Response res = Http.request("GET", base + "/usage", null, null,
                Http.headers("X-API-Key", tk, "Accept", "application/json"),
                CONNECT_MS, 8000, 128 * 1024);
        if (res.code == 429) {
            throw new QuotaExceededException(quotaMessage(res.text()));
        }
        if (!res.ok()) {
            throw new IllegalStateException("HTTP " + res.code + " " + errorHint(res.text()));
        }
        return new JSONObject(res.text());
    }

    /** 地址规范化：去空格、去结尾斜杠、缺协议时补 http。 */
    public static String normalize(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    /** 从服务端错误体里摘 message，便于页面显示。 */
    private static String errorHint(String body) {
        if (body == null) return "";
        try {
            JSONObject o = new JSONObject(body);
            JSONObject err = o.optJSONObject("error");
            if (err != null) {
                String code = err.optString("code", "");
                String msg = err.optString("message", "");
                return (code.isEmpty() ? "" : code + "：") + msg;
            }
        } catch (Throwable ignored) {
        }
        String s = body.trim();
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }

    /** 从 QUOTA_EXCEEDED 错误体里摘一句适合播报的短提示。网关原始 message 很长，不适合念出来。 */
    private static String quotaMessage(String body) {
        String msg = errorHint(body); // 形如 "QUOTA_EXCEEDED：令牌月度额度已用尽：…"
        return msg.isEmpty() ? "本月语音识别额度已用完，请下个月再试。" : "本月语音识别额度已用完。";
    }

    private static final String BOUNDARY = "----R1BrainFormBoundary";

    /** 拼一个最小的 multipart：只需要 file 一个字段。 */
    private static byte[] multipart(byte[] wav) throws Exception {
        String head = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"clip.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n";
        String tail = "\r\n--" + BOUNDARY + "--\r\n";
        ByteArrayOutputStream bos = new ByteArrayOutputStream(wav.length + 256);
        bos.write(head.getBytes("UTF-8"));
        bos.write(wav);
        bos.write(tail.getBytes("UTF-8"));
        return bos.toByteArray();
    }

    /** 给裸 PCM 套一个 44 字节的 WAV 头。 */
    public static byte[] wavHeader(int dataLen, int rate, int channels, int bits) {
        byte[] h = new byte[44];
        int byteRate = rate * channels * bits / 8;
        int blockAlign = channels * bits / 8;
        h[0] = 'R'; h[1] = 'I'; h[2] = 'F'; h[3] = 'F';
        writeInt(h, 4, 36 + dataLen);
        h[8] = 'W'; h[9] = 'A'; h[10] = 'V'; h[11] = 'E';
        h[12] = 'f'; h[13] = 'm'; h[14] = 't'; h[15] = ' ';
        writeInt(h, 16, 16);
        h[20] = 1; h[21] = 0;              // PCM
        h[22] = (byte) channels; h[23] = 0;
        writeInt(h, 24, rate);
        writeInt(h, 28, byteRate);
        h[32] = (byte) blockAlign; h[33] = 0;
        h[34] = (byte) bits; h[35] = 0;
        h[36] = 'd'; h[37] = 'a'; h[38] = 't'; h[39] = 'a';
        writeInt(h, 40, dataLen);
        return h;
    }

    private static void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }
}
