---
name: r1-dev
description: 斐讯 R1 音箱改造项目的开发手册，覆盖 L1（原厂包注入桥）、L2（业务 App）、L3（网页控制台）的代码规范、构建、部署、真机验证与已知故障。当你要改这个仓库的任何代码、打包、刷机、排查设备问题，或不确定该把逻辑放哪一层时使用。
---

# R1 项目开发技能

> 这份文件回答「改动之前要知道什么、改完怎么证明它真的能用」。
> 规则冲突时的优先级：`AGENTS.md` > 本文件 > `docs/` 其它文档。

---

## 0. 排障方法论（比任何结论都重要）

这个仓库里最贵的错误不是写错代码，而是**在错误的层上改代码**。
2026-11-03 的语音故障连着踩了三次，每次都是同一个病根：**没有先拿原始证据**。

### 0.1 证据优先级

原始数据 > 日志结论 > 文档结论 > 代码推断。分级的定义在 `AGENTS.md`「证据分级」，
这里只强调一句：**文档里没有日期和命令的「结论」当假设看**。
`docs/24` 那句「缓冲天然是单声道」就是推断，照着写出来的 tee 把双声道交织当单声道，
SenseVoice 只听到「悟空悟空」。

### 0.2 「没反应」类问题的固定下钻顺序

自下而上，每层都要有能证明它通过的东西，**不允许跳层猜**：

| # | 问题 | 证据在哪 | 通过的样子 |
|---|---|---|---|
| 1 | 事件发生了吗 | `adb logcat -s R1BRIDGE:V` | 有 `ASR_AUDIO: r1asr.wav Nms` |
| 2 | 文件拿得到吗 | L2 `/api/v1/logs` | 不是 `EACCES (Permission denied)` |
| 3 | **数据对吗** | 把 wav 拉到电脑分析（§9） | 时长/声道/电平和人耳一致 |
| 4 | 服务听懂了吗 | `/api/v1/logs` 里 `自建 ASR -> ...` | 文本等于你说的话 |
| 5 | 业务执行了吗 | `/api/v1/player`、播报日志 | `playing=true` / 有播报 |

第 3 层是最容易被跳过的一层，也是这次卡最久的一层：
L2 明明拿到了 235244 字节、时长「5.25 秒」，也都上传成功，
但内容其实是被当成单声道解析的双声道交织——**数字全对，数据是错的**。

### 0.3 三个自我校验的硬信号

1. **时长对不上人说话的秒数**（正好 2 倍/4 倍）⇒ 先怀疑声道数或采样率，不要怀疑 ASR。
   `16000Hz × 16bit × 单声道 = 32000 字节/秒`，这是唯一基准。
2. **识别结果和原话毫无语义关系**（「嗯嗯」「悟空悟空」）⇒ 音频格式问题，不是模型问题。
   模型再差也会给出音近的字。
3. **同一份数据换个解释就通了** ⇒ 说明原来的解释是错的，立刻回头找文档里哪句是推断。

### 0.4 改架构前先看「已废弃通道」

`AGENTS.md` 里有一张「已废弃通道」表。**`asrv3.hivoice.cn` 的识别服务其实还活着**
（2026-11-03 实测会返回正确文本），但这不代表可以回退到原厂通道——
那是产品决策，不是技术发现。发现自己「顺手修好了但绕过了既有设计」时：
先停下、把证据摆出来、问一句，不要自己拍板改方向。

### 0.5 一次只验一个假设

改 L1/L2 之后**必须端到端复现一次真实场景**（对着音箱说话 / 点页面），
日志对上了才算完。`pm install` 返回 `Success` 只是「装进去了」，不是「修好了」。

---

## 1. 三条铁律

1. **不动 `/system`、不升级 Android、不用 `adb install`**（这台机器上必卡死）。
   装包只有一条路：`adb push` → 设备端 `pm install -r`。
2. **协议值只出现在枚举/常量里，文案只出现在 `label()` 里**。
   禁止 `"网易云".equals(x)` 这种拿中文当类型的判断。
3. **改完必须上机验证**，不是在本地「看起来对」。验证命令与期望输出见 §6。

---

## 2. 仓库地图

