package com.r1.brain;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 云音乐音源枚举：一处定义，搜索 / 取地址 / 登录态 / Cookie 落盘全部收敛在这里。
 *
 * <p>为什么要枚举而不是散落的字符串：
 * <ul>
 *   <li>{@code "netease"} / {@code "qq"} 是前后端约定的协议值，只允许出现在 {@link #id()} 里；</li>
 *   <li>「网易云」「QQ 音乐」是给人看的文案，只允许出现在 {@link #label()} 里；</li>
 *   <li>新增一个音源 = 在这里加一个枚举常量，编译器会强制补齐四个方法，
 *       不会再出现「加了源但某条分支漏改」的情况。</li>
 * </ul>
 *
 * <p>协议约束：{@link #id()} 会原样出现在 HTTP 参数 {@code source=} 与前端
 * {@code R1.cloudSource} 里，**改名等于改协议**，前端要同步。
 *
 * @author xudong.hua,deepseek
 * @since 2026-10-31 21:40 星期六
 */
public enum MusicSource {

    /** 网易云音乐：免版权曲目匿名可取地址，会员曲目需 Cookie。 */
    NETEASE("netease", "网易云") {
        @Override
        public JSONArray search(Config cfg, String keyword) {
            return NeteaseProvider.search(keyword, cfg.neteaseCookie);
        }

        @Override
        public String songUrl(Config cfg, String songId) {
            return NeteaseProvider.songUrl(songId, cfg.neteaseCookie);
        }

        @Override
        public boolean hasLogin(Config cfg) {
            return cfg.neteaseCookie != null && !cfg.neteaseCookie.trim().isEmpty();
        }

        @Override
        public void saveCookie(Config cfg, String cookie) {
            cfg.neteaseCookie = cookie;
        }
    },

    /** QQ 音乐：搜索匿名可用，取播放地址必须带登录 Cookie（vkey 需要 uin）。 */
    QQ("qq", "QQ 音乐") {
        @Override
        public JSONArray search(Config cfg, String keyword) {
            return QQMusicProvider.search(keyword);
        }

        @Override
        public String songUrl(Config cfg, String songId) {
            return QQMusicProvider.songUrl(songId, cfg.qqCookie);
        }

        @Override
        public boolean hasLogin(Config cfg) {
            return QQMusicProvider.hasLogin(cfg.qqCookie);
        }

        @Override
        public void saveCookie(Config cfg, String cookie) {
            cfg.qqCookie = cookie;
        }
    },

    /**
     * Songloft 本地曲库：只读用户自己上传到服务器的音乐。
     *
     * <p>地址与令牌没填全时 {@link #isEnabled} 返回 false，整条链路会直接跳过它，
     * 所以没部署本地库的用户行为完全不变。
     */
    SONGLOFT("songloft", "本地曲库") {
        @Override
        public JSONArray search(Config cfg, String keyword) {
            return SongloftProvider.search(cfg, keyword);
        }

        @Override
        public String songUrl(Config cfg, String songId) {
            return SongloftProvider.songUrl(cfg, songId);
        }

        @Override
        public boolean hasLogin(Config cfg) {
            return SongloftProvider.isReady(cfg);
        }

        @Override
        public void saveCookie(Config cfg, String cookie) {
            // 本地曲库没有 Cookie：地址与令牌走 /api/v1/config 的 songloftUrl / songloftToken。
        }

        @Override
        public boolean isEnabled(Config cfg) {
            return SongloftProvider.isReady(cfg);
        }
    };

    private final String id;
    private final String label;

    MusicSource(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /**
     * 搜索的默认尝试顺序：本地曲库 → 网易云 → QQ 音乐。
     *
     * <p>本地库排第一是产品决策而不是技术偶然：自己的文件音质最好、不依赖 CDN、
     * 不看会员脸色。搜不到（或未配置地址/令牌）才会落到云端音源，见 {@link #isEnabled}。
     *
     * <p>放在这里而不是调用方，是为了「新增音源时只改本文件」：
     * 想让它参与自动降级，往这个数组里加一个常量即可。
     */
    private static final MusicSource[] SEARCH_CHAIN = {SONGLOFT, NETEASE, QQ};

    /**
     * 当前生效的搜索顺序：在 {@link #SEARCH_CHAIN} 基础上按设置里的「点歌音源」过滤/排序。
     *
     * <p>用户在设置里勾选了哪些音源、以什么顺序，就只走哪些：
     * 只勾本地曲库就绝不碰云端；本地库 + 网易云就是「先本地、搜不到再云端」。
     * 一个都没勾（或配置被清空）时退回默认顺序，避免点了歌反而没声音。
     *
     * @param cfg 配置
     * @return 生效的音源顺序，永不为空
     * @author xudong.hua,claude
     * @since 2026-11-05 23:20 星期四
     */
    public static MusicSource[] chain(Config cfg) {
        List<MusicSource> out = new ArrayList<MusicSource>();
        String csv = cfg == null ? null : cfg.musicProviders;
        if (csv != null) {
            for (String part : csv.split(",")) {
                MusicSource s = of(part);
                if (s != null && !out.contains(s)) out.add(s);
            }
        }
        if (out.isEmpty()) {
            out.addAll(Arrays.asList(SEARCH_CHAIN));
        }
        return out.toArray(new MusicSource[0]);
    }

    /** 本地曲库是否在本次点歌的候选里（决定要不要做本地模糊匹配）。 */
    public static boolean localEnabled(Config cfg) {
        for (MusicSource s : chain(cfg)) {
            if (s == SONGLOFT) return true;
        }
        return false;
    }

    /**
     * 一次命中的搜索结果。
     *
     * @author xudong.hua,deepseek
     * @since 2026-10-31 23:05 星期六
     */
    public static final class Hit {
        /** 命中哪个音源。 */
        public final MusicSource source;
        /** 该音源返回的歌曲列表，保证非空。 */
        public final JSONArray list;

        Hit(MusicSource source, JSONArray list) {
            this.source = source;
            this.list = list;
        }
    }

    /**
     * 按 {@link #SEARCH_CHAIN} 顺序搜索，返回第一个有结果的音源。
     *
     * @param cfg     配置
     * @param keyword 关键词
     * @return 命中结果；全都没有结果返回 null
     */
    public static Hit firstWithResults(Config cfg, String keyword) {
        for (MusicSource s : chain(cfg)) {
            if (!s.isEnabled(cfg)) continue;
            JSONArray list = s.search(cfg, keyword);
            if (list != null && list.length() > 0) {
                return new Hit(s, list);
            }
        }
        return null;
    }

    /**
     * 按 {@link #SEARCH_CHAIN} 顺序搜出**所有**有结果的音源。
     *
     * <p>为什么不是「第一个命中就算赢」：本地库里可能有这首歌的记录但文件被删了、
     * 或者格式这台机器放不了（24bit FLAC），这时候必须还能落到网易云/QQ。
     * 调用方按返回顺序逐个尝试取地址，全失败才报错，这才是「优先」而不是「独占」。
     *
     * @param cfg     配置
     * @param keyword 关键词
     * @return 命中的音源列表（可能为空）
     */
    public static List<Hit> allWithResults(Config cfg, String keyword) {
        List<Hit> hits = new ArrayList<Hit>();
        for (MusicSource s : chain(cfg)) {
            if (!s.isEnabled(cfg)) continue;
            JSONArray list = s.search(cfg, keyword);
            if (list != null && list.length() > 0) {
                hits.add(new Hit(s, list));
            }
        }
        return hits;
    }

    /** 协议值（HTTP 参数、前端状态），不要用来做界面文案。 */
    public String id() {
        return id;
    }

    /** 给人看的名字（播报、提示），不要用来做逻辑判断。 */
    public String label() {
        return label;
    }

    /**
     * 按协议值找音源。
     *
     * @param id 协议值，如 {@code netease} / {@code qq}
     * @return 匹配的音源；未知返回 null
     */
    public static MusicSource of(String id) {
        if (id == null) return null;
        for (MusicSource s : values()) {
            if (s.id.equalsIgnoreCase(id.trim())) return s;
        }
        return null;
    }

    /**
     * 搜索歌曲。
     *
     * @param cfg     配置（取 Cookie）
     * @param keyword 关键词
     * @return 歌曲数组，失败返回空数组
     */
    public abstract JSONArray search(Config cfg, String keyword);

    /**
     * 取播放直链。
     *
     * @param cfg    配置（取 Cookie）
     * @param songId 歌曲 id
     * @return 直链；拿不到返回 null
     */
    public abstract String songUrl(Config cfg, String songId);

    /**
     * 该音源当前是否可用（已激活）。
     *
     * <p>默认 true：网易云/QQ 匿名就能搜，没有「未登录就跳过」的说法。
     * 需要地址或令牌才能工作的音源（本地曲库）覆盖它返回 false，链路就会跳过。
     *
     * @param cfg 配置
     * @return true 表示可以参与搜索
     */
    public boolean isEnabled(Config cfg) {
        return true;
    }

    /**
     * 是否已登录（有 Cookie）。
     *
     * @param cfg 配置
     * @return true 表示有登录态
     */
    public abstract boolean hasLogin(Config cfg);

    /**
     * 把 Cookie 写进配置（调用方负责 {@code cfg.save()}）。
     *
     * @param cfg    配置
     * @param cookie Cookie 原文
     */
    public abstract void saveCookie(Config cfg, String cookie);
}
