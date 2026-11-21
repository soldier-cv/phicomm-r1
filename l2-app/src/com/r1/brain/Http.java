package com.r1.brain;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * 统一 HTTP 客户端：超时、字符集、响应体上限、错误流处理都只写一遍。
 *
 * <p>为什么要收口：原来 5 个类各写一份 {@code HttpURLConnection} 样板（网易云、QQ 音乐、
 * Home Assistant、天气、大模型），超时分别是 4/5/10 秒不等，读响应体的字节上限也各写各的，
 * 出问题要改五处。现在统一走这里，异常语义只有一种。
 *
 * <p>约定：
 * <ul>
 *   <li>2xx 读 {@code getInputStream()}，非 2xx 读 {@code getErrorStream()}，
 *       让调用方能拿到服务端的错误正文（错误信息比状态码有用）；</li>
 *   <li>响应体最多读 {@link #MAX_BYTES}，超过就截断并记日志 ——
 *       这台设备常驻内存只有几十 MB，不能因为一个接口返回大 JSON 就 OOM；</li>
 *   <li>所有流在 finally 里关闭，连接一律 {@code disconnect()}。</li>
 * </ul>
 *
 * <p>例外：TTS 音频走 {@link SpeechEngine} 自己的流式落盘，**故意不用这里** ——
 * 整段音频读进内存会在这台机器上 OOM。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 22:30 星期六
 */
public final class Http {

    private static final String TAG = Brain.TAG;

    /** 建连超时（毫秒）。局域网/公网都够用。 */
    public static final int CONNECT_TIMEOUT_MS = 5000;
    /** 默认读超时（毫秒）。 */
    public static final int READ_TIMEOUT_MS = 15000;
    /** 单次响应体上限：2MB。 */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final String UA = "R1Brain/1.0 (Android 5.1; rk322x-box)";

    private Http() {
    }

    /**
     * HTTP 响应。
     *
     * @author xudong.hua,deepseek
     * @since 2026-10-31 22:30 星期六
     */
    public static final class Response {
        /** HTTP 状态码；连接层异常时为 -1。 */
        public final int code;
        /** 响应体原始字节（最多 {@link #MAX_BYTES}）。 */
        public final byte[] bytes;

        Response(int code, byte[] bytes) {
            this.code = code;
            this.bytes = bytes;
        }

        /** 是否 2xx。 */
        public boolean ok() {
            return code >= 200 && code < 300;
        }

        /** 按 UTF-8 取文本。 */
        public String text() {
            try {
                return new String(bytes, "UTF-8");
            } catch (Throwable t) {
                return "";
            }
        }
    }

    /**
     * 拼一个小请求头表，避免每次手工 new HashMap 再逐个 put。
     *
     * @param kv 键值成对出现，如 {@code headers("Cookie", c, "Referer", r)}
     * @return 请求头表（可为空表）
     */
    public static Map<String, String> headers(String... kv) {
        Map<String, String> m = new HashMap<String, String>();
        if (kv == null) return m;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i] != null && kv[i + 1] != null) m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /**
     * 发一个 GET 并返回文本。
     *
     * @param url     地址
     * @param headers 额外请求头（可传 null）
     * @return 响应体；2xx 之外抛 IOException
     */
    public static String getText(String url, Map<String, String> headers) throws IOException {
        Response r = request("GET", url, null, null, headers);
        if (!r.ok()) throw new IOException("HTTP " + r.code);
        return r.text();
    }

    /**
     * 发一个 JSON POST 并返回文本。
     *
     * @param url     地址
     * @param json    请求体
     * @param headers 额外请求头（可传 null）
     * @return 响应体；2xx 之外抛 IOException
     */
    public static String postJson(String url, String json, Map<String, String> headers) throws IOException {
        byte[] payload;
        try {
            payload = json.getBytes("UTF-8");
        } catch (Throwable t) {
            throw new IOException("请求体编码失败: " + t);
        }
        Response r = request("POST", url, payload, "application/json; charset=utf-8", headers);
        if (!r.ok()) throw new IOException("HTTP " + r.code);
        return r.text();
    }

    /**
     * 发一个表单 POST 并返回文本。
     *
     * @param url     地址
     * @param form    表单串（调用方自行 URL 编码）
     * @param headers 额外请求头（可传 null）
     * @return 响应体；2xx 之外抛 IOException
     */
    public static String postForm(String url, String form, Map<String, String> headers) throws IOException {
        byte[] payload;
        try {
            payload = form.getBytes("UTF-8");
        } catch (Throwable t) {
            throw new IOException("请求体编码失败: " + t);
        }
        Response r = request("POST", url, payload, "application/x-www-form-urlencoded", headers);
        if (!r.ok()) throw new IOException("HTTP " + r.code);
        return r.text();
    }

    /**
     * 通用请求，按默认超时与上限。
     *
     * @param method      请求方法
     * @param url         地址
     * @param payload     请求体，可为 null
     * @param contentType 请求体类型，可为 null
     * @param headers     额外请求头，可为 null
     * @return 响应（2xx 与非 2xx 都会返回，由调用方判断）
     * @throws IOException 连接层失败
     */
    public static Response request(String method, String url, byte[] payload,
                                   String contentType, Map<String, String> headers) throws IOException {
        return request(method, url, payload, contentType, headers,
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, MAX_BYTES);
    }

    /**
     * 通用请求（自定义超时与上限）。
     *
     * @param method      请求方法
     * @param url         地址
     * @param payload     请求体，可为 null
     * @param contentType 请求体类型，可为 null
     * @param headers     额外请求头，可为 null
     * @param connectMs   建连超时
     * @param readMs      读超时
     * @param maxBytes    响应体上限
     * @return 响应
     * @throws IOException 连接层失败
     */
    public static Response request(String method, String url, byte[] payload, String contentType,
                                   Map<String, String> headers, int connectMs, int readMs,
                                   int maxBytes) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            // 这台机器是 Android 5.1，系统根证书太旧；补一份额外根，否则新站点全部握手失败
            if (conn instanceof HttpsURLConnection) {
                javax.net.ssl.SSLSocketFactory sf = Tls.socketFactory();
                if (sf != null) ((HttpsURLConnection) conn).setSSLSocketFactory(sf);
            }
            conn.setConnectTimeout(connectMs);
            conn.setReadTimeout(readMs);
            conn.setRequestProperty("User-Agent", UA);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        conn.setRequestProperty(e.getKey(), e.getValue());
                    }
                }
            }
            if (payload != null) {
                conn.setDoOutput(true);
                if (contentType != null) {
                    conn.setRequestProperty("Content-Type", contentType);
                }
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(payload);
                    os.flush();
                } finally {
                    try { os.close(); } catch (Throwable ignored) { }
                }
            }

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            return new Response(code, readAll(in, maxBytes));
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) { }
            }
        }
    }

    /** 读满上限就停，避免接口返回大包把内存打爆。 */
    private static byte[] readAll(InputStream in, int maxBytes) {
        if (in == null) return new byte[0];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        long total = 0;
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) {
                    Log.w(TAG, "HTTP 响应超过上限 " + maxBytes + " 字节，已截断");
                    break;
                }
                bos.write(buf, 0, n);
            }
        } catch (Throwable t) {
            Log.w(TAG, "读取响应失败: " + t);
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
        }
        return bos.toByteArray();
    }
}