| 路径 | 是什么 | 什么时候改 |
|---|---|---|
| `l1/` | 顶替 `com.phicomm.speaker.device` 的 Janus 包；只做唤醒/ASR 转发/灯环 | 桥协议变化、灯环动作变化 |
| `l2-app/` | `com.r1.brain` 业务 App，8686 HTTP 服务 | 99% 的需求 |
| `web/` | 页面模板 + 样式 + 引擎，构建时内联进 APK | 界面 |
| `docs/` | 规范与手册：`20-device-ops.md` 部署、`23-testing.md` 自检、`22-call-chains.md` 链路、`11-troubleshooting.md` 排障 | 行为变化就同步 |
| `一键安装包/` | 给客户的成品，构建自动同步 APK | 不手改 |
| `sysapps/` | 已证明走不通的空壳替换工程，留作证据 | 不要动 |

L1 打包成本极高（30MB 推送约 100 秒 + 安装 45 秒），**能放 L2 就别放 L1**。

---

## 3. 常用工作流

### 3.1 改 L2（大多数情况）

```powershell
# 1) 只改 Java 时
pwsh -NoProfile -ExecutionPolicy Bypass -File .\l2-app\build.ps1
# 2) 改了 web/ 时（build.ps1 会顺带跑页面构建，但单独跑能更快发现问题）
node web/build-pages.js
node web/verify-pages.js
pwsh -NoProfile -ExecutionPolicy Bypass -File .\l2-app\build.ps1

# 3) 装到设备（先停进程，否则可能装了没换）
adb -s <IP>:5555 shell "am force-stop com.r1.brain"
adb -s <IP>:5555 push .\l2-app\build\r1brain.apk /data/local/tmp/r1brain.apk
adb -s <IP>:5555 shell "cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/r1brain.apk > i.log 2>&1' >/dev/null 2>&1 &"
# 等 15 秒
adb -s <IP>:5555 shell "cat /data/local/tmp/i.log"    # 期望最后一行 Success
# 4) 确认真的换上了（看时间戳，别只看 Success）
adb -s <IP>:5555 shell "ls -l /data/app/com.r1.brain-*/base.apk"
adb -s <IP>:5555 shell "am startservice -n com.r1.brain/.MainService"
```

