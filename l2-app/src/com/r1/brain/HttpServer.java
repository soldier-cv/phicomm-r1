package com.r1.brain;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import com.r1.brain.dlna.DlnaRenderer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 斐讯 R1 极简 HTTP 控制台与 REST API 服务（监听 8686 端口，时间统一采用北京时间 GMT+08:00）。
 *
 * 路由列表：
 *   GET  /                       → 按终端 UA 自动跳转 /desktop/home.html 或 /mobile/home.html（保留 ?token=）
 *   GET  /desktop/*.html         → 桌面端 4 个功能页面 (home / chat / music / settings)
 *   GET  /mobile/*.html          → 移动端 4 个功能页面 (home / chat / music / settings)
 *   GET  /assets/*               → 公共字体、样式与引擎脚本 (fonts.css / blackgold.css / clear.css / r1-engine.js / r1-theme.js)
 *   GET  /api/v1/status          → 音箱真实硬件遥测、音量、蓝牙、网络与运行状态
 *   GET  /api/v1/history         → 读取对话历史列表（最近 50 条）
 *   DELETE /api/v1/history       → 清空对话历史列表
 *   POST /api/v1/chat            → 发送文本指令或问答，返回执行结果并记录北京时间
 *   GET  /api/v1/config          → 读取音箱持久化配置（敏感密钥自动脱敏）
 *   POST /api/v1/config          → 保存音箱持久化配置（LLM / TTS / 智能家居 / 天气 / 闹钟）
 *   POST /api/v1/token           → 设置网页访问令牌（首次开放，设置后强制校验）
 *   GET  /api/v1/config/export   → 导出完整配置 JSON（含密钥，用于换机迁移）
 *   POST /api/v1/config/import   → 导入配置 JSON（令牌不导入，避免把自己锁在外面）
 *   GET  /api/v1/music/list      → 扫描音箱本地存储 (/sdcard/Music 等) 音频曲库列表
 *   GET  /api/v1/music/lyrics    → 读取与音频同目录同名的 .lrc 歌词文件
 *   GET  /api/v1/player          → 查询音箱本机播放器状态
 *   POST /api/v1/player/*        → 控制音箱本机播放器 (play / toggle / pause / stop / next / prev / seek / mode / sleep)
 *   POST /api/v1/bluetooth       → 开关音箱硬件蓝牙
 *   GET  /api/v1/logs            → 读取音箱实时 Android logcat 日志
 *   POST /api/v1/tts             → 触发音箱 TTS 语音播报
 *   POST /api/v1/tts/stop        → 停止 TTS 语音播报
 *   GET  /api/v1/tts/status      → 查询 TTS 引擎状态
 *   GET/POST /api/v1/volume      → 读取或调节音箱硬件 STREAM_MUSIC 主音量
 *   GET  /api/v1/apps            → 获取音箱已安装应用列表及运行状态
 *   POST /api/v1/apps/install    → 极低内存流式上传 APK 并通过回环 ADB 静默安装/OTA自我升级
 *   POST /api/v1/apps/uninstall  → 静默卸载指定应用 (pm uninstall)
 *   POST /api/v1/apps/stop       → 强行停止指定应用 (am force-stop)
 *   POST /api/v1/system/reboot   → 重启斐讯 R1 音箱系统
 *   POST /api/v1/system/clean    → 清理临时安装包与音频缓存
 *   GET  /api/v1/weather         → 查询城市天气（免 Key 开放源 + 和风天气双通道）
 *   POST /api/v1/music/cookie          → 保存网易云 / QQ 音乐的登录 Cookie
 *   GET  /api/v1/music/search          → 云音乐搜索（source=netease|qq）
 *   POST /api/v1/music/play            → 取播放地址并播放（source=netease|qq）
 *   GET  /api/v1/l1/light              → 经 L1 控制灯环效果（mode=off|loading|wakeup|dormant|net）
 *   GET  /api/v1/l1/light/state        → 最近一次灯环指令与回执
 *   GET  /api/v1/cast/status           → DLNA / AirPlay 端口是否在听
 *   POST /api/v1/cast/wake             → 唤醒原厂播放器主服务后再探测（没有开关）
 *
 * 鉴权：所有 /api/ 请求必须携带令牌（X-Token / Authorization: Bearer / ?token= /
 * 登录过 /?token= 后下发的 r1_token Cookie / JSON body 的 token 字段）。
 * 静态页面本身公开，方便浏览器先打开页面再补令牌。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 17:22 星期五
 */
public class HttpServer {

    private static final String TAG = Brain.TAG;
    public static final int PORT = 8686;
    private static final TimeZone CN_TZ = TimeZone.getTimeZone("GMT+08:00");
    private static final int MAX_BODY = 64 * 1024;
    /**
     * /api/v1/logs 的返回上限。
     *
     * <p>这里限的是「一次返回多少」，不是磁盘占用：logcat 是系统环形缓冲，自己会覆盖旧行。
     * 所以宁可给足（默认 200 行、单条 1500 字），别让排查时看不到堆栈；
     * 调用方还能用 {@code ?lines=} 自己在 20~400 之间要。
     */
    private static final int LOG_LINES_DEFAULT = 200;
    private static final int LOG_LINES_MAX = 400;
    private static final int LOG_MSG_MAX = 1500;
    /** 同一时刻只允许一个 logcat 读取，避免网页反复刷新时堆进程。 */
    private final AtomicBoolean logBusy = new AtomicBoolean(false);
    /** ASR 试听上传要带一段 WAV 的 base64（10 秒 16k 单声道约 320KB，编码后约 430KB）。 */
    private static final int ASR_TEST_BODY = 768 * 1024;
    private static final int MAX_CHAT_CHARS = 500;
    private static final int WORKERS = 8;
    private static final long MUSIC_CACHE_MS = 5000L;
    /** 静态资源缓存 5 分钟：内容不变，避免每次刷新都在 20~50KB/s 链路上重传约 300KB。 */
    private static final String STATIC_CACHE = "public, max-age=300";
    private static final Pattern LOGCAT_LINE = Pattern.compile(
            "^(\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\s+([VDIWEF])/([^(]+?)\\(\\s*\\d+\\):\\s?(.*)$");

    /** 仅用于把当前请求路径带进错误日志，不影响业务。 */
    private static final ThreadLocal<String> CURRENT_REQUEST = new ThreadLocal<String>();

    private final Context ctx;
    private final Brain brain;
    private final SpeechEngine speech;
    private ServerSocket server;
    private volatile boolean running;
    /**
     * 有界工作队列：慢接口（大模型 20s、TTS 15s、logcat 4s）占满 8 个线程后，
     * 无界队列会让请求无限堆积直到 OOM。这里最多再排 32 个，超出直接回 503。
     */
    private volatile ThreadPoolExecutor workers = newWorkers();
    private final Object assetLock = new Object();
    private final Map<String, byte[]> assetCache = new HashMap<String, byte[]>();
    private long assetCacheBytes = 0;
    private boolean assetCacheWarned = false;
    private volatile List<File> musicCache;
    private volatile long musicCacheAt = 0;
    private String token = "";
    /** 令牌是否由用户设置过。false 时控制台开放访问（首次使用）。 */
    private volatile boolean tokenUserSet = false;

    public HttpServer(Context ctx, Brain brain, SpeechEngine speech) {
        this.ctx = ctx.getApplicationContext();
        this.brain = brain;
        this.speech = speech;
    }

