package com.r1.brain;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 本地自检探针（不进 APK）：验证 HttpServer / Config / SpeechEngine 里不依赖 Android 的纯函数。
 *
 * <p>为什么不写 JVM 单元测试：项目约定见 docs/23-testing.md §七 —— 业务逻辑紧贴 Android API。
 * 这里只覆盖**纯函数**（reasonOf / readChunked / clampMaxTokens / split / isAllowedSource），
 * 用和 IntentProbe 相同的方式在电脑上跑，能在上机前拦住「状态行写错」「分句不分」这类回归。
 *
 * <p>依赖 {@code org.json} 的行为（Config.toJson / applyJson）在电脑上无法执行 ——
 * android.jar 里的 org.json 是 stub（调用会抛 {@code RuntimeException: Stub!}），
 * 改用设备上的接口冒烟验证，命令见 docs/23-testing.md。
 *
 * <p>用法（在仓库根目录）：
 * <pre>
 *   $sdk = "$env:LOCALAPPDATA\Android\Sdk"
 *   javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$sdk\platforms\android-36\android.jar" `
 *     -cp l2-app\build\classes -d $env:TEMP\r1probe tools\HttpProbe.java
 *   java -cp "l2-app\build\classes;$env:TEMP\r1probe;$sdk\platforms\android-36\android.jar" `
 *     com.r1.brain.HttpProbe "$env:TEMP\r1probe\http.txt"
 *   Get-Content "$env:TEMP\r1probe\http.txt" -Encoding UTF8
 * </pre>
 *
 * @author xudong.hua,deepseek
 * @since 2026-11-05 12:00 星期四
 */
public class HttpProbe {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) throws Exception {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(args[0]), "UTF-8"));
        try {
            Class<?> hs = Class.forName("com.r1.brain.HttpServer");
            Method reason = hs.getDeclaredMethod("reasonOf", int.class);
            Method parse = hs.getDeclaredMethod("parseJson", String.class);
            Method chunked = hs.getDeclaredMethod("readChunked", java.io.InputStream.class, int.class);
            reason.setAccessible(true);
            parse.setAccessible(true);
            chunked.setAccessible(true);

            check(w, "reasonOf(200) = OK", "OK".equals(reason.invoke(null, 200)));
            check(w, "reasonOf(302) = Found", "Found".equals(reason.invoke(null, 302)));
            check(w, "reasonOf(500) = Internal Server Error",
                    "Internal Server Error".equals(reason.invoke(null, 500)));
            check(w, "reasonOf(503) = Service Unavailable",
                    "Service Unavailable".equals(reason.invoke(null, 503)));
            check(w, "reasonOf(413) = Payload Too Large",
                    "Payload Too Large".equals(reason.invoke(null, 413)));

            check(w, "parseJson 已收口（JSON 行为在设备上验证）", parse != null);

            Method allowed = hs.getDeclaredMethod("methodAllowed", String.class, String.class);
            allowed.setAccessible(true);
            check(w, "methodAllowed(GET, status) = true",
                    Boolean.TRUE.equals(allowed.invoke(null, "GET", "/api/v1/status")));
            check(w, "methodAllowed(POST, status) = false",
                    Boolean.FALSE.equals(allowed.invoke(null, "POST", "/api/v1/status")));
            check(w, "methodAllowed(POST, chat) = true",
                    Boolean.TRUE.equals(allowed.invoke(null, "POST", "/api/v1/chat")));
            check(w, "methodAllowed(GET, chat) = false",
                    Boolean.FALSE.equals(allowed.invoke(null, "GET", "/api/v1/chat")));
            check(w, "methodAllowed(DELETE, history) = true",
                    Boolean.TRUE.equals(allowed.invoke(null, "DELETE", "/api/v1/history")));

            Object body = chunked.invoke(null,
                    new ByteArrayInputStream("4\r\n{\"a\"\r\n0\r\n\r\n".getBytes("UTF-8")), 1024);
            check(w, "readChunked 正确解码",
                    body != null && "{\"a\"".equals(new String((byte[]) body, "UTF-8")));
            Object tooBig = chunked.invoke(null,
                    new ByteArrayInputStream("10\r\n0123456789abcdef\r\n0\r\n\r\n".getBytes("UTF-8")), 4);
            check(w, "readChunked 超限返回 null", tooBig == null);

            boolean hasToJson = false;
            boolean hasApplyJson = false;
            for (Method m : Config.class.getMethods()) {
                if ("toJson".equals(m.getName()) && m.getParameterTypes().length == 1) hasToJson = true;
                if ("applyJson".equals(m.getName())) hasApplyJson = true;
            }
            check(w, "Config.toJson / applyJson 已收口", hasToJson && hasApplyJson);
            check(w, "clampMaxTokens(0) = 32", Config.clampMaxTokens(0) == 32);
            check(w, "clampMaxTokens(9999) = 2048", Config.clampMaxTokens(9999) == 2048);
            check(w, "normalizeChatUrl 补 /v1", "https://api.x.com/v1/chat/completions"
                    .equals(LlmClient.normalizeChatUrl("https://api.x.com")));

            // TTS 分句：没有句号、只有逗号的长句也必须被切开
            Class<?> se = Class.forName("com.r1.brain.SpeechEngine");
            Method split = se.getDeclaredMethod("split", String.class);
            split.setAccessible(true);
            List<?> parts = (List<?>) split.invoke(null,
                    "好的。这是一句很长很长的话，但是里面只有逗号没有句号，应该按逗号切开，"
                            + "然后再继续往下说，直到超过六十个字为止，这样才不会让单句无限长下去，嗯，就这样。");
            boolean allShort = true;
            for (Object p : parts) {
                if (((String) p).length() > 60) allShort = false;
            }
            check(w, "split 每句 <= 60 字", allShort && parts.size() >= 3);

            check(w, "isAllowedSource 拒绝 ..",
                    !MusicPlayer.isAllowedSource("/sdcard/Music/../../etc/passwd"));
            check(w, "isAllowedSource 允许 http",
                    MusicPlayer.isAllowedSource("http://example.com/a.mp3"));
        } finally {
            w.printf("%n通过 %d / %d%n", pass, pass + fail);
            w.close();
        }
        if (fail > 0) System.exit(1);
    }

    private static void check(PrintWriter w, String name, boolean ok) {
        w.printf("[%s] %s%n", ok ? "OK" : "FAIL", name);
        if (ok) pass++; else fail++;
    }
}
