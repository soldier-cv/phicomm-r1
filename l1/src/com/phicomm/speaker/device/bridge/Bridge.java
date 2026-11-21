package com.phicomm.speaker.device.bridge;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * L1 桥：把原厂唤醒/ASR 的结果转发给 L2（com.r1.brain）。
 *
 * <p>设计约束：
 * <ul>
 *   <li>本类只依赖 {@code android.*}，不引用任何厂商类，便于独立编译与测试；</li>
 *   <li>不修改 AndroidManifest —— 原厂包的清单在签名 zip 里，改了签名就废；</li>
 *   <li>不做业务判断、不发网络请求，只做「取文本 → 发广播」。</li>
 * </ul>
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-30 21:40 星期五
 */
public final class Bridge {

    private static final String TAG = "R1BRIDGE";

    /** 事件类型：语音识别结果 / 心跳 / 灯环回执。协议值，与 L2 的 BridgeProtocol 逐字一致。 */
    private static final String TYPE_ASR_RESULT = "ASR_RESULT";
    private static final String TYPE_L1_HELLO = "L1_HELLO";
    private static final String TYPE_LIGHT_RESULT = "LIGHT_RESULT";
    /** 识别结果可信度太低（原厂本地语法硬套命令），L2 应请用户重说而不是执行。 */
    private static final String TYPE_ASR_WEAK = "ASR_WEAK";
    /**
     * 一轮说话的录音文件路径。原厂云端已停止服务（见 docs/24-self-hosted-asr.md），
     * 语音输入改为这条通道：L1 录 WAV，L2 整段上传整合 ASR 网关（见 docs/24）。
     */
    private static final String TYPE_ASR_AUDIO = "ASR_AUDIO";
    /** 开始录这一轮，extra 带路径；L2 用它给音乐「让路」（不是流式，整段上传仍靠 ASR_AUDIO）。与 L2 的 BridgeProtocol 逐字一致。 */
    private static final String TYPE_ASR_START = "ASR_START";
    /**
     * 唤醒词刚命中、还没开始录命令。L2 收到立刻停掉音乐/TTS。
     *
     * <p>为什么必须比 ASR_START 更早：播放中麦克风给不出可用音频（实测 rms≈3），
     * 等到开麦再停已经晚了一轮。
     */
    private static final String TYPE_WAKE = "WAKE";
    /** 这一轮被取消（原厂取消 / 录音太短 / TTS 尾音保护），L2 应立即释放流式会话。 */
    private static final String TYPE_ASR_CANCEL = "ASR_CANCEL";
    /**
     * L2 让我们用原厂 TTS 念的那句念完了，extra 带 L2 给的 {@link #EXTRA_ID}。
     *
     * <p>原厂 TTS 没有播完回调，L2 只能靠它知道「这一轮什么时候结束」，
     * 好去恢复音乐、把灯环换回常亮效果。
     */
    private static final String TYPE_TTS_DONE = "TTS_DONE";
    private static final String TYPE_AIRPLAY_RESULT = "AIRPLAY_RESULT";
    private static final String CMD_LIGHT = "light";
    private static final String CMD_LISTEN = "listen";
    private static final String CMD_MUTE = "mute";
    private static final String CMD_SPEAK = "speak";
    private static final String CMD_STOP = "stop";
    /** 设置开机提示音的音量上限（0~1）。 */
    private static final String CMD_BEEP = "beep";
    /** 暂停/恢复原厂音乐服务（投屏/蓝牙由它出声），extra {@code action}=pause|resume。 */
    private static final String CMD_MEDIA = "media";
    /**
     * 尝试启动投屏接收端（DLNA/AirPlay）。
     *
     * <p>原厂播放器只在「播放停止 → 等待投屏」的状态迁移里调 {@code startServer()}，
     * 开机不会走到，所以接收端一直不广播（实测只开 5555/8686 两个端口）。
     * 这里把原厂音乐播放器 {@code stop()} 一下，尽量促成那次迁移。
     */
    private static final String CMD_CAST = "cast";
    /**
     * 打开/关闭原厂外部接收端（AirPlay + 原厂 DLNA + 蓝牙 A2DP 接收）。
     *
     * <p>真正的入口不是在 device 进程里反射，而是给 {@code com.phicomm.speaker.player}
     * 发 {@code msgcenter} 消息：type=0x40，sub=1 开始等待外部连接（会调
     * {@code EasyPlayer.startWaitingExternalConnect()} → {@code bootAllExternalConnectPlayer()}
     * → {@code AirPortPlayer.startServer()}），sub=2 关闭，sub=3 续期。
     * Shairport 默认监听 TCP 5002（不是 7000），并用 mDNS 广播 {@code _raop._tcp}。
     */
    private static final String CMD_AIRPLAY = "airplay";
    /** 与 {@code PhicommXController.openBlueToothStatus} 相同的 msgcenter 类型。 */
    private static final int MSG_TYPE_EXTERNAL_CONNECT = 0x40;
    private static final int MSG_SUB_EXTERNAL_START = 1;
    private static final int MSG_SUB_EXTERNAL_STOP = 2;
    private static final int MSG_SUB_EXTERNAL_RESTART = 3;
    /**
     * 直接给原厂播放器发一条消息（extra {@code t}=类型, {@code s}=子命令），只用于排查。
     *
     * <p>类型见 {@code android.os.MessageDispatchManager}：4=PLAYER、16=WIFI、4096=LIGHT…
     */
    private static final String CMD_MSG = "msg";
    /**
     * 唤醒轮次是否启用提前判停（extra {@code on}）。L2 的配置是唯一真相，
     * 启动时与每次保存设置都会发一次，所以 L1 只放内存、不落盘。
     */
    private static final String CMD_ENDPOINT = "endpoint";

    /** extra 键名（与 L2 的 BridgeProtocol.EXTRA_* 一致）。 */
    private static final String EXTRA_VER = "v";
    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_TEXT = "text";
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_OK = "ok";
    private static final String EXTRA_TS = "ts";
    private static final String EXTRA_CMD = "cmd";
    /** 识别可信度（原厂 score），只在 ASR_WEAK 里带。 */
    private static final String EXTRA_SCORE = "score";
    /** ASR_AUDIO 里录音文件的绝对路径。 */
    private static final String EXTRA_PATH = "path";
    /** speak 指令的附加项：念完自动重新进入识别（连续对话）。 */
    private static final String EXTRA_LISTEN = "listen";
    /** beep 指令的附加项：提示音音量上限（float，0~1）。 */
    private static final String EXTRA_CAP = "cap";
    /** ASR_CANCEL 的取消原因（仅日志用）。 */
    private static final String EXTRA_REASON = "reason";
    /** speak 指令与 TTS_DONE 事件里的播报序号（L2 生成，原样带回）。 */
    private static final String EXTRA_ID = "id";

    /**
     * 唤醒后、开麦前留给 L2 暂停播放的时间。
     *
     * <p>实测播放中开麦拿到的 PCM 是数字静音；L2 收到 WAKE 后需要几十毫秒把
     * MediaPlayer 停掉。这里在 {@code openAudioIn} 入口（原厂真正打开麦克风之前）
     * 等一小段，代价是每次唤醒多 250ms，但用户此时还在听唤醒提示音。
     */
    private static final long OPEN_DELAY_MS = 250;

    /** 与 L2 约定的广播 action（见 docs/07-protocol.md）。 */
    public static final String ACTION = "com.r1.bridge";

    /** L2 发指令给 L1 的 action（灯环等硬件动作）。 */
    public static final String ACTION_CMD = "com.r1.bridge.cmd";

    /** L2 包名，用于显式定向广播，避免被其它应用收到。 */
    public static final String L2_PACKAGE = "com.r1.brain";

    /** L2 常驻服务的组件名：L1 负责在开机后把它拉起来。 */
    private static final String L2_SERVICE = "com.r1.brain.MainService";

    /** 原厂 SharedPreferences 文件名（SharedPreferencesHelper 的默认值）。 */
    private static final String PREF_FILE = "defaultSp";

    /** 原厂「已绑定」标志位，闸门检查的就是它。 */
    private static final String PREF_BIND = "isBinded";

    /** 我们自己那份 SharedPreferences（不跟原厂配置混在一起）。 */
    private static final String PREF_SELF = "r1bridge";
    /** 提示音音量上限。 */
    private static final String PREF_BEEP_CAP = "beepCap";

    private static volatile Context sCtx;

    /** 原厂 ANTEngine 实例，由 ExampleANTEngineInitializer.onEngineInitDone 注入，用于「继续听」。 */
    private static volatile Object sEngine;

    /** L2 配好 TTS 后会置为 true：L1 在转发语音后压掉原厂的播报，避免双播。 */
    private static volatile boolean sMuteOriginal = false;

    /** 我们正在用原厂 TTS 说 L2 的话；此时“压原厂播报”的轮询必须让路，否则会掐掉自己。 */
    private static volatile boolean sOwnSpeak = false;

    /** 最近一次播出去的文本，用于过滤“麦克风听到自己”产生的假识别。 */
    private static volatile String sLastSpokenText = "";

    /** 最近一次播报结束的时间（我们或原厂的），用于回声保护窗口。 */
    private static volatile long sLastTtsEndAt = 0;

    /** 「压原厂播报」窗口的截止时间；唤醒时清零，让那个轮询立刻收手。 */
    private static volatile long sSuppressUntil = 0;

    /** 主线程 Handler（懒加载，类可能在任意线程被第一次加载）。 */
    private static volatile Handler sMain;
    /**
     * 提示音（开机音）的音量上限，0~1。
     *
     * <p>原厂 {@code UniMediaPlayer.playBeepSound} 把播放器增益写死成 1.0，
     * 于是开机那段 {@code bootloader_completed.mp3} 的响度只由系统媒体音量决定，
     * 而开机那一刻音量往往还没恢复成用户设置，听起来就比平时播歌响得多。
     * 这里给播放器增益封顶，并可经 {@code beep} 指令在运行时调整。
     */
    private static volatile float sBeepCap = 0.4f;

    private Bridge() {
    }

