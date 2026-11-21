# 第三方 MOD 包结构分析（Janus / 代码构成 / 可拆解清单）

> 对象：`artifacts/device_live_janus.apk`
> md5 `5e96e68c2595edd37f14d6ee868a0951`，33,064,967 B
> 与 `D:\BaiduNetdiskDownload\R1 MOD apk\一键安装 (20261007)\一键安装\apps\unisound_MOD_v1.0.4.apk` 同一份
> 方法：`aapt` / `dexdump -d` / `jarsigner` / `keytool` / 自写 Python 式布局解析
> 标记：**看到** = 命令输出可见；**推断** = 由证据推出；**未知** = 没验证

---

## 1. 文件结构：这是一个 Janus 包（看到）

```
offset 0            6465780a 30333500   "dex\n035\0"        ← DEX 魔数
offset 32           33064967           DEX header 的 file_size = 整个文件
offset 8,128,580    PK\x03\x04         zip 第一个 local header
offset 33,053,875   PK\x01\x02         zip 中央目录（绝对偏移）
offset 33,064,945   PK\x05\x06         EOCD
```

| 区段 | 范围 | 大小 |
|---|---|---:|
| 前置 DEX | 0 – 8,128,580 | 8.1 MB |
| 内层 APK（zip） | 8,128,580 – 33,064,967 | **24,936,387 B** |

**含义（看到 + 推断）**

- ART 打开这个 APK 文件时先读到 `dex\n035`，于是**把整个文件当成一个 DEX 加载** → 真正执行的代码是前置那 8.1 MB。
- PackageManager 走 zip 一侧：读中央目录 → 安装、提取 `lib/`、读 `AndroidManifest.xml`。
- 中央目录里的偏移是**绝对偏移**（指向 33 MB 文件内部）。把尾部 24.9 MB 单独切出来，偏移全错，`aapt` 会报 `bad offsets`；把每个 entry 的 local header offset 减去 8,128,580 后，它就是一个完整的、能被 `aapt` 读的原厂 APK。
- 内层 APK 大小 24,936,387 B，与 `11-runbook.md` 记录的原厂 `Unisound.apk` 大小一致（**推断**：内层就是原厂 APK 原样放入）。

---

## 2. 签名（看到）

```
jarsigner -verify  → 由 "EMAILADDRESS=phicomm@phicomm.com, CN=R1, OU=ShenZhen RD Center,
                     O=Company, L=ShenZhen, ST=GuangDong, C=CN" 签名
算法：SHA256withRSA, 2048 位    条目时间：2017-11-29
keytool SHA1: F1:9D:BC:11:51:37:57:DA:6F:EC:0F:55:C0:90:01:2C:CE:F5:24:66
```

- **不是**真正的斐讯证书，是伪造 Subject 的自签名证书。
- v1（JAR）签名只覆盖 zip 条目，**不覆盖前置 DEX** —— 这就是为什么可以直接改代码而签名照样通过。
- **后果（推断）**：换一台没装过这个 MOD 的原厂机，`pm install` 会撞 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（与 `/system/app/Unisound` 的签名不符），必须先卸载系统包。

---

## 3. 包里有两套代码（看到）

| | zip 内 `classes.dex` | **前置 DEX（真正运行）** |
|---|---:|---:|
| 类总数 | 5,306 | **6,935** |
| `com.unisound.*` | 997 | **836** |
| `com.phicomm.speaker.*` | 143 | **217** |
| └ `…device.custom.*` | 94 | **163** |
| └ `…custom.config.WebServer` | 0 | **10** |
| `io.ktor.*` | 0 | **699** |
| `xyz.yhsj.*`（kmusic + 自带 json） | 0 | **351** |
| `android.support.v4.*` | 763 | 0 |
| `androidx.*` | 0 | ~600 |
| `rx.*` | 473 | 0 |

**结论（推断）**：作者不是"打补丁"，而是**把整个 App 重写了一遍**（support→androidx、rx→协程、新加 Ktor + Kotlin + 自家音乐库），并把原厂的 `classes.dex` 留在 zip 里不执行。

---

## 4. 他增删了什么（看到，按类名前缀统计）

前置 DEX 相对 zip 内 dex：**删掉 2,631 类，新增 4,260 类**

| 删掉 | 数量 | | 新增 | 数量 |
|---|---:|---|---|---:|
| `android.support.v4` | 760 | | `io.ktor.*` | 699 |
| `rx.internal.*` / `rx` | 473 | | `androidx.*` | ~600 |
| `com.google.android.*` | 230 | | `com.baidu.mobstat` | 302 |
| `com.unisound.vui.*` | **139** | | `xyz.yhsj.kmusic` | 274 |
| `com.tencent.bugly` | 124 | | `com.alibaba.fastjson` | 175 |
| `org.apache.commons` | 106 | | `com.google.android.*` | 102 |
| `com.kaolafm.sdk` | 60 | | `com.baidu.location` | 98 |
| `com.baidu.location` | 60 | | | |
| `org.eclipse.paho`（MQTT） | 52 | | | |

