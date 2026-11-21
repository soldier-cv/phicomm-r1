# 设备部署与运维

> 本文替代一切"一键脚本"。**每条命令后面都写了期望输出**——照着做，能对得上就是对的。
> 文中所有结论都在 2026-10-31 的真机上跑过，未验证的内容会明确标「未验证」。
> 最后更新：2026-10-31

---

## 两条路，按需选择

| | 路径 A · 只想用 | 路径 B · 想动手 |
|---|---|---|
| 适用 | 不想动系统里的任何包 | 愿意 clone 仓库自己构建 |
| 需要 | 两个 APK | JDK + Android SDK + Python + Node |
| 装什么 | L2（必需）、L1（可选，语音唤醒） | 同左，另外可做系统瘦身 |
| 风险 | 低，随时 `pm uninstall` 退回 | 中，涉及顶替原厂包 |

---

# 路径 A · 用现成 APK 刷入

## A1. 两个包分别是什么

| 包 | 作用 | 大小 | 必需吗 |
|---|---|---:|---|
| `r1brain.apk`（L2） | 业务 App：控制台网页、大模型对话、TTS、音乐、闹钟。**普通应用** | ≈ 270 KB | **必需** |
| `r1brain-l1.apk`（L1） | 顶替 `com.phicomm.speaker.device`，保留原厂唤醒/ASR/灯环，把识别文本转发给 L2 | ≈ 31 MB | 可选（装了才有语音唤醒） |

> L1 会**顶替原厂包**。它用的是原厂平台签名（Janus 方式），所以能直接覆盖系统应用；
> 卸载后自动回落到 `/system/app/Unisound` 的原厂版本。

## A2. 连接设备

```powershell
adb connect <音箱IP>:5555
adb devices
```

期望：`<音箱IP>:5555    device`

> IP 会变（DHCP）。找不到就在路由器后台按 MAC `<音箱MAC>` 查，
> 或者扫本机网段的 8686 端口（L2 的控制台端口）。

## A3. 安装（两步，不要用 `adb install`）

```powershell
# ① 先停进程：进程还在跑时，pm 可能报 Success 却没真正换掉 APK（实测踩过）
adb -s <IP>:5555 shell "am force-stop com.r1.brain"

# ② 推包
adb -s <IP>:5555 push r1brain.apk /data/local/tmp/r1brain.apk

# ③ 设备端后台安装（adbd 不稳，后台跑完再读日志）
adb -s <IP>:5555 shell "cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/r1brain.apk > /data/local/tmp/i.log 2>&1' >/dev/null 2>&1 &"
```

等 15 秒：

```powershell
adb -s <IP>:5555 shell "cat /data/local/tmp/i.log"
```

期望：`pkg: /data/local/tmp/r1brain.apk` + `Success`

**再核对一次包真的换了**（只看 Success 不够）：

```powershell
adb -s <IP>:5555 shell "ls -l /data/app/com.r1.brain-*/base.apk"
```

期望：时间戳是刚才，大小与 `l2-app/build/r1brain.apk` 一致。

L1 同理，把文件名换成 `r1brain-l1.apk`，**等 45 秒**（31 MB）：

```powershell
adb -s <IP>:5555 push r1brain-l1.apk /data/local/tmp/r1brain-l1.apk
adb -s <IP>:5555 shell "cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/r1brain-l1.apk > /data/local/tmp/l1.log 2>&1' >/dev/null 2>&1 &"
# 等 45 秒后
adb -s <IP>:5555 shell "cat /data/local/tmp/l1.log"

> ⚠️ 装完 L1 后**原厂进程有时不会自启**（实测 2026-11-04 23:57：`pm install` 报 Success，
> 但 `ps` 里已经看不到 `com.phicomm.speaker.device`，表现是完全唤不醒）。补一条：
>
> ```bash
> adb -s <IP>:5555 shell "am start -n com.phicomm.speaker.device/.ui.MainActivity"
> ```
>
> 起来后再看 `R1BRIDGE` 日志里的 `engine attached:`，以及 `/api/v1/status` 的 `l1:true`。

```

