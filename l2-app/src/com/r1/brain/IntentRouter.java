package com.r1.brain;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地意图路由：先于大模型执行，命中即返回播报文本。
 *
 * <p>覆盖范围（顺序即优先级）：
 * <ol>
 *   <li>打断播报：别说了 / 安静</li>
 *   <li>闹钟：N 分钟后提醒我、明天七点叫我、取消闹钟</li>
 *   <li>睡眠定时、播放/暂停/切歌/继续播放</li>
 *   <li>点歌：放一首 X / 播放一首周杰伦的歌 / 换一首（走云音乐搜索 + 取地址）</li>
 *   <li>场景：晚安（停播 + 30 分钟定时 + 20% 音量）、早上好（报时 + 天气 + 50% 音量）</li>
 *   <li>报时 / 日期 / 天气</li>
 *   <li>重启、清理、诊断</li>
 *   <li>音量：调到 N%、调高、调低、静音</li>
 *   <li>Home Assistant 开关设备（配置了 haUrl + 令牌才生效）</li>
 * </ol>
 *
 * <p>任何一条都无法命中时返回 {@code null}，由 {@link Brain} 交给大模型。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 13:20 星期六
 */
public final class IntentRouter {

    private static final String TAG = Brain.TAG;
    private static final TimeZone CN_TZ = TimeZone.getTimeZone("GMT+08:00");

    /** 音量数值：先说「音量」再说数字，中间允许「调到/设为/百分之」这类连接词。 */
    private static final Pattern VOL_NUM = Pattern.compile("音量.*?(\\d+)");
    /** 中文数字版本：音量调到三十 / 音量百分之四十。 */
    private static final Pattern VOL_CN = Pattern.compile("音量[^0-9]{0,4}?[到为成]([零一二三四五六七八九十百]{1,4})");
    private static final Pattern SLEEP_MIN = Pattern.compile("(\\d+)\\s*分钟");
    /** 一次性闹钟：五分钟以后 / 30 分钟后提醒我。 */
    private static final Pattern ALARM_AFTER = Pattern.compile("(\\d{1,3})\\s*分钟(?:后|之后|以后)?");
    /** 定点闹钟：七点、7:30、明天 06 点 30 分。 */
    private static final Pattern ALARM_AT = Pattern.compile("(\\d{1,2})\\s*[点时:：]\\s*(\\d{1,2})?\\s*分?");
    /** 点歌：放一首 X / 播放 X / 来一首 X / 我想听 X。 */
    private static final Pattern PLAY_SONG = Pattern.compile(
            "^(?:我)?(?:想|要)?(?:请)?(?:帮我|给我)?(?:播放|放|来|听|点)(?:一|两)?(?:首|支|个)?(.+)$");

    /** 「换一首」用的随机源。 */
    private static final Random RANDOM = new Random();

    /** 上一次点歌的关键词与结果，供「换一首」复用。 */
    private static volatile String lastQuery = "";
    private static volatile boolean lastQueryIsArtist = false;
    private static volatile String lastPlayedTitle = "";

    private IntentRouter() {
    }

    /**
     * 尝试用本地意图处理一条指令。
     *
     * @param ctx  上下文
     * @param text 用户原话
     * @return 播报文本；未命中返回 null
     */
    public static String handle(Context ctx, String text) {
        if (text == null) return null;
        String raw = text.trim();
        if (raw.isEmpty()) return null;
        // 正确率靠这里保证：ASR 只要「近似」，这里负责把它归一到能匹配的形态。
        // 三件事：剥唤醒词/口头语、修常见同音错、丢句尾标点。
        String t = fixNearMiss(stripLead(raw));
        if (t.isEmpty()) return null;
        if (!t.equals(raw)) {
            Log.i(TAG, "意图归一: 「" + raw + "」-> 「" + t + "」");
        }
        try {
            String r;
            if ((r = interrupt(ctx, t)) != null) return r;
            if ((r = chatMode(ctx, t)) != null) return r;
            if ((r = receivers(ctx, t)) != null) return r;
            if ((r = phone(t)) != null) return r;
            if ((r = alarm(ctx, t)) != null) return r;
            if ((r = music(ctx, t)) != null) return r;
            if ((r = scene(ctx, t)) != null) return r;
            if ((r = timeAndWeather(ctx, t)) != null) return r;
            if ((r = systemOps(ctx, t)) != null) return r;
            if ((r = volume(ctx, t)) != null) return r;
            if ((r = light(ctx, t)) != null) return r;
            if ((r = HomeAssistant.handle(Config.load(ctx), t)) != null) return r;
        } catch (Throwable e) {
            Log.w(TAG, "local intent failed: " + e);
        }
        return null;
    }

    /** 开头的唤醒词与口头语：它们对意图没有信息量，留着只会让正则匹配不上。 */
    private static final String[] LEAD_NOISE = {
            "小讯小讯", "小讯", "你好小讯", "嗨小讯", "喂小讯",
            "嗯", "呃", "那个", "麻烦", "请帮我", "帮我", "给我", "请"
    };

    /**
     * 剥掉句首的唤醒词与口头语、句尾标点。
     *
     * <p>为什么要剥：录音从原厂开麦就开始，很可能连「小讯小讯」一起录进去；
     * 而点歌正则要求句首是「播放/放/来/听/点」，多一个「小讯」就整条匹配不上。
     *
     * @param s 原话
     * @return 干净的话
     */
    private static String stripLead(String s) {
        String out = s == null ? "" : s.trim();
        for (int i = 0; i < 8; i++) {
            String before = out;
            out = trimLeadPunct(out);
            for (String p : LEAD_NOISE) {
                if (out.startsWith(p) && out.length() > p.length()) {
                    out = out.substring(p.length());
                    break;
                }
            }
            if (out.equals(before)) {
                break;
            }
        }
        return stripTail(trimLeadPunct(out));
    }