> ⚠️ **`com.unisound.vui` 被删了 139 个类**，这是最需要逐个确认的一批：
> 里面很可能就包含云知声的**云端鉴权 / 三道闸门**逻辑。也就是说作者已经把 SDK 改过，
> 他这份 836 类的 unisound 和原厂 997 类的 unisound **不是同一个东西**。
> **未确认**：具体删了哪些、是否有本地 wake/ASR 相关类被误删。

---

## 5. 8080 是哪个类（看到）

```
com.phicomm.speaker.device.custom.config.WebServer        ← 端口常量 8080
com.phicomm.speaker.device.custom.config.WebServer$1/$4/$6
io.ktor.*                                                  ← 699 类，Kotlin 的 HTTP 框架
```

- 8080 的页面 HTML 是**代码里的字符串常量**：直接在 dex 里能 grep 到
  `placeholder='http://ip:3000'`、`toggle_ambient_light`、`qr.alipay.com/fkx11842wkvrfij9dcy80e2` 等。
- `xyz.yhsj.kmusic`（351 类）是作者自己的多源音乐库，已实现：
  - QQ 音乐：`c.y.qq.com/soso/...`、`fcg_music_express_mobile3.fcg`、`dl.stream.qqmusic.qq.com`
  - 百度 / 虾米 / 千千（taihe）/ 网易代理
- 页面里还有支付宝/微信打赏二维码（一键包 9.9 元那个）。

> **对项目的影响**：作者的音乐登录有**现成实现**，但这是闭源二进制；
> 能不能用取决于他开源时的许可证。**未知**。

---

## 6. 语音链的落点（看到）

| 位置 | 证据 |
|---|---|
| `com.phicomm.speaker.device.utils.PhicommUtils` | 字符串 `"asr word: "` —— 对应 logcat 的 `PPP: asr word: 播放音乐` |
| `com.unisound.vui.handler.filter.NLUDispatcher` | `onASREventRecordingStart` |
| `com.unisound.vui.handler.launch.DefaultANTLaunchHandler` | `onASREventRecordingStart` |
| 原生库 | `libuscasr.so`(363KB) `libasrfix.so`(1.16MB) `libofflinenlujni.so`(395KB) `libaec.so`(50KB) `libyzstts.so`(2.08MB) |
| 模型 | `assets/asrfix/data` **28,800,785 B**（唯一的大文件） |

**推断**：ASR 文本最终流经 `PhicommUtils`，这是 L1 最省事的挂钩点（一行日志的上一层就是回调）。

---

## 7. 可拆解清单

### 必须保留（语音能力）

| 内容 | 大小/数量 | 说明 |
|---|---|---|
| `com.unisound.*` | 836 类 | 唤醒 / ASR / 本地 NLU / TTS |
| `lib/armeabi-v7a/libuscasr.so` | 363 KB | 流式识别 |
| `lib/armeabi-v7a/libasrfix.so` | 1.16 MB | 离线模型加载 |
| `lib/armeabi-v7a/libofflinenlujni.so` | 395 KB | 本地 NLU |
| `lib/armeabi-v7a/libaec.so` | 50 KB | 回声消除 |
| `lib/armeabi-v7a/libyzstts.so` | 2.08 MB | 原厂 TTS |
| `assets/asrfix/data` | 28.8 MB | 离线模型 |
| `assets/defaultjsgf/` `assets/jsgf_clg/` | 675 KB + 89 KB | 语法/唤醒词 |
| `assets/{sweet,female,male,child,lzl}/*.pcm` | ~500 KB | 唤醒应答音 |
| `assets/cityCode.txt` `un2py.mg` `suffix/` | ~165 KB | 识别后处理 |

### 可以删（对语音无用）

| 内容 | 数量 | 用途 |
|---|---:|---|
| `com.phicomm.speaker.device.custom.config.WebServer*` | 10 | 8080 服务 |
| `io.ktor.*` | 699 | 8080 的 HTTP 框架 |
| `xyz.yhsj.*` | 351 | 作者的播放/音乐库 |
| `com.baidu.mobstat` | 302 | 百度统计 |
| `com.baidu.location` | 98 | 定位 |
| `androidx.*` | ~600 | 新版支持库（原厂用 support/v4） |
| `fastjson` / `gson` | 355 | JSON |
| `greendao` / `litepal` | 202 | 数据库 |
| `volley` / `okhttp` | 124 | 网络 |
| `paho`（MQTT） | 0（已删） | 推送 |

