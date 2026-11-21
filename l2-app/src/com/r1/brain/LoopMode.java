package com.r1.brain;

/**
 * 播放循环模式。
 *
 * <p>为什么要有这个枚举：模式值同时出现在三个地方 —— 网页按钮轮换、HTTP 参数
 * {@code POST /api/v1/player/mode}、播放器内部判断。以前是裸字符串
 * （{@code list/single/shuffle}），前端拼错一个字母就静默退回列表循环，
 * 排查时看不出来。现在只有 {@link #value()} 里出现协议值。
 *
 * <p>{@link #value()} 会原样出现在 HTTP 与前端 {@code state.loopMode} 里，改名等于改协议。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 23:10 星期六
 */
public enum LoopMode {

    /** 顺序循环（默认）。 */
    LIST("list", "列表循环"),
    /** 单曲循环。 */
    SINGLE("single", "单曲循环"),
    /** 随机播放。 */
    SHUFFLE("shuffle", "随机播放");

    private final String value;
    private final String label;

    LoopMode(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /** 协议值。 */
    public String value() {
        return value;
    }

    /** 给人看的名字。 */
    public String label() {
        return label;
    }

    /**
     * 按协议值解析，无法识别时退回 {@link #LIST}。
     *
     * @param v 协议值或用户输入
     * @return 对应的模式，保证非 null
     */
    public static LoopMode of(String v) {
        if (v != null) {
            for (LoopMode m : values()) {
                if (m.value.equalsIgnoreCase(v.trim())) return m;
            }
        }
        return LIST;
    }
}
