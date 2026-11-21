package com.r1.brain;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 和风天气数据源（官方 API）。
 *
 * <p>支持两种凭据，由 {@code weatherAuth} 决定：
 * <ul>
 *   <li>{@code apikey}（默认）：请求头 {@code X-QW-Api-Key}，老式 API KEY；</li>
 *   <li>{@code jwt}：用 Ed25519 私钥现签一个短时 token，请求头
 *       {@code Authorization: Bearer <jwt>}。控制台里叫「JSON Web Token」凭据，
 *       需要 Project ID + Key ID + 私钥三样，缺一不可。</li>
 * </ul>
 *
 * <p>域名支持账号专属 API Host——和风已逐步停用公共域名，新账号必须填自己的 Host。
 * 城市查询优先走专属 Host 的 {@code /geo/v2/city/lookup}，失败再回落公共 {@code geoapi}。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 22:55 星期六
 */
public final class QWeatherProvider implements WeatherProvider {

    private static final String TAG = Brain.TAG;
    private static final String DEFAULT_HOST = "devapi.qweather.com";
    private static final String PUBLIC_GEO_HOST = "geoapi.qweather.com";

    /** JWT 有效期（秒）。和风要求不超过 24 小时，这里取短一点更安全。 */
    private static final int JWT_TTL_SEC = 900;
    /** 提前 5 分钟换新 token，避免边界上刚好过期。 */
    private static final long JWT_REFRESH_MS = (JWT_TTL_SEC - 300) * 1000L;

    private static volatile String sJwt = "";
    private static volatile long sJwtAt = 0;
    private static volatile String sJwtFor = "";

    /** 播报用的日期词：1=明天，2=后天。 */
    static String dayWord(int day) {
        if (day == 1) return "明天";
        if (day == 2) return "后天";
        if (day >= 3) return day + "天后";
        return "";
    }

    @Override
    public String name() {
        return "qweather";
    }

    @Override
    public boolean isAvailable(Config cfg) {
        if (cfg == null) return false;
        if (isJwt(cfg)) {
            // 设备时钟还没同步（断电后 RTC 从 2018 起跳）时，签出来的 JWT 一定是过期的
            // （exp = iat + 900s 落在 2018 年），服务端必拒。直接判定不可用，
            // 让天气链去走免签名的免费源，等时间正常后自动回到和风。
            if (System.currentTimeMillis() < SANE_EPOCH_MS) {
                return false;
            }
            return !blank(cfg.weatherJwtProjectId) && !blank(cfg.weatherJwtKeyId) && !blank(cfg.weatherJwtKey);
        }
        return !blank(cfg.weatherKey);
    }

    /** 2024-01-01 00:00 UTC：早于它说明系统时间还没被 NTP 校准。 */
    private static final long SANE_EPOCH_MS = 1704067200000L;