    /**
     * L1 初始化，由原厂 ExampleApp.onCreate 调用一次。
     *
     * @param ctx Application context
     */
    public static synchronized void init(Context ctx) {
        if (sCtx != null || ctx == null) {
            return;
        }
        sCtx = ctx.getApplicationContext();

        // 拆闸门：原厂 PhicommInterceptHandler.onWakeupResult() 会检查 isBinded，
        // 默认 false 时会播「设备未绑定」并终止这一轮。这里直接置真。
        try {
            SharedPreferences sp = sCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            if (!sp.getBoolean(PREF_BIND, false)) {
                sp.edit().putBoolean(PREF_BIND, true).apply();
                Log.i(TAG, "isBinded -> true");
            }
        } catch (Throwable t) {
            Log.w(TAG, "set bind state failed: " + t);
        }

        // 上次设置过的提示音上限（L2 通过 beep 指令写入）
        try {
            sBeepCap = clampGain(beepPrefs().getFloat(PREF_BEEP_CAP, sBeepCap));
        } catch (Throwable t) {
            Log.w(TAG, "load beep cap failed: " + t);
        }

        Log.i(TAG, "L1 bridge ready, ver=" + 4);

        // 开机后主动把 L2 拉起来：BOOT_COMPLETED 会漏（包被 force-stop 过就永久收不到，
        // 直到有人手动启动一次），而 8686 是整套业务的门，没起来就等于音箱是哑的。
        ensureL2();

        // 指令通道：L2 发广播过来，L1 在这里执行「灯环」等只有原厂代码才碰得到的硬件动作。
        // 不写 AndroidManifest —— 用运行时注册，原厂清单原样保留。
        try {
            sCtx.registerReceiver(new LightCommandReceiver(), new IntentFilter(ACTION_CMD));
            Log.i(TAG, "cmd receiver registered: " + ACTION_CMD);
        } catch (Throwable t) {
            Log.w(TAG, "register cmd receiver failed: " + t);
        }

        // 心跳：让 L2 的 /status 能真实反映「L1 是否在线」，而不是写死 false
        Thread hb = new Thread(new Runnable() {
            @Override
            public void run() {
                int beats = 0;
                while (true) {
                    try {
                        Intent it = new Intent(ACTION);
                        it.setPackage(L2_PACKAGE);
                        // v=4：支持通过 msgcenter 0x40 打开/关闭原厂 AirPlay 接收端。
                        // L2 用 >= 3 判断能否自动续听（L1_VER_FOLLOW_UP）。
                        it.putExtra(EXTRA_VER, 4);
                        it.putExtra(EXTRA_TYPE, TYPE_L1_HELLO);
                        it.putExtra(EXTRA_TS, System.currentTimeMillis());
                        sCtx.sendBroadcast(it);
                        // 每 10 次心跳（5 分钟）补拉一次 L2：被系统清掉或崩溃后无人拉起时兜底
                        if (++beats % 10 == 0) {
                            ensureL2();
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        Thread.sleep(30000);
                    } catch (Throwable ignored) {
                        return;
                    }
                }
            }
        }, "r1bridge-heartbeat");
        hb.setDaemon(true);
        hb.start();

        // 引擎体检：停摆自愈（见 ENGINE_CHECK_MS 注释）
        Thread guard = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(ENGINE_CHECK_MS);
                    } catch (Throwable t) {
                        return;
                    }
                    ensureEngineAwake();
                }
            }
        }, "r1bridge-engine-guard");
        guard.setDaemon(true);
        guard.start();
    }

    /**
     * 原厂引擎是否还在唤醒态；不在就重进一次。
     *
     * <p>避开三种时刻：我们自己正在播报、引擎正在播报、正在录音/等录音——
     * 这时 {@code enterWakeup} 会打断语音或截断录音。失败只记日志，不影响其它链路。
     *
     * <p>这是**兜底**，不是主修：真正让引擎停在 {@code engine_stop} 的是
     * 「唤醒提示音期间被我们 cancelTTS」，修在 {@link #suppressOriginalSpeech()}。
     * 这里只兜住其它未知原因造成的停摆。
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 21:40 星期四
     */
    private static void ensureEngineAwake() {
        if (sEngine == null) {
            return;
        }
        if (sOwnSpeak || isEngineTtsPlaying()) {
            return;
        }
        if (sWavOut != null || sExpectCommand) {
            return;
        }
        Boolean awake = engineBool("isWakeup");
        if (awake == null || awake) {
            return;
        }
        Boolean asr = engineBool("isASR");
        if (asr == null || asr) {
            return;
        }
        Log.w(TAG, "原厂引擎不在唤醒态（疑似 engine_stop 后未恢复），自动重进唤醒");
        callEngine("enterWakeup");
    }

    /** 反射调原厂引擎的 boolean 方法；失败返回 null。 */
    private static Boolean engineBool(String method) {
        final Object engine = sEngine;
        if (engine == null) {
            return null;
        }
        try {
            Object r = engine.getClass().getMethod(method).invoke(engine);
            return r instanceof Boolean ? (Boolean) r : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 确保 L2 的常驻服务在跑；没在跑就显式启动它。
     *
     * <p>为什么由 L1 拉：L2 依赖 {@code BOOT_COMPLETED} 自启，但包只要被 {@code force-stop} 过一次，
     * 这个广播就再也不投递给它（Android 的「已停止」语义），要等有人手动启动才恢复。
     * 显式 {@code startService} 不受这个限制，而且失败也只记日志，不影响唤醒链路。
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 21:15 星期四
     */
    private static void ensureL2() {
        final Context ctx = sCtx;
        if (ctx == null) {
            return;
        }
        try {
            if (isL2Running(ctx)) {
                return;
            }
            Intent it = new Intent();
            it.setClassName(L2_PACKAGE, L2_SERVICE);
            ctx.startService(it);
            Log.i(TAG, "L2 未运行，已发出启动请求");
        } catch (Throwable t) {
            Log.w(TAG, "拉起 L2 失败: " + t);
        }
    }

    /** L2 的 MainService 是否在运行（API 22 上 drop-in 可用，只是已废弃）。 */
    private static boolean isL2Running(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return false;
            }
            List<ActivityManager.RunningServiceInfo> list = am.getRunningServices(64);
            if (list == null) {
                return false;
            }
            for (ActivityManager.RunningServiceInfo si : list) {
                if (si == null || si.service == null) {
                    continue;
                }
                if (L2_PACKAGE.equals(si.service.getPackageName())
                        && si.service.getClassName().endsWith("MainService")) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "查询 L2 运行状态失败: " + t);
        }
        return false;
    }

    /**
     * 由原厂 NLUDispatcher 调用：一条识别/语义结果进来。
     *
     * <p><b>为什么不能只找 "text"</b>：原厂传进来的是
     * {@code {"net_asr":[...],"net_nlu":[...],"local_asr":[...]}} 这种大对象，
     * 里面的 "text" 是**语义归一化后的指令或回复文本**（例如「为您播放故事:」），
     * 不是用户说的话。真正的识别结果在 {@code net_asr[].recognition_result} 里
     * （{@code last_result:true} 那条才是最终结果）。
     *
     * @param json 原厂识别结果的原始 JSON
     */
    public static void onAsrResult(String json) {
        if (json == null || json.length() == 0) {
            return;
        }
        String text = null;
        try {
            Object root = Json.parse(json);
            text = netAsrText(root);
            if (text == null || text.trim().isEmpty()) {
                text = localAsrText(root);
            }
        } catch (Throwable t) {
            Log.w(TAG, "解析 ASR JSON 失败，退回字符串扫描: " + t);
        }        if (text == null || text.trim().isEmpty()) {
            // 老格式兜底
            text = pick(json, "asr_recongize");
            if (text == null) text = pick(json, "text");
        }
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        long now = System.currentTimeMillis();

        // 原厂的报错回包会被当成识别结果塞进来（例如 uniCarRet.returnCode=609），
        // 它不是用户说的话，转发出去只会让大模型去解释一段 JSON。
        if (looksLikeVendorError(text)) {
            Log.i(TAG, "ASR ignored (原厂错误回包): " + text);
            return;
        }

        // 麦克风会把音箱自己刚说的话收回来，变成一条“假指令”，导致一句话被回答好几遍。
        // 播报期间、以及播报刚结束的一小段时间内，一律不认。
        if (sOwnSpeak || isEngineTtsPlaying() || now - sLastTtsEndAt < 1200) {
            Log.i(TAG, "ASR ignored (正在/刚播完): " + text);
            return;
        }
        // 同一句话被原厂回灌时，字面几乎一样，直接按回声丢掉
        if (isEchoOfLastSpeech(text)) {
            Log.i(TAG, "ASR ignored (回声): " + text);
            return;
        }

        // 去重：原厂同一句会来好几条（partial/full），3 秒内只转发一次
        if (text.equals(sLastAsrText) && now - sLastAsrAt < 3000) {
            return;
        }
        sLastAsrText = text;
        sLastAsrAt = now;

        // 原厂本地语法是「从一批命令里挑最像的」，听不清时也会硬套一个命令，
        // 实测「今天天气」被套成了「暂停」（score -23）。这种结果不能当用户说的话执行，
        // 否则会出现「问天气，它说已暂停播放」。低分直接通知 L2 让用户重说。
        if (sLastFromLocal && sLastScore < LOW_SCORE) {
            Log.i(TAG, "ASR weak (本地硬匹配 score=" + sLastScore + "): " + text);
            sendWeak(text, sLastScore);
            suppressOriginalSpeech();
            return;
        }

        Log.i(TAG, "ASR raw: " + text + " (score=" + sLastScore + ", " + (sLastFromLocal ? "local" : "net") + ")");
        send(text);
        // 原厂 NLU 也会在同一句话上自己作答（“这个我不能操作”之类），
        // 从这一刻起持续压掉它，直到我们自己的播报接手。
        suppressOriginalSpeech();
    }

    /** 告诉 L2「这句没听清」，让它用语音请用户再说一次，而不是执行一个乱猜的命令。 */
    private static void sendWeak(String text, double score) {
        final Context ctx = sCtx;
        if (ctx == null) return;
        try {
            Intent it = new Intent(ACTION);
            it.setPackage(L2_PACKAGE);
            it.putExtra(EXTRA_VER, 1);
            it.putExtra(EXTRA_TYPE, TYPE_ASR_WEAK);
            it.putExtra(EXTRA_TEXT, text);
            it.putExtra(EXTRA_SCORE, score);
            it.putExtra(EXTRA_TS, System.currentTimeMillis());
            ctx.sendBroadcast(it);
            Log.i(TAG, "ASR weak -> L2: " + text);
        } catch (Throwable t) {
            Log.e(TAG, "sendWeak failed: " + t);
        }
    }

    /** 是否是原厂接口的错误回包（不是用户说的话）。 */
    private static boolean looksLikeVendorError(String text) {
        return text.contains("returnCode") || text.contains("uniCarRet")
                || text.contains("http post re");
    }

    /** 识别结果是不是我们刚播出去的那句话被麦克风收回来了。 */
    private static boolean isEchoOfLastSpeech(String text) {
        String last = sLastSpokenText;
        if (last == null || last.isEmpty()) return false;
        String a = stripForCompare(text);
        String b = stripForCompare(last);
        if (a.length() < 5 || b.length() < 5) return false;
        return a.contains(b) || b.contains(a);
    }

    /** 去掉标点空白，只留可比对的字。 */
    private static String stripForCompare(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** 立即停掉原厂 TTS（语音“别说了”用）。 */
    private static void stopOriginalTts() {
        final Object engine = sEngine;
        if (engine == null) return;
        sOwnSpeak = false;
        main().post(new Runnable() {
            @Override
            public void run() {
                try {
                    cancelEngineTts();
                    Log.i(TAG, "original tts stopped");
                } catch (Throwable t) {
                    Log.w(TAG, "stop original tts failed: " + t);
                }
            }
        });
    }

    /** 主线程 Handler。 */
    private static Handler main() {
        Handler h = sMain;
        if (h == null) {
            h = new Handler(Looper.getMainLooper());
            sMain = h;
        }
        return h;
    }

    /** 反射调用原厂 {@code cancelTTS()}（必须在主线程）。 */
    private static void cancelEngineTts() {
        final Object engine = sEngine;
        if (engine == null) return;
        try {
            Class<?> c = Class.forName("com.unisound.vui.engine.ANTEngine");
            c.getMethod("cancelTTS").invoke(engine);
        } catch (Throwable t) {
            Log.w(TAG, "cancelTTS failed: " + t);
        }
    }

    /** 反射查询原厂引擎当前是否在播 TTS。 */
    private static boolean isEngineTtsPlaying() {
        final Object engine = sEngine;
        if (engine == null) return false;
        try {
            Class<?> c = Class.forName("com.unisound.vui.engine.ANTEngine");
            Object r = c.getMethod("isTTSPlaying").invoke(engine);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 压掉原厂自己的播报，直到我们开口。
     *
     * <p>原厂 NLU 与我们的 L2 会同时处理同一句话：它先念“这个我不能操作”，
     * 我们再念“好的，已开启连续对话”，用户听到的就是一句话两个回答。
     * 这里在识别后 20 秒内每 300ms 检查一次，只要原厂在播、而播的不是我们的话，
     * 就取消它；我们自己说话期间（{@code sOwnSpeak}）一律让路。
     *
     * <p><b>唤醒后必须立刻收手</b>：原厂唤醒时会播「唤醒提示音」，那也是 TTS。
     * 2026-11-05 21:30:48 实测，我们在唤醒提示音上发了 {@code cancelTTS}（:48.251），
     * 8 毫秒后原厂 {@code doCancelEngine}（:48.259）把刚起来的识别一起取消，
     * 接着 {@code engine_stop}（:48.390）——用户之后再喊多少遍都不理人。
     * 所以：一收到唤醒就关掉这个窗口，并且识别/唤醒期间绝不下手。
     */
    private static void suppressOriginalSpeech() {
        if (!sMuteOriginal) return;
        sSuppressUntil = System.currentTimeMillis() + 20000;
        main().post(new Runnable() {
            private boolean wasPlaying = false;

            @Override
            public void run() {
                if (!sMuteOriginal || System.currentTimeMillis() > sSuppressUntil) return;
                if (sExpectCommand) {
                    // 正在等用户说话（含唤醒提示音那段）：这时的 TTS 不是抢答，
                    // 取消它会连带把引擎取消掉，宁可不压
                    wasPlaying = false;
                    main().postDelayed(this, 300);
                    return;
                }
                boolean playing = isEngineTtsPlaying();
                // 原厂自己念完的时刻记下来，用来挡住“播报刚结束、回声还在”的假识别
                if (wasPlaying && !playing) sLastTtsEndAt = System.currentTimeMillis();
                wasPlaying = playing;
                if (!sOwnSpeak && playing) {
                    cancelEngineTts();
                }
                main().postDelayed(this, 300);
            }
        });
    }

    /**
     * 原厂引擎要念一句 TTS：允许吗？
     *
     * <p>由 {@code NativeANTEngine.doPlayTTS} 的入口调用（构建期注入）。
     * 原厂 NLU 与 L2 会同时处理同一句话，原厂会先念「这个我不能操作」之类，
     * 用户听到的就是一问两答。以前靠「发现它在播就 cancelTTS」事后压制，
     * 2026-11-05 21:30:48 把唤醒提示音误当成抢答取消，连带 {@code doCancelEngine}，
     * 引擎停在 {@code engine_stop}，之后再也叫不醒——所以改成在出口直接丢弃。
     *
     * <p>放行两种：没开启接管（{@code sMuteOriginal=false}）、以及我们自己要念的（{@code sOwnSpeak}）。
     * 唤醒提示音走音频文件，不经过这里。
     *
     * @return true 表示让这句真的播出来
     * @author xudong.hua,claude
     * @since 2026-11-05 21:58 星期四
     */
    public static boolean allowEngineTts() {
        if (!sMuteOriginal || sOwnSpeak) {
            return true;
        }
        Log.i(TAG, "丢弃原厂 TTS（已由 L2 接管）");
        return false;
    }

    /**
     * 用原厂 TTS 播一句话（L2 没配 HTTP TTS 时的兜底，保证语音指令一定有语音反馈）。
     *
     * <p>走 {@code ANTEngine.playTTS(String)}，和原厂自己说话是同一条链路；
     * 必须在主线程调用，且不能等它播完（回调线程里同步等待会锁住 audio 管线）。
     *
     * <p>念完（或念不出来）都会回一条 {@code TTS_DONE}：L2 靠它结束这一轮
     * （恢复音乐、换回常亮灯效、决定要不要续听）。
     *
     * @param text       要播的文本
     * @param thenListen true 表示念完自动重新进入识别（旧版 L2 用；新版由 L2 收到 TTS_DONE 后自己发 listen）
     * @param id         L2 给的播报序号，原样放进 TTS_DONE；0 表示旧版 L2，不回执
     */
    private static void speakOriginal(final String text, final boolean thenListen, final int id) {
        if (text == null || text.trim().isEmpty()) {
            sendTtsDone(id, false);
            return;
        }
        final Object engine = sEngine;
        if (engine == null) {
            Log.w(TAG, "speak ignored: engine 未注入");
            sendTtsDone(id, false);
            return;
        }
        main().post(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> c = Class.forName("com.unisound.vui.engine.ANTEngine");
                    // 先停掉原厂可能正在念的答案，再念我们的；期间打开自我保护，
                    // 让“压原厂播报”的轮询不要把我们掐掉。
                    sOwnSpeak = true;
                    sLastSpokenText = text;
                    cancelEngineTts();
                    c.getMethod("playTTS", String.class).invoke(engine, text);
                    Log.i(TAG, "original tts <- " + (text.length() > 40 ? text.substring(0, 40) + "…" : text));
                    watchOwnSpeech(text.length(), thenListen, id);
                } catch (Throwable t) {
                    sOwnSpeak = false;
                    Log.w(TAG, "playTTS failed: " + t);
                    sendTtsDone(id, false);
                }
            }
        });
    }

    /**
     * 盯着我们自己的播报：念完才放开压制；要求连续对话时，等真的念完再进识别。
     *
     * <p>原厂 TTS 没有播完回调，但引擎有 {@code isTTSPlaying()}，用它判断比按字数
     * 估时长准得多；同时保留一个按字数算的兜底上限，避免状态卡住。
     *
     * @param textLength 文本长度（用于兜底估算）
     * @param thenListen 念完是否重新进入识别
     * @param id         播报序号（TTS_DONE 回执用）
     */
    private static void watchOwnSpeech(final int textLength, final boolean thenListen, final int id) {
        final long limit = System.currentTimeMillis() + Math.min(90000L, 1500L + textLength * 260L);
        main().postDelayed(new Runnable() {
            private int checks = 0;
            private boolean sawPlaying = false;

            @Override
            public void run() {
                checks++;
                boolean playing = isEngineTtsPlaying();
                if (playing) sawPlaying = true;
                // 连续几次都没看到它在播才算念完。原厂 TTS 从 playTTS 到 isTTSPlaying()
                // 变 true 有延迟（实测 0.5 秒上下），只查两次会把「还没开口」当成「已经念完」，
                // 于是 25 个字的回复 0.8 秒就报 done，用户什么都听不到。
                boolean done = !playing && (sawPlaying || checks >= 5);
                if (done || System.currentTimeMillis() > limit) {
                    sOwnSpeak = false;
                    sLastTtsEndAt = System.currentTimeMillis();
                    Log.i(TAG, "original tts done");
                    sendTtsDone(id, true);
                    if (thenListen) {
                        // 播完立刻开麦会把自己的尾音录进去：实测 2026-11-03 23:33
                        // 「enterASR -> ok」后 0.14 秒就开录，结果录到 10 秒满、ASR 只认出「嗯。」，
                        // 大模型还把这声「嗯」当成同意，回了一句「已帮你开启连续对话」。
                        main().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                requestListen();
                            }
                        }, 700);
                    }
                    return;
                }
                main().postDelayed(this, 300);
            }
        }, 400);
    }

    /** 最近一次转发的识别文本与时间，用于去重。 */
    private static volatile String sLastAsrText = "";
    private static volatile long sLastAsrAt = 0;

    /** 最近一条结果的可信度与来源：原厂本地语法是「强行匹配」时分数会很低。 */
    private static volatile double sLastScore = 0;
    private static volatile boolean sLastFromLocal = false;

    /** 低到这个分数以下，基本是本地语法把没听清的话硬套到某个命令上（实测 -23.47 就是「今天天气」→「暂停」）。 */
    private static final double LOW_SCORE = -20;

    /** 取 {@code net_asr} 里 last_result=true 的识别结果（用户原话）。 */
    private static String netAsrText(Object root) {
        List<?> net = Json.array(root, "net_asr");
        if (net == null) return null;
        String fallback = null;
        double fallbackScore = 0;
        for (Object item : net) {
            String r = Json.string(item, "recognition_result");
            if (r == null) continue;
            fallback = r;
            fallbackScore = Json.num(item, "score", 0);
            if (Json.bool(item, "last_result")) {
                sLastScore = Json.num(item, "score", 0);
                sLastFromLocal = false;
                return r;
            }
        }
        if (fallback != null) {
            sLastScore = fallbackScore;
            sLastFromLocal = false;
        }
        return fallback;
    }

    /** 取 {@code local_asr} 的结果；里面可能是 NLU JSON（取它的 text）或纯文本。 */
    private static String localAsrText(Object root) {
        List<?> local = Json.array(root, "local_asr");
        if (local == null) return null;
        String best = null;
        double bestScore = 0;
        for (Object item : local) {
            String r = Json.string(item, "recognition_result");
            if (r == null) continue;
            String s = r.trim();
            if (s.startsWith("{")) {
                try {
                    Object inner = Json.parse(s);
                    String svc = Json.string(inner, "service");
                    if (svc != null && svc.contains("error")) continue;
                    String t = Json.string(inner, "text");
                    if (t != null && !t.trim().isEmpty()) {
                        best = t;
                        bestScore = Json.num(item, "score", 0);
                    }
                } catch (Throwable ignored) {
                }
            } else if (!s.isEmpty()) {
                best = s;
                bestScore = Json.num(item, "score", 0);
            }
        }
        if (best != null) {
            sLastScore = bestScore;
            sLastFromLocal = true;
        }
        return best;
    }

    /** 直接把一段已确定的文本转发给 L2。 */
    public static void send(String text) {
        final Context ctx = sCtx;
        if (ctx == null || text == null) {
            return;
        }
        try {
            Intent it = new Intent(ACTION);
            it.setPackage(L2_PACKAGE);
            it.putExtra(EXTRA_VER, 1);
            it.putExtra(EXTRA_TYPE, TYPE_ASR_RESULT);
            it.putExtra(EXTRA_TEXT, text);
            it.putExtra(EXTRA_TS, System.currentTimeMillis());
            ctx.sendBroadcast(it);
            Log.i(TAG, "ASR -> L2: " + text);
        } catch (Throwable t) {
            Log.e(TAG, "send failed: " + t);
        }
    }

    /**
     * 灯环指令接收器（L2 → L1）。
     *
     * @author xudong.hua,deepseek
     * @since 2026-10-31 14:10 星期六
     */
    private static final class LightCommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String cmd = intent.getStringExtra(EXTRA_CMD);
                if (CMD_MUTE.equals(cmd)) {
                    boolean on = intent.getBooleanExtra("on", false);
                    // 每 2 分钟都会重发一次，只在值变化时记日志，别刷满 logcat
                    if (on != sMuteOriginal) {
                        Log.i(TAG, "mute original tts = " + on);
                    }
                    sMuteOriginal = on;
                    return;
                }
                if (CMD_STOP.equals(cmd)) {
                    stopOriginalTts();
                    return;
                }
                if (CMD_MEDIA.equals(cmd)) {
                    // L2 结束一轮时让我们恢复被唤醒暂停的投屏/原厂播放
                    controlExternalMusic(!"pause".equals(intent.getStringExtra("action")));
                    return;
                }
                if (CMD_MSG.equals(cmd)) {
                    sendPlayerMessage(intent.getIntExtra("t", -1), intent.getIntExtra("s", -1));
                    return;
                }
                if (CMD_AIRPLAY.equals(cmd)) {
                    final boolean on = intent.getBooleanExtra("on", true);
                    sendExternalConnect(on);
                    return;
                }
                if (CMD_CAST.equals(cmd)) {
                    // 旧指令：兼容仍走反射 + PLAYER/stop 的排查路径，不再作为 AirPlay 主开关。
                    tryAirPlayOnPlayer(true);
                    sendPlayerStop();
                    return;
                }
                if (CMD_SPEAK.equals(cmd)) {
                    speakOriginal(intent.getStringExtra(EXTRA_TEXT),
                            intent.getBooleanExtra(EXTRA_LISTEN, false),
                            intent.getIntExtra(EXTRA_ID, 0));
                    return;
                }
                if (CMD_LISTEN.equals(cmd)) {
                    requestListen();
                    return;
                }
                if (CMD_ENDPOINT.equals(cmd)) {
                    sEarlyEndpoint = intent.getBooleanExtra("on", sEarlyEndpoint);
                    Log.i(TAG, "early endpoint = " + sEarlyEndpoint);
                    return;
                }
                if (CMD_LIGHT.equals(cmd)) {
                    applyLight(intent.getStringExtra(EXTRA_MODE));
                    return;
                }
                if (CMD_BEEP.equals(cmd)) {
                    float cap = intent.getFloatExtra(EXTRA_CAP, sBeepCap);
                    sBeepCap = clampGain(cap);
                    beepPrefs().edit().putFloat(PREF_BEEP_CAP, sBeepCap).apply();
                    Log.i(TAG, "beep cap = " + sBeepCap);
                    return;
                }
                if ("engine".equals(cmd)) {
                // 两用：adb 调试时点 ANTEngine 的方法做诊断；L2 在「连续两轮唤醒零录音」时
                // 会发 cmd=engine, method=enterWakeup 做引擎软恢复（见 docs/07 3.3e）。
                    callEngine(intent.getStringExtra("method"));
                }
            } catch (Throwable t) {
                Log.w(TAG, "cmd failed: " + t);
            }
        }
    }

    /**
     * 由 onEngineInitDone 注入原厂引擎实例。
     *
     * @param engine ANTEngine 实例（用 Object 接收，避免编译期依赖厂商类）
     */
    public static void setEngine(Object engine) {
        sEngine = engine;
        Log.i(TAG, "engine attached: " + (engine == null ? "null" : engine.getClass().getName()));
    }

    /** 最近一次「继续听」的时间，防止连点/回声导致反复进 ASR。 */
    private static volatile long sLastListen = 0;

    /** 外部播放（投屏/原厂播放器）是不是被我们暂停的，用于一轮结束后恢复。 */
    private static volatile boolean sMediaPausedByUs = false;

    /**
     * 暂停/恢复「原厂音乐服务」的播放（投屏与蓝牙都由它出声）。
     *
     * <p>为什么原版能做到我们一开始做不到：播放器在 {@code MusicService} 里，原厂设备应用
     * 在**同一个进程**里直接拿到 {@code getMusicPlayer()}（{@code CommonPlayer}），
     * 唤醒时顺手 {@code pause()}。我们此前只暂停了 L2 自己的 MediaPlayer，够不着它。
     *
     * <p>做法：绑一次服务 → 反射 {@code MusicBinder.getService()} → {@code getMusicPlayer()}
     * → 调 pause/resume → 立刻解绑。只在真有音乐在播时才绑，避免无谓地拉起服务。
     *
     * @param pause true 暂停，false 恢复
     * @author xudong.hua,claude
     * @since 2026-11-06 22:10 星期五
     */
    private static void controlExternalMusic(final boolean pause) {
        final Context ctx = sCtx;
        if (ctx == null) return;
        if (pause && !isMusicActive(ctx)) {
            return;
        }
        // 没被我们暂停过就不用为「恢复」去绑服务，省一次跨进程调用
        if (!pause && !sMediaPausedByUs) {
            return;
        }
        withMusicPlayer(new PlayerAction() {
            @Override
            public void run(Object player) throws Exception {
                if (pause) {
                    Object playing = call(player, "isPlaying");
                    if (Boolean.TRUE.equals(playing)) {
                        call(player, "pause");
                        sMediaPausedByUs = true;
                        Log.i(TAG, "已暂停外部播放（投屏/原厂播放器）");
                    }
                } else if (sMediaPausedByUs) {
                    call(player, "resume");
                    sMediaPausedByUs = false;
                    Log.i(TAG, "已恢复外部播放");
                }
            }
        });
    }

    /**
     * 反射调用一个方法，先按 public 找，再沿父类找 declared。
     *
     * <p>原厂很多方法是**包级可见**（{@code MusicService.getMusicPlayer()} 就是），
     * {@link Class#getMethod} 只认 public，之前「投屏暂停/恢复」静默失败就是栽在这里
     * （日志 {@code NoSuchMethodException: getMusicPlayer []}）。
     *
     * @param target 目标对象
     * @param name   方法名
     * @param args   参数
     * @return 返回值，可为 null
     * @throws Exception 找不到或调用失败
     */
    private static Object call(Object target, String name, Object... args) throws Exception {
        if (target == null) {
            throw new NullPointerException("call " + name + " on null");
        }
        Class<?> c = target.getClass();
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) {
                    continue;
                }
                m.setAccessible(true);
                return m.invoke(target, args);
            }
            c = c.getSuperclass();
        }
        throw new NoSuchMethodException(name + " on " + target.getClass().getName());
    }


    /**
     * 在原厂 {@code CommonPlayer} 上反射尝试打开或关闭 AirPlay/DLNA 接收端。
     *
     * <p>已知路径：播放器只在「播放停止 → 等待投屏」时调 {@code startServer()}；
     * 部分实现还有 {@code bootAllExternalConnectPlayer}。这里把对象图里名字像
     * EasyPlayer / AirPlayer 的对象都扫一遍，能调的方法都调，并把结果回给 L2。
     * 成不成以 L2 再探 TCP 7000 为准，这里不假装成功。
     */
    private static void tryAirPlayOnPlayer(final boolean on) {
        withMusicPlayer(new PlayerAction() {
            @Override
            public void run(Object player) throws Exception {
                StringBuilder log = new StringBuilder();
                boolean any = inspectAndInvoke(player, on, log);
                sendAirplayResult(any, log.toString());
            }
        });
    }

    private static boolean inspectAndInvoke(Object player, boolean on, StringBuilder log) {
        log.append("player=").append(player.getClass().getName()).append("; ");
        dumpInteresting(player, "player", log);
        boolean any = invokeAirPlayMethods(player, on, log);
        // 先 stop 再试 start，给「播放停止 → 等待投屏」一条边
        if (on) {
            Object r = tryCall(player, "stop");
            if (r != TRY_MISSING) {
                log.append("stop=").append(r == null ? "void" : String.valueOf(r)).append("; ");
                any = true;
            }
        }
        for (Object child : relatedObjects(player, log)) {
            if (invokeAirPlayMethods(child, on, log)) any = true;
        }
        Log.i(TAG, "airplay inspect on=" + on + " any=" + any + " " + log);
        return any;
    }

    private static final String[] AIRPLAY_START = {
            "startServer", "bootAllExternalConnectPlayer", "startExternalConnectPlayer",
            "bootExternalConnectPlayer", "startAllExternalConnectPlayer",
            "startAirPlay", "startAirplay", "startAirPlayServer", "enableAirPlay",
            "startDlna", "enterCast", "startCastServer", "enableCast", "openCast"
    };
    private static final String[] AIRPLAY_STOP = {
            "stopServer", "stopAllExternalConnectPlayer", "stopExternalConnectPlayer",
            "stopAirPlay", "stopAirplay", "disableAirPlay", "stopDlna", "disableCast"
    };

    private static boolean invokeAirPlayMethods(Object target, boolean on, StringBuilder log) {
        if (target == null) return false;
        boolean any = false;
        String[] names = on ? AIRPLAY_START : AIRPLAY_STOP;
        for (int i = 0; i < names.length; i++) {
            Object r = tryCall(target, names[i]);
            if (r == TRY_MISSING) continue;
            log.append(names[i]).append("(").append(target.getClass().getSimpleName()).append(")=");
            log.append(r == null ? "void/null" : String.valueOf(r)).append("; ");
            any = true;
        }
        return any;
    }

    private static List<Object> relatedObjects(Object player, StringBuilder log) {
        List<Object> out = new ArrayList<Object>();
        String[] getters = {
                "getEasyPlayer", "getAirPlayer", "getCastPlayer", "getExternalPlayer",
                "getDlnaPlayer", "getAirplayPlayer", "getConnectPlayer", "getPlayer"
        };
        for (int i = 0; i < getters.length; i++) {
            Object v = tryCall(player, getters[i]);
            if (v == TRY_MISSING || v == null || v == player) continue;
            log.append(getters[i]).append("->").append(v.getClass().getName()).append("; ");
            dumpInteresting(v, getters[i], log);
            out.add(v);
        }
        Class<?> c = player.getClass();
        while (c != null && c != Object.class) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (Throwable t) {
                break;
            }
            for (int i = 0; i < fields.length; i++) {
                Field f = fields[i];
                String n = f.getName().toLowerCase();
                if (!(n.contains("air") || n.contains("dlna") || n.contains("cast")
                        || n.contains("easy") || n.contains("connect")
                        || n.contains("server") || n.contains("external"))) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(player);
                    if (v == null || v == player || isPrimitiveLike(v)) continue;
                    log.append("field ").append(f.getName()).append("->")
                            .append(v.getClass().getName()).append("; ");
                    dumpInteresting(v, f.getName(), log);
                    out.add(v);
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return out;
    }

    private static void dumpInteresting(Object target, String label, StringBuilder log) {
        if (target == null) return;
        int n = 0;
        Class<?> c = target.getClass();
        StringBuilder names = new StringBuilder();
        while (c != null && c != Object.class && n < 40) {
            Method[] ms;
            try {
                ms = c.getDeclaredMethods();
            } catch (Throwable t) {
                break;
            }
            for (int i = 0; i < ms.length && n < 40; i++) {
                String name = ms[i].getName();
                String low = name.toLowerCase();
                if (!(low.contains("server") || low.contains("air") || low.contains("dlna")
                        || low.contains("cast") || low.contains("external") || low.contains("boot")
                        || low.contains("connect") || low.equals("stop") || low.equals("start"))) {
                    continue;
                }
                if (names.length() > 0) names.append(",");
                names.append(name);
                n++;
            }
            c = c.getSuperclass();
        }
        if (names.length() > 0) {
            log.append("methods[").append(label).append("]=").append(names).append("; ");
        }
    }

    private static boolean isPrimitiveLike(Object v) {
        return v instanceof Number || v instanceof Boolean || v instanceof Character
                || v instanceof String || v.getClass().isPrimitive() || v.getClass().isArray();
    }

    private static final Object TRY_MISSING = new Object();

    /** 反射调无参方法；没有这个方法时返回 {@link #TRY_MISSING}。 */
    private static Object tryCall(Object target, String name) {
        try {
            return call(target, name);
        } catch (NoSuchMethodException e) {
            return TRY_MISSING;
        } catch (Throwable t) {
            Log.w(TAG, "airplay call " + name + " on " + (target == null ? "null" : target.getClass().getName()) + ": " + t);
            return "ERR:" + t;
        }
    }

    private static void sendAirplayResult(boolean ok, String detail) {
        final Context ctx = sCtx;
        if (ctx == null) return;
        try {
            Intent it = new Intent(ACTION);
            it.setPackage(L2_PACKAGE);
            it.putExtra(EXTRA_VER, 1);
            it.putExtra(EXTRA_TYPE, TYPE_AIRPLAY_RESULT);
            it.putExtra(EXTRA_OK, ok);
            String text = detail == null ? "" : detail;
            if (text.length() > 1800) text = text.substring(0, 1800) + "…";
            it.putExtra(EXTRA_TEXT, text);
            it.putExtra(EXTRA_TS, System.currentTimeMillis());
            ctx.sendBroadcast(it);
            Log.i(TAG, "airplay result ok=" + ok + " " + text);
        } catch (Throwable t) {
            Log.e(TAG, "sendAirplayResult failed: " + t);
        }
    }

    /** 对原厂音乐播放器做一件事。 */
    private interface PlayerAction {
        void run(Object player) throws Exception;
    }

    /**
     * 直接给原厂播放器发一条 {@code PLAYER/stop}（{@code MSG_TYPE_PLAYER=4}, 子命令 4）。
     *
     * <p>原厂播放器只在「播放停止 → 等待投屏」的状态迁移里 {@code startServer()}；
     * 这条消息就是它自己的停止命令（{@code PhicommPlayerUtils.stop} 发的同一条）。
     * 设备应用自己的 {@code CommonPlayer} 未必是外部播放器，所以两条都发。
     */
    /**
     * 走原厂官方入口：与 {@code PhicommXController.open/closeBlueToothStatus()} 同一条
     * {@code msgcenter} 消息。打开会进入「等待外部连接」，最长约 90 秒无客户端则自动退出
     * （L2 在偏好仍为打开时负责续期）；期间灯环会切到原厂等待效果 0xcc。
     *
     * <p>不要在这条消息后再发 PLAYER/stop（type=4,sub=4）：{@code EasyPlayer.stop()}
     * 会把刚进入的 WaitingConnect 拉回 Stopped，等于立刻关掉刚打开的接收端。
     */
    private static void sendExternalConnect(boolean on) {
        int sub = on ? MSG_SUB_EXTERNAL_START : MSG_SUB_EXTERNAL_STOP;
        boolean sent = sendPlayerMessage(MSG_TYPE_EXTERNAL_CONNECT, sub);
        String detail = "msgcenter type=0x40 sub=" + sub
                + (on ? " startWaitingExternalConnect" : " stopWaitingExternalConnect")
                + "（Shairport 端口 5002，不是 7000）"
                + (sent ? "" : "；消息发送失败");
        sendAirplayResult(sent, detail);
        Log.i(TAG, "airplay official sent=" + sent + " " + detail);
    }

    private static void sendPlayerStop() {
        sendPlayerMessage(4, 4);
    }

    /** 给原厂播放器发一条 {type, subType, -1, null}。 */
    private static boolean sendPlayerMessage(int type, int sub) {
        try {
            Class<?> cls = Class.forName("com.phicomm.speaker.device.custom.message.MessageSenderDelegate");
            Object d = cls.getMethod("getInstance").invoke(null);
            if (d == null) {
                Log.w(TAG, "msg: MessageSenderDelegate 为空");
                return false;
            }
            call(d, "send", type, sub, -1, null);
            Log.i(TAG, "msg -> 播放器: type=" + type + " sub=" + sub);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "msg 发送失败: " + t);
            return false;
        }
    }

    /** 绑一次原厂音乐服务，拿到 {@code CommonPlayer} 执行动作，然后立刻解绑。 */
    private static void withMusicPlayer(final PlayerAction action) {
        final Context ctx = sCtx;
        if (ctx == null) return;
        try {
            Intent it = new Intent();
            it.setClassName(ctx, "com.unisound.vui.handler.session.music.MusicService");
            ctx.bindService(it, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    try {
                        Object svc = call(binder, "getService");
                        Object player = call(svc, "getMusicPlayer");
                        if (player == null) return;
                        action.run(player);
                    } catch (Throwable t) {
                        Log.w(TAG, "外部播放控制失败: " + t);
                    } finally {
                        try { ctx.unbindService(this); } catch (Throwable ignored) { }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            }, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            Log.w(TAG, "绑定原厂音乐服务失败: " + t);
        }
    }

    /** 当前是否有音乐在放（含投屏/蓝牙，由系统混音状态判断）。 */
    private static boolean isMusicActive(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            return am != null && am.isMusicActive();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 续听（连续对话）：不再调原厂 {@code enterASR}，而是直接从唤醒态的麦克风流里录下一句。
     *
     * <p>为什么换：实测 2026-11-04 {@code enterASR} 触发的录音是静音（10 秒、单声道、peak≈170），
     * 它走的不是 4 麦 HAL 那条通路。原厂停在唤醒态时仍在不停 {@code readData} 做唤醒检测，
     * 这条流本身有声音，所以只要把「下一段要录」的标记打上，{@link #onAudioData} 就会懒启动写盘，
     * 并由我们自己判停（原厂这时不会 closeAudioIn）。
     *
     * <p>收音从 {@link #FOLLOW_DELAY_MS} 之后才开始，避开喇叭尾音；
     * {@link #FOLLOW_NO_SPEECH_MS} 内没人开口就发 {@code ASR_CANCEL(follow-silence)}；
     * 若引擎根本没在读麦克风（没有数据进来），{@link #FOLLOW_WATCHDOG_MS} 后发 {@code follow-timeout}。
     * 3 秒内重复请求直接忽略。
     */
    private static void requestListen() {
        long now = System.currentTimeMillis();
        if (now - sLastListen < 3000) {
            Log.i(TAG, "listen ignored: 3 秒内重复请求");
            // 没有挂起的续听就得回个取消，否则 L2 一直以为在听、音乐一直停着
            boolean idle;
            synchronized (sWavLock) {
                idle = !sFollowUp && sWavOut == null;
            }
            if (idle) {
                sendAsrCancel("listen-dup");
            }
            return;
        }
        sLastListen = now;
        final int seq;
        synchronized (sWavLock) {
            if (sWavOut != null) {
                Log.i(TAG, "listen ignored: 当前已有录音在进行");
                return;
            }
            seq = ++sFollowSeq;
            sFollowUp = true;
            sFollowUpAt = now + FOLLOW_DELAY_MS;
            sWakeAt = sFollowUpAt;
            sExpectCommand = true;
        }
        Log.i(TAG, "续听：" + FOLLOW_DELAY_MS + "ms 后从唤醒态麦克风流收音（不走 enterASR）");
        main().postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean expired;
                synchronized (sWavLock) {
                    expired = seq == sFollowSeq && sFollowUp;
                    if (expired && sWavOut == null) {
                        sFollowUp = false;
                        sExpectCommand = false;
                    } else {
                        expired = false;
                    }
                }
                if (expired) {
                    Log.w(TAG, "续听超时：引擎没有送来任何音频");
                    sendAsrCancel("follow-timeout");
                } else if (seq == sFollowSeq && sFollowUp) {
                    // 还在录：说明判停没触发（比如一直有噪声），到上限强制收尾
                    Log.w(TAG, "续听录音超过上限，强制收尾");
                    finishRound(false);
                }
            }
        }, FOLLOW_WATCHDOG_MS);
    }

    /** 原厂灯环控制器实例，懒加载（反射拿到，不参与编译期依赖）。 */
    private static volatile Object sLightCtl;

    /** 上一次生效的灯环效果（用于跳过重复下发时的 24 段清除）。 */
    private static volatile String sLastLight = "";

    /** 「流光」扫描线程是否在跑。 */
    private static volatile boolean sScanning = false;
    /** 扫描线程引用；切效果前要等它退出，否则会在“关灯”之后又点亮。 */
    private static volatile Thread sScanThread;

    /**
     * 停止流光，并等线程真正退出。
     */
    private static void stopScan() {
        sScanning = false;
        Thread t = sScanThread;
        sScanThread = null;
        if (t != null) {
            try {
                t.join(800);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 流光/扫描：用原厂的 24 段唤醒灯位做「点亮到第 i 段」的动画。
     *
     * <p>原厂没有 RGB 接口，这是在不换硬件的前提下唯一能做出的“动效”。
     */
    private static void startScan() {
        if (sScanning) return;
        sScanning = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> cls = Class.forName("com.phicomm.speaker.device.custom.ipc.PhicommLightController");
                    Method m = cls.getMethod("turnOnWakeupIndexLight", int.class);
                    Object ctl = sLightCtl;
                    if (ctl == null) return;
                    while (sScanning) {
                        for (int i = 1; i <= 24 && sScanning; i++) {
                            m.invoke(ctl, i);
                            // 每步一次 IPC：单核机器上 60ms 一步会和录音线程抢 CPU，100ms 一圈 2.4 秒
                            try { Thread.sleep(100); } catch (Throwable ignored) { }
                        }
                    }
                } catch (Throwable e) {
                    Log.w(TAG, "light scan failed: " + e);
                } finally {
                    sScanning = false;
                }
            }
        }, "r1bridge-light-scan");
        t.setDaemon(true);
        sScanThread = t;
        t.start();
    }


    /**
     * 灯环效果的协议值，与 L2 {@code LightEffect#value()} 逐字一致。
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    private static boolean isKnownLight(String m) {
        return "off".equals(m) || "loading".equals(m) || "wakeup".equals(m)
                || "dormant".equals(m) || "net".equals(m) || "scan".equals(m);
    }

    /**
     * 执行灯环动作。
     *
     * <p>原厂的灯环控制在 {@code PhicommLightController} 里，它通过 IPC 交给下层守护进程，
     * 只有这几种预置效果，没有任意 RGB 接口：
     * <ul>
     *   <li>{@code off} 全灭</li>
     *   <li>{@code loading} 呼吸/转圈（思考中）</li>
     *   <li>{@code wakeup} 回到最近一次唤醒的颜色</li>
     *   <li>{@code dormant} 休眠微光</li>
     *   <li>{@code net} 断网提示</li>
     * </ul>
     *
     * @param mode 效果名
     */
    private static void applyLight(String mode) {
        final Context ctx = sCtx;
        if (ctx == null) {
            return;
        }
        boolean ok = false;
        try {
            Object ctl = sLightCtl;
            if (ctl == null) {
                Class<?> cls = Class.forName("com.phicomm.speaker.device.custom.ipc.PhicommLightController");
                ctl = cls.getConstructor(Context.class).newInstance(ctx);
                sLightCtl = ctl;
            }
            Class<?> cls = ctl.getClass();
            String m = mode == null ? "off" : mode;
            if (!isKnownLight(m)) {
                // 不认识的效果不能当成功：以前会把灯全清掉再回执 ok=true，网页以为生效了
                throw new IllegalArgumentException("未知灯环效果: " + m);
            }
            if ("scan".equals(m)) {
                startScan();
            } else {
                // 先让流光线程退出，再把 24 段唤醒灯全部清掉；
                // 否则残留的 index 灯会盖住呼吸/休眠效果，看起来就是“白光长亮”。
                stopScan();
                // L2 在「等用户说话」时会每 2 秒保活一次同一个效果。
                // 同一个效果重复下发时不必再清 24 段唤醒灯（那是 24 次 IPC，
                // 在录音线程旁边白烧 CPU），只把目标效果重新点亮即可。
                if (!m.equals(sLastLight) || "off".equals(m)) {
                    cls.getMethod("turnOffALLWakeupLight").invoke(ctl);
                }
                cls.getMethod("turnOffLoadingLight").invoke(ctl);
                if ("loading".equals(m)) {
                    cls.getMethod("turnOnLoadingLight").invoke(ctl);
                } else if ("wakeup".equals(m)) {
                    cls.getMethod("turnOnWakeupLastLight").invoke(ctl);
                } else if ("dormant".equals(m)) {
                    cls.getMethod("turnOnDormantLight").invoke(ctl);
                } else if ("net".equals(m)) {
                    cls.getMethod("turnOnNetDisconnectedLight").invoke(ctl);
                }
                sLastLight = m;
            }
            ok = true;
            Log.i(TAG, "light -> " + m);
        } catch (Throwable t) {
            Log.w(TAG, "light " + mode + " failed: " + t);
        }

        // 回执：让 L2 知道到底成没成，网页上不猜
        try {
            Intent r = new Intent(ACTION);
            r.setPackage(L2_PACKAGE);
            r.putExtra(EXTRA_VER, 2);
            r.putExtra(EXTRA_TYPE, TYPE_LIGHT_RESULT);
            r.putExtra(EXTRA_MODE, mode == null ? "off" : mode);
            r.putExtra(EXTRA_OK, ok);
            r.putExtra(EXTRA_TS, System.currentTimeMillis());
            ctx.sendBroadcast(r);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 极简 JSON 解析器：只为了从原厂那一大坨 ASR/NLU 结果里按结构取值。
     *
     * <p>不用 fastjson/gson：L1 只依赖 {@code android.*}，加个解析器 100 行足够。
     *
     * @since 2026-10-31 23:40 星期六
     */
    private static final class Json {

        private final String src;
        private int pos;

        private Json(String src) {
            this.src = src;
        }

        static Object parse(String s) {
            Json p = new Json(s);
            Object v = p.value();
            return v;
        }

        static Object get(Object node, String key) {
            if (!(node instanceof Map)) return null;
            return ((Map<?, ?>) node).get(key);
        }

        static String string(Object node, String key) {
            Object v = get(node, key);
            return (v instanceof String) ? (String) v : null;
        }

        static boolean bool(Object node, String key) {
            return Boolean.TRUE.equals(get(node, key));
        }

        static List<?> array(Object node, String key) {
            Object v = get(node, key);
            return (v instanceof List) ? (List<?>) v : null;
        }

        /** 取数字字段；没有或不是数字时返回默认值。 */
        static double num(Object node, String key, double def) {
            Object v = get(node, key);
            return (v instanceof Number) ? ((Number) v).doubleValue() : def;
        }

        private Object value() {
            skip();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (c == '{') return object();
            if (c == '[') return list();
            if (c == '"') return str();
            if (c == 't') { expect("true"); return Boolean.TRUE; }
            if (c == 'f') { expect("false"); return Boolean.FALSE; }
            if (c == 'n') { expect("null"); return null; }
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new HashMap<String, Object>();
            pos++; // {
            skip();
            if (pos < src.length() && src.charAt(pos) == '}') { pos++; return m; }
            while (pos < src.length()) {
                skip();
                String k = str();
                skip();
                if (pos < src.length() && src.charAt(pos) == ':') pos++;
                Object v = value();
                m.put(k, v);
                skip();
                if (pos < src.length() && src.charAt(pos) == ',') { pos++; continue; }
                if (pos < src.length() && src.charAt(pos) == '}') { pos++; break; }
                break;
            }
            return m;
        }

        private List<Object> list() {
            List<Object> l = new ArrayList<Object>();
            pos++; // [
            skip();
            if (pos < src.length() && src.charAt(pos) == ']') { pos++; return l; }
            while (pos < src.length()) {
                l.add(value());
                skip();
                if (pos < src.length() && src.charAt(pos) == ',') { pos++; continue; }
                if (pos < src.length() && src.charAt(pos) == ']') { pos++; break; }
                break;
            }
            return l;
        }

        private String str() {
            if (pos >= src.length() || src.charAt(pos) != '"') return null;
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') break;
                if (c == '\\' && pos < src.length()) {
                    char n = src.charAt(pos++);
                    switch (n) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 <= src.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                                    pos += 4;
                                } catch (Throwable ignored) { }
                            }
                            break;
                        default: sb.append(n);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object number() {
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9') || c == 'e' || c == 'E') pos++;
                else break;
            }
            String n = src.substring(start, pos);
            try { return Double.valueOf(n); } catch (Throwable t) { return n; }
        }

        private void expect(String word) {
            if (src.startsWith(word, pos)) pos += word.length();
        }

        private void skip() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }
    }

    /**
     * 极简 JSON 取值，避免为了一个字段引入 fastjson / gson。
     * 只处理「键存在且值为字符串」这一种情况，取不到返回 null。
     *
     * @param json  原始 JSON
     * @param key   字段名
     * @return 字段值，未找到或非字符串时返回 null
     */
    private static String pick(String json, String key) {
        try {
            String needle = "\"" + key + "\"";
            int k = json.indexOf(needle);
            if (k < 0) {
                return null;
            }
            int colon = json.indexOf(':', k + needle.length());
            if (colon < 0) {
                return null;
            }
            int i = colon + 1;
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= json.length() || json.charAt(i) != '"') {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int j = i + 1; j < json.length(); j++) {
                char c = json.charAt(j);
                if (c == '\\' && j + 1 < json.length()) {
                    char n = json.charAt(++j);
                    if (n == 'u' && j + 4 < json.length()) {
                        sb.append((char) Integer.parseInt(json.substring(j + 1, j + 5), 16));
                        j += 4;
                    } else {
                        sb.append(n);
                    }
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "pick(" + key + ") failed: " + t);
        }
        return null;
    }

    // ==================================================================
    // 录音转发（自建 ASR 通道）
    //
    // 原厂云端 aios-home.hivoice.cn 已停止服务，识别文本通道作废，
    // 语音输入改为：从原厂音频源 tee 一份 PCM → 写 WAV → 广播 ASR_AUDIO。
    // 详见 docs/24-self-hosted-asr.md。
    // ==================================================================

    /** 录音文件名前缀：每轮一个独立文件（见 startTeeLocked 的注释）。 */
    private static final String WAV_NAME = "r1asr";
    /** 上一轮的文件，下一轮开始时删掉，避免 cache 里堆文件。 */
    private static File sLastWav;
    /**
     * 写盘格式固定为 16000Hz / 16bit / **单声道**（ASR 网关只收这个）。
     *
     * <p>注意输入不一定是单声道：4 麦 HAL 给的是双声道交织，{@code com.unisound.common.e}
     * 的 AudioRecord 兜底才是单声道。写盘前会按 {@link VoiceDsp#detectChannels} 判一次再折叠。
     */
    private static final int PCM_RATE = 16000;
    private static final int PCM_CHANNELS = 1;
    private static final int PCM_BITS = 16;
    /** 单轮上限 10 秒（按写出去的单声道帧算）：自建 ASR 服务也只收 10 秒。 */
    private static final int MAX_PCM_FRAMES = PCM_RATE * 10;
    /** 少于 0.5 秒的片段当作没说话，不发给 L2（否则唤醒后不说话也会触发一次识别）。 */
    private static final int MIN_PCM_BYTES = PCM_RATE * 2 / 2;
    /** 判定输入声道前先攒 0.6 秒双声道（19200 字节），判完再决定怎么写。 */
    private static final int PROBE_BYTES = PCM_RATE * 2 * 2 * 6 / 10;
    /** 唤醒后多久之内开始的录音才算这一轮的命令，超时就不录了。 */
    private static final long WAKE_WINDOW_MS = 8000;

    /*
     * 判停参数（两种轮次分开）。
     *
     * 唤醒轮：原厂 VAD 会收尾，但很慢（实测 2 秒的话录成 5062ms）。提前判停默认关闭，
     * 由 L2 设置页「提前判停」打开；打开后前 1.2 秒内开始的声音段不算数——
     * 那是唤醒词「小讯小讯」的尾巴（2026-11-04 21:50 就是它让旧判停在正题前收了尾）。
     *
     * 续听轮：原厂停在唤醒态，不会给我们 closeAudioIn，只能自己判停；6 秒没人开口就放弃。
     */
    private static final int WAKE_GRACE_MS = 1200;
    private static final int WAKE_MIN_SPEECH_MS = 600;
    private static final int WAKE_SILENCE_MS = 1200;
    private static final int FOLLOW_MIN_SPEECH_MS = 400;
    private static final int FOLLOW_SILENCE_MS = 1000;
    private static final int FOLLOW_NO_SPEECH_MS = 6000;
    /**
     * 续听：TTS 结束后再等这么久才开始收音。
     *
     * <p>原来 700ms 是为了避开原厂 TTS 尾音；但 L2 现在是在「播完」之后才发 listen，
     * 喇叭已经安静了。700ms 会把用户脱口而出的开头切掉（实测 2026-11-07 11:29
     * 「明天天气」被识别成「快点连接」）。降到 300ms。
     */
    private static final long FOLLOW_DELAY_MS = 300;
    /** 续听看门狗：这么久还没收尾（引擎根本不再读麦克风）就放弃这一轮。 */
    private static final long FOLLOW_WATCHDOG_MS = 13000;
    /**
     * 引擎自愈周期。
     *
     * <p>原厂引擎会停在 {@code engine_stop} 不恢复（实测 2026-11-05 21:30:48 停摆，
     * 之后 5 分钟内用户喊了多遍唤醒词，一条事件都没有；21:35 开始每 5 分钟抛一次
     * {@code com.unisound.common.u.b} 异常）。L2 那套「连续两轮唤醒没录音」的兜底在这里失效：
     * 引擎停摆后连唤醒事件都不再产生，计数永远不动。所以由 L1 定时体检，不在唤醒态就重进。
     */
    private static final long ENGINE_CHECK_MS = 60000;
    /** 打断：持续这么久的人声才打断播报（毫秒），单帧噪声不算。 */
    private static final long BARGE_LOUD_MS = 300;
    private static final int WAV_HEADER_BYTES = 44;

    /** 写盘锁：录音在引擎线程，收尾在引擎线程，但仍加锁以防将来别处调用。 */
    private static final Object sWavLock = new Object();
    private static RandomAccessFile sWavOut;
    private static File sWavFile;
    /** 已写进文件的单声道帧数（1 帧 = 2 字节）。 */
    private static int sWavFrames;
    /** 输入声道：0 = 还没判出来，1 = 单声道，2 = 双声道交织。 */
    private static int sWavInChannels;
    /**
     * 最近一次「有声音时」判出的输入声道，跨轮记住。
     *
     * <p>为什么需要：续听轮是在静音里做 0.6 秒探测（唤醒后 700ms 才开始），左右声道没有差异，
     * {@link VoiceDsp#detectChannels} 只能保守判成单声道——而真实数据是双声道交织，
     * 于是写盘错位、ASR 只拿到噪声（实测 2026-11-06 22:47 续听连续两次识别为空，peak 2520/rms 268）。
     * 同一台机器的输入通路不会每轮变，所以探测段没声音时沿用上一轮的结果。
     */
    private static volatile int sKnownChannels;
    /** 判声道用的缓冲（前 0.6 秒）。 */
    private static final byte[] sWavProbe = new byte[PROBE_BYTES];
    private static int sWavProbeLen;
    /** 写盘用的临时缓冲，避免在录音线程上反复分配。 */
    private static final byte[] sWavScratch = new byte[PROBE_BYTES];
    /**
     * 本轮的自适应增益（判完声道后创建）。
     *
     * <p>实测这台机器的麦克风非常"闷"：真实说话峰值只有 362（约 -39 dBFS），
     * 接口文档建议 -20 ~ -6 dBFS。以前按开头 0.6 秒估一次增益再硬削波，
     * 开头恰好是停顿时会估成 12 倍、把正题削成方波；现在逐块调整，见 {@link VoiceDsp.Agc}。
     */
    private static VoiceDsp.Agc sAgc;
    /** 上一轮结束时的增益：续听轮探测段是静音，按它起步，别从 2 倍慢慢爬（开头会偏轻）。 */
    private static float sLastGain = 2f;
    /** 本轮的判停器；null 表示这一轮交给原厂 VAD 收尾。 */
    private static VoiceDsp.Endpointer sEndpointer;
    /** 唤醒轮是否提前判停（L2 设置下发，默认关）。 */
    private static volatile boolean sEarlyEndpoint = false;
    /** 播报期间的能量包络与持续人声时长（打断用）。 */
    private static int sBargeEmaRms;
    private static long sBargeLoudMs;
    private static long sBargeLastLogAt;
    /** 最近一次打断的时间：2 秒内不再判打断。 */
    private static volatile long sLastBargeAt;
    /** 唤醒（或 L2 请求继续听）之后，下一段录音属于用户这句话。 */
    private static volatile boolean sExpectCommand;
    private static volatile long sWakeAt;
    /**
     * 当前（待开始或进行中的）这一轮是不是续听。
     *
     * <p>续听不再调 {@code enterASR}：实测它触发的录音是静音（单声道、10 秒、peak≈170），
     * 不是 4 麦 HAL 那条通路。原厂停在唤醒态时一直在读麦克风做唤醒检测，
     * 续听就直接从这条流里录，自己判停。
     */
    private static volatile boolean sFollowUp;
    /** 续听最早从这个时刻开始收音（避开 TTS 尾音）。 */
    private static volatile long sFollowUpAt;
    /** 续听轮次序号：看门狗只管自己那一轮。 */
    private static volatile int sFollowSeq;

    /**
     * 原厂唤醒成功时调用（{@code NLUDispatcher.onWakeupResult} 的补丁）。
     *
     * <p>只记一个「接下来这段录音要录」的标记：原厂每几秒就会开合一次麦克风做唤醒轮询，
     * 不标记的话会把一整天的环境音都录下来。唤醒词优先于续听：正在续听也改成唤醒轮。
     *
     * @author xudong.hua,deepseek
     * @since 2026-11-01 19:10 星期日
     */
    public static void onWakeup() {
        // 唤醒那一刻原厂会播「唤醒提示音」；压制轮询如果此时 cancelTTS，
        // 会把引擎连带取消（实测 engine_stop），所以立刻关闭这个窗口
        sSuppressUntil = 0;
        // 投屏/蓝牙由原厂 MusicService 出声，我们在同一进程里能直接暂停它，
        // 否则这一轮麦克风拿到的是数字静音（实测 peak=74 rms=3）
        controlExternalMusic(true);
        synchronized (sWavLock) {
            if (sFollowUp && sWavOut != null) {
                // 续听录到一半用户又喊了唤醒词：丢掉这段，交给唤醒轮重新录
                File f = sWavFile;
                closeQuietly();
                if (f != null) f.delete();
            }
            sFollowUp = false;
            sFollowSeq++;
            sExpectCommand = true;
            sWakeAt = System.currentTimeMillis();
        }
        sendWake();
    }

    /**
     * 原厂开始一轮识别录音时调用（{@code com.unisound.common.e.openAudioIn} 的补丁）。
     *
     * @author xudong.hua,deepseek
     * @since 2026-11-01 19:10 星期日
     */
    public static void onAudioInOpen() {
        if (!sExpectCommand || sFollowUp) {
            // 续听轮不靠 open/close：数据进来时在 onAudioData 里懒启动
            return;
        }
        long sinceWake = System.currentTimeMillis() - sWakeAt;
        // 自己刚说完话就别开录：会把尾音当成用户回答（实测 23:33 录到 10 秒、ASR 只认出「嗯。」）。
        if (System.currentTimeMillis() - sLastTtsEndAt < 700) {
            Log.i(TAG, "开麦太贴近播报结尾，本轮不录");
            sExpectCommand = false;
            sendAsrCancel("tts-tail");
            return;
        }
        if (sinceWake > WAKE_WINDOW_MS) {
            sExpectCommand = false;
            sendAsrCancel("wake-window");
            return;
        }
        // 开麦前先等 L2 把播放停掉：播放中拿到的 PCM 是数字静音（实测 peak=74 rms=3），
        // 停不下来这一轮必然识别为空。250ms 不改变体感——用户这时还在听唤醒提示音。
        try {
            Thread.sleep(OPEN_DELAY_MS);
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "开麦（唤醒后 " + sinceWake + "ms，已等播放让路 " + OPEN_DELAY_MS + "ms）");
        synchronized (sWavLock) {
            startTeeLocked();
        }
    }

    /**
     * 原厂每读到一块 PCM 就调一次（{@code readData} 的补丁）。
     *
     * <p><b>跑在原厂录音线程上</b>：只做一次内存拷贝 + 一次写盘，绝不抛异常、绝不做网络或加锁等待，
     * 否则会把原厂识别卡住（这台机器只有 512MB 内存）。
     *
     * @param buf 原厂刚读满的缓冲区
     * @param len 有效字节数
     * @author xudong.hua,deepseek
     * @since 2026-11-01 19:10 星期日
     */
    public static void onAudioData(byte[] buf, int len) {
        if (buf == null || len <= 0) {
            return;
        }
        // 播报期间只做「有没有人说话」的能量监测：检测到就打断，别写盘。
        // 当前有录音在写时不进这个分支——否则播报状态一旦残留，
        // 本该写盘的音频会被全部吃掉（实测 21:29 的碎片就是这样来的）。
        if (shouldMonitorBarge()) {
            monitorBargeIn(buf, len);
            return;
        }
        int verdict = VoiceDsp.Endpointer.CONTINUE;
        synchronized (sWavLock) {
            if (sWavOut == null) {
                // 原厂有时唤醒后不再 open/close 一次，直接在原来的流上继续读；
                // 续听轮更是从来没有 open/close。两种都在这里懒启动。
                if (!sExpectCommand) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (sFollowUp && now < sFollowUpAt) {
                    // 续听还在避让喇叭尾音
                    return;
                }
                if (!sFollowUp && now - sWakeAt > WAKE_WINDOW_MS) {
                    sExpectCommand = false;
                    return;
                }
                startTeeLocked();
                if (sWavOut == null) {
                    return;
                }
            }
            if (sWavInChannels == 0) {
                // 还没判出输入声道：先把开头这一小段攒在内存里，判完再按单声道写出去。
                // 不能先按猜的口径写盘再回头改，wav 头已经发出去了就来不及了。
                int n = Math.min(len, sWavProbe.length - sWavProbeLen);
                System.arraycopy(buf, 0, sWavProbe, sWavProbeLen, n);
                sWavProbeLen += n;
                if (sWavProbeLen < sWavProbe.length) {
                    return;
                }
                flushProbeLocked();
                if (n < len) {
                    // 这一块超出探测段的部分直接写（以前会被塞回探测缓冲后丢掉）
                    byte[] rest = new byte[len - n];
                    System.arraycopy(buf, n, rest, 0, rest.length);
                    verdict = writePcmLocked(rest, rest.length);
                }
            } else {
                verdict = writePcmLocked(buf, len);
            }
        }
        // 收尾放在锁外面：回填 WAV 头 + 发广播，别占着录音锁
        if (verdict == VoiceDsp.Endpointer.END) {
            Log.i(TAG, "静音够久，提前收尾（不等原厂 VAD）");
            finishRound(false);
        } else if (verdict == VoiceDsp.Endpointer.NO_SPEECH) {
            Log.i(TAG, "续听 " + FOLLOW_NO_SPEECH_MS + "ms 没人开口，本轮放弃");
            dropRound("follow-silence");
        }
    }

    /**
     * 判声道、建增益器与判停器，再把探测段写出去（调用方持有 {@link #sWavLock}）。
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    private static void flushProbeLocked() {
        int detected = VoiceDsp.detectChannels(sWavProbe, sWavProbeLen);
        int probeRms = VoiceDsp.rms(sWavProbe, sWavProbeLen, detected == 2 ? 2 : 1);
        if (detected == 0) {
            // 判不出（探测段是静音）：沿用上一轮；一次都没判出过就按本机实测的双声道
            sWavInChannels = sKnownChannels > 0 ? sKnownChannels : 2;
            Log.i(TAG, "探测段判不出声道（rms=" + probeRms + "），使用 " + sWavInChannels + " 声道");
        } else {
            sWavInChannels = detected;
            if (probeRms >= 40) {
                sKnownChannels = detected;
            }
        }
        sAgc = new VoiceDsp.Agc();
        sAgc.prime(sWavProbe, sWavProbeLen, sWavInChannels);
        // 续听轮探测段基本是静音，prime 会把增益压回 2x；沿用上一轮学到的增益，开头才不会偏轻
        if (sFollowUp) {
            sAgc.setGain(sLastGain);
        }
        if (sFollowUp) {
            sEndpointer = new VoiceDsp.Endpointer(0, FOLLOW_MIN_SPEECH_MS, FOLLOW_SILENCE_MS, FOLLOW_NO_SPEECH_MS);
        } else if (sEarlyEndpoint) {
            sEndpointer = new VoiceDsp.Endpointer(WAKE_GRACE_MS, WAKE_MIN_SPEECH_MS, WAKE_SILENCE_MS, 0);
        } else {
            sEndpointer = null;
        }
        Log.i(TAG, "录音输入声道 = " + sWavInChannels + "，底噪 " + sAgc.floor()
                + "，起始增益 " + sAgc.gain() + "x" + (sFollowUp ? "（续听）" : "")
                + (sEndpointer == null ? "" : "，自己判停"));
        writePcmLocked(sWavProbe, sWavProbeLen);
        sWavProbeLen = 0;
    }

    /**
     * 播报期间的人声监测：持续够响就打断。
     *
     * <p>用自适应底噪而不是固定阈值：不同音量下麦克风底噪差很多，
     * 固定值不是误触发就是打不断。判定条件同时要求「明显高于底噪」和「持续」，
     * 单帧的敲门声、电视爆音不会打断。
     *
     * @param b   刚到的 PCM
     * @param len 有效字节数
     * @author xudong.hua,deepseek
     * @since 2026-11-04 00:05 星期三
     */
    private static void monitorBargeIn(byte[] b, int len) {
        // 声道用与写盘同一份判定，别写死 2：单声道输入时 int16 帧宽是 2 字节，不是 4
        int ch = sWavInChannels > 0 ? sWavInChannels : 2;
        int level = VoiceDsp.rms(b, len, ch);
        if (sBargeEmaRms == 0) {
            sBargeEmaRms = level;
        } else {
            sBargeEmaRms = (sBargeEmaRms * 9 + level) / 10;
        }
        long chunkMs = len * 1000L / (PCM_RATE * 2L * ch);
        // 阈值按本机麦克风的实际电平来：实测说话峰值只有 362，固定 1200 永远打不断
        long need = Math.max(350, sBargeEmaRms * 5 / 2);
        if (level > need && level > sBargeEmaRms + 150) {
            sBargeLoudMs += chunkMs;
        } else {
            sBargeLoudMs = Math.max(0, sBargeLoudMs - chunkMs);
        }
        if (sBargeLoudMs >= BARGE_LOUD_MS) {
            sBargeLoudMs = 0;
            sLastBargeAt = System.currentTimeMillis();
            Log.i(TAG, "检测到人声，打断播报（rms=" + level + " ema=" + sBargeEmaRms + "）");
            stopOriginalTts();
            // 打断即视为新的一轮：接下来的声音要录下来
            synchronized (sWavLock) {
                sFollowUp = false;
                sFollowSeq++;
                sExpectCommand = true;
                sWakeAt = System.currentTimeMillis();
            }
            // 也要像唤醒一样告诉 L2：它会作废上一句的 TTS_DONE 回执，
            // 否则被打断的那句「念完」会让 L2 收尾、把音乐在用户说话时恢复出来
            sendWake();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - sBargeLastLogAt > 10000) {
            sBargeLastLogAt = now;
            Log.i(TAG, "播报中监听 rms=" + level + " ema=" + sBargeEmaRms + " loudMs=" + sBargeLoudMs);
        }
    }

    /** 清掉 5 分钟前的历史录音，避免 cache 里越堆越多（这台机器内存/存储都紧张）。 */
    private static void cleanupOldWavs(Context ctx) {
        try {
            File dir = ctx.getCacheDir();
            File[] list = dir == null ? null : dir.listFiles();
            if (list == null) return;
            long now = System.currentTimeMillis();
            for (File f : list) {
                String n = f.getName();
                if (n.startsWith(WAV_NAME + "-") && n.endsWith(".wav")
                        && now - f.lastModified() > 300000L) {
                    f.delete();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "清理旧录音失败: " + t);
        }
    }

    /**
     * 音频正在写盘时，就不要再被「播报监测」截走。
     *
     * <p>实测 2026-11-04 21:29 出现「录到的都是碎片」：播报状态（sOwnSpeak /
     * isEngineTtsPlaying）可能残留为 true，把本该写盘的音频全喂给了打断监测。
     * 现在只有「当前没有正在进行的录音」时才进入监测分支。
     *
     * @return true 表示这段音频应该用于打断判断，而不是写盘
     */
    private static boolean shouldMonitorBarge() {
        if (sWavOut != null) {
            return false;
        }
        // 刚打断过：引擎的 isTTSPlaying 可能还没落下来，再判一次就会连发 WAKE，
        // 被 L2 当成「空唤醒」去重启引擎
        if (System.currentTimeMillis() - sLastBargeAt < 2000) {
            return false;
        }
        return sOwnSpeak || isEngineTtsPlaying();
    }

    /**
     * 把一块 PCM 经自适应增益转成单声道写盘，并喂给判停器（调用方持有 {@link #sWavLock}）。
     *
     * <p>为什么一定要转单声道：4 麦 HAL（{@code Uni4micHalJNI.readData}）给的是**双声道交织**，
     * 按单声道直接写盘，送进 ASR 的就是一段变速噪声——实测 2026-11-03 22:18 那段录音，
     * 原样喂 SenseVoice 得到「悟空悟空」，按双声道下混后得到「播放周杰伦的歌」。
     *
     * @param b   原始 PCM
     * @param len 有效字节数
     * @return 判停结论（{@link VoiceDsp.Endpointer#CONTINUE} 等）；写满 10 秒也返回 END
     * @author xudong.hua,deepseek
     * @since 2026-11-03 22:30 星期二
     */
    private static int writePcmLocked(byte[] b, int len) {
        try {
            int room = MAX_PCM_FRAMES - sWavFrames;
            if (room <= 0) {
                return sFollowUp ? VoiceDsp.Endpointer.END : VoiceDsp.Endpointer.CONTINUE;
            }
            int frames = sAgc.process(b, len, sWavInChannels, sWavScratch, room);
            if (frames <= 0) {
                return VoiceDsp.Endpointer.CONTINUE;
            }
            sWavOut.write(sWavScratch, 0, frames * 2);
            sWavFrames += frames;
            if (sEndpointer == null) {
                return VoiceDsp.Endpointer.CONTINUE;
            }
            return sEndpointer.feed(sAgc.lastSpeech(), frames * 1000 / PCM_RATE);
        } catch (Throwable t) {
            Log.w(TAG, "录音写入失败: " + t);
            closeQuietly();
            sExpectCommand = false;
            sendAsrCancel("write-fail");
            return VoiceDsp.Endpointer.CONTINUE;
        }
    }

    /**
     * 打开本轮录音文件（调用方持有 {@link #sWavLock}）。
     *
     * @author xudong.hua,deepseek
     * @since 2026-11-01 19:26 星期日
     */
    private static void startTeeLocked() {
        final Context ctx = sCtx;
        if (ctx == null) {
            return;
        }
        closeQuietly();
        try {
            // 每轮一个独立文件：以前固定用 r1asr.wav 每轮覆盖，实测 2026-11-04 21:29
            // 出现「同一轮 online 出 10 个字、终稿只剩 2 个字」——下一轮 setLength(0)
            // 把还在被 L2 读/推的文件截断了，两轮音频串在一起。
            File f = new File(ctx.getCacheDir(),
                    WAV_NAME + "-" + System.currentTimeMillis() + ".wav");
            RandomAccessFile out = new RandomAccessFile(f, "rw");
            out.setLength(0);
            // 先占 44 字节，收尾时按真实长度回填
            out.write(new byte[WAV_HEADER_BYTES]);
            sWavOut = out;
            sWavFile = f;
            File old = sLastWav;
            sLastWav = f;
            // 留最近几轮录音方便排查（续听识别为空时要拿原始 wav 验声道），5 分钟后再删
            if (old != null && !old.equals(f)
                    && System.currentTimeMillis() - old.lastModified() > 300000L) {
                old.delete();
            }
            cleanupOldWavs(ctx);
            sWavFrames = 0;
            sWavInChannels = 0;
            sWavProbeLen = 0;
            sAgc = null;
            sEndpointer = null;
            shareForL2(ctx, f);
            // 通知 L2「开始录了」，让它立刻开流式 ASR 跟着这个文件推。
            // 只在这个位置发一次：每轮录音都从这里开始（含唤醒后懒启动那条路）。
            sendAudioStart(f);
        } catch (Throwable t) {
            Log.w(TAG, "录音文件打不开: " + t);
            closeQuietly();
            // 必须告诉 L2「这一轮废了」：否则它既收不到 ASR_AUDIO 也收不到取消，
            // 要等 90 秒超时才恢复音乐和灯环（用户感知就是「喊了没反应」）。
            sExpectCommand = false;
            sendAsrCancel("tee-fail");
        }
    }

    /**
     * 放开录音文件的读权限，让 L2（另一个 UID）能读到。
     *
     * <p><b>为什么必须做</b>：本进程（原厂包）的应用目录是 0700、文件（应用 umask 0077）
     * 是 0600，跨 UID 打开必然 {@code EACCES}。实测 2026-11-03 20:29 与 20:32 两轮都是
     * {@code open failed: EACCES (Permission denied)}，L2 于是把「播放周杰伦的歌」
     * 当成没听清。Android 5.1 上跨应用读同一份文件只受 DAC 限制（MODE_WORLD_READABLE
     * 就是靠这个），所以这里把「目录可穿过 + 文件可读」补上即可，不动 SELinux。
     *
     * <p>目录只加 {@code o+x} 不加 {@code o+r}：别的应用能穿过路径读到这一个 wav，
     * 但列不出目录里还有什么。
     *
     * @param ctx 上下文（用来拿到 cacheDir 及其父目录）
     * @param f   本轮录音文件
     * @author xudong.hua,deepseek
     * @since 2026-11-03 20:37 星期二
     */
    private static void shareForL2(Context ctx, File f) {
        try {
            File cacheDir = ctx.getCacheDir();
            File dataDir = cacheDir == null ? null : cacheDir.getParentFile();
            boolean dirOk = true;
            if (dataDir != null) {
                dirOk = dataDir.setExecutable(true, false) && dirOk;
            }
            if (cacheDir != null) {
                dirOk = cacheDir.setExecutable(true, false) && dirOk;
            }
            boolean fileOk = f.setReadable(true, false);
            if (!dirOk || !fileOk) {
                Log.w(TAG, "录音文件放开权限失败: dir=" + dirOk + " file=" + fileOk + " path=" + f);
            }
        } catch (Throwable t) {
            Log.w(TAG, "录音文件放开权限异常: " + t);
        }
    }

    /**
     * 调试入口：反射调用 ANTEngine 的方法（只允许白名单）。
     *
     * <p>为什么需要：`enterASR` 触发的续听在这台机器上录到的是静音
     * （10 秒、单声道、peak≈170），得逐个试 `enterWakeup` / `stopWakeup` /
     * `releaseAudioRecord` 哪个能让麦克风真正回到 4 麦 HAL。
     * 有了这个入口，用 adb 广播就能试，不用每次重装 31MB 的 L1。
     *
     * @param method 方法名
     */
    private static void callEngine(String method) {
        final Object engine = sEngine;
        if (engine == null) {
            Log.w(TAG, "engine 未注入");
            return;
        }
        if (method == null) {
            Log.w(TAG, "缺少 method");
            return;
        }
        try {
            Class<?> c = engine.getClass();
            if ("enterWakeup".equals(method)) {
                c.getMethod("enterWakeup", boolean.class).invoke(engine, true);
                Log.i(TAG, "engine.enterWakeup(true) ok");
            } else if ("isWakeup".equals(method) || "isWakeupRecord".equals(method)
                    || "isASR".equals(method) || "isRecognition".equals(method)) {
                Object r = c.getMethod(method).invoke(engine);
                Log.i(TAG, "engine." + method + "() = " + r);
            } else if ("enterASR".equals(method) || "cancelASR".equals(method) || "stopASR".equals(method)
                    || "stopWakeup".equals(method) || "releaseAudioRecord".equals(method)
                    || "doPPTAction".equals(method)) {
                c.getMethod(method).invoke(engine);
                Log.i(TAG, "engine." + method + "() ok");
            } else {
                Log.w(TAG, "未知 engine 方法: " + method);
            }
        } catch (Throwable t) {
            Log.w(TAG, "engine." + method + " 失败: " + t);
        }
    }

    /**
     * 开机提示音该用的播放器增益（0~1），由原厂 {@code UniMediaPlayer.playBeepSound}
     * 里的 {@code setVolume(FF)} 调用（构建期注入，见 {@code l1/build.ps1} 4.9）。
     *
     * <p>取值 = min(当前媒体音量比例, {@link #sBeepCap})：
     * 正常开机时媒体音量是用户设的 4/15，提示音就跟着变轻；
     * 万一开机时音量还没恢复（还是 15/15），也不会超过上限。
     *
     * @return 0~1 的增益
     * @author xudong.hua,deepseek
     * @since 2026-11-03 21:40 星期二
     */
    public static float beepGain() {
        try {
            float follow = 1f;
            AudioManager am = (AudioManager) sCtx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (max > 0) {
                    follow = clampGain((float) cur / max);
                }
            }
            return Math.min(follow, sBeepCap);
        } catch (Throwable t) {
            return sBeepCap;
        }
    }

    /** 把增益夹到 0~1，非法值退回上限默认值。 */
    private static float clampGain(float v) {
        if (Float.isNaN(v) || v < 0f) {
            return 0f;
        }
        return v > 1f ? 1f : v;
    }

    /** 我们自己的 SharedPreferences（不在原厂的 defaultSp 里乱写）。 */
    private static SharedPreferences beepPrefs() {
        return sCtx.getSharedPreferences(PREF_SELF, Context.MODE_PRIVATE);
    }

    /**
     * 原厂结束一轮识别录音时调用（{@code closeAudioIn} 的补丁）：回填 WAV 头并通知 L2。
     *
     * @author xudong.hua,deepseek
     * @since 2026-11-01 19:10 星期日
     */
    public static void onAudioInClose() {
        if (sFollowUp) {
            // 续听轮不是原厂开的，原厂唤醒态自己的开合不代表用户说完了；交给判停器/看门狗收尾
            return;
        }
        finishRound(true);
    }

    /**
     * 结束本轮录音：回填 WAV 头、决定是丢弃还是广播给 L2。
     *
     * @param fromEngine true = 原厂 closeAudioIn 触发；false = 我们自己判停 / 续听看门狗
     * @author xudong.hua,deepseek
     * @since 2026-11-04 00:05 星期三
     */
    private static void finishRound(boolean fromEngine) {
        int bytes;
        int frames;
        int inChannels;
        boolean followUp;
        String agcNote;
        File file;
        synchronized (sWavLock) {
            if (sWavOut == null) {
                return;
            }
            // 太短的一轮（还没攒够判定声道的数据）也要按已判的口径补写完，
            // 否则这段录音只有 wav 头。
            if (sWavInChannels == 0 && sWavProbeLen > 0) {
                flushProbeLocked();
            }
            frames = sWavFrames;
            inChannels = sWavInChannels;
            followUp = sFollowUp;
            agcNote = sAgc == null ? "" : "，增益 " + sAgc.gain() + "x 软限幅 " + sAgc.clipped() + " 点";
            bytes = frames * 2;
            file = sWavFile;
            try {
                sWavOut.seek(0);
                sWavOut.write(wavHeader(bytes));
            } catch (Throwable t) {
                Log.w(TAG, "WAV 头回填失败: " + t);
                bytes = 0;
            }
            closeQuietly();
            // 唤醒轮太短时保持 sExpectCommand（与旧行为一致：原厂可能接着在同一条流上读，还能懒启动补录）；
            // 续听轮由我们自己控制，这里一律结束。
            if (followUp) {
                sExpectCommand = false;
            }
            sFollowUp = false;
            sFollowSeq++;
        }
        if (file == null) {
            return;
        }
        if (bytes < MIN_PCM_BYTES) {
            // 实测 2026-11-03 23:04：原厂唤醒灯（白光）点亮后，这一轮只录到 1280 字节就被丢弃，
            // 灯就再也没灭过，用户看到的是「一直亮着白光、喊它没反应」。
            // 太短的轮次没有任何后续动作会去关灯，这里补一刀（L2 收到取消后会再换回常亮效果）。
            Log.i(TAG, "录音太短，丢弃: " + bytes + "B");
            file.delete();
            applyLight("off");
            sendAsrCancel("too-short:" + bytes);
            return;
        }
        sExpectCommand = false;
        Log.i(TAG, (fromEngine ? "" : "[自己判停] ") + (followUp ? "[续听] " : "")
                + "ASR_AUDIO: " + file.getName() + " " + (frames * 1000L / PCM_RATE)
                + "ms（输入 " + inChannels + " 声道已折叠为单声道" + agcNote + "）");
        if (sAgc != null) {
            sLastGain = sAgc.gain();
        }
        sendAudio(file);
    }

    /**
     * 丢弃本轮录音（续听没人开口），通知 L2 取消。
     *
     * @param reason 取消原因（日志用）
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    private static void dropRound(String reason) {
        File f;
        synchronized (sWavLock) {
            f = sWavFile;
            closeQuietly();
            sExpectCommand = false;
            sFollowUp = false;
            sFollowSeq++;
        }
        if (f != null) {
            f.delete();
        }
        sendAsrCancel(reason);
    }

    /**
     * 广播「这一轮开始录了」。
     *
     * <p>流式 ASR 靠它启动：L2 收到就开 WebSocket 跟着这个文件读，边录边推；
     * 不依赖 {@code ASR_AUDIO}（那是录完才发的）。
     *
     * @param file 正在写的 WAV
     * @author xudong.hua,deepseek
     * @since 2026-11-04 01:20 星期三
     */
    private static void sendAudioStart(File file) {
        final Context ctx = sCtx;
        if (ctx == null) return;
        try {
            Intent it = new Intent(ACTION);
            it.setPackage(L2_PACKAGE);
            it.putExtra(EXTRA_VER, 1);
            it.putExtra(EXTRA_TYPE, TYPE_ASR_START);
            it.putExtra(EXTRA_PATH, file.getAbsolutePath());
            it.putExtra(EXTRA_TS, System.currentTimeMillis());
            ctx.sendBroadcast(it);
        } catch (Throwable t) {
            Log.e(TAG, "sendAudioStart failed: " + t);
        }
    }

    /**
     * 广播「L2 托我们念的那句念完了」。
     *
     * @param id 播报序号；0 表示旧版 L2 没给序号，不回执
     * @param ok false 表示没念出来（引擎未注入 / playTTS 抛异常）
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    private static void sendTtsDone(int id, boolean ok) {
        final Context ctx = sCtx;
        if (ctx == null || id == 0) return;
        try {
            Intent it = new Intent(ACTION);
            it.setPackage(L2_PACKAGE);
            it.putExtra(EXTRA_VER, 3);
            it.putExtra(EXTRA_TYPE, TYPE_TTS_DONE);
            it.putExtra(EXTRA_ID, id);
            it.putExtra(EXTRA_OK, ok);
            it.putExtra(EXTRA_TS, System.currentTimeMillis());
            ctx.sendBroadcast(it);
        } catch (Throwable t) {
            Log.e(TAG, "sendTtsDone failed: " + t);
        }
    }

    /**
     * 广播「唤醒词命中」。
     *
     * <p>这是整条链路里最早能通知 L2 的时刻（原厂 {@code onWakeupResult} 刚返回），
     * L2 收到就把音乐/TTS 停掉，给接下来的录音让出音频通路。
     */
    private static void sendWake() {
        final Context ctx = sCtx;
        if (ctx == null) return;
        try {
            Intent it = new Intent(ACTION);
            it.setPackage(L2_PACKAGE);
            it.putExtra(EXTRA_VER, 1);
            it.putExtra(EXTRA_TYPE, TYPE_WAKE);
            it.putExtra(EXTRA_TS, System.currentTimeMillis());
            ctx.sendBroadcast(it);
        } catch (Throwable t) {
            Log.e(TAG, "sendWake failed: " + t);
        }
    }

    /**
     * 广播「这一轮取消」。
     *
     * <p>没有它，L2 会以为录音还在继续，流式会话要等 30 秒超时才释放，
     * 这段时间里再唤醒也没人接——用户看到的就是「灯亮一下就没反应」。
     *
     * @param reason 取消原因，只用于日志
     */
    private static void sendAsrCancel(String reason) {
        final Context ctx = sCtx;
        if (ctx == null) return;
        try {
            Intent it = new Intent(ACTION);
            it.setPackage(L2_PACKAGE);
            it.putExtra(EXTRA_VER, 1);
            it.putExtra(EXTRA_TYPE, TYPE_ASR_CANCEL);
            it.putExtra(EXTRA_REASON, reason);
            it.putExtra(EXTRA_TS, System.currentTimeMillis());
            ctx.sendBroadcast(it);
        } catch (Throwable t) {
            Log.e(TAG, "sendAsrCancel failed: " + t);
        }
    }

    /** 把录音文件路径广播给 L2（L2 会用自建 ASR 换成文字）。 */    private static void sendAudio(File file) {        final Context ctx = sCtx;
        if (ctx == null) {
            return;
        }
        try {
            Intent it = new Intent(ACTION);
            it.setPackage(L2_PACKAGE);
            it.putExtra(EXTRA_VER, 1);
            it.putExtra(EXTRA_TYPE, TYPE_ASR_AUDIO);
            it.putExtra(EXTRA_PATH, file.getAbsolutePath());
            it.putExtra(EXTRA_TS, System.currentTimeMillis());
            ctx.sendBroadcast(it);
        } catch (Throwable t) {
            Log.e(TAG, "sendAudio failed: " + t);
        }
    }

    /** 关掉当前录音文件（调用方持有 {@link #sWavLock}）。 */
    private static void closeQuietly() {
        if (sWavOut == null) {
            return;
        }
        try {
            sWavOut.close();
        } catch (Throwable ignored) {
            // 释放资源失败无关紧要
        }
        sWavOut = null;
        sWavFrames = 0;
        sWavProbeLen = 0;
    }

    /**
     * 生成 44 字节的标准 PCM WAV 头。
     *
     * @param dataBytes 纯 PCM 数据长度
     * @author xudong.hua,deepseek
     * @since 2026-11-01 19:10 星期日
     */
    private static byte[] wavHeader(int dataBytes) {
        int byteRate = PCM_RATE * PCM_CHANNELS * PCM_BITS / 8;
        int blockAlign = PCM_CHANNELS * PCM_BITS / 8;
        byte[] h = new byte[WAV_HEADER_BYTES];
        putAscii(h, 0, "RIFF");
        putInt(h, 4, 36 + dataBytes);
        putAscii(h, 8, "WAVE");
        putAscii(h, 12, "fmt ");
        putInt(h, 16, 16);
        putShort(h, 20, 1);
        putShort(h, 22, PCM_CHANNELS);
        putInt(h, 24, PCM_RATE);
        putInt(h, 28, byteRate);
        putShort(h, 32, blockAlign);
        putShort(h, 34, PCM_BITS);
        putAscii(h, 36, "data");
        putInt(h, 40, dataBytes);
        return h;
    }

    private static void putAscii(byte[] dst, int off, String s) {
        for (int i = 0; i < s.length(); i++) {
            dst[off + i] = (byte) s.charAt(i);
        }
    }

    /** WAV 是小端序。 */
    private static void putInt(byte[] dst, int off, int v) {
        dst[off] = (byte) v;
        dst[off + 1] = (byte) (v >> 8);
        dst[off + 2] = (byte) (v >> 16);
        dst[off + 3] = (byte) (v >> 24);
    }

    private static void putShort(byte[] dst, int off, int v) {
        dst[off] = (byte) v;
        dst[off + 1] = (byte) (v >> 8);
    }
}
