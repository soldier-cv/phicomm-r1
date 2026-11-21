package com.r1.brain;

/**
 * 灯环效果枚举：把「原厂支持的 5 种预置效果」收敛成一处，语音话术与网页按钮都指向这里。
 *
 * <p>{@link #value()} 是发给 L1 的协议值，必须与 L1 里
 * {@code com.phicomm.speaker.device.bridge.Bridge#applyLight(String)} 的 switch 保持一致。
 * L1 与 L2 分开编译、不共享代码，所以这边改值必须同步改 L1 并重新打包
 * （见 docs/07-protocol.md §4.1）。
 *
 * <p>注意：原厂 {@code PhicommLightController} 只暴露这几个预置效果，没有任意 RGB 接口。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 21:55 星期六
 */
public enum LightEffect {

    /** 全灭。 */
    OFF("off", "关闭"),
    /** 呼吸/转圈，思考中效果。 */
    LOADING("loading", "呼吸灯"),
    /** 回到最近一次唤醒的颜色。 */
    WAKEUP("wakeup", "唤醒色"),
    /** 休眠微光。 */
    DORMANT("dormant", "休眠微光"),
    /** 断网提示。 */
    NET_LOST("net", "断网提示"),
    /** 流光/扫描：用 24 段唤醒灯位做的动画（原厂没有 RGB 接口）。 */
    SCAN("scan", "流光");

    private final String value;
    private final String label;

    LightEffect(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /** 发给 L1 的协议值。 */
    public String value() {
        return value;
    }

    /** 给人看的名字。 */
    public String label() {
        return label;
    }

    /**
     * 按协议值找效果。
     *
     * @param value 协议值
     * @return 匹配的效果；未知返回 null（不能当成 OFF：网页传错一个值就会把灯悄悄关掉）
     */
    public static LightEffect of(String value) {
        if (value != null) {
            for (LightEffect e : values()) {
                if (e.value.equalsIgnoreCase(value.trim())) return e;
            }
        }
        return null;
    }

    /**
     * 能不能当「常亮效果」——对话结束后灯环回到的状态。
     *
     * <p>{@link #NET_LOST} 是断网提示，不是用户想长期看到的氛围灯。
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    public boolean isAmbient() {
        return this != NET_LOST;
    }

    /**
     * 把一句中文指令映射成灯环效果。
     *
     * <p>「客厅关灯」「打开卧室灯」这类带房间/设备名的话是智能家居指令，不是音箱灯环；
     * 只有没有其它对象、或明确提到「灯环」时才算。
     *
     * @param text 用户原话
     * @return 效果；不像灯环指令返回 null
     */
    public static LightEffect match(String text) {
        if (text == null) return null;
        if (!aboutRing(text)) return null;
        if (text.contains("流光") || text.contains("流水灯") || text.contains("跑马灯")) return SCAN;
        if (text.contains("呼吸灯") || text.contains("氛围灯")) return LOADING;
        if (text.contains("休眠灯") || text.contains("小夜灯")) return DORMANT;
        if (text.contains("关灯") || text.contains("把灯关") || text.contains("灯关")
                || (text.contains("灯环") && (text.contains("关") || text.contains("灭")))) return OFF;
        if (text.contains("开灯") || text.contains("亮灯") || text.contains("把灯打开")
                || (text.contains("灯环") && (text.contains("开") || text.contains("亮")))) return WAKEUP;
        return null;
    }

    /** 带这些词的「灯」指的是家里别的灯，交给 Home Assistant。 */
    private static final String[] OTHER_LIGHTS = {
            "客厅", "卧室", "厨房", "书房", "餐厅", "阳台", "卫生间", "厕所", "浴室", "走廊", "玄关",
            "台灯", "吊灯", "顶灯", "灯带", "射灯", "筒灯", "床头", "主灯", "大灯", "空调", "风扇", "电视"
    };

    private static boolean aboutRing(String text) {
        if (text.contains("灯环") || text.contains("音箱的灯") || text.contains("音箱灯")) return true;
        for (String w : OTHER_LIGHTS) {
            if (text.contains(w)) return false;
        }
        return true;
    }
}