### 3.2 改 L1（桥协议 / 灯环）

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\l1\build.ps1   # 末尾五项自检必须全 ok
adb -s <IP>:5555 push .\l1\build\r1brain-l1.apk /data/local/tmp/r1brain-l1.apk
adb -s <IP>:5555 shell "cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/r1brain-l1.apk > l1.log 2>&1' >/dev/null 2>&1 &"
# 等 50 秒；然后确认桥活着
adb -s <IP>:5555 logcat -d -s R1BRIDGE:V      # 期望 L1 bridge ready, ver=2 + cmd receiver registered
```

改 L1 必须同步改 `docs/07-protocol.md` 与 L2 的 `BridgeProtocol`（两边值逐字一致）。

### 3.3 改界面

页面是模板字符串 + 构建期内联，**没有运行时框架**：

```
web/src/desktop-pages.js  ─┐
web/src/mobile-pages.js   ─┼─ node web/build-pages.js ─► web/desktop/*.html, web/mobile/*.html
web/assets/r1-engine.js   ─┘                              + assets/blackgold.css（Tailwind 预生成）
```

新增用到的 class 必须先确认 `web/verify-css.js` 通过，否则页面样式会「缺一块」。

---

## 4. 代码规范速查

| 规则 | 反例 | 正例 |
|---|---|---|
| 协议值 vs 文案 | `if ("netease".equals(src))` 散落各处 | `MusicSource.of(src)`，`src.id()` / `src.label()` |
| 新增实现 | 在调用方加 `else if` | 实现接口 + 往 `CHAIN` / `SEARCH_CHAIN` 加一行 |
| 网络 | 各处手写 `HttpURLConnection` | `Http.request/getText/postJson/postForm` |
| 全限定名 | 方法体里 `android.os.SystemClock.elapsedRealtime()` | 顶部 import；仅 `java.lang.Process`（与 `android.os.Process` 冲突）例外，需注释 |
| 注释 | `// 设置音量` | 说明**为什么**：`// 硬件档位只有 0~15，超过会被系统截断` |
| Javadoc | `@author 张三` | `@author xudong.hua,deepseek` + `@since 2026-10-31 13:52 星期六` |
| 提交 | `update code` | `feat(music): 语音点歌支持按歌手` |

**什么时候不要抽象**：只有一个实现、且短期不会出现第二个时，直接写。比如 TTS 两个出口
（HTTP TTS / 系统 TTS）已经被 `MainService.speak()` 收口，就不再套一层策略接口。

**什么时候必须抽象**：同一件事已经有 ≥2 个实现，或协议值会出现在 ≥2 个文件里。
本仓库的实例：`WeatherProvider`（和风/wttr）、`MusicSource`（网易云/QQ）、
`LightEffect`、`LoopMode`、`BridgeProtocol`、`Http`。

---

## 5. 关键设计约定

- **分层**：唤醒/ASR/灯环 → L1；业务/网络/播放 → L2；界面 → L3（纯静态页）。
- **意图优先**：`IntentRouter` 先于大模型；命中即返回，省钱也省延迟。
  新意图加在对应分组里，别塞进 `handle()` 主流程。
- **播报**：只有一条链路 —— `MainService.speak()` → `SpeechEngine`（HTTP TTS）→ 分句并发有序播放。
  原厂闲聊播报已在 L1 里抑制，**不要再让原厂出声**。
- **内存**：常驻可用内存只有几十 MB。禁止整段音频进内存、禁止无上限读响应体（`Http` 默认 2MB）。
- **时区**：一切时间用 `GMT+08:00`，不要用 `TimeZone.getDefault()`。

---

## 6. 上机验证（改完必做）

> 完整的四层自检（静态 / 构建 / 接口 / 设备）、常见「假绿」与「改动类型 → 必跑项」见
> `docs/23-testing.md`。下面是最小集：

**改意图解析不用上机**：`tools/IntentProbe.java` 能直接跑归一化与点歌解析（反射调用
`stripLead` / `fixNearMiss` / `parseSong`，结果写 UTF-8 文件）：

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
javac -encoding UTF-8 -bootclasspath "$sdk\platforms\android-36\android.jar" `
  -cp l2-app\build\classes -d $env:TEMP\probe tools\IntentProbe.java
java -cp "l2-app\build\classes;$env:TEMP\probe;$sdk\platforms\android-36\android.jar" `
  com.r1.brain.IntentProbe "$env:TEMP\probe\r.txt"
Get-Content "$env:TEMP\probe\r.txt" -Encoding UTF8
```

```powershell
$base = "http://<IP>:8686"

# ① 服务活着（期望 l1=True、version 与你打的一致）
(Invoke-WebRequest "$base/api/v1/status").Content

# ② 没有崩溃（期望 0）
(adb -s <IP>:5555 logcat -d | Select-String "FATAL EXCEPTION" | Measure-Object).Count

# ③ 意图/播报链路（改动了对话或音乐时）
Invoke-WebRequest "$base/api/v1/chat" -Method POST -Body '{"text":"播放一首周杰伦的歌"}' -ContentType "application/json"
(Invoke-WebRequest "$base/api/v1/player").Content      # 期望 playing=true

# ④ 灯环（改动了 L1 时）
Invoke-WebRequest "$base/api/v1/l1/light?mode=loading"  # 期望 {"ok":true,...,"l1":true}，且灯环肉眼可见变化

# ⑤ 天气（改动了 WeatherService 时）
Invoke-WebRequest "$base/api/v1/weather?city=杭州"
```

页面：`/desktop/home.html`、`/mobile/music.html` 等 8 个页面全部返回 200。

---

## 7. 故障排查表

| 现象 | 先看什么 | 常见原因 |
|---|---|---|
| `adb install` 卡住不动 | —— | 这台机器必卡，改用 `push` + `pm install` |
| `pm install` 报 Success 但功能没变 | `/data/app/com.r1.brain-*/base.apk` 的时间 | 进程没停，包没真正替换；先 `am force-stop` 再装 |
| 页面能开但按钮全没反应 | 浏览器控制台 | 内联脚本 SyntaxError，多半是 `web/src` 模板转义层数错了（`verify-pages.js` 会拦 `\``） |
| `adb shell pm list packages` 报 `error: closed` | 内存 | 设备内存吃紧，`pm` 起不来；改用 `/proc` 或直接装包 |
| L2 装完 `l1=False` | 等 30 秒 | L1 心跳 30 秒一次，90 秒窗口内才为 true |
| 唤醒后没反应 | `logcat -s R1BRIDGE:V` | 桥没起来 / `isBinded` 被改回 false |
| 同一句话播两遍 | `R1BRIDGE` 日志 | L1 的闲聊抑制补丁没打上（`build.ps1` 4.5 步） |
| 网页 404 | `node web/verify-pages.js` | 改了模板没重新 build，或 aapt2 资源名被写成反斜杠 |
| 天气返回「网络较忙」 | `logcat -s R1BRAIN:V \| grep wttr` | 根证书过旧（正常会走 HTTP 兜底）、或 wttr 抽风 |
| 音乐搜不到 | `logcat` 里 `点歌跳过` | 未登录时会员曲目取不到地址，属预期 |
| 灯环点了没反应 | `/api/v1/l1/light` 返回的 `l1` | L1 不在线，或 `Bridge` 的反射方法名与原厂不一致 |
| 每句话都回「我没听清」 | `/api/v1/logs` 找 `EACCES` | L1 录音文件权限没放开（跨 UID），见 `docs/24` §6 |
| 识别成「嗯嗯」「悟空悟空」这类无关内容 | 按 §9 把 wav 拉下来看时长/声道 | **双声道交织被当单声道写盘**，用 §9.2 的判据确认 |
| 日志里证据总被冲掉 | `/api/v1/logs` 环形缓冲只有 50 条 | 「压制原厂播报」心跳每 2 分钟一条；复现后**立刻**截图/保存，别等 |
| 点歌播报「正在为你播放」但没声音 | `logcat` 找 `NuCachedSource2: source returned error -1` | mediaserver 拉不动网易云 CDN；已由 `MusicRelay` 中转到 127.0.0.1 播放（见 `docs/22` §4） |
| 闹钟只念备注、不念天气 | `/api/v1/config` 里闹钟的 `action` | 缺 `action=weather`；网页端新增闹钟要选「天气」 |
| 闹钟天气总是「当前网络较忙」 | `logcat` 找 `NetworkOnMainThreadException` | 天气查询必须在后台线程（`AlarmScheduler` 已改） |