    private static ThreadPoolExecutor newWorkers() {
        return new ThreadPoolExecutor(WORKERS, WORKERS, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(32),
                new ThreadFactory() {
                    private final AtomicInteger seq = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "http-worker-" + seq.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized void start() {
        if (running) return;
        Config c0 = Config.load(ctx);
        token = c0.token == null ? "" : c0.token.trim();
        tokenUserSet = c0.tokenUserSet && !token.isEmpty();
        if (tokenUserSet) {
            Log.i(TAG, "控制台令牌已启用（用户已设置）");
        } else {
            Log.i(TAG, "控制台未设令牌：首次使用开放访问，请到设置页创建访问令牌");
            Log.i(TAG, "控制台地址: http://<音箱IP>:" + PORT + "/");
        }
        // stop() 会 shutdownNow 线程池；同一个实例再次 start 时必须重建，否则提交直接抛拒绝。
        if (workers.isShutdown()) workers = newWorkers();
        running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "http-accept").start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Throwable ignored) {
        }
        workers.shutdownNow();
        Log.i(TAG, "HTTP stopped");
    }

    private void acceptLoop() {
        ServerSocket ss = null;
        for (int attempt = 1; attempt <= 5 && running; attempt++) {
            try {
                ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(PORT), 50);
                break;
            } catch (Throwable t) {
                Log.w(TAG, "HTTP 绑定 " + PORT + " 失败（第 " + attempt + " 次）: " + t);
                try { if (ss != null) ss.close(); } catch (Throwable ignored) { }
                ss = null;
                try { Thread.sleep(2000); } catch (Throwable ignored) { }
            }
        }
        if (ss == null) {
            Log.e(TAG, "HTTP 启动失败，端口 " + PORT + " 不可用");
            running = false;
            return;
        }
        server = ss;
        Log.i(TAG, "HTTP listening on " + PORT);
        while (running) {
            try {
                final Socket s = server.accept();
                try {
                    workers.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                handle(s);
                            } catch (Throwable t) {
                                Log.w(TAG, "handle error " + CURRENT_REQUEST.get() + ": " + t);
                            } finally {
                                try { s.close(); } catch (Throwable ignored) { }
                                CURRENT_REQUEST.remove();
                            }
                        }
                    });
                } catch (RejectedExecutionException busy) {
                    Log.w(TAG, "HTTP 工作队列已满，拒绝连接");
                    try {
                        OutputStream out = new BufferedOutputStream(s.getOutputStream());
                        send(out, 503, "application/json; charset=utf-8",
                                jsonError("服务器繁忙，请稍后再试").getBytes("UTF-8"));
                        out.flush();
                    } catch (Throwable ignored) {
                    } finally {
                        try { s.close(); } catch (Throwable ignored) { }
                    }
                }
            } catch (Throwable t) {
                if (running) Log.w(TAG, "accept: " + t);
            }
        }
    }

    private void handle(Socket s) throws Exception {
        s.setSoTimeout(15000);
        InputStream in = new BufferedInputStream(s.getInputStream());
        OutputStream out = new BufferedOutputStream(s.getOutputStream());

        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) return;

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return;
        String method = parts[0];
        String path = parts[1];
        // 代理/部分客户端会用绝对形式：GET http://host:port/path HTTP/1.1
        if (path.startsWith("http://") || path.startsWith("https://")) {
            int scheme = path.indexOf("://");
            int slash = path.indexOf('/', scheme + 3);
            path = slash < 0 ? "/" : path.substring(slash);
        }

        int contentLength = 0;
        String hToken = null;
        String authHeader = null;
        String cookieHeader = null;
        String userAgent = "";
        String transferEncoding = null;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String name = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if (name.equalsIgnoreCase("Content-Length")) {
                    try { contentLength = Integer.parseInt(value); } catch (Throwable ignored) { }
                } else if (name.equalsIgnoreCase("X-Token")) {
                    hToken = value;
                } else if (name.equalsIgnoreCase("Authorization")) {
                    authHeader = value;
                } else if (name.equalsIgnoreCase("Cookie")) {
                    cookieHeader = value;
                } else if (name.equalsIgnoreCase("User-Agent")) {
                    userAgent = value;
                } else if (name.equalsIgnoreCase("Transfer-Encoding")) {
                    transferEncoding = value;
                }
            }
        }

        if ("OPTIONS".equalsIgnoreCase(method)) {
            // 204 不应带 Content-Type/body；CORS 头在 sendNoContent 里统一给。
            sendNoContent(out);
            out.flush();
            return;
        }

        String rawPath = path;
        String query = "";
        int q = path.indexOf('?');
        if (q >= 0) {
            query = path.substring(q + 1);
            rawPath = path.substring(0, q);
        }

        // 大文件（APK 安装包）极低内存纯流式上传，避免大数组 OOM
        if ("POST".equalsIgnoreCase(method) && "/api/v1/apps/install".equals(rawPath)) {
            CURRENT_REQUEST.set("POST /api/v1/apps/install");
            if (!checkToken(query, hToken, authHeader, cookieHeader, null)) {
                send(out, 401, "application/json; charset=utf-8",
                        jsonError("未授权：请提供有效的访问令牌").getBytes("UTF-8"));
                out.flush();
                return;
            }
            // 分块续传：这条链路只有 20~50KB/s，单次传 30MB 必然被读超时打断。
            // 客户端按 offset 分块上传，最后一块到达后才触发安装。
            s.setSoTimeout(180000);
            if (contentLength <= 0 || contentLength > 8 * 1024 * 1024) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("单块大小无效（1 字节 ~ 8MB）").getBytes("UTF-8"));
                out.flush();
                return;
            }
            int offset = 0;
            long total = -1;
            for (String kv : query.split("&")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String k = kv.substring(0, eq);
                String v = kv.substring(eq + 1);
                try {
                    if ("offset".equals(k)) offset = Integer.parseInt(v);
                    else if ("total".equals(k)) total = Long.parseLong(v);
                } catch (Throwable ignored) { }
            }
            if (offset < 0) offset = 0;
            if (total <= 0) total = (long) offset + contentLength;

            // 注意：不能写 /data/local/tmp —— 那是 shell 的目录（0771），
            // 普通应用 open 会直接 EACCES。必须落在应用自己可写、且 shell 能读的位置，
            // 这样随后 pm install -r 才读得到。
            File upDir = ctx.getExternalFilesDir(null);
            if (upDir == null) upDir = ctx.getCacheDir();
            if (!upDir.exists()) upDir.mkdirs();
            File tmpApk = new File(upDir, "upload_install.apk");

            boolean readOk = true;
            int written = 0;
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(tmpApk, "rw");
                raf.seek(offset);
                raf.setLength(offset);
                byte[] streamBuf = new byte[8192];
                int remaining = contentLength;
                while (remaining > 0) {
                    int toRead = Math.min(streamBuf.length, remaining);
                    int n = in.read(streamBuf, 0, toRead);
                    if (n < 0) {
                        readOk = false;
                        break;
                    }
                    raf.write(streamBuf, 0, n);
                    written += n;
                    remaining -= n;
                }
            } catch (Throwable t) {
                readOk = false;
                Log.w(TAG, "upload chunk error: " + t);
            } finally {
                if (raf != null) {
                    try { raf.close(); } catch (Throwable ignored) { }
                }
            }

            long next = (long) offset + written;
            JSONObject res = new JSONObject();
            try {
                res.put("ok", true);
                res.put("offset", next);
                res.put("total", total);
            } catch (Throwable ignored) { }

            if (!readOk) {
                try {
                    res.put("ok", false);
                    res.put("error", "上传块中断，请重传该块");
                } catch (Throwable ignored) { }
                send(out, 400, "application/json; charset=utf-8", res.toString().getBytes("UTF-8"));
                out.flush();
                return;
            }

            if (next >= total) {
                if (tmpApk.length() != total) {
                    try {
                        res.put("ok", false);
                        res.put("error", "大小不符: 收到 " + tmpApk.length() + " / 期望 " + total);
                    } catch (Throwable ignored) { }
                    send(out, 400, "application/json; charset=utf-8", res.toString().getBytes("UTF-8"));
                    out.flush();
                    return;
                }
                JSONObject r = DeviceOps.installApk(ctx, tmpApk);
                boolean ok = r.optBoolean("success", false);
                try {
                    res.put("install", r);
                    res.put("ok", ok);
                } catch (Throwable ignored) { }
                send(out, ok ? 200 : 500, "application/json; charset=utf-8", res.toString().getBytes("UTF-8"));
                out.flush();
                return;
            }

            send(out, 200, "application/json; charset=utf-8", res.toString().getBytes("UTF-8"));
            out.flush();
            return;
        }

        int bodyLimit = path.equals("/api/v1/asr/test") ? ASR_TEST_BODY : MAX_BODY;
        byte[] body;
        if (transferEncoding != null && transferEncoding.toLowerCase(Locale.US).contains("chunked")) {
            // 只认 Content-Length 的服务器会把 chunked 请求当成空 body 静默处理，
            // 调用方很难排查。这里做最小 chunked 解码，超限/格式错返回 413。
            body = readChunked(in, bodyLimit);
            if (body == null) {
                send(out, 413, "application/json; charset=utf-8",
                        jsonError("请求体无效或过大（上限 " + bodyLimit + " 字节）").getBytes("UTF-8"));
                out.flush();
                return;
            }
        } else {
            if (contentLength < 0 || contentLength > bodyLimit) {
                send(out, 413, "application/json; charset=utf-8",
                        jsonError("请求体过大（上限 " + bodyLimit + " 字节）").getBytes("UTF-8"));
                out.flush();
                return;
            }
            body = new byte[contentLength];
            int read = 0;
            while (read < body.length) {
                int n = in.read(body, read, body.length - read);
                if (n < 0) break;
                read += n;
            }
        }

        boolean isMobileUa = userAgent != null
                && (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone"));

        String bodyText = new String(body, "UTF-8");
        String requestKey = method + " " + rawPath;
        CURRENT_REQUEST.set(requestKey);
        long t0 = System.currentTimeMillis();
        try {
            route(method, rawPath, query, bodyText, hToken, authHeader, cookieHeader, isMobileUa, out);
        } catch (Throwable t) {
            // 兜底：绝大多数 handler 自己会发具体错误；走到这里说明有未预期异常
            // （例如畸形 JSON）。绝不能直接关 socket —— 客户端只会看到「连接被重置」。
            Log.w(TAG, "route error " + requestKey + ": " + t);
            try {
                send(out, 500, "application/json; charset=utf-8",
                        jsonError("服务器内部错误: " + t.getClass().getSimpleName()).getBytes("UTF-8"));
            } catch (Throwable ignored) {
            }
        }
        long cost = System.currentTimeMillis() - t0;
        if (method.equals("POST") || method.equals("DELETE")) {
            Log.i(TAG, "HTTP " + requestKey + " " + cost + "ms");
        }
        out.flush();
    }

    private void route(String method, String path, String query, String body,
                       String hToken, String authHeader, String cookieHeader,
                       boolean isMobileUa, OutputStream out) throws Exception {
        if (path.equals("/") || path.equals("/index.html")) {
            String target = isMobileUa ? "/mobile/home.html" : "/desktop/home.html";
            String location = target + (query.isEmpty() ? "" : "?" + query);
            String cookie = "";
            String queryToken = tokenFromQuery(query);
            if (tokenMatches(queryToken)) {
                // 浏览器打开一次带 token 的首页后，后续 /api 请求自动带 Cookie，页面无需反复拼 token
                cookie = "Set-Cookie: r1_token=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=31536000\r\n";
            }
            String redirectHead = "HTTP/1.1 302 Found\r\n"
                    + "Location: " + location + "\r\n"
                    + cookie
                    + "Cache-Control: no-store\r\n"
                    + "Content-Length: 0\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(redirectHead.getBytes("UTF-8"));
            return;
        }

        // 静态资源多页面路由 (/desktop/*.html, /mobile/*.html, /assets/*, /favicon.ico)
        if (!path.startsWith("/api/") && (path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js")
                || path.endsWith(".ico") || path.endsWith(".png") || path.endsWith(".svg"))) {
            String cleanPath;
            try {
                cleanPath = URLDecoder.decode(path, "UTF-8");
            } catch (Throwable t) {
                send(out, 400, "application/json; charset=utf-8", jsonError("非法路径").getBytes("UTF-8"));
                return;
            }
            if (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
            if (!cleanPath.contains("..") && !cleanPath.contains("\\")) {
                // 手机打开桌面页会挤成一团（左侧导航固定 16rem）。
                // 手机 UA 访问 /desktop/*.html 时跳到同名移动页；?view=desktop 可强制留下。
                if (isMobileUa && cleanPath.startsWith("desktop/") && cleanPath.endsWith(".html")
                        && !query.contains("view=desktop")) {
                    String mobilePath = "/mobile/" + cleanPath.substring("desktop/".length());
                    String location = mobilePath + (query.isEmpty() ? "" : "?" + query);
                    out.write(("HTTP/1.1 302 Found\r\nLocation: " + location
                            + "\r\nCache-Control: no-store\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                            .getBytes("UTF-8"));
                    return;
                }
                byte[] data = assetOrNull(cleanPath);
                if (data == null && "favicon.ico".equals(cleanPath)) {
                    data = assetOrNull("assets/favicon.ico");
                }
                if (data == null && !cleanPath.contains("/") && cleanPath.endsWith(".html")) {
                    data = assetOrNull((isMobileUa ? "mobile/" : "desktop/") + cleanPath);
                }
                if (data != null) {
                    String mime = "text/html; charset=utf-8";
                    if (cleanPath.endsWith(".css")) mime = "text/css; charset=utf-8";
                    else if (cleanPath.endsWith(".js")) mime = "application/javascript; charset=utf-8";
                    else if (cleanPath.endsWith(".ico")) mime = "image/x-icon";
                    else if (cleanPath.endsWith(".png")) mime = "image/png";
                    else if (cleanPath.endsWith(".svg")) mime = "image/svg+xml";
                    send(out, 200, mime, data, STATIC_CACHE);
                    return;
                }
            }
        }

        if (path.startsWith("/api/")) {
            if (!checkToken(query, hToken, authHeader, cookieHeader, body)) {
                send(out, 401, "application/json; charset=utf-8",
                        jsonError("未授权：请使用 http://<音箱IP>:" + PORT + "/?token=<启动日志里的令牌> 打开控制台").getBytes("UTF-8"));
                return;
            }
            if (!methodAllowed(method, path)) {
                send(out, 405, "application/json; charset=utf-8",
                        jsonError("该接口不支持 " + method).getBytes("UTF-8"));
                return;
            }
        }

        if (path.equals("/api/v1/status")) {
            send(out, 200, "application/json; charset=utf-8", status().getBytes("UTF-8"));
            return;
        }
        // 设置/修改访问令牌。首次设置（尚未设置）不需要旧令牌；已设置后改密码必须带旧令牌。
        if (path.equals("/api/v1/token") && method.equals("POST")) {
            if (tokenUserSet && !checkToken(query, hToken, authHeader, cookieHeader, body)) {
                send(out, 401, "application/json; charset=utf-8",
                        jsonError("修改令牌需要先通过现有令牌校验").getBytes("UTF-8"));
                return;
            }
            String newToken = "";
            try {
                newToken = new JSONObject(body).optString("token", "").trim();
            } catch (Throwable ignored) { }
            if (newToken.length() > 64) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("令牌最长 64 位").getBytes("UTF-8"));
                return;
            }
            if (!newToken.isEmpty() && newToken.length() < 4) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("令牌至少 4 位（留空则关闭鉴权）").getBytes("UTF-8"));
                return;
            }
            final String tokenValue = newToken;
            Config.update(ctx, new Config.Mutator() {
                @Override
                public void apply(Config c) {
                    c.token = tokenValue;
                    c.tokenUserSet = !tokenValue.isEmpty();
                }
            });
            token = newToken;
            tokenUserSet = !newToken.isEmpty();
            Log.i(TAG, tokenUserSet ? "访问令牌已更新（长度 " + newToken.length() + "）" : "访问令牌已关闭（控制台开放）");
            JSONObject tr = new JSONObject();
            try {
                tr.put("ok", true);
                tr.put("authRequired", tokenUserSet);
            } catch (Throwable ignored) { }
            send(out, 200, "application/json; charset=utf-8", tr.toString().getBytes("UTF-8"));
            return;
        }
        // 配置导出：换机/重装前一键备份（含密钥，需令牌）
        if (path.equals("/api/v1/config/export") && method.equals("GET")) {
            JSONObject o = Config.load(ctx).toJson(true);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
            sdf.setTimeZone(CN_TZ);
            try {
                o.put("version", 1);
                o.put("exportedAt", sdf.format(new Date()));
            } catch (Throwable ignored) { }
            send(out, 200, "application/json; charset=utf-8", o.toString().getBytes("UTF-8"));
            return;
        }
        // 配置导入：合并进当前配置（令牌不导入，避免把自己锁在外面）
        if (path.equals("/api/v1/config/import") && method.equals("POST")) {
            final JSONObject o = parseJson(body);
            if (o == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("配置不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            Config.update(ctx, new Config.Mutator() {
                @Override
                public void apply(Config c) {
                    c.applyJson(o);
                }
            });
            Log.i(TAG, "配置已导入");
            send(out, 200, "application/json; charset=utf-8", "{\"ok\":true}".getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/history")) {
            if (method.equals("DELETE") || (method.equals("POST") && body.contains("\"clear\":true"))) {
                Brain.clearHistory(ctx);
            }
            Config c = Config.load(ctx);
            JSONObject r = new JSONObject();
            r.put("ok", true);
            try {
                r.put("history", new JSONArray(c.chatHistoryJson));
            } catch (Throwable t) {
                r.put("history", new JSONArray());
            }
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/chat") && method.equals("POST")) {
            JSONObject o = parseJson(body);
            if (o == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            String text = o.optString("text", null);
            String source = o.optString("source", "Web 控制台");
            if (text == null || text.trim().isEmpty()) {
                send(out, 400, "application/json; charset=utf-8", jsonError("缺少 text").getBytes("UTF-8"));
                return;
            }
            String cleanText = text.trim();
            if (cleanText.length() > MAX_CHAT_CHARS) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("单条指令最多 " + MAX_CHAT_CHARS + " 字，当前 " + cleanText.length() + " 字").getBytes("UTF-8"));
                return;
            }
            if (source == null || source.length() > 40) source = "Web 控制台";

            long t0 = System.currentTimeMillis();
            try {
                Config cfg = Config.load(ctx);
                String reply = brain.request(cfg, cleanText);
                long costMs = System.currentTimeMillis() - t0;

                // 语音与网页写同一种历史结构（见 Brain.recordTurn），时间统一北京时间
                String nowStr = Brain.recordTurn(ctx, cleanText, reply, source, cfg.model, costMs);

                // 播报必须跟随「合成」设置：选了原厂就走 L1，不能再用 HTTP TTS（grok-tts）顶掉
                MainService svc = MainService.get();
                if (cfg.autoSpeak && svc != null) {
                    svc.announce(reply);
                }

                JSONObject r = new JSONObject();
                r.put("ok", true);
                r.put("reply", reply);
                r.put("costMs", costMs);
                r.put("model", cfg.model);
                r.put("echoMode", cfg.isEchoMode());
                r.put("time", nowStr);
                send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            } catch (Brain.BusyException busy) {
                send(out, 503, "application/json; charset=utf-8", jsonError(busy.getMessage()).getBytes("UTF-8"));
            } catch (Throwable t) {
                Log.w(TAG, "chat failed: " + t);
                send(out, 502, "application/json; charset=utf-8",
                        jsonError(t.getClass().getSimpleName() + ": " + t.getMessage()).getBytes("UTF-8"));
            }
            return;
        }
        if (path.equals("/api/v1/config")) {
            if (method.equals("GET")) {
                send(out, 200, "application/json; charset=utf-8",
                        Config.load(ctx).toJson(false).toString().getBytes("UTF-8"));
                return;
            }
            if (method.equals("POST")) {
                final JSONObject o = parseJson(body);
                if (o == null) {
                    send(out, 400, "application/json; charset=utf-8",
                            jsonError("配置不是合法 JSON").getBytes("UTF-8"));
                    return;
                }
                Config.update(ctx, new Config.Mutator() {
                    @Override
                    public void apply(Config c) {
                        c.applyJson(o);
                    }
                });
                if (MainService.get() != null) MainService.get().applyMuteOriginal();
                send(out, 200, "application/json; charset=utf-8",
                        Config.load(ctx).toJson(false).toString().getBytes("UTF-8"));
                return;
            }
        }
        if (path.equals("/api/v1/music/list")) {
            send(out, 200, "application/json; charset=utf-8", musicListJson().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/music/lyrics")) {
            send(out, 200, "application/json; charset=utf-8", lyricsJson(query).getBytes("UTF-8"));
            return;
        }
        // ---- L1 硬件指令：灯环 ----
        if (path.equals("/api/v1/cast")) {
            // GET 查自建 DLNA 状态；POST {"on":true|false} 走统一开关（与 /api/v1/receivers 同一实现）
            if ("POST".equalsIgnoreCase(method)) {
                JSONObject req = parseJson(body == null ? "" : body);
                boolean on = req != null && req.optBoolean("on", false);
                Receivers.setDlna(ctx, on);
            }
            send(out, 200, "application/json; charset=utf-8", DlnaRenderer.status().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/receivers")) {
            // 统一开关：DLNA / AirPlay / 蓝牙（含可发现）。这是开关，不是推流。
            if ("POST".equalsIgnoreCase(method)) {
                JSONObject req = parseJson(body == null ? "{}" : body);
                if (req == null) {
                    send(out, 400, "application/json; charset=utf-8",
                            jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                    return;
                }
                JSONObject r = Receivers.apply(ctx, req);
                send(out, r.optBoolean("ok", false) ? 200 : 502,
                        "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
                return;
            }
            send(out, 200, "application/json; charset=utf-8",
                    Receivers.status(ctx).toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/l1/light")) {
            JSONObject r = new JSONObject();
            LightEffect effect = LightEffect.of(q(query, "mode"));
            if (effect == null) {
                // 以前未知值会被当成 off，网页传错一个参数灯就被悄悄关掉
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("未知灯环效果，可选 off/loading/wakeup/dormant/net/scan").getBytes("UTF-8"));
                return;
            }
            MainService svc = MainService.get();
            boolean ok = svc != null ? svc.session().setAmbient(effect) : L1Bridge.sendLight(ctx, effect);
            r.put("ok", ok);
            r.put("mode", effect.value());
            r.put("l1", svc != null && svc.l1Alive());
            send(out, ok ? 200 : 500, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/l1/light/state")) {
            send(out, 200, "application/json; charset=utf-8", L1Bridge.stateJson().getBytes("UTF-8"));
            return;
        }
        // ---- 云音乐：Cookie 登录 / 搜索 / 播放（网易云 + QQ） ----
        if (path.equals("/api/v1/music/cookie") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            MusicSource src = MusicSource.of(req.optString("source", ""));
            String cookie = req.optString("cookie", "").trim();
            if (src == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("未知音乐源").getBytes("UTF-8"));
                return;
            }
            final MusicSource srcF = src;
            final String cookieF = cookie;
            Config.update(ctx, new Config.Mutator() {
                @Override
                public void apply(Config c) {
                    srcF.saveCookie(c, cookieF);
                }
            });
            Log.i(TAG, "已保存 " + src.label() + " 的登录 Cookie（长度 " + cookie.length() + "）");
            send(out, 200, "application/json; charset=utf-8",
                    "{\"ok\":true}".getBytes("UTF-8"));
            return;
        }
        // 本地曲库连通性测试：设置页「测试」按钮用它区分「地址错」和「令牌错」。
        // 一律返回 200，让前端能拿到 error 原文；ok=false 由调用方判断。
        if (path.equals("/api/v1/music/songloft/test") && method.equals("POST")) {
            JSONObject r = SongloftProvider.test(Config.load(ctx));
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/music/search")) {
            MusicSource src = MusicSource.of(q(query, "source"));
            String keyword = q(query, "q");
            JSONArray list = new JSONArray();
            if (src != null && keyword != null && !keyword.trim().isEmpty()) {
                list = src.search(Config.load(ctx), keyword.trim());
            }
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("source", src == null ? "" : src.id());
            r.put("list", list);
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/music/play") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            MusicSource src = MusicSource.of(req.optString("source", ""));
            String id = req.optString("id", "");
            String title = req.optString("title", "云音乐");
            Config c = Config.load(ctx);
            String url = src == null ? null : src.songUrl(c, id);
            JSONObject r = new JSONObject();
            if (url == null) {
                String hint;
                if (src != null && !src.hasLogin(c)) {
                    hint = src.label() + "需要登录：请在电脑浏览器登录后把 Cookie 整段粘贴到本页";
                } else {
                    hint = "拿不到播放地址：可能未登录、无版权或需要会员";
                }
                r.put("ok", false);
                r.put("error", hint);
            } else {
                MusicPlayer.get().play(url, title);
                r.put("ok", true);
                r.put("url", url);
            }
            send(out, r.optBoolean("ok", false) ? 200 : 500,
                    "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.startsWith("/api/v1/player")) {
            handlePlayer(method, path, body, out);
            return;
        }
        if (path.equals("/api/v1/bluetooth") && method.equals("POST")) {
            JSONObject req = parseJson(body == null ? "{}" : body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8", jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            JSONObject all = Receivers.status(ctx);
            JSONObject cur = all.optJSONObject("bluetooth");
            if (cur == null || !cur.optBoolean("supported", false)) {
                send(out, 409, "application/json; charset=utf-8", jsonError("该设备不支持蓝牙").getBytes("UTF-8"));
                return;
            }
            boolean want = req.has("enabled") ? req.optBoolean("enabled", true) : !cur.optBoolean("on", false);
            Boolean disc = req.has("discoverable") ? Boolean.valueOf(req.optBoolean("discoverable")) : null;
            boolean ok = Receivers.setBluetooth(ctx, want, disc);
            JSONObject r = Receivers.status(ctx);
            JSONObject bt = r.optJSONObject("bluetooth");
            if (bt == null) bt = new JSONObject();
            JSONObject outj = new JSONObject();
            outj.put("ok", ok);
            outj.put("btEnabled", bt.optBoolean("on"));
            outj.put("discoverable", bt.optBoolean("discoverable"));
            outj.put("scanMode", bt.optInt("scanMode"));
            outj.put("btName", bt.optString("name"));
            outj.put("error", bt.optString("error"));
            send(out, ok ? 200 : 502, "application/json; charset=utf-8", outj.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/logs")) {
            send(out, 200, "application/json; charset=utf-8", logsJson(query).getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/tts") && method.equals("POST")) {
            String text = null;
            try {
                JSONObject o = new JSONObject(body);
                text = o.optString("text", null);
                if (text == null || text.trim().isEmpty()) text = o.optString("input", null);
            } catch (Throwable ignored) { }
            if (text == null || text.trim().isEmpty()) {
                send(out, 400, "application/json; charset=utf-8", jsonError("缺少 text").getBytes("UTF-8"));
                return;
            }
            if (speech == null || !speech.isConfigured()) {
                send(out, 409, "application/json; charset=utf-8", jsonError("TTS 未配置：请先在设置页填写 TTS 服务端点 URL").getBytes("UTF-8"));
                return;
            }
            final CountDownLatch latch = new CountDownLatch(1);
            final int[] firstMs = { -1 };
            final String[] err = { null };
            speech.speak(text, new SpeechEngine.Callback() {
                @Override
                public void onStarted(int firstAudioMs, int durationMs) {
                    firstMs[0] = firstAudioMs;
                    latch.countDown();
                }
                @Override
                public void onDone() { latch.countDown(); }
                @Override
                public void onError(String message) {
                    err[0] = message;
                    latch.countDown();
                }
            });
            boolean started = false;
            try { started = latch.await(15, TimeUnit.SECONDS); } catch (Throwable ignored) { }
            JSONObject r = new JSONObject();
            r.put("ok", err[0] == null);
            if (err[0] != null) r.put("error", err[0]);
            r.put("firstAudioMs", firstMs[0]);
            r.put("state", speech.status());
            if (!started && err[0] == null) {
                // 15 秒内没拿到首帧不代表失败：SpeechEngine 的后台编排不会因这里超时而取消。
                // 标 pending 让前端知道「已提交、仍在处理」，不要按失败重试造成重复播报。
                r.put("pending", true);
                r.put("note", "已提交，首帧超过 15 秒，后台继续合成/播放");
            }
            send(out, err[0] == null ? 200 : 502, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/tts/stop") && method.equals("POST")) {
            if (speech != null) speech.stop();
            // 原厂 TTS 兜底时也要能停（「别说了」走的是同一组动作）
            L1Bridge.stopSpeak(ctx);
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("state", speech == null ? "unconfigured" : speech.status());
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/tts/status")) {
            send(out, 200, "application/json; charset=utf-8",
                    (speech == null ? "{\"configured\":false}" : speech.stateJson()).getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/volume")) {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                send(out, 502, "application/json; charset=utf-8", jsonError("音频服务不可用").getBytes("UTF-8"));
                return;
            }
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            if (method.equals("POST")) {
                try {
                    JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
                    if (req.has("pct")) {
                        int pct = req.optInt("pct", 40);
                        // 超出 0~100 直接拒绝（400），不夹到边界：调用方算错时不能把音箱炸响
                        if (pct < 0 || pct > 100) {
                            send(out, 400, "application/json; charset=utf-8",
                                    jsonError("pct 必须在 0~100 之间").getBytes("UTF-8"));
                            return;
                        }
                        int target = Math.round((pct / 100f) * max);
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                    } else if (req.has("value")) {
                        int v = req.optInt("value", -1);
                        if (v < 0 || v > max) {
                            send(out, 400, "application/json; charset=utf-8",
                                    jsonError("value 必须在 0~" + max + " 之间").getBytes("UTF-8"));
                            return;
                        }
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "volume: " + t);
                }
            }
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("value", cur);
            r.put("max", max);
            r.put("pct", Math.round((cur * 100f) / max));
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/apps")) {
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("apps", DeviceOps.getInstalledApps(ctx));
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/apps/uninstall") && method.equals("POST")) {
            String pkg = null;
            try {
                JSONObject o = new JSONObject(body);
                pkg = o.optString("package", null);
            } catch (Throwable ignored) { }
            if (pkg == null || pkg.trim().isEmpty()) {
                send(out, 400, "application/json; charset=utf-8", jsonError("缺少 package 参数").getBytes("UTF-8"));
                return;
            }
            if (pkg.equals(ctx.getPackageName())) {
                send(out, 400, "application/json; charset=utf-8", jsonError("禁止通过控制台卸载本智脑核心服务").getBytes("UTF-8"));
                return;
            }
            boolean ok = DeviceOps.uninstallApp(pkg);
            JSONObject r = new JSONObject();
            r.put("ok", ok);
            r.put("package", pkg);
            if (!ok) r.put("error", "卸载失败，请确认是否为特权保护的系统关键组件");
            send(out, ok ? 200 : 500, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/apps/stop") && method.equals("POST")) {
            String pkg = null;
            try {
                JSONObject o = new JSONObject(body);
                pkg = o.optString("package", null);
            } catch (Throwable ignored) { }
            if (pkg == null || pkg.trim().isEmpty()) {
                send(out, 400, "application/json; charset=utf-8", jsonError("缺少 package 参数").getBytes("UTF-8"));
                return;
            }
            boolean ok = DeviceOps.stopApp(ctx, pkg);
            JSONObject r = new JSONObject();
            r.put("ok", ok);
            r.put("package", pkg);
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/system/reboot") && method.equals("POST")) {
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("message", "音箱系统正在重启...");
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            out.flush();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try { Thread.sleep(800); } catch (Throwable ignored) { }
                    DeviceOps.reboot();
                }
            }).start();
            return;
        }
        if (path.equals("/api/v1/system/clean") && method.equals("POST")) {
            JSONObject res = DeviceOps.cleanSystem(ctx);
            res.put("ok", res.optBoolean("success", true));
            send(out, 200, "application/json; charset=utf-8", res.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/weather")) {
            Config cfg = Config.load(ctx);
            String targetCity = null;
            String only = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("city=")) {
                        try {
                            targetCity = URLDecoder.decode(param.substring(5), "UTF-8");
                        } catch (Throwable ignored) { }
                    } else if (param.startsWith("provider=")) {
                        try {
                            only = URLDecoder.decode(param.substring(9), "UTF-8");
                        } catch (Throwable ignored) { }
                    }
                }
            }
            if (targetCity == null || targetCity.trim().isEmpty()) {
                targetCity = cfg.weatherCity;
            }
            WeatherService.Report wr = WeatherService.queryReport(cfg, targetCity, only);
            JSONObject r = new JSONObject();
            r.put("ok", !wr.text.isEmpty());
            r.put("city", targetCity);
            r.put("report", wr.text);
            r.put("provider", wr.provider);
            r.put("degraded", wr.degraded);
            // 每个源这次到底发生了什么（未配置 / 超时 / Key 无效 / 成功耗时），页面直接显示
            r.put("detail", wr.detail);
            r.put("fallback", wr.provider.isEmpty() && !wr.text.isEmpty());
            r.put("hasWeatherKey", cfg.weatherKey != null && !cfg.weatherKey.trim().isEmpty());
            r.put("host", cfg.weatherHost == null || cfg.weatherHost.trim().isEmpty()
                    ? "devapi.qweather.com" : cfg.weatherHost.trim());
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- 天气测试：允许直接测页面上「还没保存」的 Key / Host ----
        if (path.equals("/api/v1/weather/test") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            Config cfg = Config.load(ctx);
            // 只是本地临时覆盖，不写回配置：用户点「测试」时还没按保存
            String key = req.optString("weatherKey", "");
            if (!key.trim().isEmpty()) cfg.weatherKey = key.trim();
            String host = req.optString("weatherHost", "");
            if (!host.trim().isEmpty()) cfg.weatherHost = host.trim();
            String auth = req.optString("weatherAuth", "");
            if (!auth.trim().isEmpty()) cfg.weatherAuth = auth.trim();
            String iss = req.optString("weatherJwtIss", "");
            if (!iss.trim().isEmpty()) cfg.weatherJwtIss = iss.trim();
            String pid = req.optString("weatherJwtProjectId", "");
            if (!pid.trim().isEmpty()) cfg.weatherJwtProjectId = pid.trim();
            String kid = req.optString("weatherJwtKeyId", "");
            if (!kid.trim().isEmpty()) cfg.weatherJwtKeyId = kid.trim();
            String jwtKey = req.optString("weatherJwtKey", "");
            if (!jwtKey.trim().isEmpty() && !jwtKey.contains("***")) cfg.weatherJwtKey = jwtKey.trim();
            String city = req.optString("city", "");
            if (city.trim().isEmpty()) city = cfg.weatherCity;
            String only = req.optString("provider", "auto");
            WeatherService.Report wr = WeatherService.queryReport(cfg, city, only);
            JSONObject r = new JSONObject();
            r.put("ok", !wr.text.isEmpty());
            r.put("city", city);
            r.put("report", wr.text);
            r.put("provider", wr.provider);
            r.put("degraded", wr.degraded);
            r.put("detail", wr.detail);
            r.put("hasWeatherKey", cfg.weatherKey != null && !cfg.weatherKey.trim().isEmpty());
            r.put("host", cfg.weatherHost == null || cfg.weatherHost.trim().isEmpty()
                    ? "devapi.qweather.com" : cfg.weatherHost.trim());
            r.put("testedKey", key.trim().isEmpty() ? "已保存的 Key" : "页面上的 Key（未保存）");
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- 自建 ASR：健康检查（允许用页面上还没保存的地址） ----
        if (path.equals("/api/v1/asr/health")) {
            Config c = Config.load(ctx);
            String url = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("url=")) {
                        try {
                            url = URLDecoder.decode(param.substring(4), "UTF-8");
                        } catch (Throwable ignored) { }
                        break;
                    }
                }
            }
            if (url == null || url.trim().isEmpty()) url = c.asrUrl;
            // 令牌优先取请求参数（页面尚未保存时用），否则用已保存的
            String tok = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("token=")) {
                        try { tok = URLDecoder.decode(param.substring(6), "UTF-8"); } catch (Throwable ignored) { }
                        break;
                    }
                }
            }
            if (tok == null || tok.trim().isEmpty()) tok = c.asrToken;
            JSONObject r = new JSONObject();
            r.put("endpoint", AsrClient.normalize(url));
            try {
                JSONObject h = AsrClient.health(url, tok);
                r.put("ok", true);
                r.put("billingMonth", h.optString("billing_month", ""));
                r.put("resetDate", h.optString("reset_date", ""));
                r.put("hasToken", tok != null && !tok.trim().isEmpty());
                r.put("raw", h.toString());
            } catch (Throwable t) {
                r.put("ok", false);
                r.put("error", t.getMessage() == null ? t.toString() : t.getMessage());
            }
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- 自建 ASR：转写测试（可选带一段 WAV 的 base64，验证真的能出字） ----
        if (path.equals("/api/v1/asr/test") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            Config c = Config.load(ctx);
            String url = req.optString("url", "");
            if (url.trim().isEmpty()) url = c.asrUrl;
            JSONObject r = new JSONObject();
            r.put("endpoint", AsrClient.normalize(url));
            String b64 = req.optString("audio", "");
            try {
                if (b64.trim().isEmpty()) {
                    JSONObject h = AsrClient.health(url, c.asrToken);
                    r.put("ok", true);
                    r.put("billingMonth", h.optString("billing_month", ""));
                    r.put("note", "读到用量快照；要验证转写请上传一段 16k 单声道 WAV");
                } else {
                    byte[] audio = Base64.decode(b64, Base64.DEFAULT);
                    AsrClient.Result res = AsrClient.transcribe(withAsrUrl(c, url), audio);
                    r.put("ok", !res.text.isEmpty());
                    r.put("text", res.text);
                    r.put("durationMs", res.durationMs);
                    r.put("engine", res.engine);
                    r.put("via", res.endpoint);
                }
            } catch (Throwable t) {
                r.put("ok", false);
                r.put("error", t.getMessage() == null ? t.toString() : t.getMessage());
            }
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- 整合 ASR 网关用量：给页面显示「本月已用 / 剩余」，读 /usage 不计次数 ----
        if (path.equals("/api/v1/asr/quota")) {
            Config c = Config.load(ctx);
            JSONObject r = new JSONObject();
            r.put("endpoint", AsrClient.normalize(c.asrUrl));
            String asrTok = c.asrToken == null ? "" : c.asrToken.trim();
            r.put("hasToken", !asrTok.isEmpty());
            try {
                JSONObject h = AsrClient.health(c.asrUrl, c.asrToken);
                JSONArray toks = h.optJSONArray("tokens");
                JSONObject mine = (toks != null && toks.length() > 0) ? toks.optJSONObject(0) : null;
                r.put("ok", true);
                r.put("billingMonth", h.optString("billing_month", ""));
                r.put("resetDate", h.optString("reset_date", ""));
                if (mine != null) {
                    r.put("used", mine.optInt("used", 0));
                    r.put("remaining", mine.optInt("remaining", 0));
                    r.put("limit", mine.optInt("monthly_limit", 0));
                    r.put("unlimited", mine.optBoolean("unlimited", false));
                }
                r.put("raw", h.toString());
            } catch (Throwable t) {
                r.put("ok", false);
                r.put("error", t.getMessage() == null ? t.toString() : t.getMessage());
            }
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- 和风 JWT：生成密钥对（公钥贴到控制台，私钥留在设备） ----
        if (path.equals("/api/v1/weather/keygen") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            JSONObject r = new JSONObject();
            try {
                byte[] seed = Ed25519.randomSeed();
                byte[] pub = Ed25519.publicKey(seed);
                String priv = Ed25519.privateKeyBase64(seed);
                r.put("ok", true);
                r.put("privateKey", priv);
                r.put("publicKeyPem", Ed25519.publicKeyPem(pub));
                r.put("publicKeyBase64", Ed25519.publicKeyBase64(pub));
                if (req.optBoolean("save", true)) {
                    final String privKey = priv;
                    final String pubPem = Ed25519.publicKeyPem(pub);
                    Config.update(ctx, new Config.Mutator() {
                        @Override
                        public void apply(Config c) {
                            c.weatherJwtKey = privKey;
                            c.weatherJwtPub = pubPem;
                            if ("apikey".equalsIgnoreCase(c.weatherAuth)) c.weatherAuth = "jwt";
                        }
                    });
                    r.put("saved", true);
                } else {
                    r.put("saved", false);
                }
            } catch (Throwable t) {
                r.put("ok", false);
                r.put("error", t.getMessage() == null ? t.toString() : t.getMessage());
            }
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- 和风 JWT：从私钥推导公钥（导入现成私钥时用） ----
        if (path.equals("/api/v1/weather/pubkey") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            Config c = Config.load(ctx);
            String priv = req.optString("weatherJwtKey", "");
            if (priv.trim().isEmpty() || priv.contains("***")) priv = c.weatherJwtKey;
            JSONObject r = new JSONObject();
            try {
                byte[] seed = Ed25519.seedFromPkcs8(priv);
                byte[] pub = Ed25519.publicKey(seed);
                r.put("ok", true);
                r.put("publicKeyPem", Ed25519.publicKeyPem(pub));
                r.put("publicKeyBase64", Ed25519.publicKeyBase64(pub));
            } catch (Throwable t) {
                r.put("ok", false);
                r.put("error", t.getMessage() == null ? t.toString() : t.getMessage());
            }
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- 和风 JWT：只签发不请求，页面用来确认 ProjectID/KeyID/私钥是否配套 ----
        if (path.equals("/api/v1/weather/jwt") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            Config c = Config.load(ctx);
            String pid = req.optString("weatherJwtProjectId", c.weatherJwtProjectId);
            String kid = req.optString("weatherJwtKeyId", c.weatherJwtKeyId);
            String key = req.optString("weatherJwtKey", "");
            if (key.trim().isEmpty() || key.contains("***")) key = c.weatherJwtKey;
            JSONObject r = new JSONObject();
            try {
                long now = System.currentTimeMillis() / 1000;
                String iss = req.optString("weatherJwtIss", c.weatherJwtIss);
                String token = QWeatherProvider.buildJwt(iss, pid, kid, key, now, 900);
                String[] parts = token.split("\\.");
                r.put("ok", true);
                r.put("token", token);
                r.put("header", new String(Base64.decode(parts[0], Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8"));
                r.put("payload", new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8"));
                r.put("expiresIn", 900);
            } catch (Throwable t) {
                r.put("ok", false);
                r.put("error", t.getMessage() == null ? t.toString() : t.getMessage());
            }
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- Home Assistant 连通性测试（允许测试未保存的地址/令牌） ----
        if (path.equals("/api/v1/ha/test") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            Config c = Config.load(ctx);
            String url = req.optString("haUrl", c.haUrl);
            String token = req.optString("haToken", "");
            if (token.trim().isEmpty()) token = c.haToken;
            JSONObject r = new JSONObject();
            try {
                JSONObject res = HomeAssistant.test(url, token);
                r.put("ok", true);
                r.put("entities", res.optInt("entities", 0));
                r.put("controllable", res.optInt("controllable", 0));
            } catch (Throwable t) {
                r.put("ok", false);
                r.put("error", t.getMessage());
            }
            send(out, r.optBoolean("ok", false) ? 200 : 500,
                    "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- 投屏：原厂播放器没有开关接口。这里只能唤醒它的主服务，再按端口是否在听回报。 ----
        if (path.equals("/api/v1/cast/status") || (path.equals("/api/v1/cast/wake") && method.equals("POST"))) {
            boolean woke = false;
            if (path.equals("/api/v1/cast/wake")) {
                String echo = DeviceOps.execShell(
                        "am startservice -n com.phicomm.speaker.player/.EchoService", 8000);
                woke = echo != null && echo.indexOf("Error") < 0;
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            boolean dlna = portListening(1900, true);
            boolean airplay = portListening(7000, false);
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("woke", woke);
            r.put("dlna", dlna);
            r.put("airplay", airplay);
            r.put("controllable", false);
            send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- 大模型：模型列表 与 连通性测试（都用当前页面上的值，允许未保存） ----
        if (path.equals("/api/v1/llm/models")) {
            Config c = Config.load(ctx);
            String baseUrl = q(query, "baseUrl");
            String apiKey = q(query, "apiKey");
            JSONObject r = new JSONObject();
            try {
                List<String> models = LlmClient.listModels(
                        baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl : c.baseUrl,
                        apiKey != null && !apiKey.trim().isEmpty() ? apiKey : c.apiKey);
                JSONArray arr = new JSONArray();
                for (String m : models) arr.put(m);
                r.put("ok", true);
                r.put("models", arr);
                r.put("endpoint", LlmClient.modelsUrl(
                        baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl : c.baseUrl));
            } catch (Throwable t) {
                r.put("ok", false);
                r.put("error", t.getMessage());
            }
            send(out, r.optBoolean("ok", false) ? 200 : 500,
                    "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        if (path.equals("/api/v1/llm/test") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            Config c = Config.load(ctx);
            String baseUrl = req.optString("baseUrl", c.baseUrl);
            String apiKey = req.optString("apiKey", "");
            String model = req.optString("model", c.model);
            if (apiKey.trim().isEmpty()) apiKey = c.apiKey;
            JSONObject r = new JSONObject();
            long t0 = System.currentTimeMillis();
            try {
                String reply = LlmClient.ping(baseUrl, apiKey, model, ReasoningEffort.of(c.reasoningEffort));
                r.put("ok", true);
                r.put("reply", reply);
                r.put("model", model);
                r.put("endpoint", LlmClient.normalizeChatUrl(baseUrl));
                r.put("costMs", System.currentTimeMillis() - t0);
            } catch (Throwable t) {
                r.put("ok", false);
                r.put("error", t.getMessage());
                r.put("endpoint", LlmClient.normalizeChatUrl(baseUrl));
                r.put("costMs", System.currentTimeMillis() - t0);
            }
            send(out, r.optBoolean("ok", false) ? 200 : 500,
                    "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        // ---- TTS：真播一句，返回是否出声与首音耗时；明确指出调的是哪个端点 ----
        if (path.equals("/api/v1/tts/test") && method.equals("POST")) {
            JSONObject req = parseJson(body);
            if (req == null) {
                send(out, 400, "application/json; charset=utf-8",
                        jsonError("请求不是合法 JSON").getBytes("UTF-8"));
                return;
            }
            Config c = Config.load(ctx);
            String text = req.optString("text", "斐讯 R1 语音合成测试正常。");
            JSONObject r = new JSONObject();
            r.put("endpoint", c.ttsUrl);
            r.put("model", c.ttsModel);
            r.put("voice", c.ttsVoice);
            // 选了原厂语音：直接用 L1 的原厂 TTS，和 HTTP TTS 无关
            if ("native".equals(c.ttsEngine)) {
                MainService svc = MainService.get();
                if (svc == null) {
                    r.put("ok", false);
                    r.put("error", "服务未运行");
                    send(out, 500, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
                    return;
                }
                svc.speak(text);
                r.put("ok", true);
                r.put("engine", "native");
                // 原厂语音不经过 HTTP TTS，没有首音耗时；显式返回 -1，避免前端拼出 "undefinedms"
                r.put("firstAudioMs", -1);
                r.put("note", "当前是原厂语音，不走 HTTP 合成，没有首音耗时");
                send(out, 200, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
                return;
            }
            if (speech == null || !speech.isConfigured()) {
                r.put("ok", false);
                r.put("error", "TTS 端点未配置（设置页填写 URL 后重试）");
                send(out, 400, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
                return;
            }
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final String[] errBox = new String[]{null};
            final int[] firstMs = new int[]{-1};
            boolean started = speech.speak(text, new SpeechEngine.Callback() {
                @Override
                public void onStarted(int firstAudioMs, int durationMs) {
                    firstMs[0] = firstAudioMs;
                    latch.countDown();
                }

                @Override
                public void onDone() {
                    latch.countDown();
                }

                @Override
                public void onError(String message) {
                    errBox[0] = message;
                    latch.countDown();
                }
            });
            if (!started) {
                r.put("ok", false);
                r.put("error", errBox[0] == null ? "播报未能启动" : errBox[0]);
                send(out, 500, "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
                return;
            }
            boolean heard = false;
            try {
                heard = latch.await(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Throwable ignored) {
            }
            r.put("ok", heard && errBox[0] == null);
            r.put("firstAudioMs", firstMs[0]);
            if (errBox[0] != null) r.put("error", errBox[0]);
            send(out, r.optBoolean("ok", false) ? 200 : 500,
                    "application/json; charset=utf-8", r.toString().getBytes("UTF-8"));
            return;
        }
        send(out, 404, "application/json; charset=utf-8", jsonError("没有这个接口: " + path).getBytes("UTF-8"));
    }

    /**
     * 方法守卫：只读接口只接受 GET，写接口只接受 POST/DELETE。
     *
     * <p>未知路径返回 true，交给后面的 404 分支 —— 新增接口默认放行，不会因为忘了登记被误拦。
     *
     * @param method 请求方法
     * @param path   已去掉查询串的路径
     * @return true 表示允许
     * @author xudong.hua,deepseek
     * @since 2026-11-05 12:00 星期四
     */
    private static boolean methodAllowed(String method, String path) {
        String m = method == null ? "" : method.toUpperCase(Locale.US);
        if (path.equals("/api/v1/status")
                || path.equals("/api/v1/config/export")
                || path.equals("/api/v1/music/list")
                || path.equals("/api/v1/music/lyrics")
                || path.equals("/api/v1/music/search")
                || path.equals("/api/v1/l1/light")
                || path.equals("/api/v1/l1/light/state")
                || path.equals("/api/v1/logs")
                || path.equals("/api/v1/tts/status")
                || path.equals("/api/v1/apps")
                || path.equals("/api/v1/weather")
                || path.equals("/api/v1/asr/health")
                || path.equals("/api/v1/llm/models")
                || path.equals("/api/v1/cast/status")) {
            return "GET".equals(m);
        }
        if (path.equals("/api/v1/config")
                || path.equals("/api/v1/volume")
                || path.equals("/api/v1/receivers")
                || path.equals("/api/v1/cast")) {
            return "GET".equals(m) || "POST".equals(m);
        }
        if (path.equals("/api/v1/history")) {
            return "GET".equals(m) || "DELETE".equals(m) || "POST".equals(m);
        }
        if (path.startsWith("/api/v1/player")) {
            return "GET".equals(m) || "POST".equals(m);
        }
        if (path.equals("/api/v1/cast/wake")) {
            return "POST".equals(m);
        }
        if (path.equals("/api/v1/token")
                || path.equals("/api/v1/config/import")
                || path.equals("/api/v1/chat")
                || path.equals("/api/v1/music/cookie")
                || path.equals("/api/v1/music/play")
                || path.equals("/api/v1/music/songloft/test")
                || path.equals("/api/v1/bluetooth")
                || path.equals("/api/v1/tts")
                || path.equals("/api/v1/tts/stop")
                || path.equals("/api/v1/tts/test")
                || path.equals("/api/v1/apps/install")
                || path.equals("/api/v1/apps/uninstall")
                || path.equals("/api/v1/apps/stop")
                || path.equals("/api/v1/system/reboot")
                || path.equals("/api/v1/system/clean")
                || path.equals("/api/v1/weather/test")
                || path.equals("/api/v1/weather/keygen")
                || path.equals("/api/v1/weather/pubkey")
                || path.equals("/api/v1/weather/jwt")
                || path.equals("/api/v1/asr/test")
                || path.equals("/api/v1/ha/test")
                || path.equals("/api/v1/llm/test")) {
            return "POST".equals(m);
        }
        return true;
    }

    /** 所有 /api 请求统一鉴权；令牌可来自 Header、Bearer、Query、Cookie 或 JSON body。 */
    private boolean checkToken(String query, String hToken, String authHeader, String cookieHeader, String body) {
        // 首次使用（用户还没设置令牌）时开放访问，否则设备刚装好就无法打开控制台
        if (!tokenUserSet) return true;
        String candidate = hToken;
        if (isEmpty(candidate) && authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            candidate = authHeader.substring(7).trim();
        }
        if (isEmpty(candidate)) candidate = tokenFromQuery(query);
        if (isEmpty(candidate)) candidate = tokenFromCookie(cookieHeader);
        if (isEmpty(candidate) && body != null && body.length() > 2 && body.charAt(0) == '{') {
            try {
                candidate = new JSONObject(body).optString("token", "");
            } catch (Throwable ignored) { }
        }
        return tokenMatches(candidate);
    }

    private boolean tokenMatches(String candidate) {
        if (!tokenUserSet) return true;
        return candidate != null && candidate.equals(token);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 是否在监听某个端口（读 /proc/net，不需要 root）。 */
    private static boolean portListening(int port, boolean udp) {
        String hex = Integer.toHexString(port).toUpperCase();
        while (hex.length() < 4) hex = "0" + hex;
        String file = udp ? "/proc/net/udp" : "/proc/net/tcp";
        java.io.BufferedReader br = null;
        try {
            br = new java.io.BufferedReader(new java.io.FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                int i = line.indexOf(':');
                if (i < 0) continue;
                String rest = line.substring(i + 1);
                int sp = rest.indexOf(' ');
                if (sp < 0) continue;
                String portHex = rest.substring(0, sp).trim();
                if (portHex.endsWith(hex)) return true;
            }
        } catch (Throwable ignored) {
        } finally {
            if (br != null) {
                try { br.close(); } catch (Throwable ignored) { }
            }
        }
        return false;
    }

    /** 取查询参数（URL 解码），没有则返回 null。 */
    private static String q(String query, String name) {
        if (query == null || query.isEmpty()) return null;
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(name)) {
                try {
                    return URLDecoder.decode(kv.substring(i + 1), "UTF-8");
                } catch (Throwable t) {
                    return kv.substring(i + 1);
                }
            }
        }
        return null;
    }

    private static String tokenFromQuery(String query) {        if (query == null || query.isEmpty()) return null;
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals("token")) {
                try {
                    return URLDecoder.decode(kv.substring(i + 1), "UTF-8");
                } catch (Throwable ignored) {
                    return kv.substring(i + 1);
                }
            }
        }
        return null;
    }

    private static String tokenFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return null;
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith("r1_token=")) return p.substring("r1_token=".length());
        }
        return null;
    }

    /**
     * 解析请求体：空 body 返回空对象；非法 JSON 返回 null（调用方回 400）。
     * 把「畸形 JSON 直接抛异常 → socket 被关掉、客户端看不到任何响应」收口成可预期的 400。
     */
    private static JSONObject parseJson(String body) {
        if (body == null || body.trim().isEmpty()) return new JSONObject();
        try {
            return new JSONObject(body);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读 chunked 请求体；超过上限或格式错返回 null。 */
    private static byte[] readChunked(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) return null;
            int semi = sizeLine.indexOf(';');
            if (semi >= 0) sizeLine = sizeLine.substring(0, semi);
            int size;
            try {
                size = Integer.parseInt(sizeLine.trim(), 16);
            } catch (Throwable t) {
                return null;
            }
            if (size == 0) {
                // trailer（可能有 0 行或多行），读到空行为止
                String trailer;
                while ((trailer = readLine(in)) != null && !trailer.isEmpty()) { }
                return body.toByteArray();
            }
            if (size < 0 || body.size() + size > limit) return null;
            byte[] buf = new byte[size];
            int off = 0;
            while (off < size) {
                int n = in.read(buf, off, size - off);
                if (n < 0) return null;
                off += n;
            }
            body.write(buf, 0, size);
            readLine(in); // 每块末尾的 CRLF
        }
    }

    /** 204 响应：不带 Content-Type/body，但保留 CORS 头，供预检使用。 */
    private static void sendNoContent(OutputStream out) throws Exception {
        String head = "HTTP/1.1 204 No Content\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type, X-Token, Authorization\r\n"
                + "Access-Control-Max-Age: 600\r\n"
                + "Cache-Control: no-store\r\n"
                + "Content-Length: 0\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
    }

    private String status() throws Exception {
        JSONObject o = new JSONObject();
        Config c = Config.load(ctx);
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int vol = 0;
        int volMax = 1;
        if (am != null) {
            vol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            volMax = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        }
        long[] mem = memInfoKb();

        String ver = "";
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            ver = pi.versionName;
        } catch (Throwable ignored) { }

        BluetoothAdapter bt = null;
        try { bt = BluetoothAdapter.getDefaultAdapter(); } catch (Throwable ignored) { }
        boolean btOn = false;
        String btName = "";
        try {
            btOn = bt != null && bt.isEnabled();
            if (btOn) btName = String.valueOf(bt.getName());
        } catch (Throwable ignored) { }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        sdf.setTimeZone(CN_TZ);

        o.put("ok", true);
        o.put("online", true);
        o.put("version", ver);
        o.put("hardware", Build.HARDWARE);
        o.put("deviceModel", Build.MODEL);
        o.put("androidRelease", Build.VERSION.RELEASE);
        o.put("ip", localIp());
        o.put("serverTime", sdf.format(new Date()));
        o.put("timezone", "Asia/Shanghai (UTC+8)");
        o.put("btSupported", bt != null);
        o.put("btEnabled", btOn);
        o.put("btName", btName);
        try {
            JSONObject rec = Receivers.status(ctx);
            o.put("receivers", rec);
            JSONObject bb = rec.optJSONObject("bluetooth");
            if (bb != null) o.put("btDiscoverable", bb.optBoolean("discoverable"));
        } catch (Throwable ignored) { }
        o.put("uptimeMs", SystemClock.elapsedRealtime());
        o.put("memFreeKb", mem[0]);
        o.put("memTotalKb", mem[1]);
        o.put("socTempC", socTempC());
        o.put("pid", Process.myPid());
        o.put("tts", speech == null ? "unconfigured" : speech.status());
        o.put("ttsConfigured", speech != null && speech.isConfigured());
        o.put("autoSpeak", c.autoSpeak);
        o.put("l1", MainService.get() != null && MainService.get().l1Alive());
        o.put("l1Version", MainService.get() == null ? 0 : MainService.get().l1Version());
        o.put("port", PORT);
        o.put("authRequired", tokenUserSet);
        o.put("model", c.model);
        o.put("echoMode", c.isEchoMode());
        o.put("volume", vol);
        o.put("volumeMax", volMax);
        o.put("volumePct", Math.round((vol * 100f) / volMax));
        return o.toString();
    }

    /**
     * 临时覆盖 ASR 地址（测试页面上还没保存的值用，不落盘）。
     *
     * @param c   loaded config
     * @param url 页面填的地址
     * @return 同一个对象（只用于本次请求）
     */
    private static Config withAsrUrl(Config c, String url) {
        c.asrEngine = "self";
        c.asrUrl = url == null ? "" : url.trim();
        return c;
    }

    /** 扫描音箱本地存储目录中的真实音频文件（5 秒缓存，避免轮询时反复扫盘）。 */
    private String musicListJson() {
        JSONObject res = new JSONObject();
        JSONArray arr = new JSONArray();
        try {
            List<File> files = scanCached();
            int idx = 1;
            for (File f : files) {
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                String ext = dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.US) : "AUDIO";
                long bytes = f.length();
                String sizeStr = bytes >= 1048576L
                        ? String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
                        : ((bytes / 1024) + " KB");
                String path = f.getAbsolutePath();
                int pathDot = path.lastIndexOf('.');
                File lrcFile = new File((pathDot > 0 ? path.substring(0, pathDot) : path) + ".lrc");

                JSONObject item = new JSONObject();
                item.put("id", "local_" + (idx++));
                item.put("title", MusicPlayer.titleOf(f));
                item.put("subTitle", name);
                item.put("artist", "音箱本地存储");
                item.put("album", String.valueOf(f.getParent()));
                item.put("format", ext);
                item.put("spec", ext + " · " + sizeStr);
                item.put("source", "local");
                item.put("path", path);
                item.put("hasLrc", lrcFile.isFile());
                arr.put(item);
            }
            res.put("ok", true);
            res.put("tracks", arr);
        } catch (Throwable t) {
            Log.w(TAG, "musicList: " + t);
            try {
                res.put("ok", false);
                res.put("tracks", arr);
                res.put("error", String.valueOf(t.getMessage()));
            } catch (Throwable ignored) { }
        }
        return res.toString();
    }

    private synchronized List<File> scanCached() {
        long now = System.currentTimeMillis();
        List<File> cached = musicCache;
        if (cached != null && now - musicCacheAt < MUSIC_CACHE_MS) return cached;
        List<File> fresh = MusicPlayer.scan();
        musicCache = fresh;
        musicCacheAt = now;
        return fresh;
    }

    /** 读取与音频同名的 .lrc 歌词文件，没有则返回空字符串。 */
    private String lyricsJson(String query) throws Exception {
        JSONObject r = new JSONObject();
        r.put("ok", true);
        r.put("lrc", "");
        String path = null;
        if (query != null) {
            for (String kv : query.split("&")) {
                int i = kv.indexOf('=');
                if (i > 0 && kv.substring(0, i).equals("path")) {
                    path = URLDecoder.decode(kv.substring(i + 1), "UTF-8");
                    break;
                }
            }
        }
        if (path != null && MusicPlayer.isAllowedSource(path) && path.startsWith("/")) {
            int dot = path.lastIndexOf('.');
            File lrc = new File((dot > 0 ? path.substring(0, dot) : path) + ".lrc");
            if (lrc.isFile() && lrc.length() < 512 * 1024) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                InputStream is = new BufferedInputStream(new FileInputStream(lrc));
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                } finally {
                    try { is.close(); } catch (Throwable ignored) { }
                }
                r.put("lrc", new String(bos.toByteArray(), "UTF-8"));
            }
        }
        return r.toString();
    }

    /** 播放器控制接口：play / toggle / pause / stop / next / prev / seek / mode / sleep。 */
    private void handlePlayer(String method, String path, String body, OutputStream out) throws Exception {
        MusicPlayer player = MusicPlayer.get();
        String action = path.substring("/api/v1/player".length());
        if (action.startsWith("/")) action = action.substring(1);
        if (method.equals("POST") && action.length() > 0) {
            JSONObject req;
            try {
                req = new JSONObject(body.length() == 0 ? "{}" : body);
            } catch (Throwable t) {
                req = new JSONObject();
            }
            int before = player.stateVersion();
            if (action.equals("play")) {
                String src = req.optString("source", "");
                if (!MusicPlayer.isAllowedSource(src)) {
                    send(out, 400, "application/json; charset=utf-8", jsonError("无效的播放源：仅支持音箱存储内的音频文件或 HTTP(S) 音频流").getBytes("UTF-8"));
                    return;
                }
                player.play(src, req.optString("title", ""));
            } else if (action.equals("toggle")) {
                player.toggle();
            } else if (action.equals("pause")) {
                player.pause();
            } else if (action.equals("stop")) {
                player.stop();
            } else if (action.equals("next")) {
                player.step(1);
            } else if (action.equals("prev")) {
                player.step(-1);
            } else if (action.equals("seek")) {
                player.seekTo(Math.max(0, req.optInt("ms", 0)));
            } else if (action.equals("mode")) {
                player.setLoop(LoopMode.of(req.optString("loop", LoopMode.LIST.value())));
            } else if (action.equals("sleep")) {
                player.setSleep(req.optInt("minutes", 0));
            } else {
                send(out, 404, "application/json; charset=utf-8", jsonError("没有这个播放器操作: " + action).getBytes("UTF-8"));
                return;
            }
            // 等一次状态落地（最多 500ms）。固定 sleep(80) 在慢设备上不可靠，
            // 而且会平白占住一个 HTTP 工作线程。
            long deadline = System.currentTimeMillis() + 500;
            while (player.stateVersion() == before && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        send(out, 200, "application/json; charset=utf-8", player.statusJson().getBytes("UTF-8"));
    }

    /** 获取音箱当前局域网 IPv4 地址（排除回环网卡）。 */
    private static String localIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) { }
        return "";
    }

    /**
     * 读取音箱真实 Android logcat 日志（有界但给足）。
     *
     * <p>行数默认 {@link #LOG_LINES_DEFAULT}，可用 {@code ?lines=}(20~400) 覆盖；
     * 单条超 {@link #LOG_MSG_MAX} 字才截断（堆栈一般够用）。
     * logcat 是系统环形缓冲，会自己覆盖旧行，不会无限增长；这里限的只是单次返回量。
     *
     * @param query 请求查询串（取 lines=）
     */
    private String logsJson(String query) {
        JSONObject res = new JSONObject();
        JSONArray arr = new JSONArray();
        int lines = LOG_LINES_DEFAULT;
        try {
            String v = q(query, "lines");
            if (v != null && !v.trim().isEmpty()) {
                lines = Integer.parseInt(v.trim());
            }
        } catch (Throwable ignored) { }
        if (lines < 20) lines = 20;
        if (lines > LOG_LINES_MAX) lines = LOG_LINES_MAX;
        if (!logBusy.compareAndSet(false, true)) {
            try {
                res.put("ok", true);
                res.put("logs", arr);
                res.put("busy", true);
            } catch (Throwable ignored) { }
            return res.toString();
        }
        java.lang.Process proc = null; // 必须写全限定名：本文件已 import android.os.Process
        Thread watchdog = null;
        BufferedReader reader = null;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-t", String.valueOf(lines), "-v", "time"});
            final java.lang.Process watched = proc;
            watchdog = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        return; // 主流程已读完，提前退出，不留睡满 4 秒的线程
                    }
                    try { watched.destroy(); } catch (Throwable ignored) { }
                }
            }, "logcat-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();

            reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("---------")) continue;
                JSONObject item = new JSONObject();
                Matcher m = LOGCAT_LINE.matcher(s);
                if (m.matches()) {
                    item.put("time", m.group(2));
                    item.put("level", levelOf(m.group(3).charAt(0)));
                    item.put("tag", m.group(4).trim());
                    item.put("msg", clip(m.group(5), LOG_MSG_MAX));
                } else {
                    item.put("time", "");
                    item.put("level", "INFO");
                    item.put("tag", "SYSTEM");
                    item.put("msg", clip(s, LOG_MSG_MAX));
                }
                arr.put(item);
            }
            res.put("ok", true);
            res.put("logs", arr);
        } catch (Throwable t) {
            Log.w(TAG, "logs: " + t);
            try {
                res.put("ok", false);
                res.put("logs", arr);
                res.put("error", String.valueOf(t.getMessage()));
            } catch (Throwable ignored) { }
        } finally {
            if (watchdog != null) watchdog.interrupt();
            if (reader != null) {
                try { reader.close(); } catch (Throwable ignored) { }
            }
            if (proc != null) {
                try { proc.getErrorStream().close(); } catch (Throwable ignored) { }
                try { proc.getOutputStream().close(); } catch (Throwable ignored) { }
                try { proc.destroy(); } catch (Throwable ignored) { }
            }
            logBusy.set(false);
        }
        return res.toString();
    }

    /** 单条日志太长（堆栈）就截断，避免一次拉取被一条错误撑爆。 */
    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String levelOf(char c) {
        if (c == 'E' || c == 'F') return "ERR";
        if (c == 'W') return "WARN";
        if (c == 'D' || c == 'V') return "DEBUG";
        return "INFO";
    }

    /** 读取 /proc/meminfo，返回 [MemFreeKb, MemTotalKb]。 */
    static long[] memInfoKb() {
        long free = -1;
        long total = 490 * 1024L;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    total = Long.parseLong(line.replaceAll("[^0-9]", ""));
                } else if (line.startsWith("MemFree:")) {
                    free = Long.parseLong(line.replaceAll("[^0-9]", ""));
                }
            }
        } catch (Throwable ignored) { }
        finally {
            if (r != null) {
                try { r.close(); } catch (Throwable ignored) { }
            }
        }
        return new long[]{free, total};
    }

    /** 读取 SoC 核心温度 (/sys/class/thermal/thermal_zone0/temp)。 */
    private static double socTempC() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/sys/class/thermal/thermal_zone0/temp"));
            String s = r.readLine();
            if (s != null) {
                double v = Double.parseDouble(s.trim());
                if (v > 1000) v = v / 1000.0;
                return Math.round(v * 10.0) / 10.0;
            }
        } catch (Throwable ignored) { }
        finally {
            if (r != null) {
                try { r.close(); } catch (Throwable ignored) { }
            }
        }
        return -1;
    }

    /** 读取 APK assets 内的静态资源；小文件做进程内缓存，减少反复读 zip 的 IO。 */
    private byte[] assetOrNull(String name) {
        synchronized (assetLock) {
            byte[] cached = assetCache.get(name);
            if (cached != null) return cached;
        }
        InputStream is = null;
        try {
            is = ctx.getAssets().open(name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            byte[] data = bos.toByteArray();
            synchronized (assetLock) {
                if (data.length <= 256 * 1024 && assetCacheBytes + data.length <= 1024 * 1024) {
                    assetCache.put(name, data);
                    assetCacheBytes += data.length;
                } else if (!assetCacheWarned) {
                    // 超限后不再缓存，每次请求都会重新读 APK；这不是错误，但性能会退化，至少留一条日志。
                    assetCacheWarned = true;
                    Log.w(TAG, "静态资源缓存已满（" + assetCacheBytes + " 字节），"
                            + name + " 起不再缓存，后续请求将重新读取 APK");
                }
            }
            return data;
        } catch (Throwable t) {
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private static String jsonError(String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", msg);
            return o.toString();
        } catch (Throwable t) {
            return "{\"ok\":false}";
        }
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws Exception {
        send(out, code, type, body, "no-store");
    }

    /**
     * 发送响应。
     *
     * @param cacheControl 缓存策略：API/动态内容用 {@code no-store}；内容不变的静态资源
     *                     用 {@code public, max-age=300}，避免每次刷新都重传约 300KB。
     */
    private static void send(OutputStream out, int code, String type, byte[] body,
                             String cacheControl) throws Exception {
        String reason = reasonOf(code);
        if (code >= 400) {
            String preview = "";
            try {
                preview = new String(body, "UTF-8");
                if (preview.length() > 160) preview = preview.substring(0, 160);
            } catch (Throwable ignored) { }
            Log.w(TAG, "HTTP " + code + " " + String.valueOf(CURRENT_REQUEST.get()) + " " + preview);
        }

        String head = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type, X-Token, Authorization\r\n"
                + "Access-Control-Max-Age: 600\r\n"
                + "Cache-Control: " + cacheControl + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.write(body);
    }

    private static String reasonOf(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 413: return "Payload Too Large";
            case 415: return "Unsupported Media Type";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 501: return "Not Implemented";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return code < 400 ? "OK" : "Error";
        }
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') bos.write(c);
            if (bos.size() > 8192) {
                // 超长头：丢弃本行剩余字节，避免后半行被当成下一个 header
                int d;
                while ((d = in.read()) != -1 && d != '\n') { }
                break;
            }
        }
        if (c == -1 && bos.size() == 0) return null;
        return new String(bos.toByteArray(), "UTF-8");
    }
}
