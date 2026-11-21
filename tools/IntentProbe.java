package com.r1.brain;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 临时验证工具（不进 APK）：把 IntentRouter 的归一化与点歌解析单独跑一遍。
 *
 * 只调用三个不依赖 Android 的私有静态方法：stripLead / fixNearMiss / parseSong。
 * 结果写到 UTF-8 文件，避免控制台代码页把中文搞乱。
 */
public class IntentProbe {

    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("com.r1.brain.IntentRouter");
        Method strip = c.getDeclaredMethod("stripLead", String.class);
        Method fix = c.getDeclaredMethod("fixNearMiss", String.class);
        Method parse = c.getDeclaredMethod("parseSong", String.class);
        strip.setAccessible(true);
        fix.setAccessible(true);
        parse.setAccessible(true);

        String[] cases = {
                "小讯小讯，播放周杰伦的歌。",
                "小讯小讯播放周杰伦的歌",
                "嗯，那个连续兑换。",
                "连系兑换",
                "把音两调小一点",
                "帮我播放七里香",
                "我想听周杰伦的歌",
                "给我放一首晴天",
                "天汽怎么样",
                "明天闹中七点",
                "下一手",
                "播放周杰伦的歌。",
                "讲个故事",
                "暂停播放",
                "关闭连续对话",
                "小讯小讯"
        };

        PrintWriter w = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(args[0]), "UTF-8"));
        for (String raw : cases) {
            String lead = (String) strip.invoke(null, raw);
            String norm = (String) fix.invoke(null, lead);
            Object q = parse.invoke(null, norm);
            String kw = "-";
            String artist = "-";
            if (q != null) {
                Field fk = q.getClass().getDeclaredField("keyword");
                Field fa = q.getClass().getDeclaredField("artistMode");
                fk.setAccessible(true);
                fa.setAccessible(true);
                kw = String.valueOf(fk.get(q));
                artist = String.valueOf(fa.get(q));
            }
            w.printf("%-26s | 归一: %-18s | 点歌关键词: %-12s | artist=%s%n",
                    raw, norm, kw, artist);
        }
        w.close();
    }
}