    /** 去掉句首标点：「小讯小讯，播放…」剥掉唤醒词后前面还留一个逗号。 */
    private static String trimLeadPunct(String s) {
        String out = s == null ? "" : s;
        while (out.length() > 0 && "，。！？、,.!?…~　 \t".indexOf(out.charAt(0)) >= 0) {
            out = out.substring(1);
        }
        return out.trim();
    }

    /**
     * 常见同音错纠正：只修命令词，不动歌名/歌手。
     *
     * <p>实测样本：ASR 把「连续对话」听成「连续兑换」「连系兑换」。
     * 这类错误逐字看是错的，但对用户来说是「没办成事」——这里按整词替换修掉。
     *
     * @param s 归一后的文本
     * @return 修正后的文本
     */
    private static String fixNearMiss(String s) {
        if (s == null || s.isEmpty()) return "";
        String out = s;
        for (int i = 0; i < NEAR_MISS.length; i += 2) {
            if (out.contains(NEAR_MISS[i])) {
                out = out.replace(NEAR_MISS[i], NEAR_MISS[i + 1]);
            }
        }
        return out;
    }

    /** 近音纠错表：错误写法 → 正确写法（成对排列，只收命令词）。 */
    private static final String[] NEAR_MISS = {
            "连续兑换", "连续对话",
            "连系兑换", "连续对话",
            "连续对换", "连续对话",
            "免换醒", "免唤醒",
            "音亮", "音量",
            "音两", "音量",
            "天汽", "天气",
            "天启", "天气",
            "闹中", "闹钟",
            "脑钟", "闹钟",
            "暂停播放", "暂停",
            "下一手", "下一首",
            "上一手", "上一首"
    };

    // ---------------------------------------------------------------- 打断播报

    /** 是否是「别说了」这类打断指令（MainService 用它决定忙时是否仍要处理）。 */
    public static boolean isInterrupt(String t) {
        return t != null && (t.contains("别说了") || t.contains("闭嘴") || t.contains("停止播报")
                || t.equals("安静") || t.contains("安静点") || t.contains("不要说了"));
    }

    private static String interrupt(Context ctx, String t) {
        if (!isInterrupt(t)) {
            return null;
        }
        MainService svc = MainService.get();
        if (svc != null && svc.speech() != null) {
            svc.speech().stop();
        }
        // 如果当前用的是原厂 TTS 兜底，也要把它停掉
        L1Bridge.stopSpeak(ctx);
        return "好。";
    }

    // ---------------------------------------------------------------- 接收端开关（DLNA / AirPlay / 蓝牙）

    /**
     * 打开/关闭/查询 DLNA、隔空播放、蓝牙（含进入配对）。
     *
     * <p>这是开关，不是从音箱向外推流。手机端的系统投屏 / AirPlay / 蓝牙音频仍然由系统或 App 发起。
     * 实现全部在 {@link Receivers}，与 HTTP、小程序共用。
     */
    private static String receivers(Context ctx, String t) {
        boolean pair = t.contains("配对") || t.contains("可发现") || t.contains("可被发现")
                || t.contains("发现模式");
        boolean bt = t.contains("蓝牙") || pair;
        boolean airplay = t.contains("隔空播放") || t.contains("airplay") || t.contains("隔空投屏");
        boolean dlna = t.contains("dlna") || t.contains("投屏") || t.contains("投到音箱");
        if (!bt && !airplay && !dlna) return null;

        boolean ask = t.contains("开了吗") || t.contains("开了没") || t.contains("状态")
                || ((t.contains("查") || t.contains("看看") || t.contains("问一下"))
                    && !t.contains("打开") && !t.contains("关闭") && !t.contains("关掉"));
        if (ask) return Receivers.statusSpeech(ctx);

        boolean off = t.contains("关") || t.contains("退出") || t.contains("停止") || t.contains("别");
        // 「退出配对 / 关闭配对」只退可发现，不关适配器
        if (pair && off) return Receivers.applyVoice(ctx, "discoverable", false);
        if (pair) return Receivers.applyVoice(ctx, "discoverable", true);
        if (bt) return Receivers.applyVoice(ctx, "bluetooth", !off);
        if (airplay) return Receivers.applyVoice(ctx, "airplay", !off);
        return Receivers.applyVoice(ctx, "dlna", !off);
    }

    // ---------------------------------------------------------------- 打电话（明确做不到）

    /**
     * 「打电话/拨号」这类请求：设备没有拨号能力，本地直接说清楚，别让大模型绕一圈。
     *
     * <p>注意「电话模式」是连续对话的别名，已经在 {@link #chatMode} 里处理，
     * 这里的判定必须排除它，否则会把「开启电话模式」当成要拨号。
     */
    private static String phone(String t) {
        if (t.contains("电话模式") || t.contains("对话模式")) return null;
        boolean wantsCall = t.contains("打给") || t.contains("拨打") || t.contains("通话中")
                || t.contains("打电话") || t.contains("拨号") || t.contains("呼叫");
        if (!wantsCall) return null;
        return "我没法打电话。";
    }