    @Override
    public String query(Config cfg, String city, int day) throws Exception {
        String host = normalizeHost(cfg.weatherHost);
        boolean customHost = !DEFAULT_HOST.equals(host) && !PUBLIC_GEO_HOST.equals(host);

        String q = URLEncoder.encode(city, "UTF-8");
        JSONObject geo = null;
        String customErr = null;
        if (customHost) {
            try {
                geo = fetch("https://" + host + "/geo/v2/city/lookup?location=" + q, cfg);
            } catch (Exception e) {
                // 专属 Host 写错不算致命：再试公共城搜域名（老账号仍可用），
                // 但要把这次失败的原因留下来，页面才能看出是「域名解析不了」还是「Key 无效」
                customErr = e.getMessage();
                Log.w(TAG, "和风专属 Host 查询失败，改用公共 geoapi: " + customErr);
            }
        }
        if (geo == null || !geo.has("location")) {
            try {
                geo = fetch("https://" + PUBLIC_GEO_HOST + "/v2/city/lookup?location=" + q, cfg);
            } catch (Exception e) {
                if (customErr != null) {
                    throw new IllegalStateException(
                            "专属 Host(" + host + ")：" + customErr
                                    + "；公共 geoapi：" + e.getMessage(), e);
                }
                throw e;
            }
        }
        if (!geo.has("location")) {
            throw new IllegalStateException("城市「" + city + "」没有匹配到和风的城市 ID："
                    + geo.optString("code", "?") + " " + geo.optString("error", ""));
        }

        String locationId = geo.getJSONArray("location").getJSONObject(0).getString("id");
        JSONObject loc = geo.getJSONArray("location").getJSONObject(0);
        String lat = loc.optString("lat", "");
        String lon = loc.optString("lon", "");

        // 明天/后天：/v7/weather/3d 的 daily 数组
        if (day > 0) {
            JSONObject d3 = fetch("https://" + host + "/v7/weather/3d?location=" + locationId, cfg);
            JSONArray daily = d3.optJSONArray("daily");
            if (daily == null || daily.length() <= day) {
                throw new IllegalStateException("没有" + dayWord(day) + "的预报："
                        + d3.optString("code", "?") + " " + d3.optString("error", ""));
            }
            JSONObject d = daily.getJSONObject(day);
            return "【" + dayWord(day) + city + "天气】" + d.optString("textDay", d.optString("text", ""))
                    + "，" + d.optString("tempMin", "--") + "~" + d.optString("tempMax", "--") + "℃，"
                    + d.optString("windDirDay", "") + " " + d.optString("windScaleDay", "") + "级。";
        }

        // 新版 API（JWT 账号用的是这个）：/weather/v1/current/{lat}/{lon}
        if (!lat.isEmpty() && !lon.isEmpty()) {
            try {
                JSONObject cur = fetch("https://" + host + "/weather/v1/current/" + lat + "/" + lon, cfg);
                if (cur.has("temperature") || cur.has("condition")) {
                    return formatV1(city, cur);
                }
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                // 只有「这条路径不存在」才退回老接口；鉴权失败要原样抛出去，否则看不出真原因
                if (!msg.contains("HTTP 404") && !msg.contains("HTTP 400")) throw e;
                Log.w(TAG, "新版 /weather/v1/current 不可用，退回 /v7/weather/now: " + msg);
            }
        }

        JSONObject nowJson = fetch("https://" + host + "/v7/weather/now?location=" + locationId, cfg);
        if (!nowJson.has("now")) {
            throw new IllegalStateException("实时天气返回异常："
                    + nowJson.optString("code", "?") + " " + nowJson.optString("error", ""));
        }

        JSONObject now = nowJson.getJSONObject("now");
        return "【" + city + "天气】当前" + now.optString("text", "晴")
                + "，气温 " + now.optString("temp", "--") + "℃，"
                + now.optString("windDir", "") + " " + now.optString("windScale", "") + "级，"
                + "相对湿度 " + now.optString("humidity", "") + "%。";
    }

    /** 新版 `/weather/v1/current` 的响应转成一句话。 */
    private static String formatV1(String city, JSONObject cur) {
        JSONObject cond = cur.optJSONObject("condition");
        String text = cond == null ? "晴" : cond.optString("text", "晴");
        JSONObject temp = cur.optJSONObject("temperature");
        String t = temp == null ? "--" : trimNum(temp.optDouble("value", 0));
        double hum = cur.optDouble("humidity", -1);
        String humText = hum < 0 ? "" : "，相对湿度 " + Math.round(hum * 100) + "%";
        JSONObject wind = cur.optJSONObject("wind");
        String windText = "";
        if (wind != null) {
            JSONObject dir = wind.optJSONObject("direction");
            String compass = dir == null ? "" : dir.optString("compass", "");
            String scale = wind.optString("scale", "");
            if (!compass.isEmpty() || !scale.isEmpty()) {
                windText = "，" + compassCn(compass) + " " + scale + "级";
            }
        }
        return "【" + city + "天气】当前" + text + "，气温 " + t + "℃" + windText + humText + "。";
    }

