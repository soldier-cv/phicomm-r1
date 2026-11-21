package com.r1.brain;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * 离线验证 {@link LightEffect}：语音灯环指令与智能家居指令不再互相抢。
 *
 * <p>只调用纯 Java 的静态方法，不碰 Android API，结果写 UTF-8 文件。
 *
 * @author xudong.hua,claude
 * @since 2026-11-05 19:20 星期四
 */
public class LightProbe {

    public static void main(String[] args) throws Exception {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[0]), "UTF-8"), true);
        Object[][] cases = {
                {"关灯", LightEffect.OFF},
                {"把灯关了", LightEffect.OFF},
                {"开灯", LightEffect.WAKEUP},
                {"打开呼吸灯", LightEffect.LOADING},
                {"开个流光", LightEffect.SCAN},
                {"小夜灯", LightEffect.DORMANT},
                {"关掉灯环", LightEffect.OFF},
                {"把灯环打开", LightEffect.WAKEUP},
                {"客厅关灯", null},
                {"关闭客厅灯", null},
                {"打开卧室的灯", null},
                {"把台灯关了", null},
                {"打开灯带", null},
                {"现在几点", null},
        };
        int fail = 0;
        for (Object[] c : cases) {
            LightEffect got = LightEffect.match((String) c[0]);
            boolean ok = got == c[1];
            if (!ok) fail++;
            out.println((ok ? "[PASS] " : "[FAIL] ") + c[0] + " -> " + got + "（期望 " + c[1] + "）");
        }
        boolean unknown = LightEffect.of("rainbow") == null && LightEffect.of("Loading") == LightEffect.LOADING;
        if (!unknown) fail++;
        out.println((unknown ? "[PASS] " : "[FAIL] ") + "未知协议值返回 null、大小写不敏感");
        boolean ambient = !LightEffect.NET_LOST.isAmbient() && LightEffect.SCAN.isAmbient();
        if (!ambient) fail++;
        out.println((ambient ? "[PASS] " : "[FAIL] ") + "断网提示不能当常亮效果");
        out.println(fail == 0 ? "ALL PASS" : "FAILURES: " + fail);
        out.close();
        System.exit(fail == 0 ? 0 : 1);
    }
}
