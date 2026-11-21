package com.r1.brain;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型接入的小工具：URL 规范化、模型列表、连通性测试。
 *
 * <p>为什么单独一个类：设置页的「获取模型」「测试连接」和真正对话用的是同一套地址规则，
 * 不能一边补 {@code /v1}、另一边不补，否则「测试通过但对话 404」。
 *
 * <p>URL 规则（就是设置页上写的那条提示）：
 * <ul>
 *   <li>只填到域名（如 {@code https://api.openai.com}）→ 自动补 {@code /v1/chat/completions}；</li>
 *   <li>填到 {@code /v1} → 补 {@code /chat/completions}；</li>
 *   <li>已经是完整地址（含 {@code /chat/completions}）→ 原样使用；</li>
 *   <li>智谱这类带自定义路径的（{@code /api/paas/v4/chat/completions}）→ 原样使用。</li>
 * </ul>
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 22:30 星期六
 */
public final class LlmClient {

    private static final String TAG = Brain.TAG;
    /** 列模型时最多返回多少个，避免下拉框被塞爆。 */
    private static final int MAX_MODELS = 200;

    private LlmClient() {
    }

    /**
     * 把用户填的地址规范成完整的 chat completions 地址。
     *
     * @param raw 原始输入（域名 / 带 /v1 / 完整地址）
     * @return 完整地址；输入为空返回空串
     */
    public static String normalizeChatUrl(String raw) {
        if (raw == null) return "";
        String u = raw.trim();
        if (u.isEmpty()) return u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/chat/completions")) return u;
        String path = pathOf(u);
        if (path.isEmpty() || path.equals("/")) {
            return u + "/v1/chat/completions";
        }
        if (path.equals("/v1") || path.equals("/v1/")) {
            return u + "/chat/completions";
        }
        // 其它自定义路径：认为用户就是想要这个地址（例如 /api/paas/v4）
        if (u.endsWith("/v4") || u.endsWith("/v2") || u.endsWith("/v3")) {
            return u + "/chat/completions";
        }
        return u;
    }

    /**
     * 由 chat completions 地址推出同源的 {@code /models} 地址。
     *
     * @param chatUrl 完整或未完整的 chat 地址
     * @return models 地址
     */
    public static String modelsUrl(String chatUrl) {
        String u = normalizeChatUrl(chatUrl);
        if (u.endsWith("/chat/completions")) {
            u = u.substring(0, u.length() - "/chat/completions".length());
        }
        return u + "/models";
    }

    /**
     * 拉取可用模型列表（OpenAI 兼容的 {@code GET /models}）。
     *
     * @param chatUrl 对话地址（可为域名）
     * @param apiKey  API Key，可为空（本地 Ollama 之类）
     * @return 模型 id 列表，按字母序
     * @throws Exception 网络或返回格式错误
     */
    public static List<String> listModels(String chatUrl, String apiKey) throws Exception {
        String url = modelsUrl(chatUrl);
        Http.Response res = Http.request("GET", url, null, null,
                Http.headers("Authorization", apiKey == null || apiKey.trim().isEmpty()
                        ? null : "Bearer " + apiKey.trim()),
                5000, 15000, 2 * 1024 * 1024);
        if (!res.ok()) {
            throw new IllegalStateException("HTTP " + res.code + " " + shorten(res.text()));
        }
        JSONObject root = new JSONObject(res.text());
        JSONArray data = root.optJSONArray("data");
        List<String> out = new ArrayList<String>();
        if (data != null) {
            for (int i = 0; i < data.length() && out.size() < MAX_MODELS; i++) {
                JSONObject o = data.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                if (!id.isEmpty()) out.add(id);
            }
        }
        java.util.Collections.sort(out);
        Log.i(TAG, "模型列表 " + url + " → " + out.size() + " 个");
        return out;
    }

    /**
     * 用一次性极短请求测试「地址 + Key + 模型」是否可用。
     *
     * <p>预算不能给太小：推理型模型会先花看不见的思考 token，8 token 那种预算下正文必然为空，
     * 老版本把空串当成功，设置页显示「通了」而真对话一直失败。
     *
     * @param chatUrl 对话地址
     * @param apiKey  API Key
     * @param model   模型名
     * @param effort  思考档位（和真对话用同一份设置，否则"测试通过但对话失败"）
     * @return 模型回的正文（通常是一句"收到"），用于证明链路真的通了
     * @throws Exception 任何一步失败，包括"返回 200 但正文为空"
     */
    public static String ping(String chatUrl, String apiKey, String model, ReasoningEffort effort) throws Exception {
        String url = normalizeChatUrl(chatUrl);
        ReasoningEffort mode = effort == null ? ReasoningEffort.AUTO : effort;
        JSONObject body = new JSONObject();
        body.put("model", model);
        mode.apply(body);
        // 留出推理型模型的思考额度，避免"200 但正文为空"的假通过
        body.put("max_tokens", mode.mayThink() ? 768 : 256);
        body.put("temperature", 0);
        body.put("stream", false);
        JSONArray messages = new JSONArray();
        JSONObject m = new JSONObject();
        m.put("role", "user");
        m.put("content", "只回复两个字：收到");
        messages.put(m);
        body.put("messages", messages);

        Http.Response res = Http.request("POST", url, body.toString().getBytes("UTF-8"),
                "application/json; charset=utf-8",
                Http.headers("Authorization", "Bearer " + (apiKey == null ? "" : apiKey.trim())),
                5000, 20000, 2 * 1024 * 1024);
        if (!res.ok()) {
            throw new IllegalStateException("HTTP " + res.code + " " + shorten(res.text()));
        }
        JSONObject json = new JSONObject(res.text());
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IllegalStateException("返回里没有 choices");
        }
        JSONObject choice = choices.optJSONObject(0);
        JSONObject msg = choice == null ? null : choice.optJSONObject("message");
        String content = msg == null ? "" : msg.optString("content", "").trim();
        String finish = choice == null ? "" : choice.optString("finish_reason", "");
        if (content.isEmpty()) {
            throw new IllegalStateException("模型返回空内容（finish=" + (finish.isEmpty() ? "未知" : finish)
                    + "）：大概率输出额度被思考占满，请调大「最大输出」或换非推理型模型");
        }
        return content;
    }

    /** 取 URL 的路径部分（不含协议与主机）。 */
    private static String pathOf(String url) {
        int scheme = url.indexOf("://");
        int start = scheme >= 0 ? scheme + 3 : 0;
        int slash = url.indexOf('/', start);
        return slash < 0 ? "" : url.substring(slash);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").trim();
        return t.length() > 160 ? t.substring(0, 160) : t;
    }
}
