package com.r1.brain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.r1.brain.dlna.DlnaRenderer;

/**
 * 一轮语音交互的收尾与灯环状态（L2 侧）。
 *
 * <p>以前这些事散在各处、而且大多没人做：唤醒时暂停了音乐却没人恢复；思考时灯不动，
 * 大模型慢的那几秒用户以为没听见；用户设的氛围灯一轮对话后就被原厂唤醒灯冲掉。
 * 现在一轮的生命周期集中在这里：
 *
 * <pre>
 *   WAKE ──► 听（原厂唤醒灯） ──► 思考（loading） ──► 播报 ──► 结束
 *                                                         │
 *                       续听（连续对话） ◄───────────────┤
 *                                                         ▼
 *                                       恢复音乐 + 灯环回到常亮效果
 * </pre>
 *
 * <p>「结束」只有一个入口 {@link #end(String)}，播报完成、播报失败、取消、没听清都走它，
 * 而且幂等：重复调用不会重复恢复音乐。所有方法都在主线程执行（内部自己 post）。
 *
 * @author xudong.hua,claude
 * @since 2026-11-05 18:30 星期四
 */
public final class VoiceSession {

    private static final String TAG = Brain.TAG;
    /** 兜底：一轮最长这么久就强制收尾（播报回调丢了也能恢复音乐和灯）。 */
    private static final long ROUND_TIMEOUT_MS = 90000;
    /** 已经开口播报后的兜底（长故事最多 2000 字，约 8 分钟）。 */
    private static final long SPEAKING_TIMEOUT_MS = 10 * 60000L;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** 当前是否在一轮里（WAKE 之后、end 之前）。 */
    private boolean active;
    /** 用户设定的常亮效果；对话结束后灯环回到它。 */
    private volatile LightEffect ambient = LightEffect.OFF;
    /** 轮次号：每次开新一轮自增；volatile 是因为 {@link #token()} 会在别的线程读。 */
    private volatile int round;

    /**
     * 「在听你说话」时点亮的灯环效果。
     *
     * <p>原厂没有 RGB 接口（{@code PhicommLightController} 只有 off/loading/wakeup/dormant/net
     * 和 24 段唤醒灯位），能不能选颜色由固件决定，这里只能选预置效果。
     * 选呼吸灯的原因：它和原厂「开始录音」时的表现一致（{@code LightListener.onRecognizeStart}
     * 就是 {@code turnOnLoadingLight}），亮度足够、不额外占 CPU，能一眼看出「它在等我」。
     */
    private static final LightEffect LISTEN_EFFECT = LightEffect.LOADING;

    /**
     * 「在听」的灯环保活间隔。
     *
     * <p>必须保活：原厂引擎在它自己判停时会 {@code turnOffLoadingLight}，而我们的续听靠
     * 自己判停、比原厂判得晚，不重新点亮就会出现「还在录，灯已经灭了」。
     */
    private static final long LISTEN_KEEPALIVE_MS = 2000;

    /** 是否处于「等用户说话」状态（只有它决定保活线程跑不跑）。 */
    private boolean listening;

