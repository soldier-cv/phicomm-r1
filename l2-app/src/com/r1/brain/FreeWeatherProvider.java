package com.r1.brain;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * 免 Key 开放天气源（wttr.in）：返回一行自然语言天气，用作零配置与降级兜底。
 *
 * <p>三个实测坑，都写在这里免得下次重踩：
 * <ul>
 *   <li>{@code format} 里的 {@code %C/%t/%h/%w} 必须整体百分号编码，否则
 *       {@code java.net.URL} 直接抛 {@code Invalid % sequence}；</li>
 *   <li>必须用 curl 风格的 User-Agent，否则返回 HTML 页面而不是单行文本；</li>
 *   <li>这台机器是 Android 5.1（2015 年的根证书），**不认 wttr.in 现在用的
 *       Let's Encrypt 根**，HTTPS 会抛 {@code Trust anchor ... not found}。
 *       所以先试 HTTPS，握手失败再退回 HTTP。天气是公开数据，
 *       这个降级可以接受；将来系统根证书能更新时自然就走回 HTTPS。</li>
 * </ul>
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 22:56 星期六
 */
public final class FreeWeatherProvider implements WeatherProvider {

    private static final String TAG = Brain.TAG;
    /** wttr.in 只对类 curl 的 UA 返回纯文本。 */
    private static final String CURL_UA = "curl/7.68.0";
    private static final int MAX_BYTES = 64 * 1024;
    /** 单行天气模板。 */
    private static final String FORMAT = "%C %t 湿度%h 风向%w";

    @Override
    public String name() {
        return "wttr";
    }

    @Override
    public boolean isAvailable(Config cfg) {
        return true;
    }

    @Override
    public String query(Config cfg, String city, int day) throws Exception {
        if (day > 0) {
            return forecastDay(city, day);
        }
        String path = "/" + URLEncoder.encode(city, "UTF-8")
                + "?format=" + URLEncoder.encode(FORMAT, "UTF-8") + "&lang=zh-cn";

        Http.Response res;
        try {
            res = fetch("https://wttr.in" + path);
        } catch (IOException httpsError) {
            // 老系统根证书不认 Let's Encrypt，退回 HTTP；再失败就把原始错误抛出去
            Log.w(TAG, "wttr HTTPS 失败（多为根证书过旧），改用 HTTP: " + httpsError);
            res = fetch("http://wttr.in" + path);
        }
        if (!res.ok()) throw new IllegalStateException("HTTP " + res.code);

        String text = res.text().trim();
        if (text.isEmpty() || text.contains("Unknown location") || text.contains("<html>")) {
            return null;
        }
        return "【" + city + "实时天气】" + text + "。";
    }

    /**
     * 明天/后天的预报：wttr.in 的 `?format=j1` 带 3 天数据（`?1` 参数会被忽略，实测）。
     *
     * @param city 城市
     * @param day  1=明天，2=后天
     * @return 播报句；数据不足返回 null
     * @author xudong.hua,claude
     * @since 2026-11-07 11:55 星期六
     */
    private static String forecastDay(String city, int day) throws Exception {
        String path = "/" + URLEncoder.encode(city, "UTF-8") + "?format=j1";
        Http.Response res;
        try {
            res = fetch("https://wttr.in" + path);
        } catch (IOException httpsError) {
            res = fetch("http://wttr.in" + path);
        }
        if (!res.ok()) throw new IllegalStateException("HTTP " + res.code);
        JSONObject j = new JSONObject(res.text());
        JSONArray w = j.optJSONArray("weather");
        if (w == null || w.length() <= day) return null;
        JSONObject d = w.getJSONObject(day);
        String desc = "";
        JSONArray hourly = d.optJSONArray("hourly");
        if (hourly != null && hourly.length() > 4) {
            JSONObject h = hourly.optJSONObject(4);
            if (h != null) {
                JSONArray wd = h.optJSONArray("weatherDesc");
                if (wd != null && wd.length() > 0) {
                    desc = wd.optJSONObject(0).optString("value", "");
                }
            }
        }
        return "【" + QWeatherProvider.dayWord(day) + city + "天气】" + zh(desc)
                + "，" + d.optString("mintempC", "--") + "~" + d.optString("maxtempC", "--") + "℃。";
    }

    /** wttr 的天气描述是英文，转成能念的中文（未收录的原样返回）。 */
    private static String zh(String en) {
        if (en == null) return "";
        String e = en.toLowerCase(Locale.US);
        if (e.contains("sunny") || e.contains("clear")) return "晴";
        if (e.contains("partly")) return "多云";
        if (e.contains("cloud") || e.contains("overcast")) return "阴";
        if (e.contains("drizzle")) return "小雨";
        if (e.contains("rain") || e.contains("shower")) return "有雨";
        if (e.contains("snow") || e.contains("sleet")) return "有雪";
        if (e.contains("thunder")) return "雷阵雨";
        if (e.contains("fog") || e.contains("mist")) return "有雾";
        return en;
    }

    private static Http.Response fetch(String url) throws IOException {
        return Http.request("GET", url, null, null,
                Http.headers("User-Agent", CURL_UA, "Accept", "*/*"),
                5000, 5000, MAX_BYTES);
    }
}