    /** 数字去掉多余小数：31.71 → 31.7，31.0 → 31。 */
    private static String trimNum(double v) {
        String s = String.valueOf(Math.round(v * 10) / 10.0);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** 新版接口的罗盘方位（n/ne/sw…）转成中文。 */
    private static String compassCn(String c) {
        if (c == null) return "";
        String s = c.toLowerCase();
        if (s.equals("n")) return "北风";
        if (s.equals("ne") || s.equals("nne") || s.equals("ene")) return "东北风";
        if (s.equals("e")) return "东风";
        if (s.equals("se") || s.equals("ese") || s.equals("sse")) return "东南风";
        if (s.equals("s")) return "南风";
        if (s.equals("sw") || s.equals("ssw") || s.equals("wsw")) return "西南风";
        if (s.equals("w")) return "西风";
        if (s.equals("nw") || s.equals("wnw") || s.equals("nnw")) return "西北风";
        return "";
    }

    /** 是否用 JWT 鉴权。 */
    public static boolean isJwt(Config cfg) {
        return cfg != null && "jwt".equalsIgnoreCase(cfg.weatherAuth);
    }

    /**
     * 现签一个 JWT（带缓存，15 分钟有效）。
     *
     * @param cfg 配置
     * @return token 字符串
     * @throws Exception 私钥格式不对或签名失败
     */
    public static synchronized String token(Config cfg) throws Exception {
        if (!isJwt(cfg)) throw new IllegalStateException("当前不是 JWT 鉴权");
        String identity = cfg.weatherJwtProjectId + "|" + cfg.weatherJwtKeyId + "|" + cfg.weatherJwtKey.hashCode();
        long now = System.currentTimeMillis();
        if (!sJwt.isEmpty() && identity.equals(sJwtFor) && now - sJwtAt < JWT_REFRESH_MS) {
            return sJwt;
        }
        String t = buildJwt(cfg.weatherJwtIss, cfg.weatherJwtProjectId, cfg.weatherJwtKeyId, cfg.weatherJwtKey,
                System.currentTimeMillis() / 1000, JWT_TTL_SEC);
        sJwt = t;
        sJwtAt = now;
        sJwtFor = identity;
        Log.i(TAG, "和风 JWT 已更新，有效期 " + JWT_TTL_SEC + " 秒");
        return t;
    }

    /**
     * 构造并签名一个 JWT。
     *
     * <p>按和风文档：头部 {@code {alg:EdDSA, kid:凭据ID}}，载荷
     * {@code {iss:开发者ID, sub:项目ID, iat:签发时间-30秒, exp}}。
     * {@code iat} 减 30 秒是为了容忍设备与服务端的时钟偏差。
     *
     * @param devId      开发者 ID（JWT 的 iss，Q 开头 10 位）
     * @param projectId  项目 ID（JWT 的 sub）
     * @param keyId      凭据 ID（JWT 头部的 kid）
     * @param privateKey 私钥文本（PKCS#8 base64 或 32 字节种子）
     * @param nowSec     当前时间（秒）
     * @param ttlSec     有效期（秒，最长 86400）
     * @return 完整的 JWT
     * @throws Exception 私钥不可用
     */
    public static String buildJwt(String devId, String projectId, String keyId, String privateKey,
                                  long nowSec, int ttlSec) throws Exception {
        JSONObject header = new JSONObject();
        header.put("alg", "EdDSA");
        header.put("kid", keyId);
        JSONObject payload = new JSONObject();
        if (devId != null && !devId.trim().isEmpty()) payload.put("iss", devId.trim());
        payload.put("sub", projectId);
        payload.put("iat", nowSec - 30);
        payload.put("exp", nowSec + ttlSec);

        String h = b64url(header.toString().getBytes("UTF-8"));
        String p = b64url(payload.toString().getBytes("UTF-8"));
        byte[] signingInput = (h + "." + p).getBytes("UTF-8");
        byte[] sig = Ed25519.sign(Ed25519.seedFromPkcs8(privateKey), signingInput);
        return h + "." + p + "." + b64url(sig);
    }

    /** Host 统一成「纯域名」，容忍用户填 {@code https://xxx/} 这种写法。 */
    private static String normalizeHost(String raw) {
        String h = (raw == null || raw.trim().isEmpty()) ? DEFAULT_HOST : raw.trim();
        if (h.startsWith("http://") || h.startsWith("https://")) {
            h = h.substring(h.indexOf("://") + 3);
        }
        if (h.endsWith("/")) h = h.substring(0, h.length() - 1);
        return h;
    }

    /**
     * 请求和风接口；失败时抛出带原因的错误，不能吞掉——否则页面只能看到「失败」，
     * 分不清是凭据无效、Host 写错、还是 DNS 解析不了。
     */
    private static JSONObject fetch(String url, Config cfg) throws Exception {
        boolean jwt = isJwt(cfg);
        Http.Response res = send(url, cfg, jwt);
        if (res.code == 401 || res.code == 403) {
            if (!jwt) {
                // API KEY 支持 ?key= 查询参数，Header 被拒时再试一次，
                // 两种都失败才能断定是 Key 本身的问题。
                String withKey = url + (url.contains("?") ? "&" : "?") + "key="
                        + URLEncoder.encode(cfg.weatherKey.trim(), "UTF-8");
                Http.Response alt = send(withKey, cfg, false);
                if (alt.ok()) {
                    Log.i(TAG, "和风 Header 鉴权被拒，改用 ?key= 参数成功");
                    return new JSONObject(alt.text());
                }
                throw new IllegalStateException("HTTP " + res.code + "（X-QW-Api-Key）/ "
                        + alt.code + "（?key=）均被拒（" + hostOf(url) + "）" + bodyHint(alt.text()));
            }
            throw new IllegalStateException("HTTP " + res.code + "（JWT 被拒，检查 ProjectID/KeyID/私钥是否同一凭据，"
                    + "以及系统时间是否准确）" + bodyHint(res.text()));
        }
        if (!res.ok()) {
            throw new IllegalStateException("HTTP " + res.code + "（" + hostOf(url) + "）" + bodyHint(res.text()));
        }
        return new JSONObject(res.text());
    }

    /** 按当前鉴权方式发一次请求。 */
    private static Http.Response send(String url, Config cfg, boolean jwt) throws Exception {
        Map<String, String> h;
        if (jwt) {
            h = Http.headers("User-Agent", "R1Brain/1.0", "Accept", "application/json",
                    "Authorization", "Bearer " + token(cfg));
        } else {
            h = Http.headers("User-Agent", "R1Brain/1.0", "Accept", "application/json",
                    "X-QW-Api-Key", cfg.weatherKey.trim());
        }
        try {
            return Http.request("GET", url, null, null, h, 4000, 4000, 256 * 1024);
        } catch (Exception e) {
            throw new IllegalStateException("请求 " + hostOf(url) + " 失败：" + e.getMessage(), e);
        }
    }

    /** 把和风错误正文里的 code/error 摘出来，附在错误后面，页面才能看到具体原因。 */
    private static String bodyHint(String body) {
        if (body == null) return "";
        String s = body.trim();
        if (s.isEmpty()) return "（响应体为空）";
        try {
            JSONObject o = new JSONObject(s);
            String code = o.optString("code", "");
            String err = o.optString("error", "");
            if (!code.isEmpty() || !err.isEmpty()) {
                return "，返回 " + code + (err.isEmpty() ? "" : " " + err);
            }
        } catch (Throwable ignored) {
        }
        if (s.length() > 120) s = s.substring(0, 120) + "…";
        return "，返回 " + s;
    }

    /** 取 URL 的域名，只用于错误提示。 */
    private static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (Throwable t) {
            return url;
        }
    }

    /** JWT 各段都是 base64url 且不带 padding。 */
    private static String b64url(byte[] data) {
        return Base64.encodeToString(data,
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
