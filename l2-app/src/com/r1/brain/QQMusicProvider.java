package com.r1.brain;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * QQ 音乐的最小实现：搜索（免登录）+ 取播放地址（需登录 Cookie）。
 *
 * <p>接口与结论均来自 2026-10-31 的真机/本机实测：
 * <ul>
 *   <li>搜索：{@code c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg}，匿名可用，返回歌曲 mid；</li>
 *   <li>取地址：{@code u.y.qq.com/cgi-bin/musics.fcg} 的 {@code vkey.GetVkeyServer.CgiGetVkey}，
 *       必须带 {@code sign}（zzc 算法），且匿名时 {@code purl} 为空、{@code result=104003}，
 *       所以「登录」是能否出声的前置条件，与网易云一致；</li>
 *   <li>老接口 {@code c.y.qq.com/soso/fcgi-bin/client_search_cp} 已不再返回任何歌曲，勿用。</li>
 * </ul>
 *
 * <p>签名算法 {@code zzc} 按公开的请求签名规则自行实现（取位 + 异或 + Base64），
 * 只用 {@link MessageDigest} 与 {@link Base64}，不引入任何第三方依赖。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 12:40 星期六
 */
public class QQMusicProvider {

    private static final String TAG = Brain.TAG;
    private static final String UA = "Mo" + "zilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final String REFERER = "https://y.qq.com/";

    /** 固定设备号，vkey 接口要求，取值不影响匿名/登录判定。 */
    private static final String GUID = "1000";

    /** zzc 签名取位表。 */
    private static final int[] PART_1 = {23, 14, 6, 36, 16, 7, 19};
    private static final int[] PART_2 = {16, 1, 32, 12, 19, 27, 8, 5};
    private static final int[] SCRAMBLE = {89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
            186, 123, 120, 64, 242, 133, 143, 161, 121, 179};

    /**
     * 搜索歌曲。
     *
     * @param keyword 关键词
     * @return 歌曲数组，字段 id（mid）/name/artist/album/durationMs；失败返回空数组
     */
    public static JSONArray search(String keyword) {
        JSONArray out = new JSONArray();
        try {
            String url = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?key="
                    + URLEncoder.encode(keyword, "UTF-8");
            String body = http("GET", url, null, null);
            JSONObject root = new JSONObject(body);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return out;
            JSONObject song = data.optJSONObject("song");
            if (song == null) return out;
            JSONArray items = song.optJSONArray("itemlist");
            if (items == null) return out;
            for (int i = 0; i < items.length(); i++) {
                JSONObject s = items.optJSONObject(i);
                if (s == null) continue;
                String mid = s.optString("mid", "");
                if (mid.isEmpty()) continue;
                JSONObject item = new JSONObject();
                item.put("id", mid);
                item.put("name", s.optString("name", ""));
                item.put("artist", s.optString("singer", ""));
                item.put("album", "");
                item.put("durationMs", 0);
                out.put(item);
            }
        } catch (Throwable t) {
            Log.w(TAG, "QQ 音乐搜索异常: " + t);
        }
        return out;
    }

