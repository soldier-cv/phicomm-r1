package com.r1.brain;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * 大模型的思考（推理）开关与强度。
 *
 * <p>为什么只发 {@code reasoning_effort} 一个字段：2026-11-01 在本机网关
 * （{@code 192.168.1.100:8317}）上对两个在用模型逐一实测，结论是
 * <ul>
 *   <li>{@code reasoning_effort} 两边都认：取值 {@code none} 时思考 token 直接归零
 *       （deepseek-flash 243 → 0，耗时 4.7s → 2.0s）；</li>
 *   <li>{@code thinking} / {@code enable_thinking} 只被部分上游接受，
 *       另一个上游会直接 HTTP 400 {@code Unknown parameter}；</li>
 *   <li>{@code thinking_budget} / {@code chat_template_kwargs} 被静默忽略。</li>
 * </ul>
 * 所以这里只发 {@code reasoning_effort}，并且"自动"档位一个字段都不发，
 * 保证换模型、换网关时不会因为多塞私有字段把请求打挂。
 *
 * <p>默认档位是 {@link #OFF}：思考 token 和正文抢同一份预算，关掉最不容易出"空回复"，
 * 而且实测回答更短更稳（256 预算下 2.3~3.4 秒、50~80 字、不截断）。
 *
 * <p>DeepSeek 官方文档的定义：{@code none} 关闭思考，{@code low/high/max} 开启，
 * {@code minimal} 视作 {@code low}，{@code medium}/{@code xhigh} 视作 {@code high}。
 * 因此"中"这一档在 DeepSeek 上等价于"高"，在 OpenAI 系模型上才是真正的中档。
 *
 * @author xudong.hua,deepseek
 * @since 2026-11-01 16:55 星期日
 */
public enum ReasoningEffort {

    /** 不干预：请求里不带任何思考字段，由模型自己的默认策略决定。 */
    AUTO("auto", "自动（不干预）"),
    /** 关闭思考：对应 {@code reasoning_effort: none}。 */
    OFF("none", "关闭"),
    LOW("low", "低"),
    MEDIUM("medium", "中"),
    HIGH("high", "高");

    private final String value;
    private final String label;

    ReasoningEffort(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /** 协议值：写进 {@code reasoning_effort}，同时作为配置里持久化的值。 */
    public String value() {
        return value;
    }

    /** 给人看的文案。 */
    public String label() {
        return label;
    }

    /** 容错解析：空值、老配置、未知值一律回落到"自动"，避免改坏存量设置。 */
    public static ReasoningEffort of(String raw) {
        if (raw == null) return AUTO;
        String v = raw.trim().toLowerCase(Locale.US);
        for (ReasoningEffort e : values()) {
            if (e.value.equals(v)) return e;
        }
        return AUTO;
    }

    /** 这个档位是否可能产生思考 token（"自动"下模型也可能是推理型，同样要留额度）。 */
    public boolean mayThink() {
        return this != OFF;
    }

    /**
     * 把档位写进请求体。
     *
     * <p>"自动"会顺手移除字段：请求体在重试时会被复用，
     * 不能在上一轮的 {@code reasoning_effort} 上叠加。
     *
     * @param body 待发送的 chat completions 请求体
     * @throws JSONException 请求体构造失败（理论上不会发生，交给调用方统一报错）
     */
    public void apply(JSONObject body) throws JSONException {
        if (this == AUTO) {
            body.remove("reasoning_effort");
            return;
        }
        body.put("reasoning_effort", value);
    }
}
