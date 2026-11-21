package com.r1.brain;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 给大模型用的工具集：模型只负责「选工具 + 填参数」，执行全部在本地。
 *
 * <p>为什么这层很薄：音量解析、点歌模糊匹配、灯环、闹钟、天气这些动作本地意图已经写好了。
 * 这里不重写业务，而是把工具参数拼成一句「本地意图听得懂的话」再交给
 * {@link IntentRouter#handle}，保证「模型调的」和「用户说的」走同一条路径——
 * 行为一致，也不会出现两份实现慢慢分叉。
 *
 * <p>线程：由 {@link Brain} 在后台线程调用；其中会碰音频/播放器的操作各自会 post 到主线程。
 *
 * @author xudong.hua,claude
 * @since 2026-11-05 23:45 星期四
 */
public final class Tools {

    private static final String TAG = Brain.TAG;
    private static final TimeZone CN_TZ = TimeZone.getTimeZone("GMT+08:00");

    private Tools() {
    }

    /**
     * 一次工具执行结果。
     *
     * @author xudong.hua,claude
     * @since 2026-11-05 23:45 星期四
     */
    public static final class Result {
        public final boolean ok;
        /** 直接播报的固定文案（不再交给模型改写）。 */
        public final String speak;

        Result(boolean ok, String speak) {
            this.ok = ok;
            this.speak = speak == null ? "" : speak;
        }
    }

    private static Result ok(String speak) {
        return new Result(true, speak);
    }

    private static Result fail(String speak) {
        return new Result(false, speak);
    }

    /**
     * OpenAI 兼容的工具声明。
     *
     * <p>描述写清「什么时候用」比写清「参数含义」更重要：模型决定调不调，靠的就是 description。
     *
     * @return tools 数组
     * @author xudong.hua,claude
     * @since 2026-11-05 23:45 星期四
     */
    public static JSONArray schema() {
        JSONArray tools = new JSONArray();
        tools.put(tool("set_volume", "把音箱音量设为某个百分比。用户说「音量调到三十」「设成 40」时用。",
                new Params().num("pct", "0~100 的百分比", true).build()));
        tools.put(tool("adjust_volume", "在当前音量基础上调大或调小。用户说「大一点」「小一点」「太吵了」时用。",
                new Params().num("delta", "正数调大、负数调小，单位是百分比，范围 -50~50", true).build()));
        tools.put(tool("play_music", "按歌名或歌手点歌。用户说「放一首周杰伦的歌」「播放虞兮叹」时用。",
                new Params().text("query", "歌名或歌手", true).build()));
        tools.put(tool("control_music", "控制正在播放的音乐。",
                new Params().choice("action", new String[]{"pause", "resume", "next", "prev", "stop", "play"},
                        "pause=暂停，resume=继续，next=下一首，prev=上一首，stop=停止，play=随便放一首", true).build()));
        tools.put(tool("set_light", "设置音箱灯环效果。",
                new Params().choice("mode", new String[]{"off", "loading", "wakeup", "dormant", "scan"},
                        "off=关灯，loading=呼吸灯，wakeup=唤醒色，dormant=休眠微光，scan=流光", true).build()));
        tools.put(tool("set_alarm", "设置提醒或定时播报。",
                new Params()
                        .num("after_minutes", "多少分钟后触发（1~1440）", true)
                        .text("label", "提醒内容，可为空", false)
                        .choice("kind", new String[]{"speak", "weather"}, "speak=念提醒内容，weather=播报天气", false)
                        .build()));
        tools.put(tool("cancel_alarms", "取消所有闹钟与提醒。", new Params().build()));
        tools.put(tool("get_weather", "查询天气。",
                new Params().text("city", "城市名，可为空（空则用设置里的默认城市）", false).build()));
        tools.put(tool("get_device_status", "查询音箱当前状态（音量、播放、语音底座是否在线）。", new Params().build()));
        tools.put(tool("get_time", "查询当前日期与时间。", new Params().build()));
        tools.put(tool("stop_speaking", "立刻停止播报（用户说「别说了」「安静」）。", new Params().build()));
        tools.put(tool("set_receiver",
                "打开或关闭音箱的接收端开关（不是从音箱向外推流）。用户说「打开/关闭 DLNA」「打开/关闭投屏」「打开/关闭隔空播放或 AirPlay」「打开/关闭蓝牙」「进入蓝牙配对」时用。",
                new Params()
                        .choice("target", new String[]{"dlna", "airplay", "bluetooth", "discoverable"},
                                "dlna=自建 DLNA 投屏接收，airplay=原厂隔空播放接收，bluetooth=蓝牙适配器（打开时默认进入可发现），discoverable=进入或退出蓝牙配对/可发现", true)
                        .flag("on", "true=打开，false=关闭", true)
                        .build()));
        tools.put(tool("get_receivers", "查询 DLNA、隔空播放、蓝牙是否打开，以及蓝牙是否处于可发现/配对。", new Params().build()));
        return tools;
    }

    private static JSONObject tool(String name, String desc, JSONObject params) {
        JSONObject o = new JSONObject();
        try {
            o.put("type", "function");
            JSONObject f = new JSONObject();
            f.put("name", name);
            f.put("description", desc);
            f.put("parameters", params);
            o.put("function", f);
        } catch (Throwable t) {
            Log.w(TAG, "工具声明失败: " + name + " " + t);
        }
        return o;
    }

    /**
     * JSON Schema 的简易构造器：只覆盖本文件需要的 string / integer / enum。
     *
     * @author xudong.hua,claude
     * @since 2026-11-06 00:10 星期五
     */
    private static final class Params {
        private final JSONObject props = new JSONObject();
        private final JSONArray required = new JSONArray();

        Params num(String name, String desc, boolean req) {
            return add(name, "integer", desc, null, req);
        }

        Params text(String name, String desc, boolean req) {
            return add(name, "string", desc, null, req);
        }

        Params choice(String name, String[] values, String desc, boolean req) {
            return add(name, "string", desc, values, req);
        }

        Params flag(String name, String desc, boolean req) {
            return add(name, "boolean", desc, null, req);
        }

        private Params add(String name, String type, String desc, String[] values, boolean req) {
            try {
                JSONObject one = new JSONObject();
                one.put("type", type);
                one.put("description", desc);
                if (values != null && values.length > 0) {
                    JSONArray en = new JSONArray();
                    for (String v : values) en.put(v);
                    one.put("enum", en);
                }
                props.put(name, one);
                if (req) required.put(name);
            } catch (Throwable t) {
                Log.w(TAG, "工具参数失败: " + t);
            }
            return this;
        }

        JSONObject build() {
            JSONObject p = new JSONObject();
            try {
                p.put("type", "object");
                p.put("properties", props);
                if (required.length() > 0) p.put("required", required);
            } catch (Throwable t) {
                Log.w(TAG, "工具参数收尾失败: " + t);
            }
            return p;
        }
    }

    /**
     * 执行一个工具。
     *
     * @param ctx  上下文
     * @param name 工具名
     * @param args 参数（可为空对象）
     * @return 执行结果与播报文案
     * @author xudong.hua,claude
     * @since 2026-11-05 23:45 星期四
     */
    public static Result call(Context ctx, String name, JSONObject args) {
        if (name == null) return fail("没听清。");
        JSONObject a = args == null ? new JSONObject() : args;
        try {
            if ("set_volume".equals(name)) {
                Integer pct0 = intArg(a, "pct");
                if (pct0 == null) return fail("调到多少？");
                // 超出 0~100 拒绝，不 clamp：模型偶发把「八十五」填成 845，夹到 100 会炸响
                if (pct0 < 0 || pct0 > 100) return fail("音量只能在 0 到 100 之间，没有执行。");
                return route(ctx, "音量调到" + pct0);
            }
            if ("adjust_volume".equals(name)) {
                Integer delta0 = intArg(a, "delta");
                if (delta0 == null || delta0 == 0) return fail("调大还是调小？");
                int delta = clamp(delta0, -50, 50);
                int cur = currentPct(ctx);
                int target = clamp(cur + delta, 0, 100);
                // 「小一点」不该把音量一降到静音：小幅下调最多落到 5%；真要静音有别的话术
                if (target == 0 && delta < 0 && delta > -40) target = 5;
                return route(ctx, "音量调到" + target);
            }
            if ("play_music".equals(name)) {
                String q = a.optString("query", "").trim();
                return route(ctx, q.isEmpty() ? "播放音乐" : "播放" + q);
            }
            if ("control_music".equals(name)) {
                String action = a.optString("action", "");
                if ("pause".equals(action)) return route(ctx, "暂停");
                if ("resume".equals(action)) return route(ctx, "继续播放");
                if ("next".equals(action)) return route(ctx, "下一首");
                if ("prev".equals(action)) return route(ctx, "上一首");
                if ("stop".equals(action)) return route(ctx, "停止播放");
                if ("play".equals(action)) return route(ctx, "播放音乐");
                return fail("不支持的播放操作。");
            }
            if ("set_light".equals(name)) {
                String mode = a.optString("mode", "");
                LightEffect e = LightEffect.of(mode);
                if (e == null) return fail("没有这个灯效。");
                MainService svc = MainService.get();
                if (svc == null || !svc.l1Alive()) return fail("灯环没连上 L1。");
                boolean sent = svc.session().setAmbient(e);
                return sent ? ok(e == LightEffect.OFF ? "灯环已关闭。" : "灯环已切到" + e.label() + "。")
                        : fail("灯环指令没发出去。");
            }
            if ("set_alarm".equals(name)) {
                Integer min0 = intArg(a, "after_minutes");
                if (min0 == null) return fail("几分钟后？");
                int minutes = clamp(min0, 1, 1440);
                boolean weather = "weather".equals(a.optString("kind", "speak"));
                String label = a.optString("label", "").trim();
                // 注意本地闹钟意图的触发条件是「闹钟/提醒/叫我」；只说「播报天气」匹配不到，
                // 所以天气也拼成「N分钟后提醒我播报天气」，由 action=weather 决定播什么。
                String text = minutes + "分钟后" + (weather ? "提醒我播报天气"
                        : (label.isEmpty() ? "提醒我" : "提醒我" + label));
                return route(ctx, text);
            }
            if ("cancel_alarms".equals(name)) {
                return route(ctx, "取消闹钟");
            }
            if ("get_weather".equals(name)) {
                String city = a.optString("city", "").trim();
                return ok(WeatherService.query(Config.load(ctx), city));
            }
            if ("get_device_status".equals(name)) {
                return ok(deviceStatus(ctx));
            }
            if ("get_time".equals(name)) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.CHINA);
                sdf.setTimeZone(CN_TZ);
                return ok("现在 " + sdf.format(new Date()) + "。");
            }
            if ("stop_speaking".equals(name)) {
                MainService svc = MainService.get();
                if (svc != null && svc.speech() != null) svc.speech().stop();
                L1Bridge.stopSpeak(ctx);
                return ok("好。");
            }
            if ("set_receiver".equals(name)) {
                String target = a.optString("target", "").trim();
                String onRaw = a.optString("on", "").trim();
                if (target.isEmpty()) return fail("开哪一项？");
                boolean on = "true".equalsIgnoreCase(onRaw) || "1".equals(onRaw) || "on".equalsIgnoreCase(onRaw);
                if ("false".equalsIgnoreCase(onRaw) || "0".equals(onRaw) || "off".equalsIgnoreCase(onRaw)) on = false;
                // 也接受布尔
                if (a.has("on") && (a.opt("on") instanceof Boolean)) on = a.optBoolean("on");
                String phrase;
                if ("dlna".equals(target) || "cast".equals(target)) phrase = (on ? "打开" : "关闭") + "DLNA";
                else if ("airplay".equals(target)) phrase = (on ? "打开" : "关闭") + "隔空播放";
                else if ("bluetooth".equals(target)) phrase = (on ? "打开" : "关闭") + "蓝牙";
                else if ("discoverable".equals(target) || "pairing".equals(target) || "pair".equals(target)) {
                    phrase = on ? "进入蓝牙配对" : "退出蓝牙配对";
                } else {
                    return fail("不会这项开关。");
                }
                return route(ctx, phrase);
            }
            if ("get_receivers".equals(name)) {
                return route(ctx, "查一下投屏和蓝牙状态");
            }
        } catch (Throwable t) {
            Log.w(TAG, "工具执行失败: " + name + " " + t);
            return fail("没做成。");
        }
        return fail("不会这个。");
    }

    /** 把工具参数变成一句本地意图听得懂的话，复用既有实现。 */
    private static Result route(Context ctx, String phrase) {
        String reply = IntentRouter.handle(ctx, phrase);
        if (reply == null || reply.trim().isEmpty()) {
            Log.i(TAG, "工具落到本地意图未命中: " + phrase);
            return fail("没做成。");
        }
        return ok(reply);
    }

    /** 取整数参数；缺失或不是数字返回 null（不要用 optInt 的默认值，那会把「没给」当成 0）。 */
    private static Integer intArg(JSONObject a, String key) {
        if (a == null || !a.has(key)) return null;
        Object v = a.opt(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Throwable t) {
            return null;
        }
    }

    private static int currentPct(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return 50;
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        return Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
    }

    private static String deviceStatus(Context ctx) {
        MainService svc = MainService.get();
        MusicPlayer p = MusicPlayer.get();
        return "音量 " + currentPct(ctx) + "%，" + p.summary()
                + (svc != null && svc.l1Alive() ? "，语音底座在线。" : "，语音底座没连上。");
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
