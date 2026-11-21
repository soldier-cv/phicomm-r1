package com.r1.brain;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * 网易云音乐的最小实现：搜索与取播放地址。
 *
 * <p>只用官方公开的 {@code /api/} 老接口（不需要 weapi 加密）：
 * <ul>
 *   <li>{@code /api/search/get} 搜索（免登录可用）</li>
 *   <li>{@code /api/song/enhance/player/url} 取播放地址</li>
 * </ul>
 *
 * <p>实测（2026-10-31）：免版权曲目匿名就能拿到直链；
 * 会员/VIP 曲目匿名返回 {@code code:-110}，必须带登录 Cookie（含 {@code MUSIC_U}）。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 02:10 星期六
 */
public class NeteaseProvider {

    private static final String TAG = Brain.TAG;
    private static final String UA = "Mo" + "zilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final String BASE = "https://music.163.com";

    /**
     * 搜索歌曲。
     *
     * @param keyword 关键词
     * @param cookie  登录 Cookie（可为空，匿名也能搜）
     * @return 歌曲数组，字段 id/name/artist/album/durationMs；失败返回空数组
     */
    public static JSONArray search(String keyword, String cookie) {
        JSONArray out = new JSONArray();
        try {
            String body = http("GET", BASE + "/api/search/get?s="
                    + URLEncoder.encode(keyword, "UTF-8") + "&type=1&limit=20&offset=0", cookie);
            JSONObject root = new JSONObject(body);
            JSONObject result = root.optJSONObject("result");
            if (result == null) return out;
            JSONArray songs = result.optJSONArray("songs");
            if (songs == null) return out;
            for (int i = 0; i < songs.length(); i++) {
                JSONObject s = songs.optJSONObject(i);
                if (s == null) continue;
                JSONObject item = new JSONObject();
                item.put("id", s.optString("id", ""));
                item.put("name", s.optString("name", ""));
                item.put("artist", joinArtists(s.optJSONArray("artists")));
                JSONObject album = s.optJSONObject("album");
                item.put("album", album == null ? "" : album.optString("name", ""));
                item.put("durationMs", s.optInt("duration", 0));
                out.put(item);
            }
        } catch (Throwable t) {
            Log.w(TAG, "网易云搜索异常: " + t);
        }
        return out;
    }

    /**
     * 取播放地址（需要登录 Cookie）。
     *
     * @param id     歌曲 id
     * @param cookie 登录 Cookie（含 MUSIC_U）
     * @return 可播放的直链；拿不到返回 null
     */
    public static String songUrl(String id, String cookie) {
        try {
            String body = http("GET", BASE + "/api/song/enhance/player/url?ids=[" + id + "]&br=320000", cookie);
            JSONObject root = new JSONObject(body);
            JSONArray data = root.optJSONArray("data");
            if (data == null || data.length() == 0) return null;
            JSONObject d = data.optJSONObject(0);
            if (d == null) return null;
            String url = d.optString("url", "");
            if (url == null || url.isEmpty() || "null".equals(url)) {
                Log.w(TAG, "网易云取地址为空 code=" + d.optInt("code", 0) + "（通常是未登录或无版权）");
                return null;
            }
            return url;
        } catch (Throwable t) {
            Log.w(TAG, "网易云取地址异常: " + t);
            return null;
        }
    }

    private static String joinArtists(JSONArray artists) {
        if (artists == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < artists.length(); i++) {
            JSONObject a = artists.optJSONObject(i);
            if (a == null) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(a.optString("name", ""));
        }
        return sb.toString();
    }

    /**
     * 统一走 {@link Http}：带上网易云要求的 Referer 与登录 Cookie。
     *
     * @param method 请求方法
     * @param url    完整地址
     * @param cookie 登录 Cookie，可为空
     * @return 响应体原文（由调用方判 JSON 里的 code）
     * @throws Exception 连接层失败
     */
    private static String http(String method, String url, String cookie) throws Exception {
        return Http.request(method, url, null, null,
                Http.headers("User-Agent", UA, "Referer", BASE + "/",
                        "Cookie", cookie == null ? "" : cookie.trim()))
                .text();
    }
}
