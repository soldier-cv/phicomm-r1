package com.r1.brain;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * Songloft 本地曲库的最小实现：搜索、取播放地址、连通性测试。
 *
 * <p>为什么单独一个类而不是塞进 {@link NeteaseProvider} 那种写法：
 * <ul>
 *   <li>Songloft 是自建服务，地址随部署变化，必须从 {@link Config} 读，不能写死常量；</li>
 *   <li>它的鉴权是 Bearer 令牌，且播放地址要把令牌放进 query
 *       （Android 5.1 的 MediaPlayer 加不了请求头），这条约束只属于它；</li>
 *   <li>搜索接口返回的字段名（{@code title} / {@code duration} 秒）与网易云不同，
 *       在 Provider 里转成统一的 {@code name} / {@code artist} / {@code durationMs}，
 *       上层 {@code IntentRouter} 就不必知道本地库的存在。</li>
 * </ul>
 *
 * <p>接口依据（2026-11-04 对照 songloft 2.13.7 源码确认）：
 * <ul>
 *   <li>{@code GET /api/v1/songs?keyword=X&limit=20} → {@code {songs:[...],total}}</li>
 *   <li>{@code GET /api/v1/songs/{id}/play} → 音频流，支持 Range</li>
 *   <li>鉴权中间件支持 {@code Authorization: Bearer} 与 {@code ?access_token=} 两种形式</li>
 * </ul>
 *
 * @author xudong.hua,deepseek
 * @since 2026-11-04 18:40 星期三
 */
public final class SongloftProvider {

    private static final String TAG = Brain.TAG;

    /** 单次搜索的返回条数上限：本地库按歌名/歌手模糊匹配，20 条足够挑。 */
    private static final int SEARCH_LIMIT = 20;

    private SongloftProvider() {
    }

    /**
     * 规整根地址：去空白、去尾部斜杠；不是 http(s) 一律返回空串。
     *
     * <p>只接受 http(s) 是为了在配置阶段就把 {@code file://}、{@code ftp://} 挡掉，
     * 否则拼出来的地址会在播放时才抛异常，日志很难看懂。
     *
     * @param raw 用户填写的地址
     * @return 可用于拼接的根地址，非法时为 {@code ""}
     */
    public static String normalizeBase(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (!s.startsWith("http://") && !s.startsWith("https://")) return "";
        return s;
    }

    /**
     * 是否已激活：地址与令牌都齐全才算。
     *
     * <p>这个判断同时决定两件事：搜索链要不要跳过本音源、网页上显示「已配置」还是「未配置」。
     *
     * @param cfg 配置
     * @return true 表示可以参与点歌
     */
    public static boolean isReady(Config cfg) {
        return cfg != null
                && !normalizeBase(cfg.songloftUrl).isEmpty()
                && cfg.songloftToken != null
                && !cfg.songloftToken.trim().isEmpty();
    }

    /**
     * 搜索本地曲库。
     *
     * <p>Songloft 的关键词是 SQL LIKE，匹配歌名/歌手/专辑；匹配不到就返回空数组，
     * 由 {@link MusicSource} 决定是否继续问下一个音源。
     *
     * @param cfg     配置（取地址与令牌）
     * @param keyword 关键词
     * @return 歌曲数组，字段与云端音源一致（id/name/artist/album/durationMs）；失败返回空数组
     */
    public static JSONArray search(Config cfg, String keyword) {
        JSONArray out = new JSONArray();
        String base = normalizeBase(cfg == null ? "" : cfg.songloftUrl);
        if (base.isEmpty() || !isReady(cfg)) return out;
        if (keyword == null || keyword.trim().isEmpty()) return out;
        try {
            String url = base + "/api/v1/songs?keyword="
                    + URLEncoder.encode(keyword.trim(), "UTF-8")
                    + "&limit=" + SEARCH_LIMIT;
            String body = Http.getText(url, Http.headers("Authorization", auth(cfg)));
            return mapSongs(new JSONObject(body).optJSONArray("songs"));
        } catch (Throwable t) {
            Log.w(TAG, "本地曲库搜索失败: " + t);
        }
        return out;
    }

    /**
     * 从曲库里随机取几首。
     *
     * <p>用于「播放音乐」「随便放一首」这类不带歌名的请求：原来这类请求只会去扫
     * 音箱自己的 /sdcard/Music，配了本地库之后应该改从曲库随机放，否则用户配了曲库
     * 还会被回一句「曲库为空」。
     *
     * @param cfg   配置
     * @param limit 取几首（调用方按顺序尝试，取不到地址就换下一首）
     * @return 歌曲数组，字段同 {@link #search}；失败返回空数组
     */
    public static JSONArray random(Config cfg, int limit) {
        String base = normalizeBase(cfg == null ? "" : cfg.songloftUrl);
        if (base.isEmpty() || !isReady(cfg) || limit <= 0) return new JSONArray();
        try {
            String body = Http.getText(base + "/api/v1/songs/random?limit=" + limit,
                    Http.headers("Authorization", auth(cfg)));
            return mapSongs(new JSONObject(body).optJSONArray("songs"));
        } catch (Throwable t) {
            Log.w(TAG, "本地曲库随机取歌失败: " + t);
            return new JSONArray();
        }
    }