> ⚠️ **不要用 `adb install`**：在这台机器上它会刷几千行 `waiting for device` 直到超时。
> 原因是 adbd 在长连接上会挂。实测四次，四次都卡。

## A4. 启动并验收

```powershell
adb -s <IP>:5555 shell "am startservice -n com.r1.brain/.MainService"
adb -s <IP>:5555 shell "am start -n com.phicomm.speaker.device/.ui.MainActivity"
```

三条验收命令：

```powershell
# ① L2 自检（浏览器或 curl 都行）
curl "http://<IP>:8686/api/v1/status"
```

期望 JSON 里：`"ok":true`、`"version":"0.4.0"`、`"l1":true`、`"authRequired":false`

```powershell
# ② L1 桥活着
adb -s <IP>:5555 logcat -d -s R1BRIDGE:V
```

期望：`L1 bridge ready, ver=2` 以及紧接着的 `cmd receiver registered: com.r1.bridge.cmd`

```powershell
# ③ 没有崩溃
adb -s <IP>:5555 logcat -d | Select-String "FATAL EXCEPTION"
```

期望：**无输出**

## A5. 打开控制台配置

```
http://<IP>:8686/
```

**首次访问不需要令牌。** 按需填：

| 配置 | 字段 | 备注 |
|---|---|---|
| 大模型 | 接口地址 / API Key / 模型 | OpenAI 兼容 `/chat/completions`。别用 GLM-4.7（实测 27~68 秒） |
| 语音合成 | 端点 / Key / 模型 / 音色 | OpenAI 兼容 `/v1/audio/speech`；`grok-tts` 不能写成 `x/grok-tts` |
| 天气 | 城市 / 和风 Key / 和风 API Host | 不填 Key 走免 Key 通道；新账号必须填自己的 API Host |

配完后到「设置 → 访问令牌」设一个令牌（**暴露到公网前必须设**）。
设置页还能「导出配置 JSON」，把含 Key 的完整配置存成文件，换机时再导入。

## A5.1 验收：云音乐 / 灯环 / 本地意图

装完包以后，这三条能把「新功能到底通没通」一次验完。

**① 云音乐搜索（免登录也能搜）**

```powershell
curl "http://<IP>:8686/api/v1/music/search?source=netease&q=%E5%91%A8%E6%9D%B0%E4%BC%A6"
curl "http://<IP>:8686/api/v1/music/search?source=qq&q=jay"
```

期望：`"ok":true`，`"list"` 里 4~20 条，每条有 `id` / `name` / `artist`。

**② 播放（免版权曲目可直接播；VIP 曲目要登录）**

```powershell
curl -X POST "http://<IP>:8686/api/v1/music/play" -H "Content-Type: application/json" `
     -d '{"source":"netease","id":"5257138","title":"稻香"}'
curl "http://<IP>:8686/api/v1/player"
```

期望：第一条 `"ok":true` 且带 `"url":"http://m70x.music.126.net/..."`；
第二条 `"playing":true`。若返回「拿不到播放地址」，说明这首要会员——
到音乐页点「粘贴 Cookie 登录」，把 `music.163.com` 的 Cookie 整段贴进去再试。

QQ 音乐同理，Cookie 里必须有 `uin` 和 `qm_keyst`，否则 vkey 取不到地址（`result=104003`）。

**③ 灯环**

```powershell
curl "http://<IP>:8686/api/v1/l1/light?mode=loading"
curl "http://<IP>:8686/api/v1/l1/light?mode=off"
```

期望：`{"ok":true,"mode":"loading","l1":true}`，**音箱顶部灯环同时变成呼吸效果**；
`off` 后灯环熄灭。同时 `adb logcat -s R1BRIDGE:V` 里能看到 `light -> loading`。

**④ 本地意图（不花 token）**

```powershell
curl -X POST "http://<IP>:8686/api/v1/chat" -H "Content-Type: application/json" `
     -d '{"text":"二十分钟后提醒我喝水"}'
curl -X POST "http://<IP>:8686/api/v1/chat" -H "Content-Type: application/json" `
     -d '{"text":"放一首七里香"}'