---

## 8. 提交与文档

- 提交信息用中文，带语义前缀（`feat/fix/docs/refactor/chore`），正文写「为什么」。
- 行为变了就改 `docs/`：协议 → `07`，接口 → `08`/HttpServer 注释，部署 → `20`，规范 → `AGENTS.md`。
- 每次代码审查的结论写进 `docs/21-code-review.md`（新增日期段落），不要只留在对话里。
- 一键安装包里的 APK 由 `build.ps1` 自动同步，**不要手工替换**。

---

## 9. 语音链路专项排查（附可复制命令）

语音是「用户说话 → L1 录 → L2 传 → 网关识别 → 业务」五段，任何一段错都表现为
「它没听懂」。按 §0.2 的顺序查，**第 3 段必须把文件拉下来亲眼看**。

### 9.1 取一段原始录音

```powershell
# 用 exec-out，不要用 pull：设备内存吃紧时缓存会被清，pull 会报 does not exist
adb -s <IP>:5555 exec-out "cat /data/data/com.phicomm.speaker.device/cache/r1asr.wav" > $env:TEMP\capture.wav
```

### 9.2 一眼看出声道 / 时长是否合理

```powershell
$b = [IO.File]::ReadAllBytes("$env:TEMP\capture.wav")
"rate=$([BitConverter]::ToInt32($b,24)) ch=$([BitConverter]::ToInt16($b,22)) bits=$([BitConverter]::ToInt16($b,34))"
"按单声道 $([math]::Round(($b.Length-44)/32000.0,2))s / 按双声道 $([math]::Round(($b.Length-44)/64000.0,2))s"
```

判断标准：

- 头里写 `ch=1` 但「按双声道」那个数才接近你说话的秒数 ⇒ **数据是双声道交织被写成了单声道**。
- 2026-11-03 实测的判据（`Bridge.detectChannels` 用的就是它）：
  同帧左右声道平均差 **44.9**、同声道相邻样点差 **7.4**，两者差 3 倍以上即为双声道。

### 9.3 拿真音频去问网关，不要猜模型准不准

```powershell
curl.exe -s -X POST "http://<网关IP>:8710/asr" -H "Content-Type: audio/wav" --data-binary "@$env:TEMP\v-mix.wav"
```

同一段音频的实测对照（2026-11-03 22:18 那条）：

| 输入 | 结果 |
|---|---|
| 原样（当单声道） | `悟空悟空。` |
| 按双声道下混 | `播放周杰伦的歌。` `duration_ms=3675` |

这个对照实验比读一百行代码有用：它同时证明了「录音没问题」和「我们的写法有问题」。

### 9.4 正常日志长什么样

L1（`adb logcat -s R1BRIDGE:V`）：

```text
录音输入声道 = 2                          # 判声道结果（2 = 4 麦 HAL 的双声道交织）
ASR_AUDIO: r1asr.wav 3675ms（输入 2 声道已折叠为单声道）
```

L2（`GET /api/v1/logs`）：

```text
=== 收到录音文件: .../r1asr.wav
自建 ASR -> 播放周杰伦的歌。（/asr, 3675ms, sensevoice-small）
```

任何一环缺一条，就按 §0.2 往回退一层。