    /**
     * 把 Songloft 的歌曲数组转成上层统一字段（name/artist/album/durationMs）。
     *
     * <p>Songloft 用 {@code title} 和「秒」为单位的 {@code duration}，云端音源用
     * {@code name} 和毫秒；转换只在这一处做，上层就不必知道数据来自哪个服务。
     *
     * @param songs Songloft 返回的 songs 数组，可为 null
     * @return 统一格式的数组（永不为 null）
     */
    private static JSONArray mapSongs(JSONArray songs) {
        JSONArray out = new JSONArray();
        if (songs == null) return out;
        for (int i = 0; i < songs.length(); i++) {
            JSONObject s = songs.optJSONObject(i);
            if (s == null) continue;
            try {
                JSONObject item = new JSONObject();
                item.put("id", String.valueOf(s.optLong("id", 0)));
                item.put("name", s.optString("title", ""));
                item.put("artist", s.optString("artist", ""));
                item.put("album", s.optString("album", ""));
                item.put("durationMs", (int) Math.round(s.optDouble("duration", 0) * 1000));
                out.put(item);
            } catch (Throwable ignored) {
                // 单条脏数据不该拖垮整页结果
            }
        }
        return out;
    }

    /**
     * 本地曲库全量缓存：322 首实测拉取 40ms，匹配在毫秒级，所以「预取 + 本地匹配」是可行的。
     *
     * <p>缓存 10 分钟：曲库有增删时最多 10 分钟生效，避免每次点歌都打一次全量接口。
     */
    private static volatile JSONArray sAllSongs;
    private static volatile long sAllAt;
    private static final long ALL_TTL_MS = 10 * 60 * 1000L;

    /**
     * 拉取本地曲库全量（带缓存）。
     *
     * @param cfg 配置
     * @return 统一字段的歌曲数组；失败返回上次缓存或空数组
     * @author xudong.hua,claude
     * @since 2026-11-05 23:25 星期四
     */
    public static JSONArray all(Config cfg) {
        String base = normalizeBase(cfg == null ? "" : cfg.songloftUrl);
        if (base.isEmpty() || !isReady(cfg)) return new JSONArray();
        long now = System.currentTimeMillis();
        JSONArray cached = sAllSongs;
        if (cached != null && now - sAllAt < ALL_TTL_MS) {
            return cached;
        }
        try {
            String body = Http.getText(base + "/api/v1/songs?limit=5000",
                    Http.headers("Authorization", auth(cfg)));
            JSONArray songs = mapSongs(new JSONObject(body).optJSONArray("songs"));
            sAllSongs = songs;
            sAllAt = now;
            Log.i(TAG, "本地曲库缓存 " + songs.length() + " 首");
            return songs;
        } catch (Throwable t) {
            Log.w(TAG, "拉取本地曲库失败: " + t);
            return cached != null ? cached : new JSONArray();
        }
    }

    /** 清掉全量缓存（曲库刚导入/删除后可调用）。 */
    public static void invalidateAll() {
        sAllAt = 0;
    }

