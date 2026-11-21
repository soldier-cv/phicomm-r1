package com.r1.brain;

import android.R;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Process;
import android.util.Log;

import com.r1.brain.dlna.CastPlayer;
import com.r1.brain.dlna.DlnaRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 斐讯 R1 智脑常驻前台服务。
 *
 * 职责：
 *   1. 作为前台服务常驻运行，降低被 Android 系统回收的概率
 *   2. 挂载 8686 端口 HTTP 控制台与 REST API 服务 (HttpServer)
 *   3. 挂载北京时区闹钟调度器 (AlarmScheduler) 与语音合成引擎 (SpeechEngine / Tts)
 *
 * 启动方式：
 *   adb shell am startservice -n com.r1.brain/.MainService
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 13:52 星期五
 */
public class MainService extends Service {

    private static final String TAG = Brain.TAG;
    private static final int NOTIF_ID = 1001;

    /** 供同进程广播接收器与调度器访问的静态实例。 */
    private static volatile MainService instance;

    /** L1 最后一次心跳的时间（elapsedRealtime）。 */
    private volatile long l1LastSeen = 0;
    /** L1 报上来的协议版本（心跳里的 {@code v}）；0 表示还没收到。 */
    private volatile int l1Version = 0;

    /** 是否正在等上一条语音的处理结果（防止同一句话被识别多次后连环回答）。 */
    private static volatile boolean sBusy = false;
    private static volatile long sBusyAt = 0;

    /**
     * 自建 ASR 正在转写，避免两段录音同时上传。
     *
     * <p>用 {@link AtomicBoolean} 的 compareAndSet 抢占：以前「先判断再赋值」两步之间
     * 会被另一个广播插进来，两段一起上传。取消事件也不再把它清掉——上一段如果还在传，
     * 清掉就等于放行第二段。
     */
    private final AtomicBoolean asrUploading = new AtomicBoolean(false);

    /**
     * 上传期间新来的录音只保留最新一条，转写完成后接着处理。
     *
     * <p>以前是直接丢掉：实测 2026-11-07 11:05 用户说「音量调到…」时，
     * 恰好上一段（只录到唤醒词）还在上传，后两段全被丢弃，指令就此消失。
     */
    private volatile String pendingAudioPath;

    /** 上一次唤醒的时间，与「这一轮到底有没有拿到录音」一起用于发现原厂引擎停摆。 */
    private volatile long lastWakeAt = 0;
    /** 本轮是否已经拿到录音（ASR_AUDIO）。 */
    private volatile boolean wakeGotAudio = false;
    /** 本轮是否被 L1 主动取消（取消不算故障，不能计入引擎停摆）。 */
    private volatile boolean wakeCancelled = false;
    /** 连续「唤醒后既没录音也没取消」的次数。 */
    private volatile int emptyWakes = 0;

    /**
     * 常驻唤醒锁。
     *
     * <p>前台服务本身<b>不</b>阻止 CPU 深度休眠，而闹钟调度用的是
     * {@code Handler.postDelayed}（基于 uptimeMillis，休眠时停摆）。这台设备是常插电音箱，
     * 所以在前台服务生命周期内持有一把 {@code PARTIAL_WAKE_LOCK}，保证闹钟、流式 ASR
     * 和播报不会因为熄屏休眠而延迟或整段丢失。onDestroy 里释放。
     */
    private PowerManager.WakeLock wakeLock;

    /**
     * 连续反问计数。
     *
     * <p>「你是想开启连续对话吗？」这类反问必须接着听，否则用户得重新喊唤醒词；
     * 但也不能无限问下去，所以最多连续 {@link #MAX_CLARIFY_STREAK} 次。
     * 只在主线程改；volatile 防止别的线程读到旧值。
     */
    private static volatile int sClarifyStreak = 0;
    /** 连问几次就收场：1 表示「问一次 → 再试一次 → 还不行就停」。 */
    private static final int MAX_CLARIFY_STREAK = 1;

    /**
     * 这句回复是不是在等用户接话（反问 / 请重说 / 确认）。
     *
     * <p>判断只用问号和我们自己写死的几句话，**不要用「吗」这种宽泛字面**——
     * 那会把「外面下雨吗」这类正常答复也算进去。
     *
     * @param reply 即将播报的文本
     * @return true 表示播完要自动再听一句
     */
    private static boolean needsAnswer(String reply) {
        if (reply == null) return false;
        String s = reply.trim();
        if (s.isEmpty()) return false;
        return s.endsWith("？") || s.endsWith("?")
                || s.contains("再说一遍")
                || s.contains("你是想")
                || s.contains("是想听");
    }

