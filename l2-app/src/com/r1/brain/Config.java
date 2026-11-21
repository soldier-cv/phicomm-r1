package com.r1.brain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * 全局持久化配置存储（基于 SharedPreferences）。
 * 保存大模型参数、TTS 语音参数、智能家居与天气配置、闹钟列表及最近对话历史。
 *
 * <p><b>写配置一律走 {@link #update(Context, Mutator)}。</b>
 * 早期是「各处 load 出一份快照、改几个字段、再 save 整包」，两个并发保存会互相覆盖
 * （网页保存设置 / 闹钟自动关闭 / 令牌更新 / 历史写入都会撞车，曾经把 asrEngine/asrUrl 静默丢掉）。
 * {@code update} 在进程级锁内完成「读 → 改 → 写」，保证不再丢字段；
 * {@link #save(Context)} 只保留给极少数确实要整体覆盖的场景。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 09:38 星期五
 */
public class Config {

    private static final String SP = "r1brain";
    /** 进程级写锁：所有读改写都在这把锁里串行，避免整包覆盖丢字段。 */
    private static final Object LOCK = new Object();

    // 大模型配置（默认智谱 GLM-4.5-Flash，未填 Key 时自动运行于回显模式）
    public String llmProvider = "zhipu";
    public String baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    public String apiKey = "";
    public String model = "GLM-4.5-Flash";
    public String systemPrompt = "你是斐讯 R1 智能音箱的语音助手“小讯”。回答要口语化、简短自然，控制在两三句话以内，不要用 markdown。";
    public float temperature = 0.7f;
    public int maxTokens = 512;
    /** 思考（推理）控制：默认关闭；auto/none/low/medium/high，见 {@link ReasoningEffort}。 */
    public String reasoningEffort = "none";

    /** 网页访问令牌。首次使用为空（控制台开放），由用户自己设置后强制校验。 */
    public String token = "";
    /** 令牌是否由用户设置过。false 表示仍处于「首次使用开放」状态。 */
    public boolean tokenUserSet = false;

    /** TTS 端点（OpenAI 兼容 /v1/audio/speech）。默认用原厂离线 TTS，不需要任何配置。 */
    public String ttsEngine = "native";
    public String ttsUrl = "";
    public String ttsKey = "";
    public String ttsModel = "grok-tts";
    public String ttsVoice = "zh-CN-XiaoxiaoNeural";
    public float ttsSpeed = 1.0f;
    public boolean autoSpeak = true;
    /** 连续对话：语音回复播完后自动再听一句（需要 L1 v3，6 秒没人说话就收场）。 */
    public boolean continuousChat = false;
    /**
     * 是否在开机后尝试回收原厂 AirSkill（斐讯 IoT 云消息，RSS 约 25MB）。
     *
     * <p><b>默认关</b>：2026-11-06 实测它跑在 uid 1000，`pm disable-user` 被 SecurityException 拒绝；
     * `am force-stop` 只能停到下一次开机；L2 走本地回环 adb 执行 `am` 会 15 秒超时且不生效
     * （同样命令用电脑的 adb 执行有效）。真正禁用需要 root，而 `adb root` 被 `ro.secure=1` 挡回。
     * 保留开关是为了将来有 root 时直接打开。
     */
    public boolean reclaimAirskill = false;
    /**
     * 唤醒轮让 L1 自己判停（说完静音 1.2 秒就收尾），不等原厂 VAD。
     *
     * <p>默认关：原厂 VAD 慢（2 秒的话录成 5 秒）但不会切错；L1 v3 的判停会忽略唤醒词那一段，
     * 还没在真机上标定过，所以留给用户自己打开。
     */
    public boolean earlyEndpoint = false;

    /**
     * 让大模型调用本地工具（音量/音乐/灯环/闹钟/天气/状态）。
     *
     * <p>默认开：本地意图没命中的说法（「帮我把声音弄小点」）只有靠工具才能真的执行。
     * 关掉就退回纯对话，模型会像以前一样回答「我没法操作」。
     */
    public boolean llmTools = true;

    /**
     * 开机是否自动打开自建 DLNA 投屏接收端（{@code com.r1.brain.dlna.DlnaRenderer}）。
     *
     * <p>默认开：原厂播放器那个接收端每次开机都起不来（状态机没有 IDLE→等待投屏 这条边），
     * 而手机上「搜不到设备」是用户最先遇到的问题。关掉只是不自动开，语音/网页仍然能手动打开。
     */
    public boolean castEnabled = true;

    /**
     * 开机是否尝试激活原厂 AirPlay 接收端（TCP 5002）。
     *
     * <p>默认关：能否激活取决于原厂播放器是否走到 {@code startServer()}，失败不会假装成功。
     * 打开后由 {@link Receivers} 在启动时再试一次。
     */
    public boolean airplayEnabled = false;

    /**
     * 开机是否打开蓝牙适配器。
     *
     * <p>默认关：不在用户没要求时主动开蓝牙。语音/页面打开过就会记住。
     */
    public boolean bluetoothEnabled = false;

    /**
     * 语音识别来源：{@code self} = 自建 ASR 网关（走 asrUrl）。
     *
     * <p>默认必须是 {@code self}：原厂云端（aios-home.hivoice.cn）已于 2026-11 停止服务，
     * 退回 {@code native} 等于没有语音输入。见 docs/24-self-hosted-asr.md。
     */
    public String asrEngine = "self";
    /** ASR 网关根地址（整合网关：/v1/asr/transcribe，16k/16bit/单声道，单次 ≤60 秒）。 */
    public String asrUrl = "http://192.168.1.100:8720";
    /** 整合网关的调用方令牌（X-API-Key）。明文只存本机，页面只回 hasAsrToken。 */
    public String asrToken = "";

    /** 智能家居与和风天气配置。weatherHost 为账号专属 API Host，公共域名已逐步停服。 */    public String haUrl = "";
    public String haToken = "";
    public String weatherKey = "";
    public String weatherCity = "";
    public String weatherHost = "devapi.qweather.com";
    /** 和风鉴权方式：默认 JWT（控制台新凭据只发 JWT）；apikey 仅作兼容保留。 */
    public String weatherAuth = "jwt";
    /** JWT 凭据：开发者 ID（iss）、项目 ID（sub）、凭据 ID（kid）、Ed25519 私钥。 */
    public String weatherJwtIss = "";
    public String weatherJwtProjectId = "";
    public String weatherJwtKeyId = "";
    public String weatherJwtKey = "";
    /** 和私钥成对保存的公钥明文。 */
    public String weatherJwtPub = "";

    /** 云音乐登录 Cookie（只存设备本地，不上传） */
    public String neteaseCookie = "";
    public String qqCookie = "";

    /**
     * 点歌音源及顺序（逗号分隔，协议值见 {@link MusicSource#id()}）。
     *
     * <p>默认「本地曲库 → 网易云 → QQ」：只勾本地就绝不碰云端，
     * 本地 + 网易云就是「先本地、搜不到再云端」。
     */
    public String musicProviders = "songloft,netease,qq";

    /**
     * Songloft 本地曲库根地址（如 {@code http://192.168.1.100:58091}）。
     * 为空表示未启用；与 {@link #songloftToken} 同时非空才会参与点歌，见 {@link SongloftProvider#isReady}。
     */
    public String songloftUrl = "";
    /** Songloft 个人访问令牌（永久有效，网页「个人访问令牌」里生成）。 */
    public String songloftToken = "";

    /** 持久化 JSON 字符串：闹钟列表与最近对话记录 */
    public String alarms = "[]";
    public String chatHistoryJson = "[]";

    /**
     * 一次配置写入的变更函数。
     *
     * @author xudong.hua,deepseek
     * @since 2026-11-05 12:00 星期四
     */
    public interface Mutator {
        /**
         * 在锁内修改配置对象；返回后由 {@link #update} 统一落盘。
         *
         * @param c 当前配置
         */
        void apply(Config c);
    }

    /** 没填 Key 就进"回显模式"，这样没网没 Key 也能验证整条链路。 */
    public boolean isEchoMode() {
        return apiKey == null || apiKey.trim().isEmpty();
    }

    public static Config load(Context ctx) {
        return loadFromSp(ctx);
    }

    private static Config loadFromSp(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE);
        Config c = new Config();
        c.llmProvider = sp.getString("llmProvider", c.llmProvider);
        c.baseUrl = sp.getString("baseUrl", c.baseUrl);
        c.apiKey = sp.getString("apiKey", "");
        c.model = sp.getString("model", c.model);
        c.systemPrompt = sp.getString("systemPrompt", c.systemPrompt);
        c.temperature = sp.getFloat("temperature", c.temperature);
        c.maxTokens = sp.getInt("maxTokens", c.maxTokens);
        c.reasoningEffort = sp.getString("reasoningEffort", c.reasoningEffort);
        c.token = sp.getString("token", "");
        c.tokenUserSet = sp.getBoolean("tokenUserSet", false);

        c.ttsEngine = sp.getString("ttsEngine", c.ttsEngine);
        c.ttsUrl = sp.getString("ttsUrl", "");
        c.ttsKey = sp.getString("ttsKey", "");
        c.ttsModel = sp.getString("ttsModel", c.ttsModel);
        c.ttsVoice = sp.getString("ttsVoice", c.ttsVoice);
        c.ttsSpeed = sp.getFloat("ttsSpeed", c.ttsSpeed);
        c.autoSpeak = sp.getBoolean("autoSpeak", true);
        c.continuousChat = sp.getBoolean("continuousChat", c.continuousChat);
        c.llmTools = sp.getBoolean("llmTools", c.llmTools);
        c.castEnabled = sp.getBoolean("castEnabled", c.castEnabled);
        c.airplayEnabled = sp.getBoolean("airplayEnabled", c.airplayEnabled);
        c.bluetoothEnabled = sp.getBoolean("bluetoothEnabled", c.bluetoothEnabled);
        c.reclaimAirskill = sp.getBoolean("reclaimAirskill", c.reclaimAirskill);
        c.earlyEndpoint = sp.getBoolean("earlyEndpoint", c.earlyEndpoint);
        c.asrEngine = sp.getString("asrEngine", c.asrEngine);
        // 缺省值必须用字段里的默认地址：写成 "" 会把「开箱即用」的 ASR 网关抹掉，
        // 清数据后语音输入直接不可用（AsrClient.isConfigured 要求非空）。
        c.asrUrl = sp.getString("asrUrl", c.asrUrl);
        c.asrToken = sp.getString("asrToken", c.asrToken);

        c.haUrl = sp.getString("haUrl", "");
        c.haToken = sp.getString("haToken", "");
        c.weatherKey = sp.getString("weatherKey", "");
        c.weatherCity = sp.getString("weatherCity", "");
        c.weatherHost = sp.getString("weatherHost", c.weatherHost);
        c.weatherAuth = sp.getString("weatherAuth", c.weatherAuth);
        c.weatherJwtIss = sp.getString("weatherJwtIss", "");
        c.weatherJwtProjectId = sp.getString("weatherJwtProjectId", "");
        c.weatherJwtKeyId = sp.getString("weatherJwtKeyId", "");
        c.weatherJwtKey = sp.getString("weatherJwtKey", "");
        c.weatherJwtPub = sp.getString("weatherJwtPub", "");

        c.neteaseCookie = sp.getString("neteaseCookie", "");
        c.qqCookie = sp.getString("qqCookie", "");
        c.musicProviders = sp.getString("musicProviders", c.musicProviders);
        c.songloftUrl = sp.getString("songloftUrl", "");
        c.songloftToken = sp.getString("songloftToken", "");
        c.alarms = sp.getString("alarms", "[]");
        c.chatHistoryJson = sp.getString("chatHistoryJson", "[]");
        return c;
    }

    /** 原子读改写：整个「读 → 改 → 写」在进程锁内完成，并发写不会互相覆盖。 */
    public static void update(Context ctx, Mutator m) {
        synchronized (LOCK) {
            Config c = loadFromSp(ctx);
            m.apply(c);
            saveToSp(ctx, c);
        }
    }

    /** 整体落盘。一般不要直接调用，改用 {@link #update(Context, Mutator)}。 */
    public void save(Context ctx) {
        synchronized (LOCK) {
            saveToSp(ctx, this);
        }
    }

    private static void saveToSp(Context ctx, Config c) {
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
                .putString("llmProvider", c.llmProvider)
                .putString("baseUrl", c.baseUrl)
                .putString("apiKey", c.apiKey)
                .putString("model", c.model)
                .putString("systemPrompt", c.systemPrompt)
                .putFloat("temperature", c.temperature)
                .putInt("maxTokens", c.maxTokens)
                .putString("reasoningEffort", c.reasoningEffort)
                .putString("token", c.token)
                .putBoolean("tokenUserSet", c.tokenUserSet)
                .putString("ttsEngine", c.ttsEngine)
                .putString("ttsUrl", c.ttsUrl)
                .putString("ttsKey", c.ttsKey)
                .putString("ttsModel", c.ttsModel)
                .putString("ttsVoice", c.ttsVoice)
                .putFloat("ttsSpeed", c.ttsSpeed)
                .putBoolean("autoSpeak", c.autoSpeak)
                .putBoolean("continuousChat", c.continuousChat)
                .putBoolean("llmTools", c.llmTools)
                .putBoolean("castEnabled", c.castEnabled)
                .putBoolean("airplayEnabled", c.airplayEnabled)
                .putBoolean("bluetoothEnabled", c.bluetoothEnabled)
                .putBoolean("reclaimAirskill", c.reclaimAirskill)
                .putBoolean("earlyEndpoint", c.earlyEndpoint)
                .putString("asrEngine", c.asrEngine)
                .putString("asrUrl", c.asrUrl)
                .putString("asrToken", c.asrToken)
                .putString("haUrl", c.haUrl)
                .putString("haToken", c.haToken)
                .putString("weatherKey", c.weatherKey)
                .putString("weatherCity", c.weatherCity)
                .putString("weatherHost", c.weatherHost)
                .putString("weatherAuth", c.weatherAuth)
                .putString("weatherJwtIss", c.weatherJwtIss)
                .putString("weatherJwtProjectId", c.weatherJwtProjectId)
                .putString("weatherJwtKeyId", c.weatherJwtKeyId)
                .putString("weatherJwtKey", c.weatherJwtKey)
                .putString("weatherJwtPub", c.weatherJwtPub)
                .putString("neteaseCookie", c.neteaseCookie)
                .putString("qqCookie", c.qqCookie)
                .putString("musicProviders", c.musicProviders)
                .putString("songloftUrl", c.songloftUrl)
                .putString("songloftToken", c.songloftToken)
                .putString("alarms", c.alarms)
                .putString("chatHistoryJson", c.chatHistoryJson)
                .apply();
    }

    /**
     * 输出配置 JSON。
     *
     * <p>{@code forExport = true} 用于换机迁移：返回全部真实值（含密钥与 Cookie）。
     * {@code false} 用于网页读取：密钥只回 {@code hasXxx} 布尔，避免明文进页面。
     * 唯一例外是 {@code weatherJwtKey}：设置页要回填到 Ed25519 私钥输入框才能继续编辑，
     * 所以两种模式都返回明文（前端用 {@code secretValue} 做显示掩码）。
     *
     * @param forExport 是否导出模式
     * @return 配置对象（含 {@code ok:true}）
     * @author xudong.hua,deepseek
     * @since 2026-11-05 12:00 星期四
     */
    public JSONObject toJson(boolean forExport) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
            o.put("llmProvider", nz(llmProvider));
            o.put("baseUrl", nz(baseUrl));
            o.put("model", nz(model));
            o.put("systemPrompt", nz(systemPrompt));
            o.put("temperature", temperature);
            o.put("maxTokens", maxTokens);
            o.put("reasoningEffort", nz(reasoningEffort));
            o.put("echoMode", isEchoMode());
            o.put("ttsEngine", nz(ttsEngine));
            o.put("asrEngine", nz(asrEngine));
            o.put("asrUrl", nz(asrUrl));
            o.put("hasAsrToken", !nz(asrToken).trim().isEmpty());
            o.put("ttsUrl", nz(ttsUrl));
            o.put("ttsModel", nz(ttsModel));
            o.put("ttsVoice", nz(ttsVoice));
            o.put("ttsSpeed", ttsSpeed);
            o.put("autoSpeak", autoSpeak);
            o.put("continuousChat", continuousChat);
            o.put("llmTools", llmTools);
        o.put("castEnabled", castEnabled);
            o.put("airplayEnabled", airplayEnabled);
            o.put("bluetoothEnabled", bluetoothEnabled);
            o.put("reclaimAirskill", reclaimAirskill);
            o.put("earlyEndpoint", earlyEndpoint);
            o.put("haUrl", nz(haUrl));
            o.put("weatherCity", nz(weatherCity));
            o.put("weatherHost", nz(weatherHost));
            o.put("weatherAuth", nz(weatherAuth));
            o.put("weatherJwtIss", nz(weatherJwtIss));
            o.put("weatherJwtProjectId", nz(weatherJwtProjectId));
            o.put("weatherJwtKeyId", nz(weatherJwtKeyId));
            o.put("weatherJwtPub", nz(weatherJwtPub));
            o.put("songloftUrl", nz(songloftUrl));
            o.put("musicProviders", nz(musicProviders));
            try {
                o.put("alarms", new JSONArray(alarms));
            } catch (Throwable t) {
                o.put("alarms", new JSONArray());
            }

            o.put("hasKey", !isEchoMode());
            o.put("hasTtsKey", !nz(ttsKey).trim().isEmpty());
            o.put("hasHaToken", !nz(haToken).trim().isEmpty());
            o.put("hasWeatherKey", !nz(weatherKey).trim().isEmpty());
            o.put("hasWeatherJwtKey", !nz(weatherJwtKey).trim().isEmpty());
            o.put("hasNeteaseCookie", !nz(neteaseCookie).isEmpty());
            o.put("hasQQCookie", !nz(qqCookie).isEmpty());
            o.put("hasSongloftToken", !nz(songloftToken).trim().isEmpty());
            // 设置页要回填私钥才能继续编辑，两种模式都返回
            o.put("weatherJwtKey", nz(weatherJwtKey));

            if (forExport) {
                o.put("apiKey", nz(apiKey));
                o.put("ttsKey", nz(ttsKey));
                o.put("haToken", nz(haToken));
                o.put("weatherKey", nz(weatherKey));
                o.put("neteaseCookie", nz(neteaseCookie));
                o.put("qqCookie", nz(qqCookie));
                o.put("songloftToken", nz(songloftToken));
            }
        } catch (Throwable t) {
            try { o.put("ok", false); } catch (Throwable ignored) { }
        }
        return o;
    }

    /**
     * 把请求里的字段合并进配置（未出现的字段保持不变）。
     *
     * <p>密钥字段收到 {@code ***} 时跳过，避免把页面上的掩码写回数据库。
     * 这里只做「值归一 + 长度/范围限制」，不写二进制、不碰 token。
     *
     * @param o 请求 JSON
     * @author xudong.hua,deepseek
     * @since 2026-11-05 12:00 星期四
     */
    public void applyJson(JSONObject o) {
        if (o == null) return;

        if (o.has("llmProvider")) llmProvider = o.optString("llmProvider", llmProvider);
        if (o.has("baseUrl")) baseUrl = o.optString("baseUrl", baseUrl);
        if (o.has("llmBaseUrl")) baseUrl = o.optString("llmBaseUrl", baseUrl);
        if (o.has("model")) model = o.optString("model", model);
        if (o.has("llmModel")) model = o.optString("llmModel", model);
        if (o.has("apiKey") && !masked(o.optString("apiKey", ""))) apiKey = o.optString("apiKey", "");
        if (o.has("llmApiKey") && !masked(o.optString("llmApiKey", ""))) apiKey = o.optString("llmApiKey", "");
        if (o.has("systemPrompt")) systemPrompt = o.optString("systemPrompt", systemPrompt);
        if (o.has("llmSystemPrompt")) systemPrompt = o.optString("llmSystemPrompt", systemPrompt);
        if (o.has("temperature")) {
            double t = o.optDouble("temperature", temperature);
            if (!Double.isNaN(t)) temperature = (float) Math.max(0, Math.min(2, t));
        }
        if (o.has("maxTokens")) maxTokens = clampMaxTokens(o.optInt("maxTokens", maxTokens));
        if (o.has("reasoningEffort")) {
            reasoningEffort = ReasoningEffort.of(o.optString("reasoningEffort", reasoningEffort)).value();
        }

        if (o.has("ttsEngine")) ttsEngine = o.optString("ttsEngine", ttsEngine);
        if (o.has("ttsUrl")) ttsUrl = o.optString("ttsUrl", ttsUrl);
        if (o.has("ttsKey") && !masked(o.optString("ttsKey", ""))) ttsKey = o.optString("ttsKey", ttsKey);
        if (o.has("ttsModel")) ttsModel = o.optString("ttsModel", ttsModel);
        if (o.has("ttsVoice")) ttsVoice = o.optString("ttsVoice", ttsVoice);
        if (o.has("ttsSpeed")) {
            double speed = o.optDouble("ttsSpeed", ttsSpeed);
            if (Double.isNaN(speed) || speed < 0.5 || speed > 2.0) speed = 1.0;
            ttsSpeed = (float) speed;
        }

        // 语音识别来源：设置页提交的 asrEngine/asrUrl 必须落库。
        // 曾经漏掉这两个字段，导致选了「自建 ASR」也被静默丢弃，见 docs/24-self-hosted-asr.md。
        if (o.has("asrEngine")) {
            asrEngine = "self".equals(o.optString("asrEngine", asrEngine)) ? "self" : "native";
        }
        if (o.has("asrUrl")) asrUrl = o.optString("asrUrl", asrUrl);
        if (o.has("asrToken") && !masked(o.optString("asrToken", ""))) {
            asrToken = o.optString("asrToken", "").trim();
        }

        // Songloft 本地曲库：地址与令牌一起决定这个音源是否激活（见 SongloftProvider.isReady）
        if (o.has("songloftUrl")) songloftUrl = SongloftProvider.normalizeBase(o.optString("songloftUrl", ""));
        if (o.has("songloftToken") && !masked(o.optString("songloftToken", ""))) {
            songloftToken = o.optString("songloftToken", "").trim();
        }

        if (o.has("autoSpeak")) autoSpeak = o.optBoolean("autoSpeak", autoSpeak);
        if (o.has("continuousChat")) continuousChat = o.optBoolean("continuousChat", continuousChat);
        if (o.has("llmTools")) llmTools = o.optBoolean("llmTools", llmTools);
        if (o.has("castEnabled")) castEnabled = o.optBoolean("castEnabled", castEnabled);
        if (o.has("airplayEnabled")) airplayEnabled = o.optBoolean("airplayEnabled", airplayEnabled);
        if (o.has("bluetoothEnabled")) bluetoothEnabled = o.optBoolean("bluetoothEnabled", bluetoothEnabled);
        if (o.has("reclaimAirskill")) reclaimAirskill = o.optBoolean("reclaimAirskill", reclaimAirskill);
        if (o.has("earlyEndpoint")) earlyEndpoint = o.optBoolean("earlyEndpoint", earlyEndpoint);

        if (o.has("haUrl")) haUrl = o.optString("haUrl", haUrl);
        if (o.has("haToken") && !masked(o.optString("haToken", ""))) haToken = o.optString("haToken", haToken);
        if (o.has("weatherKey") && !masked(o.optString("weatherKey", ""))) {
            weatherKey = o.optString("weatherKey", weatherKey);
        }
        if (o.has("weatherCity")) weatherCity = o.optString("weatherCity", weatherCity);
        if (o.has("weatherHost")) weatherHost = o.optString("weatherHost", weatherHost);
        if (o.has("weatherAuth")) weatherAuth = o.optString("weatherAuth", weatherAuth);
        if (o.has("weatherJwtIss")) weatherJwtIss = o.optString("weatherJwtIss", weatherJwtIss);
        if (o.has("weatherJwtProjectId")) weatherJwtProjectId = o.optString("weatherJwtProjectId", weatherJwtProjectId);
        if (o.has("weatherJwtKeyId")) weatherJwtKeyId = o.optString("weatherJwtKeyId", weatherJwtKeyId);
        if (o.has("weatherJwtKey") && !masked(o.optString("weatherJwtKey", ""))) {
            weatherJwtKey = o.optString("weatherJwtKey", weatherJwtKey);
        }
        if (o.has("weatherJwtPub")) weatherJwtPub = o.optString("weatherJwtPub", weatherJwtPub);

        // 登录 Cookie 属于配置的一部分：导出/导入要能带走，网页保存时也可以直接写。
        if (o.has("neteaseCookie")) neteaseCookie = o.optString("neteaseCookie", neteaseCookie);
        if (o.has("qqCookie")) qqCookie = o.optString("qqCookie", qqCookie);

        // 点歌音源：只保留已知协议值，顺序按用户给的来；空则保持原值
        if (o.has("musicProviders")) {
            String csv = o.optString("musicProviders", "");
            StringBuilder kept = new StringBuilder();
            for (String part : csv.split(",")) {
                MusicSource s = MusicSource.of(part);
                if (s == null) continue;
                if (kept.length() > 0) kept.append(',');
                kept.append(s.id());
            }
            if (kept.length() > 0) musicProviders = kept.toString();
        }

        if (o.has("alarms")) {
            JSONArray arr = o.optJSONArray("alarms");
            if (arr != null && arr.length() <= 50) alarms = arr.toString();
        }

        // 端点只接受 http(s)，避免误填 file:// 之类导致后续请求直接抛异常
        if (!isHttpUrl(baseUrl)) baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
        if (!ttsUrl.trim().isEmpty() && !isHttpUrl(ttsUrl)) ttsUrl = "";
        if (!asrUrl.trim().isEmpty() && !isHttpUrl(asrUrl)) asrUrl = "";
        if (!haUrl.trim().isEmpty() && !isHttpUrl(haUrl)) haUrl = "";

        if (asrUrl != null && asrUrl.length() > 512) asrUrl = asrUrl.substring(0, 512);
        if (songloftUrl != null && songloftUrl.length() > 512) songloftUrl = songloftUrl.substring(0, 512);
        // 令牌是 JWT/长串，留足空间但不允许无限长；换行会被拼接进请求头，直接清掉
        if (songloftToken != null) {
            songloftToken = songloftToken.replace("\r", "").replace("\n", "");
            if (songloftToken.length() > 2048) songloftToken = songloftToken.substring(0, 2048);
        }
        if (apiKey != null && apiKey.length() > 1024) apiKey = apiKey.substring(0, 1024);
        if (ttsKey != null && ttsKey.length() > 1024) ttsKey = ttsKey.substring(0, 1024);
        if (haToken != null && haToken.length() > 1024) haToken = haToken.substring(0, 1024);
        if (weatherKey != null && weatherKey.length() > 1024) weatherKey = weatherKey.substring(0, 1024);
    }

    /** 输出预算统一限制在 32~2048：越界值按边界取，避免把设备或上游打死。 */
    public static int clampMaxTokens(int value) {
        if (value < 32) return 32;
        return Math.min(value, 2048);
    }

    /** 密钥掩码：页面回传的 {@code ***} 不能写回数据库。 */
    private static boolean masked(String v) {
        return v == null || v.contains("***");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean isHttpUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(Locale.US);
        return u.startsWith("http://") || u.startsWith("https://");
    }

    @Override
    public String toString() {
        String masked = (apiKey == null || apiKey.isEmpty())
                ? "(空)"
                : apiKey.substring(0, Math.min(4, apiKey.length())) + "***";
        return "model=" + model + " key=" + masked + " url=" + baseUrl
                + " echo=" + isEchoMode();
    }
}