    /**
     * 在本地曲库里做「容错匹配」，用于 ASR 听错字的情况。
     *
     * <p>为什么需要：实测 ASR 把「虞兮叹」听成「虞汐叹」，服务端的 LIKE 搜索直接落空，
     * 结果落到云端播了别人的翻唱。这里把整库拉到本地逐条比较，能容忍 1~2 个字听错。
     *
     * <p>打分：完全相同 &gt; 包含 &gt; 编辑距离（每差一字扣分，按长度放宽）；歌手相同也加分。
     * 英文歌名先做小写、去标点、压空白再比，所以「Rolling in the deep」和
     * 「rolling  in the deep!」等价。
     *
     * @param cfg     配置
     * @param keyword 关键词（歌名或歌名+歌手）
     * @return 最佳匹配；没有足够接近的返回 null
     * @author xudong.hua,claude
     * @since 2026-11-05 23:25 星期四
     */
    public static JSONObject fuzzyMatch(Config cfg, String keyword) {
        if (keyword == null) return null;
        String kw = normalizeName(keyword);
        if (kw.length() < 2) return null;
        JSONArray all = all(cfg);
        if (all.length() == 0) return null;
        JSONObject best = null;
        int bestScore = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            String name = normalizeName(o.optString("name", ""));
            if (name.isEmpty()) continue;
            int score = similarity(kw, name);
            if (score <= 0) continue;
            String artist = normalizeName(o.optString("artist", ""));
            if (!artist.isEmpty() && (kw.contains(artist) || artist.contains(kw))) score += 10;
            if (score > bestScore) {
                bestScore = score;
                best = o;
            }
        }
        // 门槛：短名（≤3 字）最多错 1 个字，长名最多错 2 个字
        int need = kw.length() <= 3 ? 60 : 40;
        if (best == null || bestScore < need) return null;
        Log.i(TAG, "本地模糊匹配: 「" + keyword + "」≈「" + best.optString("name", "") + " - "
                + best.optString("artist", "") + "」(score=" + bestScore + ")");
        return best;
    }

    /** 歌名归一：小写、全角转半角、去掉标点与空白，只留下字母数字和汉字。 */
    static String normalizeName(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'Ａ' && c <= 'Ｚ') c = (char) (c - 'Ａ' + 'A');
            if (c >= 'ａ' && c <= 'ｚ') c = (char) (c - 'ａ' + 'a');
            if (c >= '０' && c <= '９') c = (char) (c - '０' + '0');
            if (Character.isLetterOrDigit(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * 两段归一化文本的相似度（0~100）。
     *
     * @param kw   用户说的
     * @param name 库里的
     * @return 分数；毫不相干返回 0
     */
    private static int similarity(String kw, String name) {
        if (kw.equals(name)) return 100;
        if (name.contains(kw) || kw.contains(name)) return 90;
        int dist = editDistance(kw, name);
        int len = Math.max(kw.length(), name.length());
        if (dist > 2) return 0;
        return Math.max(0, 90 - dist * 25 - Math.max(0, len - 10));
    }

    /** 编辑距离（短歌名足够快）。 */
    private static int editDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > 2) return 99;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[m];
    }

    /**
     * 取播放直链。
     *
     * <p>令牌必须放在 query 上：{@link MusicPlayer} 用的是系统 MediaPlayer，
     * 只能给一个 URL，加不了 Authorization 头。Songloft 的鉴权中间件读
     * {@code access_token}，所以这里拼出来的地址可以直接丢给播放器。
     *
     * @param cfg    配置（取地址与令牌）
     * @param songId 歌曲 id（Songloft 的数字主键）
     * @return 可播放的直链；参数不合法或未配置时返回 null
     */
    public static String songUrl(Config cfg, String songId) {
        String base = normalizeBase(cfg == null ? "" : cfg.songloftUrl);
        if (base.isEmpty() || !isReady(cfg)) return null;
        if (songId == null || !songId.matches("[0-9]+")) {
            Log.w(TAG, "本地曲库 id 不合法: " + songId);
            return null;
        }
        try {
            return base + "/api/v1/songs/" + songId + "/play?access_token="
                    + URLEncoder.encode(cfg.songloftToken.trim(), "UTF-8");
        } catch (Throwable t) {
            Log.w(TAG, "本地曲库拼播放地址失败: " + t);
            return null;
        }
    }

    /**
     * 连通性测试：拿一条歌曲列表，验证地址可达、令牌有效。
     *
     * <p>设置页点「测试」时调用。返回体形如 {@code {"ok":true,"total":322}}，
     * 失败则带 {@code error} 原文，便于用户分辨「地址填错」和「令牌过期」。
     *
     * @param cfg 配置
     * @return 测试结果 JSON（永不为 null）
     */
    public static JSONObject test(Config cfg) {
        JSONObject r = new JSONObject();
        String base = normalizeBase(cfg == null ? "" : cfg.songloftUrl);
        if (base.isEmpty()) {
            try {
                r.put("ok", false);
                r.put("error", "地址为空或不是 http(s)");
            } catch (Throwable ignored) { }
            return r;
        }
        if (cfg.songloftToken == null || cfg.songloftToken.trim().isEmpty()) {
            try {
                r.put("ok", false);
                r.put("error", "令牌为空");
            } catch (Throwable ignored) { }
            return r;
        }
        try {
            String body = Http.getText(base + "/api/v1/songs?limit=1",
                    Http.headers("Authorization", auth(cfg)));
            JSONObject root = new JSONObject(body);
            r.put("ok", true);
            r.put("total", root.optInt("total", 0));
        } catch (Throwable t) {
            try {
                r.put("ok", false);
                r.put("error", String.valueOf(t));
            } catch (Throwable ignored) { }
            Log.w(TAG, "本地曲库连通性测试失败: " + t);
        }
        return r;
    }

    /** Bearer 头：令牌可能被用户从网页粘贴带进空格，这里统一 trim。 */
    private static String auth(Config cfg) {
        return "Bearer " + cfg.songloftToken.trim();
    }
}
