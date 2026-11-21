# L1 桥（原厂包注入层）

把 `com.phicomm.speaker.device` 顶替成「原厂唤醒/ASR + 我们的一条广播转发」的薄壳。

## 它做什么

```
本地唤醒「小讯小讯」
   ↓
原厂本地 ASR
   ↓
NLUDispatcher.onASRResultLocal / onASRResultNet   ← 我们在这里插一行
   ↓ Bridge.onAsrResult(json)
sendBroadcast("com.r1.bridge") → com.r1.brain（L2）
   ↓
L2 调大模型 → TTS → 出声
```

**只做转发，不做业务。** 业务全部在 L2，改动不用重新打包 L1。

## 准备底包

原厂底包 `l1/vendor/Unisound_original.apk` 不随仓库分发（原厂专有，已在 `.gitignore`）。
首次构建前从你自己的设备拉取：

```powershell
adb pull /system/app/Unisound/Unisound.apk l1/vendor/Unisound_original.apk
```

`build.ps1` 会校验它的 SHA-256（`a0548242…f8841bd7`），不匹配就拒绝构建。

## 构建

```powershell
pwsh -File .\l1\build.ps1              # 用 vendor 里的底包构建
pwsh -File .\l1\build.ps1 -PullBase    # 额外与设备上的原厂包比对 SHA-256
```

产物：`l1/build/r1brain-l1.apk`

## 为什么必须这样打包

| 阶段 | 谁在看 | 看到什么 |
|---|---|---|
| 安装 | PackageManager | 只校验 zip → 签名来自原厂 APK → 能覆盖系统应用 |
| 运行 | ART | 只看文件开头 → `dex\n035` → 执行我们前置的完整 DEX |

所以前置 DEX 必须是「原厂 5,306 个类 + 我们的类」合在一起的完整 DEX；
zip 部分（24.9MB / 134 条目）**一个字节都不能动**，否则签名失效。

## 目录

```
l1/
├── src/                         我们的 Java 源码（MIT）
│   └── com/phicomm/speaker/device/bridge/Bridge.java
├── tools/
│   ├── build_janus.py           Janus 容器构造
│   └── verify_janus.py          容器自检（五道）
├── build.ps1                    一键构建
├── vendor/                      原厂底包（需自行准备，不入库）
├── .cache/                      反汇编缓存（不入库）
├── .tools/                      smali / baksmali（不入库）
└── build/                       产物（不入库）
```

## 准备工具

`.tools/` 里需要 7 个 jar（Maven Central，`repo1.maven.org`）：

```
org/smali/baksmali/2.5.2/baksmali-2.5.2.jar
org/smali/smali/2.5.2/smali-2.5.2.jar
org/smali/util/2.5.2/util-2.5.2.jar
org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar
com/google/guava/guava/27.1-android/guava-27.1-android.jar
com/beust/jcommander/1.64/jcommander-1.64.jar
org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar
```

另需 Android SDK 的 `platforms/android-34/android.jar` 与 `build-tools/36.0.0/lib/d8.jar`。

## 安装与回退

```powershell
# 安装（会覆盖系统应用，因为签名一致）
adb install -r l1\build\r1brain-l1.apk

# 回退到原厂（卸载 data 分区覆盖版，系统自动回落 /system/app/Unisound）
adb shell pm uninstall com.phicomm.speaker.device
```

> ⚠️ 如果当前装的是第三方 Janus 包（签名不同），必须先 `pm uninstall` 再装，否则报
> `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