    /**
     * 取播放地址（需要登录 Cookie，须含 uin 与 qm_keyst）。
     *
     * @param mid    歌曲 mid
     * @param cookie 登录 Cookie
     * @return 可播放直链；拿不到返回 null
     */
    public static String songUrl(String mid, String cookie) {
        try {
            String uin = cookieValue(cookie, "uin");
            uin = normalizeUin(uin);
            String payload = "{\"comm\":{\"cv\":4747474,\"ct\":24,\"format\":\"json\","
                    + "\"inCharset\":\"utf-8\",\"outCharset\":\"utf-8\",\"notice\":0,"
                    + "\"platform\":\"yqq.json\",\"needNewCode\":1,\"uin\":\"" + uin + "\","
                    + "\"g_tk_new_20200303\":5381,\"g_tk\":5381},"
                    + "\"req_0\":{\"module\":\"vkey.GetVkeyServer\",\"method\":\"CgiGetVkey\","
                    + "\"param\":{\"guid\":\"" + GUID + "\",\"songmid\":[\"" + mid + "\"],"
                    + "\"songtype\":[0],\"uin\":\"" + uin + "\",\"loginflag\":1,\"platform\":\"20\"}}}";

            String url = "https://u.y.qq.com/cgi-bin/musics.fcg?_="
                    + (System.currentTimeMillis() / 1000L) + "&sign=" + zzcSign(payload);
            String body = http("POST", url, payload.getBytes("UTF-8"), cookie);
            JSONObject root = new JSONObject(body);
            JSONObject req = root.optJSONObject("req_0");
            if (req == null) return null;
            JSONObject data = req.optJSONObject("data");
            if (data == null) return null;

            String host = "http://ws.stream.qqmusic.qq.com/";
            JSONArray sip = data.optJSONArray("sip");
            if (sip != null && sip.length() > 0) {
                String s = sip.optString(0, "");
                if (!s.isEmpty()) host = s;
            }
            JSONArray infos = data.optJSONArray("midurlinfo");
            if (infos == null || infos.length() == 0) return null;
            JSONObject info = infos.optJSONObject(0);
            if (info == null) return null;
            String purl = info.optString("purl", "");
            if (purl == null || purl.isEmpty() || "null".equals(purl)) {
                int result = info.optInt("result", 0);
                Log.w(TAG, "QQ 音乐取地址为空 result=" + result
                        + "（104003 表示未登录，其余多见于无版权/VIP 限制）");
                return null;
            }
            if (!host.endsWith("/")) host = host + "/";
            return host + purl;
        } catch (Throwable t) {
            Log.w(TAG, "QQ 音乐取地址异常: " + t);
            return null;
        }
    }

    /**
     * 判断 Cookie 里是否带了登录态。
     *
     * @param cookie Cookie 原文
     * @return true 表示含 uin 与 qm_keyst
     */
    public static boolean hasLogin(String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) return false;
        String keyst = cookieValue(cookie, "qm_keyst");
        String uin = cookieValue(cookie, "uin");
        return keyst != null && !keyst.isEmpty() && uin != null && !uin.isEmpty();
    }

    /**
     * 计算 zzc 签名：{@code "zzc" + 三段拼接}。
     *
     * @param payload 请求体原文（必须与实际发送的字节完全一致）
     * @return 小写签名串
     */
    static String zzcSign(String payload) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] d = sha1.digest(payload.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(40);
            for (byte b : d) sb.append(String.format(Locale.US, "%02X", b));
            String hash = sb.toString();

            StringBuilder p1 = new StringBuilder();
            for (int i : PART_1) p1.append(hash.charAt(i));
            StringBuilder p2 = new StringBuilder();
            for (int i : PART_2) p2.append(hash.charAt(i));

            byte[] part3 = new byte[SCRAMBLE.length];
            for (int i = 0; i < SCRAMBLE.length; i++) {
                int v = Integer.parseInt(hash.substring(i * 2, i * 2 + 2), 16);
                part3[i] = (byte) (SCRAMBLE[i] ^ v);
            }
            String b64 = Base64.encodeToString(part3, Base64.NO_WRAP).replaceAll("[\\\\/+=]", "");
            return ("zzc" + p1 + b64 + p2).toLowerCase(Locale.US);
        } catch (Throwable t) {
            Log.w(TAG, "QQ 音乐签名失败: " + t);
            return "";
        }
    }

    /** 从 Cookie 串里取某个键的值。 */
    private static String cookieValue(String cookie, String name) {
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq > 0 && p.substring(0, eq).trim().equals(name)) {
                return p.substring(eq + 1).trim();
            }
        }
        return null;
    }

    /** uin 可能是 {@code o0123456789} 形式，接口只认纯数字。 */
    private static String normalizeUin(String uin) {
        if (uin == null) return "0";
        String s = uin.trim();
        if (s.startsWith("o") || s.startsWith("O")) s = s.substring(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.length() == 0 ? "0" : sb.toString();
    }

    /**
     * 统一走 {@link Http}：QQ 音乐要求 Referer 与 Origin，登录时带 Cookie。
     *
     * @param method  请求方法
     * @param url     完整地址
     * @param payload JSON 请求体，可为 null
     * @param cookie  登录 Cookie，可为空
     * @return 响应体原文
     * @throws Exception 连接层失败
     */
    private static String http(String method, String url, byte[] payload, String cookie) throws Exception {
        String contentType = payload == null ? null : "application/json; charset=utf-8";
        return Http.request(method, url, payload, contentType,
                Http.headers("User-Agent", UA, "Referer", REFERER, "Origin", "https://y.qq.com",
                        "Cookie", cookie == null ? "" : cookie.trim()))
                .text();
    }
}
