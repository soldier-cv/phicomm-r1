package com.r1.brain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 智脑业务核心：接收文本指令 → 先交给本地意图路由 {@link IntentRouter}
 * （播报打断、闹钟、播控、点歌、场景、报时天气、运维、音量、Home Assistant 开关）
 * → 未命中再路由至大模型 Chat Completions 接口。
 *
 * <p>同一时刻只处理一条指令：语音侧本来就在单线程池里串行，网页侧通过
 * {@link #request} 的公平锁与语音互斥，避免连点把 8 个 HTTP 工作线程全压在
 * 20 秒的大模型请求上。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 17:22 星期五
 */
public class Brain {

    public static final String TAG = "R1BRAIN";

    /**
     * 异步处理结果回调接口。
     *
     * @author xudong.hua,gemini
     * @since 2026-10-30 13:52 星期五
     */
    public interface Callback {
        void onReply(String reply);
        void onError(String message);
    }

    /** 已有一条指令在处理、且短时间拿不到执行权。 */
    public static class BusyException extends Exception {
        public BusyException(String message) {
            super(message);
        }
    }

    private static final TimeZone CN_TZ = TimeZone.getTimeZone("GMT+08:00");

    private final Context ctx;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed = false;
    /** 语音 / 网页共用的执行锁：保证同一时刻只有一条指令在跑。 */
    private final ReentrantLock requestLock = new ReentrantLock(true);

    /** 多轮上下文：最多带回最近 3 轮（6 条消息），每条截断，避免 Token 膨胀。 */
    private static final int HISTORY_TURNS = 3;
    private static final int HISTORY_ITEM_MAX_CHARS = 200;
    /** 推理型模型的思考 token 与正文共用 max_tokens，这里给思考额外留的额度。 */
    private static final int REASONING_HEADROOM = 1024;
    /** 单次请求上限：HTTP 读超时只有 20 秒，再大也答不完。 */
    private static final int MAX_TOKENS_CAP = 2048;
    private static final int HISTORY_MAX = 50;

    /**
     * 记录一轮对话到历史（语音与网页共用，schema 也共用）。
     *
     * @param ctx   上下文
     * @param user  用户原话
     * @param reply 助手回复
     */
    public static void recordTurn(Context ctx, String user, String reply) {
        recordTurn(ctx, user, reply, "语音", null, -1);
    }

    /**
     * 记录一轮对话到历史。
     *
     * <p>语音和网页必须写同一种结构，否则前端读历史时字段对不上：
     * user 固定 {@code id/role/source/time/text}，assistant 固定
     * {@code id/role/model/costMs/status/time/text}。
     *
     * @param ctx     上下文
     * @param user    用户原话
     * @param reply   助手回复
     * @param source  来源（语音 / Web 控制台等）
     * @param model   模型名（语音侧可不传，落库时用当前配置）
     * @param costMs  处理耗时（-1 表示未统计）
     * @return 记录时间（HH:mm:ss，北京时间）；失败返回空串
     * @author xudong.hua,deepseek
     * @since 2026-11-05 12:00 星期四
     */
    public static String recordTurn(Context ctx, String user, String reply,
                                    String source, String model, long costMs) {
        if (ctx == null || user == null || reply == null) return "";
        final long now = System.currentTimeMillis();
        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        sdf.setTimeZone(CN_TZ);
        final String time = sdf.format(new Date(now));
        final String src = (source == null || source.trim().isEmpty()) ? "语音" : source.trim();
        synchronized (LOCK_HISTORY) {
            Config.update(ctx, new Config.Mutator() {
                @Override
                public void apply(Config c) {
                    JSONArray hist;
                    try {
                        hist = new JSONArray(c.chatHistoryJson);
                    } catch (Throwable t) {
                        hist = new JSONArray();
                    }
                    try {
                        JSONObject u = new JSONObject();
                        u.put("id", now);
                        u.put("role", "user");
                        u.put("source", src);
                        u.put("time", time);
                        u.put("text", trim(user));
                        hist.put(u);

                        JSONObject a = new JSONObject();
                        a.put("id", now + 1);
                        a.put("role", "assistant");
                        a.put("model", model == null ? c.model : model);
                        a.put("costMs", costMs);
                        a.put("status", "已执行");
                        a.put("time", time);
                        a.put("text", trim(reply));
                        hist.put(a);

                        while (hist.length() > HISTORY_MAX) {
                            JSONArray t = new JSONArray();
                            for (int i = 1; i < hist.length(); i++) t.put(hist.get(i));
                            hist = t;
                        }
                        c.chatHistoryJson = hist.toString();
                    } catch (Throwable t) {
                        Log.w(TAG, "recordTurn 构造失败: " + t);
                    }
                }
            });
        }
        return time;
    }

    /** 清空对话历史（与写入共用一把锁，避免边清边写）。 */
    public static void clearHistory(Context ctx) {
        if (ctx == null) return;
        synchronized (LOCK_HISTORY) {
            Config.update(ctx, new Config.Mutator() {
                @Override
                public void apply(Config c) {
                    c.chatHistoryJson = "[]";
                }
            });
        }
    }

    private static final Object LOCK_HISTORY = new Object();

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > HISTORY_ITEM_MAX_CHARS ? s.substring(0, HISTORY_ITEM_MAX_CHARS) : s;
    }

    public Brain(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** 服务销毁时停止线程池，避免残留请求。 */
    public void shutdown() {
        closed = true;
        pool.shutdownNow();
        Log.i(TAG, "Brain: shutdown");
    }

    public void handle(final String userText, final Callback cb) {
        final long t0 = System.currentTimeMillis();
        if (closed) {
            cb.onError("服务正在关闭");
            return;
        }
        Log.i(TAG, "handle: " + userText);

        try {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final String reply = request(Config.load(ctx), userText);
                        Log.i(TAG, "ok, cost=" + (System.currentTimeMillis() - t0) + "ms len=" + reply.length());
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                cb.onReply(reply);
                            }
                        });
                    } catch (Throwable t) {
                        final String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
                        Log.e(TAG, "failed, cost=" + (System.currentTimeMillis() - t0) + "ms, " + msg);
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                cb.onError(msg);
                            }
                        });
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "handle rejected: " + t);
            cb.onError("服务繁忙，请稍后再试");
        }
    }

    /**
     * 同步执行指令：语音与 HTTP 接口共用，同一时刻只跑一条。
     *
     * @param cfg      配置快照
     * @param userText 用户文本
     * @return 回复文本
     * @throws BusyException 5 秒内拿不到执行权（上一条还在处理）
     * @author xudong.hua,deepseek
     * @since 2026-11-05 12:00 星期四
     */
    public String request(Config cfg, String userText) throws Exception {
        if (closed) throw new IllegalStateException("服务正在关闭");
        boolean locked = false;
        try {
            locked = requestLock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) throw new BusyException("上一条指令还在处理，请稍后再试");
            return requestLocked(cfg, userText);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求被中断");
        } finally {
            if (locked) requestLock.unlock();
        }
    }

    private String requestLocked(Config cfg, String userText) throws Exception {
        String localReply = tryLocalCommand(userText);
        if (localReply != null) {
            return localReply;
        }
        if (cfg.isEchoMode()) {
            return "收到指令：「" + userText + "」。（当前尚未配置大模型 API Key，处于回显验证模式；前往设置页填入 Key 即可开启真实 AI 对话）";
        }
        return callLlm(cfg, userText);
    }

    /** 本地意图路由（见 {@link IntentRouter}）：命中即返回，未命中返回 null 交给大模型。 */
    private String tryLocalCommand(String text) {
        String reply = IntentRouter.handle(ctx, text);
        if (reply == null) {
            // 留一条「没命中本地意图」的日志：日常调正确率时就靠它看这句话漏到哪儿去了
            Log.i(TAG, "本地意图未命中，转大模型: " + text);
        }
        return reply;
    }

    /** 调用 OpenAI 兼容的 /chat/completions 接口。 */
    private String callLlm(Config cfg, String userText) throws Exception {
        if (cfg.baseUrl == null || !(cfg.baseUrl.trim().startsWith("http://") || cfg.baseUrl.trim().startsWith("https://"))) {
            throw new IllegalStateException("大模型接口地址必须以 http:// 或 https:// 开头");
        }
        if (cfg.apiKey == null || cfg.apiKey.trim().isEmpty()) {
            throw new IllegalStateException("大模型 API Key 为空");
        }

        JSONObject body = new JSONObject();
        body.put("model", cfg.model);
        body.put("temperature", cfg.temperature);
        body.put("stream", false);

        String prompt = (cfg.systemPrompt != null && !cfg.systemPrompt.trim().isEmpty())
                ? cfg.systemPrompt.trim()
                : "你是一台智能音箱。设备默认用中文普通话回话，回复念出来就要能听懂："
                + "口语化，**一句话说完，最好不超过 20 个字**，不用 markdown，不复述用户的话。"
                + "用户明确要求「详细讲讲/讲故事」时才展开。";
        // 硬规则不放进可配置的 systemPrompt：用户改设置时不该把这个约束弄丢。
        // 2026-11-03 实测：模型回了「好嘞，已帮你开启连续对话」，而配置里 continuousChat 仍是 false，
        // 用户以为开了、接着说，结果一句都没被听见。
        if (cfg.llmTools) {
            // 2026-11-05 起模型有工具了：先前的「你没有操作能力」必须换掉，
            // 否则它会回「我这边没法直接调音量」（实测被用户当场吐槽）。
            prompt = prompt + "\n\n【能用工具就直接做】\n"
                    + "- 回答最多一句话、不超过 30 个字：这些字要一个个念出来，长了一句要多花好几秒。\n"
                    + "- 用户让你做某事、且下面有对应工具：直接调用，不要先解释、不要问「要不要我帮你」、不要复述用户的话。\n"
                    + "- 缺参数就用最合理的默认值（「大一点」≈ 加 20%），不要反问；只有缺「歌名/时间/城市」这种猜不到的信息才问一句。\n"
                    + "- 一句话只调必要的工具，调完就停，不要连环调用。\n"
                    + "- 工具返回的内容就是最终结果：不要再改写或补充解释，直接按它回答。\n"
                    + "- 工具失败或没有对应工具：如实说一句做不到，不要编造「已完成」。\n"
                    + "- 没有调用工具时，绝对不能说「已为你开启/已设置/已完成」。\n";
        } else {
            prompt = prompt + "\n\n【硬规则】你没有直接操作设备的能力：开关连续对话、播放音乐、设置闹钟、"
                    + "调音量、开灯都只能由本地程序执行。你只有在真正执行后才会收到结果；"
                    + "不确定是否执行过时，不要声称「已为你开启/已设置/已完成」，也不要编造执行结果。"
                    + "用户想要某个操作时，把确切指令说出来让他照说，例如「说『开启连续对话』我就打开」。";
        }

        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", prompt);
        messages.put(sys);

        // 多轮上下文：带回最近几轮，让"再讲一个""那它呢"这类追问能接上
        try {
            JSONArray hist = new JSONArray(cfg.chatHistoryJson);
            int from = Math.max(0, hist.length() - HISTORY_TURNS * 2);
            for (int i = from; i < hist.length(); i++) {
                JSONObject it = hist.optJSONObject(i);
                if (it == null) continue;
                String role = it.optString("role", "");
                String text = it.optString("text", "");
                if (text.isEmpty()) continue;
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                JSONObject m = new JSONObject();
                m.put("role", role);
                m.put("content", text);
                messages.put(m);
            }
        } catch (Throwable t) {
            Log.w(TAG, "读取对话历史失败，按单轮处理: " + t);
        }

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", userText);
        messages.put(user);
        body.put("messages", messages);

        // 推理型模型（ouyi-5-preview、deepseek-reasoner 这类）会先花"思考 token"再写正文，
        // 两者共用同一份 max_tokens。用户填的预算本来是给正文的，这里额外留出思考额度，
        // 否则整份额度被思考吃光后正文为空（finish_reason=length），看起来就像"Key 失效"。
        // 思考控制：先按设置写 reasoning_effort；"关闭"档不再给思考留额度，
        // 其余档位（含"自动"，模型可能是推理型）都要留，否则预算会被思考吃光。
        // 模型有工具就能真的做事；上游不认 tools 时会自动去掉重发（见 requestOnce）。
        if (cfg.llmTools) {
            body.put("tools", Tools.schema());
            body.put("tool_choice", "auto");
        }

        ReasoningEffort effort = ReasoningEffort.of(cfg.reasoningEffort);
        effort.apply(body);
        int budget = Math.max(32, Math.min(MAX_TOKENS_CAP, cfg.maxTokens));
        int withHeadroom = Math.min(MAX_TOKENS_CAP, budget + (effort.mayThink() ? REASONING_HEADROOM : 0));
        LlmReply reply = requestOnce(cfg, body, withHeadroom);
        if (reply.isEmpty() && "length".equals(reply.finishReason) && withHeadroom < MAX_TOKENS_CAP) {
            // 留了思考额度还是被截断：顶到上限再试一次。只在失败路径多花一次请求。
            Log.i(TAG, "空回复(finish=length)，输出预算 " + withHeadroom + " → " + MAX_TOKENS_CAP + " 重试");
            reply = requestOnce(cfg, body, MAX_TOKENS_CAP);
        }
        // 模型决定做事：直接执行，并把工具返回的固定文案当作最终回复（不再多要一次模型请求）
        if (reply.toolCalls.length() > 0) {
            return runTools(reply);
        }
        if (reply.content.isEmpty()) {
            throw new IllegalStateException(emptyReplyMessage(reply, budget, withHeadroom));
        }
        return reply.content;
    }

    /**
     * 执行模型选中的工具，拼出要播报的话。
     *
     * <p>为什么不再回模型一轮：工具返回的就是「已调大音量，现在 40%」这种成品文案，
     * 再让模型改写只会变慢、还可能改错；多轮也更容易编。
     *
     * @param reply 模型响应（带 tool_calls）
     * @return 要播报的文本
     * @author xudong.hua,claude
     * @since 2026-11-06 00:05 星期五
     */
    private String runTools(LlmReply reply) {
        StringBuilder out = new StringBuilder();
        boolean anyOk = false;
        for (int i = 0; i < reply.toolCalls.length(); i++) {
            JSONObject tc = reply.toolCalls.optJSONObject(i);
            JSONObject fn = tc == null ? null : tc.optJSONObject("function");
            if (fn == null) continue;
            String name = fn.optString("name", "");
            JSONObject args = parseArgs(fn.optString("arguments", "{}"));
            Tools.Result r = Tools.call(ctx, name, args);
            Log.i(TAG, "工具 " + name + " -> ok=" + r.ok + " " + r.speak);
            if (r.ok) anyOk = true;
            if (!r.speak.isEmpty()) {
                if (out.length() > 0) out.append(' ');
                out.append(r.speak);
            }
            // 语音场景一句话就够，别把多个工具的结果堆成小作文
            if (out.length() > 120 || i >= 2) break;
        }
        if (out.length() == 0) {
            return anyOk ? "好了。" : "这个我没做成。";
        }
        return out.toString();
    }

    /** 工具参数是 JSON 字符串；模型偶尔给空串或坏 JSON，按空对象处理。 */
    private static JSONObject parseArgs(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) return new JSONObject();
            return new JSONObject(raw);
        } catch (Throwable t) {
            Log.w(TAG, "工具参数不是合法 JSON，按空处理: " + raw);
            return new JSONObject();
        }
    }

    /** 发一次 /chat/completions，把响应拆成「正文 + 结束原因 + 思考 token 数」。 */
    private LlmReply requestOnce(Config cfg, JSONObject body, int maxTokens) throws Exception {
        body.put("max_tokens", maxTokens);
        String url = LlmClient.normalizeChatUrl(cfg.baseUrl);
        Http.Response res = post(cfg, url, body);
        // 少数上游按严格 OpenAI 规范校验，认不出 reasoning_effort 会直接 400。
        // 这种情况去掉该字段重发一次，别让"思考设置"把本来能用的模型弄挂。
        if (res.code == 400 && body.has("reasoning_effort") && rejectedEffort(res.text())) {
            String why = res.text();
            Log.w(TAG, "上游拒绝 reasoning_effort，去掉后重试: "
                    + (why.length() > 120 ? why.substring(0, 120) : why));
            body.remove("reasoning_effort");
            res = post(cfg, url, body);
        }
        // 有的网关不认 tools：去掉后重发一次，退回纯对话（总比整句失败好）
        if (res.code == 400 && body.has("tools")) {
            String why = res.text();
            Log.w(TAG, "上游不支持 tools，去掉后重试: "
                    + (why.length() > 120 ? why.substring(0, 120) : why));
            body.remove("tools");
            body.remove("tool_choice");
            res = post(cfg, url, body);
        }
        String text = res.text();
        if (!res.ok()) {
            throw new IllegalStateException("HTTP " + res.code
                    + " " + (text.length() > 200 ? text.substring(0, 200) : text));
        }

        JSONObject json = new JSONObject(text);
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IllegalStateException("大模型响应无 choices 字段");
        }
        JSONObject choice = choices.optJSONObject(0);
        JSONObject msg = choice == null ? null : choice.optJSONObject("message");
        LlmReply reply = new LlmReply();
        reply.content = msg == null ? "" : msg.optString("content", "").trim();
        JSONArray tcs = msg == null ? null : msg.optJSONArray("tool_calls");
        reply.toolCalls = tcs == null ? new JSONArray() : tcs;
        reply.finishReason = choice == null ? "" : choice.optString("finish_reason", "");
        JSONObject usage = json.optJSONObject("usage");
        JSONObject details = usage == null ? null : usage.optJSONObject("completion_tokens_details");
        reply.reasoningTokens = details == null ? 0 : details.optInt("reasoning_tokens", 0);
        return reply;
    }

    /** 统一的 POST 出口：超时 5s/20s、响应上限 2MB（这台机器内存很小）。 */
    private Http.Response post(Config cfg, String url, JSONObject body) throws Exception {
        return Http.request("POST", url, body.toString().getBytes("UTF-8"),
                "application/json; charset=utf-8",
                Http.headers("Authorization", "Bearer " + cfg.apiKey.trim()),
                5000, 20000, 2 * 1024 * 1024);
    }

    /** 400 是不是因为上游不认 reasoning_effort（其它 400 必须原样报给用户）。 */
    private static boolean rejectedEffort(String text) {
        return text != null && text.contains("reasoning_effort");
    }

    /**
     * 把"空回复"翻译成用户能下手的原因。
     *
     * <p>这段文案会进网页对话，也可能被播报，必须说清是输出额度问题；
     * 老版本统一提示"检查 API Key / 网络"，把人带到了完全错误的方向。
     */
    private String emptyReplyMessage(LlmReply reply, int budget, int used) {
        if ("length".equals(reply.finishReason)) {
            return "大模型把输出额度全用在思考上了，正文没来得及写（max_tokens " + used
                    + (reply.reasoningTokens > 0 ? " 中思考占 " + reply.reasoningTokens : "")
                    + "）。这不是 Key 或网络问题：可以把设置页的「思考」改成关闭，或把「最大输出」调大（当前 "
                    + budget + "，建议 1024 以上）。";
        }
        return "大模型返回了空内容（finish=" + (reply.finishReason.isEmpty() ? "未知" : reply.finishReason)
                + "，输出预算 " + used + "）。请确认该模型支持 /chat/completions，或换一个模型试试。";
    }

    /** 一次大模型响应的解析结果。 */
    private static final class LlmReply {
        /** 正文（已去掉首尾空白）。 */
        String content = "";
        /** 模型要求调用的工具；没有则为空数组。 */
        JSONArray toolCalls = new JSONArray();
        /** stop / length / content_filter …，空串表示响应里没带。 */
        String finishReason = "";
        /** usage.completion_tokens_details.reasoning_tokens，非推理模型为 0。 */
        int reasoningTokens;

        /** 既没有正文、也没有工具调用（截断重试用）。 */
        boolean isEmpty() {
            return content.isEmpty() && toolCalls.length() == 0;
        }
    }
}