> **注意**：删类不等于安全。dex 里任何一处引用没删干净，类加载就会 `NoClassDefFoundError`。
> 所以"掏干净"这件事，靠手工删类风险很高，**正确做法是拿到源码重新编译**。

---

## 8. 三条改造路线

| | 做法 | 优点 | 风险 |
|---|---|---|---|
| **A** | 只替换前置 DEX，zip（24.9MB）一字节不动 | v1 签名继续有效；不用重签；最小改动 | 需要能产出合法 DEX；zip 偏移必须保持 8,128,580 |
| **B** | 用原厂 APK + 我们的 dex 重新做 Janus | 干净可控 | 要复现 Janus 构造；原厂 APK 的签名/偏移都要对 |
| **C** | 把 unisound 搬进 `com.r1.brain` | 只剩一个包 | SDK 有 `ASR_SDK_APPKEY_MD5_CHECK_ERROR` / `ASR_FIXENGINE_AUTHORIZE_ERROR` / `ActivatorInterface._packageName`，**未验证**换包名会不会被拒 |

---

## 9. 还没确认的（写代码前必须闭环）

1. **作者是否开源、用什么许可证** —— 有源码就不该逆向 dex（`D-10` 的教训：没 LICENSE = 保留所有权利）。
2. **被删掉的 139 个 `com.unisound.vui` 类是什么** —— 是不是拆闸门的关键。
3. **SDK 是否校验签名** —— 目前只看到 appkey / packageName / UDID 三类字段。

---

## 10. 容器重建验证（2026-10-30 实测）

按第 1 节的结构写了一个重建脚本：取出前置 DEX 区与原 zip 区，重新写入
`file_size = 全文件`、`SHA-1 = sha1(bytes[0x20..end])`、`Adler32 = adler32(bytes[0x0C..end])`。

**结果：输出与原始文件逐字节相同，md5 一致（`5e96e68c…0951`）。**

⇒ 容器格式已完全掌握，可以任意替换前置 DEX 而不破坏 zip 与签名。

### 10.1 DEX 的真实边界

`map_list` 里最后一张表结束于 **8,128,360**，zip 从 **8,128,580** 开始，
中间只有 220 字节填充。把前 8,128,580 字节单独截出来、把 `file_size` 改成 8,128,580
并重算校验，`dexdump` 报 `class_defs_size = 6935` —— 与全文件解析一致。

### 10.2 工具链

`artifacts/apktool.jar` 损坏（缺 EOCD），改用 Maven Central 的 smali 2.5.2：

```
org.smali:baksmali:2.5.2 / smali:2.5.2 / util:2.5.2 / dexlib2:2.5.2
com.google.guava:guava:27.1-android / com.beust:jcommander:1.64
```

反汇编 6,935 个类 → 56.8 MB smali 树。

---

## 11. ASR 劫持点：作者自己留了一个钩子（看到）

```
com/unisound/sdk/an.smali:1385
    invoke-static {p2}, Lcom/phicomm/speaker/device/utils/PhicommUtils;->hookRawAsr(Ljava/lang/String;)Ljava/lang/String;

PhicommUtils.hookRawAsr(String) -> String      # 全包唯一调用点
```

调用上下文：`an.a(JniUscClient; String; String; Z)V`（私有方法），
`p2` 是识别/语义结果的原始 JSON，`p3` 是结果类型。

`hookRawAsr` 内部（smali 行 594–860）：

1. `Log.d("PPP", "old asr raw res: " + json)`
2. `fastjson` 解析 → `getString("text")` → `Log.d("PPP", "asr word: " + text)`
3. 读 `service` 字段，`sparse-switch` 分发到 `cn.yunzhisheng.chat / .setting / .weather / .music …`
4. 按分支改写/返回 JSON

**结论**：作者就是靠这一个方法把云知声的结果劫持到自己业务上。
我们的 L1 只需要**替换这一个方法**：解析出 `text` → `sendBroadcast` 给 L2 → 返回。

⚠️ 唯一障碍：`hookRawAsr` 是静态方法、没有 `Context`。需要再打一个小补丁
（在 `ExampleApp.onCreate` 里把 application context 存到一个静态字段）。

---

## 12. 改造配方（已验证可行）

```
1. baksmali 反汇编前置 DEX
2. 改 smali（替换 hookRawAsr / 关掉 WebServer 启动 / 加广播）
3. smali 汇编 → new.dex
4. new.dex 补零到恰好 8,128,580 字节（保持 zip 起始偏移不变）
5. 拼接原 zip 的 24,936,387 字节
6. 重写 file_size / SHA-1 / Adler32
7. 得到新包 —— zip 未改动，v1 签名继续有效
```

第 4 步的"补零"是关键：只要 DEX 不超过 8,128,580 字节，
zip 的中央目录偏移就完全不用改。