    /**
     * 整句只有一个填充词（嗯 / 啊 / 哦 / 呃…）。
     *
     * <p>这类话没有任何可执行内容。以前会把它送进大模型，模型当成"同意"，
     * 回一句「已帮你开启…」，用户以为生效了其实没有。
     *
     * @param t 识别文本
     * @return true 表示可以当成没听清处理
     */
    private static boolean isFiller(String t) {
        if (t == null) return false;
        String s = t.replaceAll("[。！？，、,.!?…\\s]", "");
        if (s.isEmpty() || s.length() > 3) return false;
        for (int i = 0; i < s.length(); i++) {
            if ("嗯啊哦呃唉哎唔喔哼哟".indexOf(s.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 整段只有唤醒词（含常见近音）。
     *
     * <p>原厂有时会在唤醒后立刻收一轮，只录到「小讯小讯」本身；这种送进大模型
     * 只会得到「我在，你说。」（实测 2026-11-07 11:05），既慢又打断后续指令。
     *
     * @param t 识别文本
     * @return true 表示这句没有任何指令内容
     */
    private static boolean isWakeWordOnly(String t) {
        if (t == null) return false;
        String s = t.replaceAll("[。！？，、,.!?…\\s]", "");
        if (s.length() > 8) return false;
        // 唤醒词及其近音：小讯/小迅/小兴/小心/晓讯…
        return s.matches("[小晓][讯迅兴寻新心]") || s.matches("[小晓][讯迅兴寻新心][小晓][讯迅兴寻新心]");
    }

    private Brain brain;
    private Tts tts;
    private SpeechEngine speech;
    private HttpServer http;
    private AlarmScheduler alarms;
    private BridgeReceiver bridge;
    /** 一轮语音的收尾：恢复音乐、灯环状态。 */
    private VoiceSession session;

    /** 原厂 TTS 播报序号：L1 念完在 TTS_DONE 里带回，对上了才算这一句念完。 */
    private final AtomicInteger nativeSpeakSeq = new AtomicInteger(0);
    /** 正在等原厂 TTS 念完的那一句：序号、「念完要不要续听」、原文（原厂失败时给系统 TTS 用）。 */
    private volatile int pendingNativeId = 0;
    private volatile boolean pendingNativeListen = false;
    private volatile String pendingNativeText = "";
    /** 那一句属于哪一轮（{@link VoiceSession#token()}），收尾只收那一轮。 */
    private volatile int pendingNativeToken = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 是不是正在等「续听」的下一句（这一轮没有用户说话就该安静收场，别再播「没听清」）。 */
    private volatile boolean awaitingFollowUp;

    /** 太短取消的收尾延迟：原厂常先甩一个几十毫秒的空轮，紧接着才是正式那句。 */
    private static final long SHORT_CANCEL_GRACE_MS = 1500;
    /** 回收原厂 AirSkill 的间隔：它开机必起，且云已下线、只会在日志里刷 ENOENT。 */
    private static final long RECLAIM_INTERVAL_MS = 15 * 60 * 1000L;

    /**
     * 回收原厂 AirSkill（斐讯 IoT 云消息，uid 1000 的系统包）。
     *
     * <p>为什么只能这么干：它是 `uid 1000` 的系统应用，shell 身份执行 `pm disable-user` 会被
     * SecurityException 拒绝；`am force-stop` 虽然能停，但重启后开机广播又把 `AirService` 拉起来
     * （2026-11-06 实测）。所以退而求其次：开机稳定后停一次，此后每 15 分钟检查一次。
     * 这是可逆的软件动作，没有改 /system；配置里 `reclaimAirskill` 关掉即不再干预。
     */
    private final Runnable reclaimAirskill = new Runnable() {
        /** 开机阶段多扫几轮：开机广播可能比第一次回收更晚到（实测 901 号进程就是这么起来的）。 */
        private int passes = 0;

        @Override
        public void run() {
            passes++;
            try {
                if (Config.load(MainService.this).reclaimAirskill) {
                    String ps = DeviceOps.execShell(
                            "ps | grep com.phicomm.speaker.airskill | grep -v grep", 8000);
                    if (ps != null && !ps.trim().isEmpty()) {
                        DeviceOps.execShell("am force-stop com.phicomm.speaker.airskill", 8000);
                        Log.i(TAG, "已回收原厂 AirSkill 进程（斐讯云已下线，释放约 25MB）");
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "回收 AirSkill 失败: " + t);
            }
            if (passes < 6) {
                mainHandler.postDelayed(this, 30000);
            } else {
                mainHandler.postDelayed(this, RECLAIM_INTERVAL_MS);
            }
        }
    };

    /** 太短取消后等待「有没有下一段录音」的计时器。 */
    private final Runnable pendingCancelEnd = new Runnable() {
        @Override
        public void run() {
            String why = pendingCancelReason;
            pendingCancelReason = null;
            Log.i(TAG, "取消后 " + SHORT_CANCEL_GRACE_MS + "ms 没有新录音，收尾（" + why + "）");
            session.end("取消:" + why);
        }
    };

    /** 待收尾的取消原因；新录音一来就作废。 */
    private volatile String pendingCancelReason;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "MainService onCreate, pid=" + Process.myPid());

        // 常插电设备：持锁避免 CPU 休眠让闹钟/语音链路停摆（见字段注释）
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "r1brain:service");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
                Log.i(TAG, "已持有 PARTIAL_WAKE_LOCK");
            }
        } catch (Throwable t) {
            Log.w(TAG, "获取唤醒锁失败（闹钟可能受休眠影响）: " + t);
        }

        brain = new Brain(this);
        tts = new Tts();
        tts.init(this);
        speech = new SpeechEngine(this);
        session = new VoiceSession(this);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("r1brain", "R1 助手", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("R1 助手常驻运行状态");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        startForeground(NOTIF_ID, buildNotification());
        Log.i(TAG, "foreground started, systemTts=" + tts.status() + " httpTts=" + speech.status());

        http = new HttpServer(this, brain, speech);
        http.start();

        // 投屏接收端：自己实现的那套（原厂播放器的接收端开机永远起不来，见 docs/21）。
        // 投屏要出声时先让本地音乐停一下，否则两条流一起走同一个喇叭。
        CastPlayer.setMusicYielder(new CastPlayer.MusicYielder() {
            @Override
            public void yieldForCast() {
                try {
                    MusicPlayer.get().pause();
                } catch (Throwable t) {
                    Log.w(TAG, "投屏让路失败: " + t);
                }
            }
        });
        Config bootCfg = Config.load(this);
        if (bootCfg.castEnabled) {
            DlnaRenderer.start(this);
            Log.i(TAG, "已请求启动 DLNA 投屏接收端");
        }
        // 蓝牙 / AirPlay 偏好：等网络和播放器稳一点再应用，避免拖慢启动
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        applyReceiverPrefs();
                    }
                }, "r1-receivers-boot").start();
            }
        }, 8000);
        reportAsrUsage();

        bridge = new BridgeReceiver();
        registerReceiver(bridge, new IntentFilter(BridgeProtocol.ACTION_EVENT));
        Log.i(TAG, "L1 桥接收器已注册: " + BridgeProtocol.ACTION_EVENT);

        alarms = new AlarmScheduler(this);

        // 这台机器没有 RTC 电池，断电后从 2018 起跳；NTP 校准会让时钟整体跳变。
        // 跳变后必须重置闹钟去重状态，否则可能重复播或漏播。
        try {
            IntentFilter tf = new IntentFilter();
            tf.addAction(Intent.ACTION_TIME_CHANGED);
            tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            registerReceiver(new TimeChangeReceiver(), tf);
            Log.i(TAG, "时间跳变监听已注册");
        } catch (Throwable t) {
            Log.w(TAG, "注册时间监听失败: " + t);
        }

        // 配好 TTS 就压掉原厂播报，避免同一句话播两遍；判停开关与 L2 配置为准
        L1Bridge.sendMuteOriginal(this, true);
        L1Bridge.sendEndpoint(this, Config.load(this).earlyEndpoint);
        alarms.start();
        // 开机稳定后再回收 AirSkill：太早会与开机广播赛跑
        mainHandler.postDelayed(reclaimAirskill, 45000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand, flags=" + flags + " startId=" + startId);
        return START_STICKY;
    }

    /** 按已保存偏好恢复 AirPlay / 蓝牙。DLNA 在启动时已经处理；开机恢复蓝牙不进可发现。 */
    private void applyReceiverPrefs() {
        try {
            Config c = Config.load(this);
            if (c.airplayEnabled) {
                boolean ok = Receivers.setAirPlay(this, true);
                Log.i(TAG, "开机恢复隔空播放 ok=" + ok);
            }
            if (c.bluetoothEnabled) {
                boolean ok = Receivers.setBluetooth(this, Boolean.TRUE, Boolean.FALSE);
                Log.i(TAG, "开机恢复蓝牙 ok=" + ok);
            }
        } catch (Throwable t) {
            Log.w(TAG, "开机恢复接收端偏好失败: " + t);
        }
    }

    /** 开机读一次整合网关的用量快照（GET /usage 不算次数），便于从日志确认剩余额度。 */
    private void reportAsrUsage() {
        final Config c = Config.load(this);
        if (c.asrUrl == null || c.asrUrl.trim().isEmpty()) {
            Log.i(TAG, "未配置 ASR 地址，跳过用量查询");
            return;
        }
        if (c.asrEngine != null && !c.asrEngine.isEmpty() && !"self".equals(c.asrEngine)) {
            Log.i(TAG, "ASR 引擎不是 self，跳过用量查询: " + c.asrEngine);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "ASR 网关用量: " + AsrClient.health(c.asrUrl, c.asrToken));
                } catch (Throwable t) {
                    Log.w(TAG, "ASR 用量查询失败: " + t);
                }
            }
        }, "r1-asr-usage").start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "MainService onDestroy");
        instance = null;
        mainHandler.removeCallbacks(reclaimAirskill);
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) wakeLock.release();
            } catch (Throwable t) {
                Log.w(TAG, "释放唤醒锁失败: " + t);
            }
            wakeLock = null;
        }
        if (http != null) http.stop();
        if (alarms != null) alarms.stop();
        if (bridge != null) {
            try {
                unregisterReceiver(bridge);
            } catch (Throwable t) {
                Log.w(TAG, "unregisterReceiver: " + t);
            }
        }
        if (speech != null) speech.shutdown();
        if (tts != null) tts.shutdown();
        if (brain != null) brain.shutdown();
        MusicPlayer.destroy();
        super.onDestroy();
    }

    public static MainService get() {
        return instance;
    }

    /** 配置变了之后重新同步给 L1：「压制原厂播报」与「提前判停」。 */
    public void applyMuteOriginal() {
        L1Bridge.sendMuteOriginal(this, true);
        L1Bridge.sendEndpoint(this, Config.load(this).earlyEndpoint);
    }

    /**
     * L1 心跳：收到任何桥广播时调用。
     *
     * @param ver   广播里的协议版本（{@code v}）
     * @param hello true 表示这是 {@code L1_HELLO}：只有心跳带的是 L1 的真实版本，直接覆盖
     *              （回装旧 L1 后要能降下来，否则会去调旧 L1 那条静音的续听）
     */
    public void noteL1(int ver, boolean hello) {
        l1LastSeen = SystemClock.elapsedRealtime();
        boolean changed = false;
        // 只有 L1_HELLO 带的是真实协议版本；灯环回执等事件里的 v 是别的含义（v=2），
        // 拿它当版本会让刚开机的连续对话被误判成「L1 太旧」。
        if (hello && ver > 0 && ver != l1Version) {
            l1Version = ver;
            changed = true;
            Log.i(TAG, "L1 协议版本 = " + ver);
        }
        // L1 重启会丢掉「压制原厂播报」与判停开关，这里定时重发一次
        if (L1Bridge.resendMuteIfStale(this, true) || changed) {
            L1Bridge.sendEndpoint(this, Config.load(this).earlyEndpoint);
        }
    }

    /** L1 是否在线：90 秒内收到过心跳（L1 每 30 秒发一次）。 */
    public boolean l1Alive() {
        return l1LastSeen > 0
                && SystemClock.elapsedRealtime() - l1LastSeen < 90000;
    }

    /** L1 协议版本（心跳 {@code v}）；0 = 未知。 */
    public int l1Version() {
        return l1Version;
    }

    /**
     * 现在的 L1 能不能真正续听（v3 起不靠 enterASR）。
     *
     * <p>老 L1 的续听实测是静音录音，开了只会「请再说一遍 → 我先不听了」，所以不发。
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    public boolean canFollowUp() {
        // 版本未知（0）按「可以」处理：L2 刚启动最多 30 秒才收到心跳，
        // 这期间用户说开连续对话不该被判成「底座太旧」；真遇到老 L1，
        // 续听只会录到静音并安静收场，不耽误别的功能。
        return l1Alive() && (l1Version == 0 || l1Version >= BridgeProtocol.L1_VER_FOLLOW_UP);
    }

    /** 当前灯环常亮效果的管理者（网页与语音设置灯环都经过它）。 */
    public VoiceSession session() {
        return session;
    }

    /** 语音/指令总入口：处理文本指令并自动播报回复。 */
    public void say(String text) {
        say(text, false);
    }

    /**
     * 语音/指令总入口。
     *
     * @param text      用户文本
     * @param fromVoice 是否来自音箱麦克风（true 时按配置决定是否「继续听」）
     */
    public void say(String text, final boolean fromVoice) {
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "say: 空文本，忽略");
            return;
        }
        final String userText = text.trim();
        Log.i(TAG, "=== SAY: " + userText);
        // 先记下「这句是不是续听里说的」，再复位；否则下面的语气词分支永远进不去
        final boolean wasFollowUp = awaitingFollowUp;
        awaitingFollowUp = false;

        // 「嗯。」这类没有内容的填充词不送大模型：模型会把它当成"同意"，
        // 然后编一段执行结果（实测 2026-11-03 23:34 回了「好嘞，已帮你开启连续对话」）。
        if (fromVoice && isFiller(userText)) {
            Log.i(TAG, "填充词，当作没听清: " + userText);
            if (wasFollowUp) {
                session.end("续听只有语气词");
                return;
            }
            onHeardNothing();
            return;
        }
        // 只录到唤醒词本身（原厂会在唤醒后立刻收一轮）：不送模型，安静结束等下一轮
        if (fromVoice && isWakeWordOnly(userText)) {
            Log.i(TAG, "只录到唤醒词，忽略: " + userText);
            awaitingFollowUp = false;
            session.end("只有唤醒词");
            return;
        }

        // 同一句话可能被识别多次（回声、原厂回灌）。上一句还在等大模型时，
        // 再收一条就当重复，直接丢掉，否则会出现“只问一次、答好几遍”。
        if (fromVoice) {
            if (sBusy && System.currentTimeMillis() - sBusyAt < 8000
                    && !IntentRouter.isInterrupt(userText)) {
                Log.i(TAG, "上一句还在处理，忽略重复识别: " + userText);
                return;
            }
            sBusy = true;
            sBusyAt = System.currentTimeMillis();
            // 听懂了、开始想：灯环转呼吸，大模型慢的那几秒用户也知道它听见了
            session.thinking();
        }

        brain.handle(userText, new Brain.Callback() {
            @Override
            public void onReply(String reply) {
                sBusy = false;
                Log.i(TAG, "=== REPLY(" + reply.length() + "字): " + reply);
                Brain.recordTurn(MainService.this, userText, reply);
                boolean askBack = needsAnswer(reply);
                if (askBack) {
                    sClarifyStreak++;
                } else {
                    sClarifyStreak = 0;
                }
                // 续听条件：连续对话开着，或这句是反问（最多连问 MAX_CLARIFY_STREAK 次）。
                // 必须 L1 v3：老 L1 的续听走 enterASR，实测录到静音，开了只会再失败一次。
                boolean listenAgain = fromVoice && canFollowUp()
                        && (Config.load(MainService.this).continuousChat
                            || (askBack && sClarifyStreak <= MAX_CLARIFY_STREAK));
                if (fromVoice) {
                    speak(reply, listenAgain);
                } else {
                    announce(reply);
                }
            }

            @Override
            public void onError(String message) {
                sBusy = false;
                Log.e(TAG, "=== ERROR: " + message);
                if (fromVoice) {
                    speak("出错了。", false);
                } else {
                    announce("出错了。");
                }
            }
        });
    }

    /** 旁路播报（不属于语音轮）：HTTP TTS → 原厂 TTS（经 L1）→ 系统 TTS。等同 {@link #announce(String)}。 */
    public void speak(String text) {
        announce(text);
    }

    /**
     * 没听清时用语音请用户重说（语音指令必须有语音反馈，不能只在日志里写一句）。
     *
     * <p>触发场景：录音为空 / 识别为空 / 采集异常 / 只有填充词。
     *
     * <p><b>必须限次</b>：如果这里无条件续听，就会变成
     * 「听不懂 → 请再说一遍 → 继续听 → 还是听不懂」的死循环，用户根本停不下来
     * （实测 2026-11-04 21:29 连续 5 轮）。所以次数上限**优先于** continuousChat：
     * 连错 {@link #MAX_CLARIFY_STREAK} 次就收场，既不播也不听了。
     *
     * <p>会从 ASR 后台线程调用，统一切到主线程，避免和 {@link #onWake()} 抢播报状态。
     */
    /**
     * 没听清的统一出口：续听轮里没内容就安静收场，别念「我没听清」（那只会多耗几秒）。
     */
    private void heardNothingOrEnd() {
        if (awaitingFollowUp) {
            awaitingFollowUp = false;
            Log.i(TAG, "续听没有内容，安静结束");
            session.end("续听无内容");
            return;
        }
        onHeardNothing();
    }

    public void onHeardNothing() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                heardNothingOnMain();
            }
        });
    }

    private void heardNothingOnMain() {
        sBusy = false;
        sClarifyStreak++;
        // 连续对话里没听清：别念「我没听清」（念一遍就是好几秒），静默再听一次；
        // 最多两次，仍没有就收场，避免无限空转。
        boolean continuous = Config.load(this).continuousChat;
        if (continuous && canFollowUp() && sClarifyStreak <= 3) {
            Log.i(TAG, "连续对话中没听清，静默再听一次（第 " + sClarifyStreak + " 次）");
            awaitingFollowUp = true;
            pauseForVoice("续听");
            session.continueListening();
            L1Bridge.sendListen(this);
            return;
        }
        if (sClarifyStreak > MAX_CLARIFY_STREAK) {
            // 已经问过还没听懂：说一句收场、别再听了，等用户主动唤醒。
            Log.i(TAG, "连续 " + sClarifyStreak + " 次没听清，本轮收场（不再续听）");
            sClarifyStreak = 0;
            speak("我先不听了，需要的时候喊我。", false);
            return;
        }
        // 请重说就接着听（L1 v3 才行），用户不用再喊一遍唤醒词
        speak("没听清。", canFollowUp());
    }

    /**
     * 系统时间被 NTP 校准（从 2018 跳到当前）时，重置闹钟的去重状态。
     *
     * <p>不重置的话，`lastMinute` 还停在旧时钟上，可能同一条闹钟重复播或整分钟漏掉。
     *
     * @author xudong.hua,deepseek
     * @since 2026-11-04 08:05 星期三
     */
    private final class TimeChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (alarms != null) {
                alarms.onTimeChanged();
            }
        }
    }

    /**
     * L1 报告「唤醒词刚命中」。整条链路里最早的一刻，必须在这里让路。
     *
     * <p>为什么不能等到 {@link #onVoiceStart} 再暂停：那时麦克风已经打开，播放中拿到的
     * PCM 是数字静音（实测 2026-11-04 23:16 录 10 秒 peak=74 rms=3），这一轮必然识别为空。
     *
     * <p>顺带做引擎看门狗：连续两轮「唤醒后既没有录音也没有取消」，说明原厂识别引擎
     * 停在 {@code engine_stop} 不恢复了（实测会每 5 分钟抛一次异常），这时请求 L1 重进唤醒态。
     */
    public void onWake() {
        if (lastWakeAt > 0 && !wakeGotAudio && !wakeCancelled) {
            emptyWakes++;
        } else {
            emptyWakes = 0;
        }
        if (emptyWakes >= 2) {
            emptyWakes = 0;
            Log.w(TAG, "连续两轮唤醒没拿到录音，请求 L1 重进唤醒模式");
            L1Bridge.restartWake(this);
        }
        lastWakeAt = System.currentTimeMillis();
        wakeGotAudio = false;
        wakeCancelled = false;
        // 新一轮开始：上一轮还在等的原厂 TTS 回执作废（不能让它把这一轮提前收尾）
        pendingNativeId = 0;
        mainHandler.removeCallbacks(pendingCancelEnd);
        pendingCancelReason = null;
        pauseForVoice("唤醒");
        session.begin();
    }

    /**
     * L1 报告这一轮被取消（原厂取消 / 录音太短 / TTS 尾音保护 / 续听没人说话）。
     *
     * <p>取消也是「一轮结束」：被唤醒让路暂停的音乐要接着放，灯环回到常亮效果。
     *
     * <p>不清 {@link #asrUploading}：取消的是「这一轮的录音」，如果上一段还在上传，
     * 清掉它就等于放行第二段并发上传。
     *
     * @param reason 取消原因，只用于日志
     */
    public void onVoiceCancel(String reason) {
        wakeCancelled = true;
        emptyWakes = 0;
        awaitingFollowUp = false;
        Log.i(TAG, "本轮识别取消（" + (reason == null ? "未说明" : reason) + "），已释放录音会话");
        if (asrUploading.get() || sBusy) {
            return;
        }
        // 太短的空轮后面常常紧跟正式那句：先别收尾，否则音乐被恢复又立刻暂停，听起来一顿一顿的
        if (reason != null && reason.startsWith("too-short")) {
            pendingCancelReason = reason;
            mainHandler.removeCallbacks(pendingCancelEnd);
            mainHandler.postDelayed(pendingCancelEnd, SHORT_CANCEL_GRACE_MS);
            return;
        }
        mainHandler.removeCallbacks(pendingCancelEnd);
        pendingCancelReason = null;
        session.end("取消:" + reason);
        // 连续对话里因为「没人说话」结束：给一句提示，否则用户不知道已经退出了
        if (reason != null && reason.startsWith("follow-silence") && Config.load(this).continuousChat) {
            announce("先聊到这。");
        }
    }

    /**
     * 把播放和播报停下来，给麦克风让出音频通路。
     *
     * <p>音乐用 {@link MusicPlayer#pauseForVoice()}、投屏用 {@link DlnaRenderer#pauseForVoice()}：
     * 会记下「是唤醒停的」，这一轮结束后由 {@link VoiceSession#end(String)} 恢复。
     *
     * @param reason 触发原因，写进日志便于把「谁停的」和录音质量对上
     */
    private void pauseForVoice(String reason) {
        try {
            if (MusicPlayer.get().pauseForVoice()) {
                Log.i(TAG, reason + "：已暂停播放，把音频通路让给麦克风（本轮结束后恢复）");
            }
        } catch (Throwable t) {
            Log.w(TAG, reason + "：暂停播放失败: " + t);
        }
        try {
            if (DlnaRenderer.pauseForVoice()) {
                Log.i(TAG, reason + "：已暂停投屏播放，避免录到数字静音（本轮结束后恢复）");
            }
        } catch (Throwable t) {
            Log.w(TAG, reason + "：暂停投屏失败: " + t);
        }
        try {
            if (speech != null) speech.stopQuietly();
        } catch (Throwable t) {
            Log.w(TAG, reason + "：停止播报失败: " + t);
        }
    }

    /**
     * L1 报告一段录音开始。
     *
     * <p>在这里补一次「让路」：旧版 L1 没有 WAKE 事件，靠本事件暂停播放中的音乐；
     * 新版在 onWake() 里已经做过，重复调用幂等（已在暂停就不做事）。
     *
     * <p>不再创建流式会话——按次数计费下统一等 ASR_AUDIO 到达后整段上传，每句只计一次。
     *
     * @param path L1 正在写的 WAV 路径（只用于日志，整段上传在 onVoiceAudio）
     */
    public void onVoiceStart(final String path) {
        // 新的一段录音开始了：取消「太短空轮」的延迟收尾
        mainHandler.removeCallbacks(pendingCancelEnd);
        pendingCancelReason = null;
        pauseForVoice("开始录音");
    }

    /**
     * L1 送来一段录音（自建 ASR 链路）。在后台线程上传到 ASR 网关，拿到文字后走正常对话。
     *
     * <p>为什么不在广播线程里做：网络请求要几百毫秒到几秒，广播接收器必须在 10 秒内返回，
     * 而且这台机器只有一个核能用，阻塞会拖住整个服务。
     *
     * @param path L1 写好的 WAV/PCM 文件绝对路径
     */
    public void onVoiceAudio(final String path) {
        wakeGotAudio = true;
        emptyWakes = 0;
        final Config cfg = Config.load(this);
        if (!AsrClient.isConfigured(cfg)) {
            Log.w(TAG, "收到录音但没启用自建 ASR，忽略: " + path);
            // 语音路径必须有语音反馈；也要收尾，否则唤醒时暂停的音乐要等 90 秒兜底
            speak("语音识别还没有配置，请在设置页填好识别服务地址。", false);
            return;
        }
        if (!asrUploading.compareAndSet(false, true)) {
            pendingAudioPath = path;
            Log.i(TAG, "上一段录音还在转写，这段先排队（保留最新一条）: " + path);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = new File(path);
                    if (!f.exists()) {
                        Log.w(TAG, "录音文件不存在: " + path);
                        heardNothingOrEnd();
                        return;
                    }
                    // 跨 UID 读原厂包（L1）的私有目录：目录要 o+x、文件要 o+r，
                    // 否则这里一直是 EACCES，用户说什么都会被当成「没听清」。
                    // 实测 2026-11-03 20:29/20:32 连续两轮都是这个原因，见 docs/24-self-hosted-asr.md §6。
                    if (!f.canRead()) {
                        Log.w(TAG, "录音文件读不到（权限）: " + path + "，确认 L1 已放开录音文件权限");
                        heardNothingOrEnd();
                        return;
                    }
                    byte[] audio = readLimited(f, 400 * 1024);
                    // 诊断用：录音到底是静音、太小，还是真的有声音。没有 adb 时
                    // 只能靠这条日志判断（/api/v1/logs 就能看到）。
                    Log.i(TAG, "录音诊断: " + describeAudio(audio));
                    AsrClient.Result r = AsrClient.transcribe(cfg, audio);
                    String text = r.text == null ? "" : r.text.trim();
                    if (text.isEmpty()) {
                        Log.i(TAG, "自建 ASR 返回空文本（" + r.endpoint + "）");
                        heardNothingOrEnd();
                        return;
                    }
                    Log.i(TAG, "自建 ASR -> " + text + "（" + r.endpoint
                            + ", " + r.durationMs + "ms, " + r.engine + "）");
                    say(text, true);
                } catch (AsrClient.QuotaExceededException qe) {
                    // 本月免费额度用尽：明确播报并收尾，绝不当成「没听清」让用户反复喊。
                    Log.w(TAG, "ASR 额度用尽: " + qe.getMessage());
                    speak(qe.getMessage(), false);
                } catch (Throwable t) {
                    Log.w(TAG, "自建 ASR 失败: " + t);
                    heardNothingOrEnd();
                } finally {
                    asrUploading.set(false);
                    String next = pendingAudioPath;
                    pendingAudioPath = null;
                    if (next != null) {
                        Log.i(TAG, "转写完成，继续处理排队中的录音: " + next);
                        onVoiceAudio(next);
                    }
                }
            }
        }, "r1-asr").start();
    }

    /**
     * 一句话描述这段 WAV：时长、峰值、均方根。
     *
     * <p>用来区分三种「没听清」：静音（peak 极低）、太短（<0.5 秒）、
     * 有声音但被截断。三者修法完全不同，日志里必须先能分开——没有 adb 时
     * 这是唯一的现场证据（`/api/v1/logs` 就能看到）。
     *
     * @param wav 带 44 字节头的 PCM-WAV
     * @return 例如 "2350ms peak=812 rms=96"
     */
    private static String describeAudio(byte[] wav) {
        if (wav == null || wav.length <= 44) {
            return "空音频(" + (wav == null ? 0 : wav.length) + "B)";
        }
        int frames = (wav.length - 44) / 2;
        long sum = 0;
        int peak = 0;
        for (int i = 44; i + 1 < wav.length; i += 2) {
            int v = (short) ((wav[i] & 0xff) | (wav[i + 1] << 8));
            sum += (long) v * v;
            if (Math.abs(v) > peak) {
                peak = Math.abs(v);
            }
        }
        int rms = frames == 0 ? 0 : (int) Math.sqrt((double) sum / frames);
        return (frames * 1000 / 16000) + "ms peak=" + peak + " rms=" + rms;
    }

    /** 读文件，最多读 limit 字节（10 秒 16k 16bit 也就 320KB）。 */
    private static byte[] readLimited(File f, int limit) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[(int) Math.min(f.length(), limit)];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n <= 0) break;
                off += n;
            }
            if (off == buf.length) return buf;
            byte[] out = new byte[off];
            System.arraycopy(buf, 0, out, 0, off);
            return out;
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
        }
    }

    /**
     * 播报语音，可选播完后再听一句。
     *
     * <p>声音优先级（三级兜底，任何一级失败都会往下走，保证语音指令一定有语音反馈）：
     * <ol>
     *   <li>设置里没选「原厂语音」且配了 HTTP TTS → 用 HTTP TTS；全部句子都合成失败时转原厂 TTS；</li>
     *   <li>原厂 TTS（经 L1）；</li>
     *   <li>原厂也念不出来 → 系统 TTS。</li>
     * </ol>
     *
     * <p>这个重载是<b>语音轮的回复</b>：播完（或失败）就是这一轮的结尾——要续听就让 L1 再听一句，
     * 否则恢复音乐、灯环回到常亮效果。网页播报、闹钟用 {@link #announce(String)}，不碰这一轮。
     *
     * @param text       要播的文本
     * @param thenListen true 表示播报结束后续听一句（连续对话 / 反问）
     */
    public void speak(String text, boolean thenListen) {
        speak(text, thenListen, true);
    }

    /**
     * 旁路播报（网页「发送」、闹钟、测试）：只出声，不结束、不延长当前语音轮。
     *
     * <p>为什么要分开：用户唤醒后正在等大模型时闹钟响了，如果闹钟的播完回调也去收尾，
     * 音乐会在用户还没听到回答时被恢复出来。
     *
     * @param text 要播的文本
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    public void announce(String text) {
        speak(text, false, false);
    }

    private void speak(String text, final boolean thenListen, final boolean voiceTurn) {
        String plain = cleanForSpeech(text);
        // 语音轮：一句话念出来就要好几秒，超过 80 字按句号截断（长故事会变短，但这是语音场景的取舍）
        if (voiceTurn) {
            plain = capForSpeech(plain, 80);
        }
        final String plainFinal = plain;
        final int token = session.token();
        if (plainFinal.isEmpty()) {
            if (voiceTurn) finishTurn(token, thenListen, "空回复");
            return;
        }
        Config cfg = Config.load(this);
        boolean nativeVoice = "native".equals(cfg.ttsEngine);

        if (!nativeVoice && speech != null && speech.isConfigured()) {
            // 不论要不要续听都必须挂回调：失败时 onError 是唯一会去走原厂 TTS 兜底的地方
            // （以前不续听时回调为 null，HTTP TTS 一挂就整句没声音）。
            // speak() 返回 false 时 onError 也会被投递，所以这里不再另外兜底。
            speech.speak(plainFinal, new SpeechEngine.Callback() {
                @Override
                public void onStarted(int firstAudioMs, int durationMs) {
                    if (voiceTurn) session.speaking();
                }

                @Override
                public void onDone() {
                    if (voiceTurn) finishTurn(token, thenListen, "播报完");
                }

                @Override
                public void onError(String message) {
                    if (SpeechEngine.STOPPED.equals(message)) {
                        // 被停掉（新一轮唤醒 / 网页停止 / 「别说了」）不是失败，不能去念原厂兜底
                        if (voiceTurn) session.endIfRound(token, "播报被停止");
                        return;
                    }
                    Log.w(TAG, "HTTP TTS 播报失败，改用原厂 TTS: " + message);
                    speakNative(plainFinal, thenListen, voiceTurn, token);
                }
            });
            return;
        }
        speakNative(plainFinal, thenListen, voiceTurn, token);
    }

    /**
     * 原厂 TTS 播一句（经 L1）。
     *
     * <p>L1 v3 念完会回 {@code TTS_DONE}，由 {@link #onNativeTtsDone} 收尾；同时挂一个按字数估的兜底，
     * 防止回执丢了（L1 被杀 / 广播丢失）音乐一直停着。旧 L1 没有回执，只能估时，并把续听交给 L1 自己。
     *
     * <p>不再要求 {@link #l1Alive()}：L2 刚重启的前 30 秒还没收到心跳，但 L1 多半在线；
     * 发出去没人接也只是没声音，所以同时挂估时兜底。
     */
    private void speakNative(String plainFinal, boolean thenListen, boolean voiceTurn, int token) {
        if (voiceTurn) session.speaking();
        boolean v3 = l1Version >= BridgeProtocol.L1_VER_FOLLOW_UP;
        int id = v3 && voiceTurn ? nativeSpeakSeq.incrementAndGet() : 0;
        if (id != 0) {
            pendingNativeId = id;
            pendingNativeListen = thenListen;
            pendingNativeText = plainFinal;
            pendingNativeToken = token;
        }
        if (L1Bridge.speak(this, plainFinal, !v3 && thenListen, id)) {
            if (voiceTurn) {
                // v3：回执正常会更早到，这只是兜底（L1 自己最长 90 秒必回执）
                finishTurnLater(token, plainFinal.length() + (v3 ? 20 : 0), "原厂播报（估时）");
            }
            if (l1Alive()) {
                return;
            }
            Log.w(TAG, "L1 暂未上报心跳，原厂播报已发出，同时用系统 TTS 兜底");
        }
        pendingNativeId = 0;
        if (!tts.speak(plainFinal)) {
            Log.e(TAG, "系统 TTS 不可用（" + tts.status() + "），这一轮没有声音: " + plainFinal);
        }
        if (voiceTurn) finishTurnLater(token, plainFinal.length(), "系统播报（估时）");
    }

    /**
     * L1 报告原厂 TTS 念完了。序号对不上（被新一轮覆盖）就忽略。
     *
     * @param id L1 带回的播报序号
     * @param ok false 表示没念出来
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 19:40 星期四
     */
    public void onNativeTtsDone(int id, boolean ok) {
        if (id == 0 || id != pendingNativeId) {
            return;
        }
        pendingNativeId = 0;
        final boolean listen = pendingNativeListen;
        final int token = pendingNativeToken;
        if (!ok) {
            // 原厂也念不出来：最后一级兜底，保证有声音
            Log.w(TAG, "原厂 TTS 没念出来，退回系统 TTS");
            String last = pendingNativeText;
            if (!tts.speak(last == null || last.isEmpty() ? "出错了。" : last)) {
                Log.e(TAG, "系统 TTS 也不可用（" + tts.status() + "）");
            }
            finishTurnLater(token, last == null ? 10 : last.length(), "系统播报（估时）");
            return;
        }
        finishTurn(token, listen, "原厂播报完");
    }

    /**
     * 一句话播完之后：续听，或结束这一轮。
     *
     * @param token      播报开始时的轮次；期间用户又唤醒了就什么也不做
     * @param thenListen true 表示要续听
     * @param reason     日志用
     */
    private void finishTurn(int token, boolean thenListen, String reason) {
        // 真的播完了：按字数估的那条兜底计时器作废，否则它到点会把「续听中的这一轮」收掉
        cancelPendingFinishLater();
        if (token != session.token()) {
            return;
        }
        if (thenListen && canFollowUp()) {
            awaitingFollowUp = true;
            // 续听同样要先把播放让出来：点歌之后紧接着那一句，音乐还在响
            pauseForVoice("续听");
            session.continueListening();
            L1Bridge.sendListen(this);
            return;
        }
        awaitingFollowUp = false;
        session.endIfRound(token, reason);
    }

    /** 没有可靠播完回调的出口（旧 L1 / 系统 TTS / 回执兜底）：按字数估播完时间再收尾，只收自己那一轮。 */
    private Runnable pendingFinishLater;

    private void finishTurnLater(final int token, int chars, final String reason) {
        long ms = Math.min(95000L, 1500L + chars * 260L);
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                pendingFinishLater = null;
                session.endIfRound(token, reason);
            }
        };
        pendingFinishLater = r;
        mainHandler.postDelayed(r, ms);
    }

    /** 真收到 TTS_DONE / onDone 时，把按字数估的那条兜底计时器作废。 */
    private void cancelPendingFinishLater() {
        Runnable r = pendingFinishLater;
        if (r != null) {
            mainHandler.removeCallbacks(r);
            pendingFinishLater = null;
        }
    }

    /**
     * 语音播报用的长度上限：超过就按最后一个句末标点截断，避免「念一大段」浪费时间。
     *
     * @param s   文本
     * @param max 上限字数
     * @return 截断后的文本
     * @author xudong.hua,claude
     * @since 2026-11-07 11:40 星期六
     */
    private static String capForSpeech(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        int cut = -1;
        for (int i = 0; i < max; i++) {
            char c = s.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '!' || c == '?' || c == ';') {
                cut = i;
            }
        }
        if (cut >= 15) return s.substring(0, cut + 1);
        return s.substring(0, max) + "。";
    }

    /**
     * 把大模型返回的 markdown 洗成适合朗读的纯文本。
     *
     * @param text 原始文本
     * @return 可直接朗读的文本
     */
    private static String cleanForSpeech(String text) {
        if (text == null) return "";
        String s = text;
        s = s.replaceAll("```[\\s\\S]*?```", " ");
        s = s.replaceAll("`([^`]*)`", "$1");
        s = s.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        s = s.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        // 「6~12℃」「1-3级」这类区间：连接符直接被删会念成「六百一十二度」，
        // 先把数字之间的连接符换成「到」（实测 2026-11-08 10:52 明天天气念成了 612℃）
        s = s.replaceAll("(?<=\\d)\\s*[~～\\-－—]\\s*(?=\\d)", "到");
        s = s.replaceAll("[*_~>]", "");
        // 标题式括号/引号 TTS 会念得很别扭（天气回复就带【苏州昆山天气】），去掉它们
        s = s.replaceAll("[【】〖〗「」『』〝〞“”\"]", "");
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

    public SpeechEngine speech() {
        return speech;
    }

    public String state() {
        Config c = Config.load(this);
        return "pid=" + Process.myPid()
                + " systemTts=" + (tts == null ? "null" : tts.status())
                + " httpTts=" + (speech == null ? "null" : speech.status())
                + " cfg=[" + c + "]";
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getService(
                this, 0, new Intent(this, MainService.class), 0);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, "r1brain");
        } else {
            b = new Notification.Builder(this);
        }
        return b.setSmallIcon(R.drawable.stat_notify_sync)
                .setContentTitle("R1 助手")
                .setContentText("正在运行")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }
}