```

期望：前者回「好的，20 分钟后提醒你。」且 `GET /api/v1/config` 的 `alarms` 多一条 `once:true`；
后者回「正在为你播放…」并把播放地址交给播放器（`/api/v1/player` 变 `playing`）。

## A6. 卸载（完整回退）

```powershell
# 只退 L2，不影响原厂语音
adb -s <IP>:5555 shell pm uninstall com.r1.brain

# 退 L1，自动回落到 /system/app/Unisound 的原厂版本
adb -s <IP>:5555 shell pm uninstall com.phicomm.speaker.device
```

> 退回原厂后**唤醒会失效**（原厂三道闸门要连已下线的服务器才放行）。
> 这是原厂状态，不是弄坏了。

---

# 路径 B · 自己构建

## B1. 环境

| 依赖 | 版本 | 用途 |
|---|---|---|
| JDK | 17+（实测 21） | javac / d8 / apksigner |
| Android SDK | `platforms/android-34`、`build-tools/36.0.0` | android.jar / d8 / aapt2 |
| Python | 3.8+ | Janus 打包与自检 |
| Node.js | 16+ | 构建前端页面 |

smali/baksmali 工具链**已在仓库里**（`l1/tools/jars/`），无需另外下载。

## B2. 构建 L2

```powershell
pwsh -File .\l2-app\build.ps1
```

期望最后几行：

```
[6/6] 签名
产物: ...\l2-app\build\r1brain.apk  (273.2 KB)
已同步至一键安装包: ...\apps\r1brain.apk
```

## B3. 构建 L1

```powershell
pwsh -File .\l1\build.ps1
```

期望五条自检全过：

```
[ok] 文件头 dex\n035
[ok] file_size=...
[ok] Adler32 + SHA-1
[ok] zip 可解包，134 个条目
[ok] 134 个条目与底包逐字节一致（签名区域未改动）
```

产物 `l1\build\r1brain-l1.apk` ≈ 31 MB。

> 构建过程做了两件事（都可验证）：
> 1. 用 `l1/vendor/Unisound_original.apk` 做底包，**SHA-256 校验**（不是这个文件就拒绝构建）
> 2. 注入我们的桥后重新组装 DEX、Janus 套壳，zip 部分零改动 → 签名保持有效

## B4. 安装

同 **A3**。网络差时用分片上传：

```powershell
pwsh -File .\tools\upload-apk.ps1 -Apk .\l1\build\r1brain-l1.apk -Target <IP> -Token <控制台令牌>
```

（该脚本按 1 MB 分块、每块重试，适合链路抖动的情况；已验证可用。）

---

# 可选 · 系统瘦身

> 这不是必须的。设备裸机可用内存约 5 MB，但 `MemFree` 低不等于内存紧张——
> 判据是**有没有进程被低内存杀掉**（`am_kill`）。
> 实测卸载下面 5 个包后：RSS 合计 824 MB → 673 MB，`am_kill` 从有变无。

## C1. 可以卸载的包

| 包 | 卸载前占用 | 它是什么 | 为什么可以删 |
|---|---:|---|---|
| `com.phicomm.speaker.otaservice` | 30 MB | 向 `home.phicomm.com` 轮询固件，**能下载并安装整包** | 服务器已 NXDOMAIN；存在覆盖我们 L1 的能力 |
| `com.phicomm.speaker.systemtool` | 64 MB | 百度定位 + MQTT + 下载 + BLE/存储监控 | 远程运维通道，服务器全下线 |
| `com.phicomm.speaker.bugreport` | 31 MB | 上报绑定/播放/配网/唤醒词/音量等 | 纯遥测 |
| `com.phicomm.speaker.exceptionreporter` | 24 MB | 异常上报 | 纯遥测 |
| `com.phicomm.speaker.productiontest` | 25 MB | 产测（老化测试） | 正常使用用不到 |

**依赖检查（可复现）**：对原厂 smali 全量搜索这 5 个包名，主程序引用数为 **0**。

## C2. 不能卸载的

| 包 | 原因 |
|---|---|
| `com.phicomm.speaker.device` | 唤醒 / ASR / TTS / 灯环 / 定位 |
| `com.phicomm.speaker.player`（4 个进程） | **DLNA + AirPlay 靠它**，删了投屏就没了 |
| `com.phicomm.speaker.airskill` | AirPlay 辅助 |
| `com.phicomm.speaker.netctl` | 配网；删了以后改 WiFi 只能靠 adb |
| `com.android.bluetooth` | 当蓝牙音箱用 |

## C3. 卸载命令

```powershell
adb -s <IP>:5555 shell "cd /data/local/tmp && nohup sh -c 'pm uninstall --user 0 -k com.phicomm.speaker.otaservice > u.log 2>&1' >/dev/null 2>&1 &"
adb -s <IP>:5555 shell "cat /data/local/tmp/u.log"
```

期望：`Success`

验证：

```powershell
adb -s <IP>:5555 shell "dumpsys package com.phicomm.speaker.otaservice | grep 'User 0'"
```

期望：`installed=false`

> `-k` 保留数据目录。重启后进程才会真正消失（这些包带 `PERSISTENT`）。

## C4. 恢复命令

```powershell
adb -s <IP>:5555 shell "pm install -r /system/app/OtaService/OtaService.apk"
```

对应关系：

| 包 | 系统路径 |
|---|---|
| `com.phicomm.speaker.otaservice` | `/system/app/OtaService/OtaService.apk` |
| `com.phicomm.speaker.systemtool` | `/system/app/SystemTool/SystemTool.apk` |
| `com.phicomm.speaker.bugreport` | `/system/app/BugReport/BugReport.apk` |
| `com.phicomm.speaker.exceptionreporter` | `/system/app/ExceptionReporter/ExceptionReporter.apk` |
| `com.phicomm.speaker.productiontest` | `/system/app/ProductionTest/ProductionTest.apk` |

**期望输出是 `INSTALL_FAILED_DEXOPT`——这是正常的，不要被吓到。**

原因：这些系统包是「APK 只有清单 + 代码在 `arm/*.odex`」的预编译形态，
`pm install` 找不到 `classes.dex` 所以报 DEXOPT 失败；但包状态会恢复：

```powershell
adb -s <IP>:5555 shell "dumpsys package com.phicomm.speaker.productiontest | grep 'User 0'"
```

期望：`installed=true`，且 `codePath=/system/app/ProductionTest`（回到系统路径，沿用原 odex）。

实测：按上述流程「卸载 → 装回 → 启动服务」全程通过，进程能正常起来。

## C5. 为什么不能"替换成空壳"（已验证的死路）

试图保留包名、只把实现换成空壳（`sysapps/` 里有完整的空壳工程与日志），结论是**做不到**：

```
APK 里没有 classes.dex
  → Janus 前置 DEX        → INSTALL_FAILED_DEXOPT
  → 往 zip 里加 classes.dex → INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION
  → 重新签名               → 需要斐讯平台私钥（没有，见 09-decisions D-02）
```

所以对这几个包只有两个选项：**卸载** 或 **不动**。

## C6. 关于"换域名"挡住它们

实测这几个域名**已经全死了**：

```
home.phicomm.com        → NXDOMAIN
dev.home.phiwifi.com    → NXDOMAIN
r1feedback.phicomm.com  → NXDOMAIN
p.to                    → 活的，但 /otaupgrade/update.zip → 404
```

也就是说它们现在**连不上任何服务器**，改 DNS 不会带来额外收益，
反而要引入一台常开的 DNS 服务（PC 一关音箱就断网），不划算。

> 唯一值得警惕的是**域名将来被抢注**——那时这些残留 App 会主动去连新主人。
> 想彻底消除这个风险，只能卸载（C3）。

---

# 已知坑（都是实测踩出来的）

| 症状 | 原因 | 处理 |
|---|---|---|
| `adb install` 刷屏 `waiting for device` | adbd 在长连接上会挂 | 用 A3 的两步法（push + 设备端 `pm install`） |
| `adb connect` 成功但 `device offline`，5555 端口能连 | adbd 假死 | **断电重插**，无其它办法 |
| 命令返回 `error: closed` | 连接被掐断，结果丢了 | 把命令丢设备端后台跑，结果写文件，再 `cat` |
| `adb push` 大文件中途 EOF | 链路抖动（跨网段时尤其严重） | 用 `tools/upload-apk.ps1` 分片重传 |
| 网页 404「没有这个接口」 | aapt2 在 Windows 上把资源名写成反斜杠 | 构建脚本已加规范化（`[3.5/6]`），重建即修复 |
| 原厂 App 反复崩 `FATAL EXCEPTION: MqttTransportChannel` | Paho 静态初始化失败 | L1 已把 `MqttTransportChannel.createChannel` 置空 |
| 上传 APK 报「文件上传中断」 | 写 `/data/local/tmp` 被拒（EACCES） | 已改到应用外部目录 + 分块续传 |
| 装大包超时 | `pm install` 被 15 秒看门狗杀掉 | 已改 180 秒 |
| `MemFree` 只剩几 MB | Android 把空闲内存当缓存用，属正常 | 看 `am_kill` 次数，不看 MemFree |
| 跨网段时传输极慢（14 KB/s） | 中间隔了一层路由/NAT，RTT 200ms、丢包 10% | 把电脑和音箱放同一网段 |

---

# 验收清单（逐条打勾）

部署完成后，这些都应该成立：

- [ ] `adb devices` 能看到 `<IP>:5555  device`
- [ ] `pm install` 日志里是 `Success`
- [ ] `curl http://<IP>:8686/api/v1/status` 返回 `"ok":true`
- [ ] 同上返回 `"l1":true`（装了 L1 才有）
- [ ] `http://<IP>:8686/desktop/home.html` 返回 200
- [ ] `logcat -s R1BRIDGE:V` 有 `L1 bridge ready`
- [ ] `logcat` 里没有 `FATAL EXCEPTION`
- [ ] 说「小讯小讯」有唤醒反应
- [ ] 控制台配好大模型后，输入文字能得到回复

---

# 设备档案（一台实测机）

```
型号      rk322x-box / Phicomm_R1_E235
系统      Android 5.1.1 (API 22)，armv7（ABI 被混淆成 armeabi-v7a）
内存      512 MB 物理（MemTotal ≈ 490 MB）
存储      eMMC 7.6 GB（9 个分区，无 by-name 映射）
MAC       <音箱MAC>
签名      原厂平台证书 SHA-256
          34:A2:D2:FE:89:96:56:AF:5D:F3:FB:F6:8E:80:54:77:
          B3:DF:37:08:CA:65:C8:42:BA:EC:2D:E8:E5:78:BC:33
```

**不要尝试升级 Android。** 这台机器的价值就在那套还在工作的麦克风阵列和唤醒链，
而它们依赖斐讯定制的 HAL 与 framework（`libuni4michal.so`、`ISysPrivate` 等），
通用 ROM 一个都没有；且没有可用的刷机通道（`ro.secure=1`、无 recovery、刷坏即变砖）。
详见 `09-decisions.md` D-02 与 `11-troubleshooting.md` 第 5 节。
