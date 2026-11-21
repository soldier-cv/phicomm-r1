package com.r1.brain;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * R1 软硬件运维中枢核心服务：应用安装卸载、进程管理、自我升级与系统指令穿透。
 *
 * 通过本地回环 ADB（127.0.0.1:5555）以系统 shell (uid=2000) 身份执行特权指令，
 * 解决普通非系统 App 无法静默安装、卸载应用和重启音箱的权限瓶颈。
 *
 * @author xudong.hua,gemini
 * @since 2026-10-30 17:18 星期五
 */
public class DeviceOps {

    private static final String TAG = Brain.TAG;
    private static final int ADB_PORT = 5555;
    private static final int ADB_TIMEOUT_MS = 15000;
    /** pm install 一个大包（30MB+）在这台机器上要 30~60 秒，不能用默认 15 秒看门狗。 */
    private static final int ADB_INSTALL_TIMEOUT_MS = 180000;

    /**
     * 执行底层 Shell 命令（优先通过本地回环 ADB 穿透，ADB 不可用时降级调用 Runtime.exec）。
     */
    public static String execShell(String command) {
        return execShell(command, ADB_TIMEOUT_MS);
    }

    /**
     * 同 {@link #execShell(String)}，但可指定超时。
     *
     * @param command   要执行的命令
     * @param timeoutMs 超时毫秒数，到点会强杀子进程
     * @return 命令输出
     */
    public static String execShell(String command, int timeoutMs) {
        if (command == null || command.trim().isEmpty()) return "";
        try {
            String adbOut = execAdbRaw(command.trim(), timeoutMs);
            if (adbOut != null && !adbOut.startsWith("ADB_ERR:")) {
                return adbOut.trim();
            }
        } catch (Throwable t) {
            Log.w(TAG, "execShell adb loopback failed: " + t);
        }

        Process p = null;
        InputStream in = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "(" + command.trim() + ") 2>&1"});
            final Process watched = p;
            Thread watchdog = new Thread(new Runnable() {
                @Override
                public void run() {
                    try { Thread.sleep(timeoutMs); } catch (Throwable ignored) { }
                    try { watched.destroy(); } catch (Throwable ignored) { }
                }
            }, "shell-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();

            in = p.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > 512 * 1024) break;
                bos.write(buf, 0, n);
            }
            p.waitFor();
            return new String(bos.toByteArray(), "UTF-8").trim();
        } catch (Throwable t) {
            Log.e(TAG, "execShell runtime fallback error: " + t);
            return "ERROR: " + t.getMessage();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable ignored) { }
            }
            if (p != null) {
                try { p.getErrorStream().close(); } catch (Throwable ignored) { }
                try { p.getOutputStream().close(); } catch (Throwable ignored) { }
                try { p.destroy(); } catch (Throwable ignored) { }
            }
        }
    }

    /**
     * 通过原生 TCP 协议与音箱本机 ADB 守护进程 (127.0.0.1:5555) 通信执行命令。
     */
    private static String execAdbRaw(String shellCmd) {
        return execAdbRaw(shellCmd, ADB_TIMEOUT_MS);
    }

    /**
     * 通过原生 TCP 协议与音箱本机 ADB 守护进程 (127.0.0.1:5555) 通信执行命令。
     *
     * @param shellCmd  要执行的命令
     * @param timeoutMs 单次读超时，长命令（如 pm install）要放大
     */
    private static String execAdbRaw(String shellCmd, int timeoutMs) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", ADB_PORT), 3000);
            s.setSoTimeout(timeoutMs);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            byte[] hostPayload = "host::\0".getBytes("US-ASCII");
            out.write(buildAdbPacket(0x4e584e43, 0x01000000, 4096, hostPayload));
            out.flush();

            byte[] head = readExact(in, 24);
            if (head == null) return "ADB_ERR: no cnxn response";
            ByteBuffer hb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
            int cmd = hb.getInt();
            int pLen = hb.getInt(12);
            if (pLen > 0) readExact(in, pLen);

            if (cmd != 0x4e584e43) {
                return "ADB_ERR: cnxn rejected: " + Integer.toHexString(cmd);
            }

            byte[] openPayload = ("shell:" + shellCmd + "\0").getBytes("UTF-8");
            out.write(buildAdbPacket(0x4e45504f, 1, 0, openPayload));
            out.flush();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int remoteId = 0;

            while (true) {
                byte[] msgHdr = readExact(in, 24);
                if (msgHdr == null) break;
                ByteBuffer mb = ByteBuffer.wrap(msgHdr).order(ByteOrder.LITTLE_ENDIAN);
                int mCmd = mb.getInt();
                int arg0 = mb.getInt();
                int arg1 = mb.getInt();
                int mLen = mb.getInt();

                byte[] mData = mLen > 0 ? readExact(in, mLen) : new byte[0];

                if (mCmd == 0x59414b4f) {
                    remoteId = arg0;
                } else if (mCmd == 0x45545257) {
                    if (mData != null && mData.length > 0) {
                        output.write(mData);
                    }
                    out.write(buildAdbPacket(0x59414b4f, 1, remoteId, new byte[0]));
                    out.flush();
                } else if (mCmd == 0x45534c43) {
                    break;
                }
            }

            return new String(output.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "ADB_ERR: " + t.getMessage();
        } finally {
            if (s != null) {
                try { s.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private static byte[] buildAdbPacket(int command, int arg0, int arg1, byte[] data) {
        int dLen = data != null ? data.length : 0;
        int check = 0;
        if (data != null) {
            for (byte b : data) {
                check += (b & 0xff);
            }
        }
        ByteBuffer bb = ByteBuffer.allocate(24 + dLen).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(command);
        bb.putInt(arg0);
        bb.putInt(arg1);
        bb.putInt(dLen);
        bb.putInt(check);
        bb.putInt(command ^ 0xffffffff);
        if (dLen > 0) {
            bb.put(data);
        }
        return bb.array();
    }

    private static byte[] readExact(InputStream in, int len) {
        if (len <= 0) return new byte[0];
        byte[] buf = new byte[len];
        int read = 0;
        try {
            while (read < len) {
                int n = in.read(buf, read, len - read);
                if (n < 0) return null;
                read += n;
            }
            return buf;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 获取音箱上所有已安装应用列表（标记是否为系统应用与运行状态，并按第三方应用优先排序）。
     */
    public static JSONArray getInstalledApps(Context ctx) {
        JSONArray arr = new JSONArray();
        PackageManager pm = ctx.getPackageManager();
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);

        List<String> runningPkgs = new ArrayList<String>();
        try {
            List<RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs != null) {
                for (RunningAppProcessInfo pi : procs) {
                    if (pi.pkgList != null) {
                        for (String p : pi.pkgList) {
                            if (!runningPkgs.contains(p)) runningPkgs.add(p);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        List<PackageInfo> pkgs = pm.getInstalledPackages(0);
        List<JSONObject> list = new ArrayList<JSONObject>();

        for (PackageInfo p : pkgs) {
            try {
                JSONObject o = new JSONObject();
                o.put("packageName", p.packageName);
                o.put("versionName", p.versionName != null ? p.versionName : "--");
                o.put("versionCode", p.versionCode);
                CharSequence label = p.applicationInfo != null ? p.applicationInfo.loadLabel(pm) : p.packageName;
                o.put("name", label != null ? label.toString() : p.packageName);

                boolean isSystem = (p.applicationInfo != null)
                        && ((p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                o.put("isSystem", isSystem);
                o.put("isRunning", runningPkgs.contains(p.packageName));

                long sizeBytes = 0;
                if (p.applicationInfo != null && p.applicationInfo.sourceDir != null) {
                    File f = new File(p.applicationInfo.sourceDir);
                    if (f.exists()) sizeBytes = f.length();
                }
                o.put("sizeBytes", sizeBytes);
                o.put("sizeMb", String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0)));

                list.add(o);
            } catch (Throwable ignored) {
            }
        }

        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                boolean aSys = a.optBoolean("isSystem", false);
                boolean bSys = b.optBoolean("isSystem", false);
                if (aSys != bSys) return aSys ? 1 : -1;
                return a.optString("packageName").compareTo(b.optString("packageName"));
            }
        });

        for (JSONObject o : list) {
            arr.put(o);
        }
        return arr;
    }

    /**
     * 静默安装指定路径的 APK 文件（若是当前应用则标记 selfUpdate 并自动重启服务）。
     */
    public static JSONObject installApk(Context ctx, File apkFile) {
        JSONObject res = new JSONObject();
        if (apkFile == null || !apkFile.exists()) {
            try {
                res.put("success", false);
                res.put("error", "安装包文件不存在");
            } catch (Throwable ignored) { }
            return res;
        }
        // 路径会被拼进 `pm install -r <path>`，只接受安全字符，避免空格/分号改变命令语义
        String apkPath = apkFile.getAbsolutePath();
        if (!apkPath.matches("[A-Za-z0-9_./\\-]+")) {
            try {
                res.put("success", false);
                res.put("error", "安装包路径包含非法字符");
            } catch (Throwable ignored) { }
            return res;
        }

        PackageManager pm = ctx.getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
        String pkgName = pi != null ? pi.packageName : "";
        boolean isSelf = ctx.getPackageName().equalsIgnoreCase(pkgName);

        String cmd = "pm install -r " + apkFile.getAbsolutePath();
        String out = execShell(cmd, ADB_INSTALL_TIMEOUT_MS);
        boolean ok = out != null && out.contains("Success");

        if (!ok && out != null && out.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") && !pkgName.isEmpty()) {
            execShell("pm uninstall " + pkgName, 60000);
            out = execShell(cmd, ADB_INSTALL_TIMEOUT_MS);
            ok = out != null && out.contains("Success");
        }

        try {
            res.put("success", ok);
            res.put("package", pkgName);
            res.put("selfUpdate", isSelf);
            res.put("output", out != null ? out.trim() : "无输出");
        } catch (Throwable ignored) { }

        if (ok) {
            apkFile.delete();
            if (isSelf) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try { Thread.sleep(1200); } catch (Throwable ignored) { }
                        execShell("am startservice -n com.r1.brain/.MainService");
                    }
                }).start();
            }
        }
        return res;
    }

    /**
     * 卸载指定应用。
     */
    public static boolean uninstallApp(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return false;
        String p = packageName.trim();
        // 包名是拼进 shell 的，必须严格白名单，避免空格/分号被当成命令分隔符
        if (!p.matches("[A-Za-z0-9_.]+")) return false;
        String out = execShell("pm uninstall --user 0 -k " + p);
        if (out != null && out.contains("Success")) return true;
        out = execShell("pm uninstall " + p);
        return out != null && out.contains("Success");
    }

    /**
     * 强行停止目标应用。
     */
    public static boolean stopApp(Context ctx, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return false;
        String p = packageName.trim();
        if (!p.matches("[A-Za-z0-9_.]+")) return false;
        execShell("am force-stop " + p);
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            am.killBackgroundProcesses(p);
        } catch (Throwable ignored) {
        }
        return true;
    }

    /**
     * 重启斐讯 R1 音箱系统。
     */
    public static boolean reboot() {
        execShell("reboot");
        return true;
    }

    /**
     * 清理临时文件目录并执行 JVM 垃圾回收。
     */
    public static JSONObject cleanSystem(Context ctx) {
        JSONObject r = new JSONObject();
        long deletedBytes = 0;
        int deletedFiles = 0;
        try {
            File tmp = new File("/data/local/tmp");
            if (tmp.exists() && tmp.isDirectory()) {
                File[] list = tmp.listFiles();
                if (list != null) {
                    for (File f : list) {
                        if (f.isFile() && (f.getName().endsWith(".apk") || f.getName().endsWith(".log") || f.getName().startsWith("tmp"))) {
                            long len = f.length();
                            if (f.delete()) {
                                deletedBytes += len;
                                deletedFiles++;
                            }
                        }
                    }
                }
            }
            File cache = ctx.getCacheDir();
            if (cache != null && cache.isDirectory()) {
                File[] list = cache.listFiles();
                if (list != null) {
                    for (File f : list) {
                        if (f.isFile() && f.getName().endsWith(".mp3")) {
                            long len = f.length();
                            if (f.delete()) {
                                deletedBytes += len;
                                deletedFiles++;
                            }
                        }
                    }
                }
            }
            System.gc();
            r.put("success", true);
            r.put("deletedFiles", deletedFiles);
            r.put("freedMb", String.format("%.2f MB", deletedBytes / (1024.0 * 1024.0)));
        } catch (Throwable t) {
            try {
                r.put("success", false);
                r.put("error", t.getMessage());
            } catch (Throwable ignored) { }
        }
        return r;
    }
}
