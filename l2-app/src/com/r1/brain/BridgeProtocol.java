package com.r1.brain;

/**
 * L1 ↔ L2 桥协议常量（L2 侧）。
 *
 * <p>这些字符串是**跨进程协议**：L1（原厂包内的桥）与 L2 各自编译、各写一份，
 * 谁都不能引用对方的类。所以两边的值必须逐字一致，改一处就两端一起改，
 * 并同步 docs/07-protocol.md。
 *
 * <p>把字面量收敛到这里，至少保证 L2 内部只有一份真相：
 * {@link BridgeReceiver} 与 {@link L1Bridge} 不再各自写 {@code "com.r1.bridge"}。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 23:15 星期六
 */
public final class BridgeProtocol {

    /** L1 发事件给 L2 的 action。 */
    public static final String ACTION_EVENT = "com.r1.bridge";
    /** L2 发指令给 L1 的 action。 */
    public static final String ACTION_CMD = "com.r1.bridge.cmd";

    /** L1 的包名（顶替原厂包）。 */
    public static final String PACKAGE_L1 = "com.phicomm.speaker.device";

    /** 事件类型：语音识别结果。 */
    public static final String TYPE_ASR_RESULT = "ASR_RESULT";
    /**
     * 事件类型：唤醒词命中（还没开始录命令）。
     *
     * <p>为什么单独一条：播放中麦克风给不出可用音频（实测 rms≈3），必须在录音开始前
     * 就让 L2 把音乐/TTS 停掉。L1 在 {@code NLUDispatcher.onWakeupResult} 里立刻发出，
     * 比 {@link #TYPE_ASR_START} 早几十毫秒。
     */
    public static final String TYPE_WAKE = "WAKE";
    /**
     * 事件类型：这一轮识别被取消（原厂取消 / 录音太短 / TTS 尾音保护）。
     *
     * <p>没有它，L2 会以为录音还在继续，期间再唤醒也没人接。
     */
    public static final String TYPE_ASR_CANCEL = "ASR_CANCEL";
    /** 识别可信度太低（原厂本地语法硬套命令），应请用户重说，不要执行。 */
    public static final String TYPE_ASR_WEAK = "ASR_WEAK";
    /** L1 送来一段录音文件（整段上传整合 ASR 网关用），extra 带绝对路径。 */
    public static final String TYPE_ASR_AUDIO = "ASR_AUDIO";
    /**
     * L1 开始录这一轮，extra 带同一个录音路径。
     *
     * <p>L2 收到后立刻给音乐「让路」；不再开流式会话，整段识别等
     * {@link #TYPE_ASR_AUDIO} 到达后上传。
     */
    public static final String TYPE_ASR_START = "ASR_START";
    /** 事件类型：心跳（每 30 秒）。 */
    public static final String TYPE_L1_HELLO = "L1_HELLO";
    /** 事件类型：灯环指令回执。 */
    public static final String TYPE_LIGHT_RESULT = "LIGHT_RESULT";
    /**
     * 事件类型：L2 托 L1 用原厂 TTS 念的那句念完了（v3 起），extra 带 {@link #EXTRA_ID}。
     *
     * <p>原厂 TTS 没有播完回调，L2 只能靠它结束这一轮：恢复音乐、换回常亮灯效、决定续听。
     */
    public static final String TYPE_TTS_DONE = "TTS_DONE";

    /**
     * 支持「不靠 enterASR 的续听」与 {@link #TYPE_TTS_DONE} 的 L1 协议版本。
     *
     * <p>老 L1 的续听调 {@code enterASR}，实测录到的是静音（peak≈170），所以低于这个版本不自动续听。
     */
    public static final int L1_VER_FOLLOW_UP = 3;

    /** 指令类型：控制灯环。 */
    public static final String CMD_LIGHT = "light";
    /** 指令类型：续听一句（连续对话）。v3 起 L1 直接从唤醒态麦克风流录，不再调 enterASR。 */
    public static final String CMD_LISTEN = "listen";
    /** 指令类型：唤醒轮是否让 L1 自己判停（extra {@code on}，v3 起）。 */
    public static final String CMD_ENDPOINT = "endpoint";
    /** 指令类型：压制/恢复原厂播报（L2 配好 TTS 后打开）。 */
    public static final String CMD_MUTE = "mute";
    /** 指令类型：让 L1 用原厂 TTS 播一句话（未配 HTTP TTS 时的兜底）。 */
    public static final String CMD_SPEAK = "speak";
    /** 指令类型：立刻停掉原厂 TTS（语音“别说了”）。 */
    public static final String CMD_STOP = "stop";
    /** 指令类型：恢复被唤醒暂停的原厂播放（投屏/蓝牙），extra {@code action}=resume。 */
    public static final String CMD_MEDIA = "media";
    /** 尝试启动投屏接收端（DLNA/AirPlay）；能不能成由 L2 再查端口确认。 */
    public static final String CMD_CAST = "cast";
    /** 尝试打开/关闭原厂 AirPlay 接收端（extra {@code on}），L1 在播放器进程里反射 startServer/stopServer。 */
    public static final String CMD_AIRPLAY = "airplay";
    /** L1 反射尝试 AirPlay 后的回执，extra 带 {@link #EXTRA_TEXT}（细节）和 {@link #EXTRA_OK}。 */
    public static final String TYPE_AIRPLAY_RESULT = "AIRPLAY_RESULT";
    /** 指令类型：直接调用原厂引擎的反射方法（唤醒引擎停摆时的软恢复）。 */
    public static final String CMD_ENGINE = "engine";
    /** {@link #CMD_ENGINE} 要调用的方法名，如 {@code enterWakeup}。 */
    public static final String EXTRA_METHOD = "method";

    /** extra 键名。 */
    public static final String EXTRA_VER = "v";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_OK = "ok";
    public static final String EXTRA_TS = "ts";
    /** speak 指令与 TTS_DONE 事件里的播报序号（L2 生成，L1 原样带回）。 */
    public static final String EXTRA_ID = "id";
    /** speak 指令的附加项：念完自动重新进入识别（连续对话）。 */
    public static final String EXTRA_LISTEN = "listen";
    /** ASR_AUDIO 事件里的录音文件绝对路径。 */
    public static final String EXTRA_PATH = "path";
    /** ASR_CANCEL 事件里的取消原因（日志与排查用，不做逻辑判断）。 */
    public static final String EXTRA_REASON = "reason";

    private BridgeProtocol() {
    }
}
