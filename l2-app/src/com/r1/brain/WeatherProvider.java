package com.r1.brain;

/**
 * 天气数据源策略接口：一个实现就是一个「天气来源」。
 *
 * <p>为什么这里值得抽接口：和风与 wttr.in 是两套完全不同的协议，还有「有 Key 用和风、
 * 没有或失败降级到 wttr」的选择逻辑。把选择逻辑写在调用方，每加一个源都要改一处 if；
 * 抽成 {@link WeatherService#CHAIN} 之后，加源 = 加一个类 + 往数组里加一行。
 *
 * <p>实现约定：
 * <ul>
 *   <li>能答就返回一句可直接播报的完整话术；</li>
 *   <li>答不了（没配 Key、城市查不到）返回 null，让门面继续试下一个源；</li>
 *   <li>网络/解析异常直接抛出，由门面记日志并降级 —— 不要自己吞掉再返回假数据。</li>
 * </ul>
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 22:50 星期六
 */
public interface WeatherProvider {

    /**
     * 数据源名称，只用于日志与排查。
     *
     * @return 名称，如 {@code qweather}
     */
    String name();

    /**
     * 当前配置下这个源能不能用（例如和风必须有 Key）。
     *
     * @param cfg 配置
     * @return true 表示可以尝试
     */
    boolean isAvailable(Config cfg);

    /**
     * 查询天气。
     *
     * @param cfg  配置
     * @param city 城市名（已归一化，非空）
     * @param day  0=今天，1=明天，2=后天
     * @return 可直接播报的一句话；这个源答不了返回 null
     * @throws Exception 网络或解析失败
     */
    String query(Config cfg, String city, int day) throws Exception;
}
