package com.r1.brain;

import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 天气查询门面：负责「从话里取城市」和「按优先级挑数据源」，具体怎么查交给策略实现。
 *
 * <p>{@link #CHAIN} 的顺序就是降级顺序：先和风（有 Key 才启用），再 wttr.in（免 Key 兜底）。
 * 新增一个天气源只需要实现 {@link WeatherProvider} 并往这个数组里加一项，
 * 调用方（意图路由、{@code GET /api/v1/weather}）一行都不用改。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 17:18 星期五
 */
public final class WeatherService {

    private static final String TAG = Brain.TAG;
    private static final Pattern CITY_PAT = Pattern.compile("([\u4e00-\u9fa5]{2,6}?(?:市|区|县|镇)?)(?:的)?天气");
    /** 默认城市：用户没说、配置也空时用它。 */
    private static final String DEFAULT_CITY = "北京";

    /**
     * 数据源优先级链。顺序即降级顺序，新增源只在这里加一行。
     *
     * @since 2026-10-31 22:58 星期六
     */
    private static final WeatherProvider[] CHAIN = {
            new QWeatherProvider(),
            new FreeWeatherProvider()
    };

    private WeatherService() {
    }

    /**
     * 从自然语言里取城市名；取不到就用配置城市，再没有就用默认城市。
     *
     * @param userText      用户原话，可为 null
     * @param configuredCity 设置页里填的城市，可为空
     * @return 城市名，保证非空
     */
    public static String extractCity(String userText, String configuredCity) {
        if (userText != null) {
            Matcher m = CITY_PAT.matcher(userText);
            if (m.find()) {
                String c = m.group(1).replace("今天", "").replace("明天", "").replace("后天", "")
                        .replace("大后天", "").replace("今日", "").replace("明日", "")
                        .replace("查询", "").replace("查", "").trim();
                if (!c.isEmpty() && !c.equals("本地") && !c.equals("当地")) {
                    return c;
                }
            }
        }
        if (configuredCity != null && !configuredCity.trim().isEmpty()) {
            return configuredCity.trim();
        }
        return DEFAULT_CITY;
    }

    /**
     * 一次天气查询的结果：文本 + 到底是谁答的。
     *
     * @author xudong.hua,deepseek
     * @since 2026-10-31 22:40 星期六
     */
    public static final class Report {
        /** 可直接播报的一句话。 */
        public final String text;
        /** 数据源：{@code qweather} / {@code wttr}；全失败为空串。 */
        public final String provider;
        /** true 表示用的是降级源（不是配置里优先的那个）。 */
        public final boolean degraded;
        /** 每个源这次的真实结果，例如 {@code qweather=HTTP 401：Key 无效}。 */
        public final String detail;

        Report(String text, String provider, boolean degraded, String detail) {
            this.text = text;
            this.provider = provider;
            this.degraded = degraded;
            this.detail = detail;
        }
    }

    /**
     * 查询天气：按 {@link #CHAIN} 顺序挑第一个能答的源。
     *
     * @param cfg        配置（和风 Key / 专属 Host）
     * @param targetCity 城市名，可为空
     * @return 可直接播报的一句话；全部失败时给出可读的兜底话术
     */
    public static String query(Config cfg, String targetCity) {
        return queryReport(cfg, targetCity).text;
    }
    /**
     * 查询天气并带上是哪个源答的（网页「测试天气」要显示这个，避免分不清是谁返回的）。
     *
     * @param cfg        配置
     * @param targetCity 城市名，可为空
     * @return 结果对象，永不返回 null
     */
    public static Report queryReport(Config cfg, String targetCity) {
        return queryReport(cfg, targetCity, null, 0);
    }

    /**
     * 查询指定日期的天气。
     *
     * @param cfg        配置
     * @param targetCity 城市
     * @param day        0=今天，1=明天，2=后天
     * @return 可直接播报的句子
     */
    public static String query(Config cfg, String targetCity, int day) {
        return queryReport(cfg, targetCity, null, day).text;
    }

    /**
     * 查询天气，可指定只用某一个源（网页上「测试和风」「测试通用源」就是靠它）。
     *
     * @param cfg        配置
     * @param targetCity 城市名，可为空
     * @param only       只测这个源（{@code qweather} / {@code wttr}）；null 或 {@code auto} 走优先级链
     * @return 结果对象，永不返回 null；{@link Report#detail} 里带每个源的真实结果
     */
    public static Report queryReport(Config cfg, String targetCity, String only) {
        return queryReport(cfg, targetCity, only, 0);
    }

    /**
     * 查询天气（带日期）。
     *
     * @param cfg        配置
     * @param targetCity 城市名，可为空
     * @param only       只测这个源；null 或 {@code auto} 走优先级链
     * @param day        0=今天，1=明天，2=后天
     * @return 结果对象
     */
    public static Report queryReport(Config cfg, String targetCity, String only, int day) {
        String city = (targetCity != null && !targetCity.trim().isEmpty()) ? targetCity.trim() : DEFAULT_CITY;
        boolean single = only != null && !only.trim().isEmpty() && !"auto".equals(only);
        String firstAvailable = "";
        StringBuilder detail = new StringBuilder();
        for (WeatherProvider provider : CHAIN) {
            if (single && !provider.name().equals(only)) continue;
            String label = label(provider.name());
            if (!provider.isAvailable(cfg)) {
                append(detail, label + "：未配置（和风需要 API Key）");
                continue;
            }
            if (firstAvailable.isEmpty()) firstAvailable = provider.name();
            long t0 = System.currentTimeMillis();
            try {
                String report = provider.query(cfg, city, day);
                long cost = System.currentTimeMillis() - t0;
                if (report != null && !report.isEmpty()) {
                    append(detail, label + "：成功（" + cost + "ms）");
                    boolean degraded = !single && !provider.name().equals(firstAvailable);
                    return new Report(report, provider.name(), degraded, detail.toString());
                }
                append(detail, label + "：无结果（" + cost + "ms）");
                Log.i(TAG, provider.name() + " 无结果，尝试下一个源");
            } catch (Throwable t) {
                long cost = System.currentTimeMillis() - t0;
                String msg = t.getMessage() == null ? t.toString() : t.getMessage();
                append(detail, label + "：失败（" + cost + "ms）" + msg);
                Log.w(TAG, provider.name() + " 查询失败，尝试下一个源: " + msg);
            }
        }
        if (single) {
            return new Report("", only, false, detail.toString());
        }
        String fallback = "【" + city + "天气】当前网络较忙，暂时无法获取实时天气数据，请稍后再试。";
        return new Report(fallback, "", true, detail.toString());
    }

    /** 每个源在页面上的显示名。 */
    public static String label(String name) {
        if ("qweather".equals(name)) return "和风天气";
        if ("wttr".equals(name)) return "通用源 wttr.in";
        return name;
    }

    private static void append(StringBuilder sb, String line) {
        if (sb.length() > 0) sb.append("；");
        sb.append(line);
    }
}
