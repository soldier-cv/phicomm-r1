package com.r1.brain;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * Home Assistant 简易桥：按「设备名」找到实体并开关它。
 *
 * <p>只用到两个官方 REST 接口：
 * <ul>
 *   <li>{@code GET /api/states}：列出全部实体，用于把中文设备名匹配到 entity_id；</li>
 *   <li>{@code POST /api/services/homeassistant/turn_on|turn_off}：开/关。</li>
 * </ul>
 *
 * <p>匹配顺序：entity_id 里包含关键词 → friendly_name 里包含关键词；
 * 可开关的域优先（light / switch / fan / media_player / scene / script）。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 13:05 星期六
 */
public class HomeAssistant {

    private static final String TAG = Brain.TAG;
    /** 可被语音开关的域，顺序即优先级。 */
    private static final String[] DOMAINS = {"light", "switch", "fan", "media_player", "scene", "script", "input_boolean"};

    /**
     * 执行一条开关指令。
     *
     * @param cfg  配置（需要 haUrl 与 haToken）
     * @param text 用户原话
     * @return 播报文本；无法识别或不适用返回 null
     */
    public static String handle(Config cfg, String text) {
        if (cfg == null || cfg.haUrl == null || cfg.haUrl.trim().isEmpty()) return null;
        if (cfg.haToken == null || cfg.haToken.trim().isEmpty()) return null;
        if (text == null) return null;

        boolean on;
        String rest;
        if (text.startsWith("打开") || text.startsWith("开启") || text.contains("打开")) {
            on = true;
            rest = afterKeyword(text, "打开", "开启");
        } else if (text.startsWith("关闭") || text.startsWith("关掉") || text.contains("关闭")) {
            on = false;
            rest = afterKeyword(text, "关闭", "关掉");
        } else {
            return null;
        }
        String keyword = clean(rest);
        if (keyword.isEmpty()) return null;

        try {
            String entityId = match(cfg, keyword);
            if (entityId == null) {
                return "在 Home Assistant 里没有找到叫「" + keyword + "」的设备。";
            }
            String service = on ? "turn_on" : "turn_off";
            JSONObject body = new JSONObject();
            body.put("entity_id", entityId);
            String resp = http(cfg, "POST", "/api/services/homeassistant/" + service, body.toString());
            if (resp == null) {
                return "Home Assistant 没有响应，请检查地址和令牌。";
            }
            Log.i(TAG, "HA " + service + " -> " + entityId);
            return "好的，已" + (on ? "打开" : "关闭") + friendlyOf(cfg, entityId, keyword) + "。";
        } catch (Throwable t) {
            Log.w(TAG, "HA 指令失败: " + t);
            return "Home Assistant 指令执行失败：" + t.getMessage();
        }
    }

    /**
     * 连通性测试：读一次 {@code /api/states}，统计实体数与可语音控制的实体数。
     *
     * <p>设置页的「测试连接」用它，允许测试尚未保存的地址/令牌。
     *
     * @param url   HA 地址
     * @param token 长期访问令牌
     * @return {@code {entities: n, controllable: m}}
     * @throws Exception 连不上或返回格式不对
     */
    public static JSONObject test(String url, String token) throws Exception {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException("HA 地址为空");
        }
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException("HA 令牌为空");
        }
        Config c = new Config();
        c.haUrl = url.trim();
        c.haToken = token.trim();
        String json = http(c, "GET", "/api/states", null);
        if (json == null) {
            throw new IllegalStateException("连不上 Home Assistant（检查地址/令牌）");
        }
        JSONArray arr = new JSONArray(json);
        int controllable = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null) continue;
            if (domainRank(e.optString("entity_id", "")) >= 0) controllable++;
        }
        JSONObject out = new JSONObject();
        out.put("entities", arr.length());
        out.put("controllable", controllable);
        return out;
    }

    /** 在 /api/states 里找出最匹配的实体。 */
    private static String match(Config cfg, String keyword) throws Exception {
        String json = http(cfg, "GET", "/api/states", null);
        if (json == null) return null;
        JSONArray arr = new JSONArray(json);
        String lower = keyword.toLowerCase();

        String best = null;
        int bestScore = -1;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null) continue;
            String id = e.optString("entity_id", "");
            int domainRank = domainRank(id);
            if (domainRank < 0) continue;
            JSONObject attrs = e.optJSONObject("attributes");
            String friendly = attrs == null ? "" : attrs.optString("friendly_name", "");
            int score;
            if (id.toLowerCase().contains(lower)) {
                score = 100 + domainRank * 10 + (friendly.contains(keyword) ? 5 : 0);
            } else if (friendly.toLowerCase().contains(lower)) {
                score = 50 + domainRank * 10;
            } else {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                best = id;
            }
        }
        return best;
    }

    /** 取实体的中文名用于播报，取不到就回退关键词。 */
    private static String friendlyOf(Config cfg, String entityId, String fallback) {
        try {
            String json = http(cfg, "GET", "/api/states/" + URLEncoder.encode(entityId, "UTF-8"), null);
            if (json != null) {
                JSONObject e = new JSONObject(json);
                JSONObject attrs = e.optJSONObject("attributes");
                if (attrs != null) {
                    String f = attrs.optString("friendly_name", "");
                    if (!f.isEmpty()) return "「" + f + "」";
                }
            }
        } catch (Throwable ignored) {
        }
        return "「" + fallback + "」";
    }

    private static int domainRank(String entityId) {
        int dot = entityId.indexOf('.');
        if (dot <= 0) return -1;
        String domain = entityId.substring(0, dot);
        for (int i = 0; i < DOMAINS.length; i++) {
            if (DOMAINS[i].equals(domain)) return DOMAINS.length - i;
        }
        return -1;
    }

    /** 去掉动词后残留的“一下”“吧”“呢”等口头语。 */
    private static String afterKeyword(String text, String a, String b) {
        String s = text;
        int i = s.indexOf(a);
        if (i < 0) i = s.indexOf(b);
        if (i >= 0) s = s.substring(i + (s.startsWith(a, i) ? a.length() : b.length()));
        return s;
    }

    private static String clean(String s) {
        String r = s == null ? "" : s.trim();
        String[] junk = {"一下", "吧", "呢", "啊", "的", "灯吧", "设备"};
        for (String j : junk) {
            r = r.replace(j, "");
        }
        r = r.replace("了", "").replace("。", "").replace("，", "").replace(" ", "");
        return r.trim();
    }

    /**
     * 请求 Home Assistant。
     *
     * @param cfg    配置（提供 base 与令牌）
     * @param method 请求方法
     * @param path   以 / 开头的接口路径
     * @param body   JSON 请求体，可为 null
     * @return 响应体；HTTP 非 2xx 或连不上返回 null
     * @throws Exception 连接层失败
     */
    private static String http(Config cfg, String method, String path, String body) throws Exception {
        String base = cfg.haUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        byte[] payload = body == null ? null : body.getBytes("UTF-8");
        Http.Response res = Http.request(method, base + path, payload,
                payload == null ? null : "application/json; charset=utf-8",
                Http.headers("Authorization", "Bearer " + cfg.haToken.trim()),
                4000, 6000, 1024 * 1024);
        if (!res.ok()) {
            Log.w(TAG, "HA HTTP " + res.code + " " + path);
            return null;
        }
        return res.text();
    }
}