    // ---------------------------------------------------------------- 连续对话
    /**
     * 「开启／关闭连续对话」。
     *
     * <p>2026-11-03 实测踩过：这句以前**没有本地意图**，直接落到大模型，
     * 大模型回一句「好的，已经开启」，配置其实没变（`continuousChat` 仍是 false），
     * 于是用户接着说下一句永远没反应——表现出来的就是「卡住了」。
     */
    private static String chatMode(Context ctx, String t) {
        // 主动结束对话：用户说「结束对话/退出对话/不聊了」就直接关掉并回一句
        if (t.contains("结束对话") || t.contains("退出对话") || t.contains("不聊了")
                || t.contains("结束聊天") || t.contains("不说了")) {
            Config.update(ctx, new Config.Mutator() {
                @Override
                public void apply(Config c) {
                    c.continuousChat = false;
                }
            });
            return "先聊到这。";
        }
        // 「打电话给谁」是拨号，不是连续对话（最常用的句式是「打电话给妈妈」，不含「打给」）
        if (t.contains("打电话") && !t.contains("电话模式") && !t.contains("对话模式")
                && (t.contains("给") || t.contains("帮") || t.contains("吗"))) {
            return null;
        }
        // 真正的「拨号」动作不当作连续对话：打给谁 / 拨打 / 通话中
        if (t.contains("打给") || t.contains("拨打") || t.contains("通话中")) {
            return null;
        }
        boolean off = t.contains("关闭") || t.contains("取消") || t.contains("停止") || t.contains("关掉")
                || t.contains("退出");
        // 口语里「电话模式」「对话模式」「打电话」都像是在要求"像打电话一样一直说"，
        // 人工记命令记不准，这些模棱两可的说法一律收进连续对话。
        boolean mentioned = t.contains("连续对话") || t.contains("连续说") || t.contains("免唤醒")
                || t.contains("电话模式") || t.contains("对话模式") || t.contains("打电话");
        if (!mentioned) return null;
        // 只说了「连续对话」也当成要开：用户提这个词，意图基本就是要打开。
        // （实测 23:33 说的就是「连续对话」，旧逻辑要求带动词，结果落到大模型去了。）
        final boolean enable = !off;
        Config.update(ctx, new Config.Mutator() {
            @Override
            public void apply(Config c) {
                c.continuousChat = enable;
            }
        });
        if (!enable) {
            return "好，先喊小讯小讯。";
        }
        // 只承诺做得到的事：已知 L1 版本过老时才提醒（版本未知通常是 L2 刚启动还没收到心跳，别误报）
        MainService svc = MainService.get();
        if (svc == null || (svc.l1Version() > 0 && svc.l1Version() < BridgeProtocol.L1_VER_FOLLOW_UP)) {
            return "语音底座要升级。";
        }
        return "好，可以说了。";
    }
    // ---------------------------------------------------------------- 闹钟

    private static String alarm(Context ctx, String t) {
        // 触发条件：闹钟 / 提醒 / 叫我；或「播报 + 定时词」（例如每天早上七点半播报一次天气）
        boolean scheduledBroadcast = t.contains("播报")
                && (t.contains("每天") || t.contains("每早") || t.contains("每晚")
                    || t.contains("定时") || t.contains("早上") || t.contains("明天"));
        boolean wantsAlarm = t.contains("闹钟") || t.contains("提醒") || t.contains("叫我") || scheduledBroadcast;
        if (!wantsAlarm) return null;
        String n = normalizeNumbers(t);
        String action = t.contains("天气") ? "weather" : "speak";

        // 取消：全部关掉
        if (t.contains("取消") || t.contains("关闭") || t.contains("删掉")) {
            final int[] closed = { 0 };
            try {
                Config.update(ctx, new Config.Mutator() {
                    @Override
                    public void apply(Config c) {
                        try {
                            JSONArray arr = new JSONArray(c.alarms);
                            int count = 0;
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject a = arr.optJSONObject(i);
                                if (a == null) continue;
                                if (a.optBoolean("enabled", false)) {
                                    a.put("enabled", false);
                                    count++;
                                }
                            }
                            closed[0] = count;
                            if (count > 0) c.alarms = arr.toString();
                        } catch (Throwable e) {
                            Log.w(TAG, "取消闹钟失败: " + e);
                        }
                    }
                });
            } catch (Throwable e) {
                Log.w(TAG, "取消闹钟失败: " + e);
                return "取消失败，去网页关。";
            }
            if (closed[0] == 0) return "没有闹钟。";
            return "已关 " + closed[0] + " 个闹钟。";
        }