    /** 保活：每 2 秒把「聆听」灯重新点亮一次。 */
    private final Runnable listenKeepAlive = new Runnable() {
        @Override
        public void run() {
            if (!listening || !active) {
                return;
            }
            L1Bridge.sendLight(ctx, LISTEN_EFFECT);
            main.postDelayed(this, LISTEN_KEEPALIVE_MS);
        }
    };

    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            if (active) {
                Log.w(TAG, "本轮超过 " + ROUND_TIMEOUT_MS / 1000 + " 秒没有收尾，强制结束");
                endNow("timeout");
            }
        }
    };

    public VoiceSession(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /**
     * 用户（网页或语音）设定了灯环效果：立刻生效，并记为常亮效果。
     *
     * @param effect 效果
     * @return 指令是否发出
     */
    public boolean setAmbient(LightEffect effect) {
        LightEffect e = effect == null ? LightEffect.OFF : effect;
        if (e.isAmbient()) {
            ambient = e;
        }
        return L1Bridge.sendLight(ctx, e);
    }

    /** 当前常亮效果。 */
    public LightEffect ambient() {
        return ambient;
    }

    /**
     * 麦克风开了、等用户说话：灯环点亮「聆听」效果并开始保活。
     *
     * <p>只能从主线程调用（{@link #begin()} / {@link #continueListening()} 里的 post 已经保证）。
     */
    private void startListeningLight() {
        listening = true;
        L1Bridge.sendLight(ctx, LISTEN_EFFECT);
        main.removeCallbacks(listenKeepAlive);
        main.postDelayed(listenKeepAlive, LISTEN_KEEPALIVE_MS);
    }

    /** 不用再等了（开始思考/播报/收尾）：停掉保活，让调用方接着设自己的效果。 */
    private void stopListeningLight() {
        listening = false;
        main.removeCallbacks(listenKeepAlive);
    }

    /**
     * 唤醒词命中：开始新一轮，灯环立刻切到「聆听」。
     *
     * <p>音乐的让路由调用方先做（必须在开麦之前），这里只记状态、点灯。
     */
    public void begin() {
        // 轮次号同步自增：唤醒后紧接着的取消/收尾回调要能拿到「新一轮」的号，
        // 否则它们会带着旧号被跳过（这一轮就再也收不了尾）。
        round++;
        main.post(new Runnable() {
            @Override
            public void run() {
                active = true;
                main.removeCallbacks(timeout);
                main.postDelayed(timeout, ROUND_TIMEOUT_MS);
                startListeningLight();
            }
        });
    }

    /** 听完了、开始想：灯切到呼吸（思考中），让用户知道它听见了；兜底超时从这里重新算。 */
    public void thinking() {
        if (!active) {
            // 续听轮、或旧 L1 没有 WAKE 事件：从这里补一个开始
            round++;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                active = true;
                // ASR + 大模型（最多 45 秒）+ 首句合成可能超过 90 秒，不能从唤醒时算起
                main.removeCallbacks(timeout);
                main.postDelayed(timeout, ROUND_TIMEOUT_MS);
                // 从「听」切换到「想」：效果同为呼吸灯，但要停掉聆听保活
                stopListeningLight();
                L1Bridge.sendLight(ctx, LightEffect.LOADING);
            }
        });
    }

    /**
     * 开口了：思考灯撤掉，换回常亮效果（播报期间不再转圈）。
     *
     * <p>兜底超时同时放宽到 {@link #SPEAKING_TIMEOUT_MS}：讲故事这类长播报会超过 90 秒，
     * 不能在播到一半时把音乐恢复出来。正常情况下播完回调会先收尾。
     */
    public void speaking() {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (active) {
                    main.removeCallbacks(timeout);
                    main.postDelayed(timeout, SPEAKING_TIMEOUT_MS);
                    stopListeningLight();
                    L1Bridge.sendLight(ctx, ambient);
                }
            }
        });
    }

    /**
     * 要续听：本轮不收尾（音乐保持暂停），灯切到「聆听」并保活，等用户的下一句。
     */
    public void continueListening() {
        // 新的一轮听：同步自增，作废上一轮按字数估的收尾计时器
        round++;
        main.post(new Runnable() {
            @Override
            public void run() {
                if (!active) {
                    active = true;
                }
                main.removeCallbacks(timeout);
                main.postDelayed(timeout, ROUND_TIMEOUT_MS);
                startListeningLight();
            }
        });
    }

    /**
     * 这一轮结束：恢复被唤醒让路暂停的音乐，灯环回到常亮效果。幂等。
     *
     * @param reason 结束原因（日志用）
     */
    public void end(final String reason) {
        // 只结束自己那一轮：ASR 线程/延时回调可能晚于新一轮的 begin 到达
        final int token = round;
        main.post(new Runnable() {
            @Override
            public void run() {
                if (token != round) {
                    Log.i(TAG, "忽略过期的收尾（" + reason + "）：已经是第 " + round + " 轮");
                    return;
                }
                endNow(reason);
            }
        });
    }

    /**
     * 只结束指定的那一轮（延时收尾用）：期间用户又唤醒了，就不能把新一轮收掉。
     *
     * @param token  {@link #token()} 当时的值
     * @param reason 结束原因（日志用）
     */
    public void endIfRound(final int token, final String reason) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (token == round) {
                    endNow(reason);
                }
            }
        });
    }

    /**
     * 当前轮次令牌（只在主线程读写，这里读到的可能落后一个 post，对「是否同一轮」的判断足够）。
     *
     * @return 轮次号
     */
    public int token() {
        return round;
    }

    private void endNow(String reason) {
        if (!active) {
            return;
        }
        active = false;
        main.removeCallbacks(timeout);
        stopListeningLight();
        boolean resumed = MusicPlayer.get().resumeAfterVoice();
        boolean castResumed = false;
        try {
            castResumed = DlnaRenderer.resumeAfterVoice();
        } catch (Throwable t) {
            Log.w(TAG, "恢复投屏失败: " + t);
        }
        // 投屏/蓝牙走的是原厂播放器，L1 在唤醒时暂停了它，这里让 L1 恢复
        L1Bridge.resumeExternalMedia(ctx);
        L1Bridge.sendLight(ctx, ambient);
        Log.i(TAG, "本轮结束（" + reason + "）" + (resumed ? "，已恢复音乐" : "")
                + (castResumed ? "，已恢复投屏" : "")
                + "，灯环回到「" + ambient.label() + "」");
    }

    /** 是否在一轮里。 */
    public boolean active() {
        return active;
    }
}