        String label = t.length() > 30 ? t.substring(0, 30) : t;
        Matcher after = ALARM_AFTER.matcher(n);
        if (after.find()) {
            int minutes = clamp(Integer.parseInt(after.group(1)), 1, 24 * 60);
            SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.CHINA);
            hm.setTimeZone(CN_TZ);
            String at = hm.format(new Date(System.currentTimeMillis() + minutes * 60000L));
            String err = addAlarm(ctx, at, label, true, action);
            if (err != null) return err;
            String when = minutes >= 60
                    ? (minutes / 60) + " 小时" + (minutes % 60 == 0 ? "" : (minutes % 60) + " 分钟")
                    : minutes + " 分钟";
            if ("weather".equals(action)) return "好，" + when + "播天气。";
            return "好，" + when + "提醒你。";
        }

        Matcher at = ALARM_AT.matcher(n);
        if (at.find()) {
            int hour = Integer.parseInt(at.group(1));
            int minute = at.group(2) == null ? 0 : Integer.parseInt(at.group(2));
            if (hour > 23 || minute > 59) {
                return "几点？说「明天七点」。";
            }
            String hhmm = String.format(Locale.CHINA, "%02d:%02d", hour, minute);
            boolean daily = t.contains("每天") || t.contains("每早") || t.contains("每晚")
                    || t.contains("每周") || t.contains("定时");
            boolean tomorrow = t.contains("明天");
            String err = addAlarm(ctx, hhmm, label, !daily, action);
            if (err != null) return err;

            String when = daily ? "每天" : (tomorrow ? "明天" : "下一次");
            if ("weather".equals(action)) {
                Config cfg = Config.load(ctx);
                String city = (cfg.weatherCity == null || cfg.weatherCity.trim().isEmpty())
                        ? "默认城市（没设就是北京）" : cfg.weatherCity.trim();
                return "好，" + when + " " + hhmm + " 播天气。";
            }
            return "好，" + when + " " + hhmm + " 提醒你。";
        }
        return "几点？";
    }

    /** 追加一个闹钟，超过 50 个时不写。 */
    private static String addAlarm(Context ctx, String hhmm, String label, boolean once, String action) {
        try {
            final JSONObject a = new JSONObject();
            a.put("id", String.valueOf(System.currentTimeMillis()));
            a.put("time", hhmm);
            a.put("label", label);
            a.put("enabled", true);
            a.put("once", once);
            a.put("action", action == null ? "speak" : action);
            final boolean[] full = { false };
            Config.update(ctx, new Config.Mutator() {
                @Override
                public void apply(Config c) {
                    try {
                        JSONArray arr;
                        try {
                            arr = new JSONArray(c.alarms);
                        } catch (Throwable e) {
                            arr = new JSONArray();
                        }
                        if (arr.length() >= 50) {
                            full[0] = true;
                            return;
                        }
                        arr.put(a);
                        c.alarms = arr.toString();
                    } catch (Throwable e) {
                        Log.w(TAG, "添加闹钟失败: " + e);
                    }
                }
            });
            if (full[0]) return "闹钟满了，去网页删。";
            Log.i(TAG, "已添加闹钟 " + hhmm + " once=" + once + " action=" + a.optString("action") + " label=" + label);
            return null;
        } catch (Throwable e) {
            Log.w(TAG, "添加闹钟失败: " + e);
            return "闹钟没保存成。";
        }
    }

    // ---------------------------------------------------------------- 音乐

    /**
     * 不带歌名的「放点音乐」：优先从本地曲库随机挑一首。
     *
     * <p>为什么要有它：原来这类请求只会去扫音箱自己的 {@code /sdcard/Music}，
     * 配了 Songloft 反而被回一句「曲库为空」——用户没法理解为什么明明配了曲库还说空。
     *
     * @param ctx 上下文
     * @return 播报文本；Songloft 未启用时返回 null，由调用方退回设备本地目录
     */
    private static String playSomething(Context ctx) {
        Config cfg = Config.load(ctx);
        if (!SongloftProvider.isReady(cfg)) return null;
        JSONArray list = SongloftProvider.random(cfg, 5);
        if (list.length() == 0) {
            return "曲库是空的，先扫歌。";
        }
        // 随机起点 + 逐个尝试：某首文件被删或格式放不了，换下一首，不把失败甩给用户
        int start = RANDOM.nextInt(list.length());
        for (int k = 0; k < list.length(); k++) {
            JSONObject o = list.optJSONObject((start + k) % list.length());
            if (o == null) continue;
            String id = o.optString("id", "");
            String name = o.optString("name", "本地曲库");
            String artist = o.optString("artist", "");
            String url = SongloftProvider.songUrl(cfg, id);
            if (url == null) continue;
            MusicPlayer.get().play(url, name + (artist.isEmpty() ? "" : " - " + artist));
            lastQuery = "";
            lastQueryIsArtist = false;
            lastPlayedTitle = name;
            return "放《" + name + "》。";
        }
        return "取不到地址，稍后再试。";
    }

    private static String music(Context ctx, String t) {
        MusicPlayer player = MusicPlayer.get();
        // 带天气词的话（「听说明天下雨吗」）不是点歌，交给天气意图
        if (t.contains("天气") || t.contains("下雨") || t.contains("气温") || t.contains("多少度")) {
            return null;
        }
        // 「放点音乐 / 来点音乐」这种没有关键词的说法，别当成歌名去搜「点音乐」
        if (t.matches(".*(放|来|听)(点|些|一点)(音乐|歌|歌儿|曲子|歌儿).*")) {
            if (player.isPlaying()) return "正在播放。";
            String r = playSomething(ctx);
            return r != null ? r : player.toggle();
        }
        Matcher sleep = SLEEP_MIN.matcher(t);
        if (sleep.find() && (t.contains("停") || t.contains("关") || t.contains("睡眠") || t.contains("定时"))) {
            int minutes = Integer.parseInt(sleep.group(1));
            player.setSleep(minutes);
            return "好的，" + minutes + " 分钟后停。";
        }
        if (t.contains("取消定时") || t.contains("关闭定时") || t.contains("取消睡眠")) {
            player.setSleep(0);
            return "定时关了。";
        }
        if (t.contains("暂停") || t.contains("停止播放") || t.contains("停止音乐")) {
            // 唤醒时音乐已经被「让路」停了，这里要认这个状态：用户说「暂停」就是不想再恢复。
            if (player.isVoicePaused()) {
                player.pause();
                return "已暂停。";
            }
            // 没在播就说实话：听错一句话不该被包装成「已暂停播放」
            if (!player.isPlaying()) return "没在播放。";
            player.pause();
            return "已暂停。";
        }
        if (t.contains("下一首") || t.contains("切歌")) {
            if (player.step(1)) return "下一首。";
            // 设备本地目录为空时，从本地曲库再挑一首，别回「曲库为空」误导用户
            String next = playSomething(ctx);
            return next != null ? next : "曲库为空，请先把音乐放到音箱 /sdcard/Music 目录。";
        }
        if (t.contains("上一首")) {
            return player.step(-1) ? "已切到上一首。" : "曲库为空，请先把音乐放到音箱 /sdcard/Music 目录。";
        }
        if (t.contains("继续播放") || t.equals("播放") || t.equals("播放音乐")) {
            if (player.isPlaying()) return "正在播放。";
            // 手里有歌才是续播；没有才去曲库挑一首新的
            String r = player.hasTrack() ? null : playSomething(ctx);
            return r != null ? r : player.toggle();
        }
        if (t.contains("换一首") || t.contains("再来一首") || t.contains("再来一个")) {
            if (lastQuery.isEmpty()) {
                String r = playSomething(ctx);
                return r != null ? r : "还没点过歌，先说一首吧。";
            }
            return playSong(ctx, lastQuery, lastQueryIsArtist, lastPlayedTitle);
        }
        SongQuery q = parseSong(t);
        if (q != null) {
            return playSong(ctx, q.keyword, q.artistMode, null);
        }
        // 「随便放一首」：没有关键词，就从曲库随机放一首
        if (t.matches(".*(放|来|听)(一|两)?首(歌|音乐|曲子|歌儿)$") || t.contains("随便放一首")) {
            if (player.isPlaying()) return "正在播放，说「下一首」切歌。";
            String r = playSomething(ctx);
            return r != null ? r : player.toggle();
        }
        return null;
    }

    /** 点歌解析结果：关键词 + 是不是「某歌手的歌」。 */
    private static final class SongQuery {
        final String keyword;
        final boolean artistMode;

        SongQuery(String keyword, boolean artistMode) {
            this.keyword = keyword;
            this.artistMode = artistMode;
        }
    }

    /**
     * 从「放一首七里香」「播放一首周杰伦的歌」里取出关键词。
     *
     * @param t 用户原话
     * @return 解析结果；不像点歌返回 null
     */
    private static SongQuery parseSong(String t) {
        Matcher m = PLAY_SONG.matcher(t);
        if (!m.find()) return null;
        // 语音识别出来的句子通常带句号（「播放周杰伦的歌。」），
        // 不先去掉，后面的「…的歌」后缀就匹配不上，会被当成歌名去搜
        // （实测 2026-11-03 22:49 就是按歌名搜到了《周杰伦的歌》这首翻唱）。
        String s = stripTail(m.group(1) == null ? "" : m.group(1).trim());
        if (s.isEmpty()) return null;

        // 「某歌手的歌」：长后缀优先，避免「的歌曲」被「的歌」先吃掉
        boolean artist = false;
        for (String suffix : new String[]{"的歌儿", "的歌曲", "的歌"}) {
            if (s.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length()).trim();
                artist = true;
                break;
            }
        }
        if (s.isEmpty()) return null;
        if (s.equals("音乐") || s.equals("歌") || s.equals("歌曲") || s.equals("点歌")) return null;
        if (s.equals("播放") || s.startsWith("下一")) return null;
        return new SongQuery(s, artist);
    }

    /**
     * 去掉句尾的标点与语气词。
     *
     * <p>ASR 出的文本自带句号（「播放周杰伦的歌。」），而意图层用的是后缀匹配，
     * 一个句号就能让「…的歌」失效。
     *
     * @param s 原话
     * @return 去掉尾部标点后的文本
     */
    private static String stripTail(String s) {
        if (s == null) return "";
        String out = s.trim();
        while (out.length() > 0) {
            char c = out.charAt(out.length() - 1);
            if (c == '。' || c == '.' || c == '！' || c == '!' || c == '？' || c == '?'
                    || c == '，' || c == ',' || c == '、' || c == '~' || c == '～' || c == ' ') {
                out = out.substring(0, out.length() - 1);
            } else {
                break;
            }
        }
        return out.trim();
    }

    /**
     * 云音乐点歌：先搜网易云，搜不到再试 QQ 音乐（QQ 只搜索不需要登录）。
     *
     * @param ctx        上下文
     * @param keyword    歌名或歌手名
     * @param artistMode true 表示关键词是歌手，随机挑一首他的歌
     * @param avoidTitle 换一首时避开刚播过的那首
     * @return 播报文本
     */
    private static String playSong(Context ctx, String keyword, boolean artistMode, String avoidTitle) {
        Config cfg = Config.load(ctx);
        try {
            // 先做一次「本地容错匹配」：ASR 常把歌名听错一两个字（兮→汐），
            // 服务端 LIKE 搜索会直接落空，于是回落云端播了别人的翻唱（实测 2026-11-05 22:50）。
            // 本地库只有几百首，整库比较是毫秒级，见 SongloftProvider.all()。
            if (MusicSource.localEnabled(cfg)) {
                JSONObject fz = SongloftProvider.fuzzyMatch(cfg, keyword);
                if (fz != null) {
                    String canonical = fz.optString("name", "");
                    if (!canonical.isEmpty() && !canonical.equals(keyword)) {
                        Log.i(TAG, "点歌纠正: 「" + keyword + "」-> 「" + canonical + "」");
                        keyword = canonical;
                    }
                }
            }
            // 按优先级拿到所有有结果的音源：顺序由设置里的「点歌音源」决定
            // （默认 本地曲库 → 网易云 → QQ）。
            // 不能只取第一个：本地库可能有记录但文件没了，或格式这台机器放不了（24bit FLAC），
            // 那种情况必须还能落到云端音源，否则「优先」就变成了「独占」。
            List<MusicSource.Hit> hits = MusicSource.allWithResults(cfg, keyword);
            if (hits.isEmpty()) {
                return "没搜到「" + keyword + "」，换个说法。";
            }

            MusicSource loginSource = null;
            for (MusicSource.Hit hit : hits) {
                MusicSource source = hit.source;
                JSONArray list = hit.list;

                // 按歌手点歌时优先取「第一顺位就是这位歌手」的条目，退而求其次才用合唱/翻唱
                List<JSONObject> strict = new ArrayList<JSONObject>();
                List<JSONObject> loose = new ArrayList<JSONObject>();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject o = list.optJSONObject(i);
                    if (o == null) continue;
                    if (avoidTitle != null && avoidTitle.equals(o.optString("name", ""))) continue;
                    String artist = o.optString("artist", "");
                    if (!artistMode) {
                        strict.add(o);
                    } else if (leadArtistMatches(artist, keyword)) {
                        strict.add(o);
                    } else if (artist.toLowerCase().contains(keyword.toLowerCase())) {
                        loose.add(o);
                    }
                    if (strict.size() >= 10) break;
                }
                List<JSONObject> pool = !strict.isEmpty() ? strict : (!loose.isEmpty() ? loose : new ArrayList<JSONObject>());
                if (pool.isEmpty()) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject o = list.optJSONObject(i);
                        if (o != null) pool.add(o);
                    }
                }

                // 按歌名点歌时把「正版原名」排前面：稻香 > 稻香 (女声版) > 稻香remix
                if (!artistMode) {
                    final String kw = keyword;
                    Collections.sort(pool, new Comparator<JSONObject>() {
                        @Override
                        public int compare(JSONObject a, JSONObject b) {
                            return titleScore(b.optString("name", ""), kw)
                                    - titleScore(a.optString("name", ""), kw);
                        }
                    });
                }

                // 随机起点 + 逐个尝试取地址：会员曲目取不到就换下一首，不把失败甩给用户
                int tries = Math.min(pool.size(), 8);
                int start = RANDOM.nextInt(Math.max(1, tries));
                // 云端音源可能给到「试听片段」（实测放几句就 caching reached eos）。
                // 只要池子里有整首，就先跳过这些片段。
                boolean hasFull = false;
                if (source != MusicSource.SONGLOFT) {
                    for (JSONObject o : pool) {
                        if (o.optInt("durationMs", 0) >= 45000) {
                            hasFull = true;
                            break;
                        }
                    }
                }
                for (int k = 0; k < tries; k++) {
                    JSONObject pick = pool.get((start + k) % pool.size());
                    String id = pick.optString("id", "");
                    String name = pick.optString("name", keyword);
                    String artist = pick.optString("artist", "");
                    int durMs = pick.optInt("durationMs", 0);
                    if (hasFull && durMs > 0 && durMs < 45000) {
                        Log.i(TAG, "点歌跳过（试听片段 " + durMs + "ms）: " + name + " - " + artist);
                        continue;
                    }

                    String url = source.songUrl(cfg, id);
                    if (url == null) {
                        if (!source.hasLogin(cfg)) loginSource = source;
                        Log.i(TAG, "点歌跳过（取不到地址）: " + name + " - " + artist + " @" + source.label());
                        continue;
                    }

                    MusicPlayer.get().play(url, name + (artist.isEmpty() ? "" : " - " + artist));
                    lastQuery = keyword;
                    lastQueryIsArtist = artistMode;
                    lastPlayedTitle = name;
                    if (artistMode) {
                        return "放" + keyword + "的《" + name + "》。";
                    }
                    return "放《" + name + "》。";
                }
                // 这个音源没戏，按优先级换下一个
            }

            if (loginSource != null) {
                return "这几首需要登录，去网页贴 Cookie。";
            }
            return "取不到地址，换一首。";
        } catch (Throwable e) {
            Log.w(TAG, "点歌失败: " + e);
            return "点歌失败。";
        }
    }

    /**
     * 给歌名打分：原名最高，带版本后缀的次之，翻唱/现场再低。
     *
     * @param name    接口返回的歌名
     * @param keyword 用户说的歌名
     * @return 分数越大越像原版
     */
    private static int titleScore(String name, String keyword) {
        String n = name == null ? "" : name.trim().toLowerCase();
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        if (k.isEmpty()) return 0;
        int score;
        if (n.equals(k)) {
            score = 100;
        } else if (n.startsWith(k)) {
            score = 60 - Math.min(40, n.length() - k.length());
        } else if (n.contains(k)) {
            score = 30;
        } else {
            return 0;
        }
        for (String bad : new String[]{"live", "现场", "翻唱", "cover", "女声", "男声", "钢琴", "dj", "remix", "伴奏"}) {
            if (n.contains(bad)) {
                score -= 15;
                break;
            }
        }
        return score;
    }

    /**
     * 判断这首歌是不是「这位歌手本人」唱的。
     *
     * <p>网易云的 artists 字段是 {@code 周杰伦/阿信} 这种斜杠拼接，第一段才是主唱；
     * 只有第一段匹配才算，避免把「容祖儿/周杰伦」这种合唱当成周杰伦的歌。
     *
     * @param artistField 接口返回的歌手串
     * @param keyword     用户说的歌手
     * @return true 表示主唱就是这位
     */
    private static boolean leadArtistMatches(String artistField, String keyword) {
        if (artistField == null || keyword == null) return false;
        String a = artistField.toLowerCase().replace(" ", "");
        String k = keyword.toLowerCase().replace(" ", "");
        String lead = a.split("[/、,&]")[0];
        return lead.equals(k) || lead.contains(k);
    }

    // ---------------------------------------------------------------- 场景

    private static String scene(Context ctx, String t) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (t.contains("晚安") || t.contains("睡觉了") || t.equals("睡了")) {
            MusicPlayer.get().setSleep(30);
            setVolumePct(am, 20);
            return "晚安，30 分钟后停。";
        }
        if (t.contains("早上好") || t.contains("起床了") || t.equals("起床")) {
            setVolumePct(am, 50);
            StringBuilder sb = new StringBuilder("早上好。");
            SimpleDateFormat sdf = new SimpleDateFormat("HH点mm分", Locale.CHINA);
            sdf.setTimeZone(CN_TZ);
            sb.append("现在是 ").append(sdf.format(new Date())).append("。");
            Config cfg = Config.load(ctx);
            if (cfg.weatherKey != null && !cfg.weatherKey.trim().isEmpty()) {
                sb.append(WeatherService.query(cfg, WeatherService.extractCity("", cfg.weatherCity)));
            } else {
                sb.append("天气服务还没配置密钥，可以在网页设置页填上。");
            }
            return sb.toString();
        }
        return null;
    }

    private static void setVolumePct(AudioManager am, int pct) {
        if (am == null) return;
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(pct / 100f * max), 0);
    }

    // ---------------------------------------------------------------- 时间与天气

    private static String timeAndWeather(Context ctx, String t) {
        if (t.contains("几点") || t.contains("现在时间") || t.contains("北京时间") || t.contains("今天日期")
                || t.contains("今天几号") || t.contains("星期几")) {
            boolean wantDate = t.contains("日期") || t.contains("几号") || t.contains("星期");
            SimpleDateFormat sdf = new SimpleDateFormat(wantDate ? "M月d日 EEE HH:mm" : "HH:mm", Locale.CHINA);
            sdf.setTimeZone(CN_TZ);
            return "现在 " + sdf.format(new Date()) + "。";
        }
        if (t.contains("天气") || t.contains("气温") || t.contains("下雨") || t.contains("多少度")) {
            Config cfg = Config.load(ctx);
            String city = WeatherService.extractCity(t, cfg.weatherCity);
            // 明天/后天：走预报接口，而不是永远回「当前天气」（实测以前三句都是实时天气）
            int day = t.contains("后天") || t.contains("大后天") ? 2
                    : (t.contains("明天") || t.contains("明日") ? 1 : 0);
            return WeatherService.query(cfg, city, day);
        }
        return null;
    }

    // ---------------------------------------------------------------- 运维

    private static String systemOps(final Context ctx, String t) {
        if (t.equals("重启音箱") || t.equals("重启系统") || t.equals("系统重启")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try { Thread.sleep(1200); } catch (Throwable ignored) { }
                    DeviceOps.reboot();
                }
            }).start();
            return "正在重启。";
        }
        if (t.contains("清理垃圾") || t.contains("清理缓存") || t.contains("深度清理")
                || (t.contains("清理") && !t.contains("闹钟") && !t.contains("历史") && !t.contains("记录"))) {
            JSONObject cleanRes = DeviceOps.cleanSystem(ctx);
            String freed = cleanRes.optString("freedMb", "");
            return (freed.isEmpty() || freed.startsWith("0")) ? "清理完成。" : "已清理，释放 " + freed + "。";
        }
        if (t.contains("诊断") || t.contains("体检") || t.contains("系统状态")) {
            long[] mem = HttpServer.memInfoKb();
            long up = SystemClock.elapsedRealtime() / 1000;
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            int max = am == null ? 1 : Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int cur = am == null ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int pct = Math.round(cur * 100f / max);
            long usedMb = mem[0] >= 0 ? (mem[1] - mem[0]) / 1024 : -1;
            return "已开机 " + (up / 3600) + " 小时 " + ((up % 3600) / 60) + " 分钟，"
                    + "内存 " + (usedMb >= 0 ? usedMb + "M/" : "") + (mem[1] / 1024) + "M，"
                    + "音量 " + pct + "%，" + MusicPlayer.get().summary() + "。";
        }
        return null;
    }

    // ---------------------------------------------------------------- 音量

    /**
     * 调大音量的常见说法。
     *
     * <p>只列整词/短语，不做「含大就算大」这种松匹配：「讲个大笑话」也含「大」。
     * 这些是「用户怎么说」，不是协议值，放这里不违反「文案不进逻辑」——协议值与界面文案仍各在各处。
     */
    private static final String[] VOL_UP = {
            "调大音量", "调大声音", "增大音量", "加大音量", "提高音量", "调高音量", "放大音量",
            "音量调大", "音量调高", "音量再大", "音量大点", "音量大一点", "音量太小", "音量再大点",
            "声音大点", "声音大一点", "声音调大", "声音太小", "大声点", "大点声",
            "响一点", "响点", "太小声", "听不见"
    };

    /** 调小音量的常见说法。 */
    private static final String[] VOL_DOWN = {
            "调小音量", "调小声音", "减小音量", "降低音量", "调低音量", "放小音量",
            "音量调小", "音量调低", "音量小点", "音量小一点", "音量太大", "音量大了", "音量再小点",
            "声音小点", "声音小一点", "声音调小", "声音太大", "小声点", "小点声",
            "轻一点", "太吵", "太大声", "震耳"
    };

    private static String volume(Context ctx, String t) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return null;
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);

        Matcher m = VOL_NUM.matcher(t);
        if (m.find()) {
            int raw;
            try {
                raw = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return "音量数值太大，没有执行。";
            }
            // 超出 0~100 一律拒绝，绝不 clamp 成 100 执行：
            // 语音识别常把「八十五」错成「845」，夹到 100 会把音箱突然炸响（2026-11-15 实测）。
            if (raw > 100) {
                return "音量最大只有百分之百，" + raw + " 超出范围，没有执行。";
            }
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(raw / 100f * max), 0);
            return volumeReply(raw);
        }
        // 相对说法（「小一点/大一点/太高了」）优先：这时句子里也可能出现「一」，
        // 先让中文数字匹配会把「音量小一点」当成「音量调到 1%」（实测）。
        if (!hits(t, VOL_UP) && !hits(t, VOL_DOWN)) {
            // 语音识别经常给中文数字（「音量调到三十」），只认阿拉伯数字会漏
            Matcher mc = VOL_CN.matcher(t);
            if (mc.find()) {
                int v = cnToInt(mc.group(1));
                // 中文数字同样超出 100 就拒绝，不夹到 100
                if (v > 100) {
                    return "音量最大只有百分之百，" + v + " 超出范围，没有执行。";
                }
                if (v >= 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v / 100f * max), 0);
                    return volumeReply(v);
                }
            }
        }
        if (t.contains("静音")) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            return "已静音。";
        }
        // 先判「小」：避免「音量太大了」里的「大」被当成调大
        if (hits(t, VOL_DOWN)) {
            int target = Math.max(0, cur - 2);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            return "音量 " + Math.round(target * 100f / max) + "%。";
        }
        if (hits(t, VOL_UP)) {
            int target = Math.min(max, cur + 2);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            return "音量 " + Math.round(target * 100f / max) + "%。";
        }
        return null;
    }

    /**
     * 音量设定的播报文案。
     *
     * <p>只说用户关心的百分比。「硬件档位 4/15」这类排查信息只进日志，
     * 念出来只会让人觉得莫名其妙（实测被用户当场吐槽）。
     *
     * @param pct 0~100
     * @return 播报文本
     * @author xudong.hua,claude
     * @since 2026-11-05 22:40 星期四
     */
    private static String volumeReply(int pct) {
        if (pct <= 0) return "已静音。";
        if (pct >= 100) return "音量最大。";
        return "音量 " + pct + "%。";
    }

    private static boolean hits(String t, String[] words) {
        for (String w : words) {
            if (t.contains(w)) return true;
        }
        return false;
    }

    /** 把「二十分钟」「七点半」这类中文数字换成阿拉伯数字，便于统一正则处理。 */
    private static String normalizeNumbers(String t) {
        String s = t.replace("半个小时", "30分钟").replace("半小时", "30分钟")
                .replace("一个小时", "60分钟").replace("一小时", "60分钟")
                .replace("一个钟头", "60分钟").replace("一刻钟", "15分钟")
                .replace("点半", "点30");
        Matcher m = Pattern.compile("([零一二三四五六七八九十两]+)(?=\\s*(?:分钟|点|时))").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int v = cnToInt(m.group(1));
            m.appendReplacement(sb, v >= 0 ? String.valueOf(v) : Matcher.quoteReplacement(m.group(1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 中文数字转整数，支持 0~999（「三十」「一百」「一百二十」）。
     *
     * @param cn 例如 七 / 十 / 二十 / 二十三 / 两 / 一百 / 一百零五
     * @return 数值；无法识别返回 -1
     */
    private static int cnToInt(String cn) {
        if (cn == null || cn.isEmpty()) return -1;
        String digits = "零一二三四五六七八九";
        int hundred = cn.indexOf('百');
        if (hundred >= 0) {
            int high = 1;
            if (hundred > 0) {
                if (hundred != 1) return -1;
                char c = cn.charAt(0);
                high = c == '两' ? 2 : digits.indexOf(c);
                if (high < 0) return -1;
            }
            String tail = cn.substring(hundred + 1);
            int low;
            if (tail.isEmpty()) {
                low = 0;
            } else if (tail.startsWith("零")) {
                low = tail.length() == 2 ? digits.indexOf(tail.charAt(1)) : -1;
            } else {
                low = cnToInt(tail);
            }
            if (low < 0) return -1;
            return high * 100 + low;
        }
        int ten = cn.indexOf('十');
        if (ten < 0) {
            if (cn.length() != 1) return -1;
            if (cn.charAt(0) == '两') return 2;
            int i = digits.indexOf(cn.charAt(0));
            return i;
        }
        int high = 1;
        if (ten > 0) {
            char c = cn.charAt(0);
            int i = c == '两' ? 2 : digits.indexOf(c);
            if (i < 0) return -1;
            high = i;
        }
        int low = 0;
        if (ten + 1 < cn.length()) {
            int i = digits.indexOf(cn.charAt(ten + 1));
            if (i < 0) return -1;
            low = i;
        }
        return high * 10 + low;
    }

    // ---------------------------------------------------------------- 灯环（经 L1）

    private static String light(Context ctx, String t) {
        LightEffect effect = LightEffect.match(t);
        if (effect == null) return null;
        MainService svc = MainService.get();
        if (svc == null || !svc.l1Alive()) {
            return "灯环没连上 L1。";
        }
        // 记成常亮效果：这一轮结束后灯环回到它，而不是被思考灯/唤醒灯冲掉
        svc.session().setAmbient(effect);
        if (effect == LightEffect.OFF) return "灯关了。";
        if (effect == LightEffect.DORMANT) return "微光。";
        if (effect == LightEffect.LOADING) return "呼吸灯。";
        if (effect == LightEffect.SCAN) return "流光。";
        return "灯亮了。";
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
