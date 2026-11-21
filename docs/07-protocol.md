# L1 ↔ L2 桥协议（当前实现，v3）

> 这里写的是**设备上正在跑的协议**，不是设想。改协议要重新 Janus 打包 L1（约 110 秒推送 + 45 秒安装），
> 所以能加字段就别改结构。

---

## 1. 参与方

| 角色 | 包名 | 签名 | 说明 |
|---|---|---|---|
| **L1** | `com.phicomm.speaker.device` | 原厂 phicomm 证书（Janus 保留） | 只做唤醒、ASR、转发、灯环 |
| **L2** | `com.r1.brain` | 我们自己的证书 | 所有业务 + HTTP 8686 |

两边 uid 不同、签名不同，用不了 `protectionLevel="signature"` 的自定义权限；
v2 的鉴权方式是**定向广播**（`Intent.setPackage` 指定对方包名），
不做证书校验、不做握手令牌 —— 局域网内可投递广播的应用都能发，这是已知取舍（见 §7）。

---

## 2. 两条 action

| action | 方向 | 用途 |
|---|---|---|
| `com.r1.bridge` | L1 → L2 | 事件：心跳、唤醒、录音、取消、原厂播报完成、灯环回执 |
| `com.r1.bridge.cmd` | L2 → L1 | 命令：灯环、提示音音量、播报、续听、判停开关、压制原厂播报、引擎软恢复 |

两条都用 **extras 直传**，不套 JSON 信封（省一次解析，也省得为加字段改结构）。

L1 侧接收器在 `Bridge.init()` 里**动态注册**（`registerReceiver`），
不改 AndroidManifest —— 原厂清单在签名 zip 里，改了签名就废。

---

## 3. L1 → L2 事件

### 3.1 `ASR_RESULT` —— 最重要的一条

| extra | 类型 | 说明 |
|---|---|---|
| `v` | int | 协议版本，当前 `2` |
| `type` | String | `ASR_RESULT` |
| `text` | String | 识别文本（已去唤醒词） |
| `ts` | long | 发送时间戳 |

注入点：
- `NLUDispatcher.onASRResult(String)` /
  `NLUDispatcher.onASRResult(ASRResult)` → `Bridge.onAsrResult(json)`
- `Bridge` 用极简字符串扫描取 `text` / `asr_recongize` 字段，不引 fastjson。

L2 收到后直接进 `MainService.say(text)`，不再回 ACK（原厂会话不需要）。

### 3.2 `ASR_WEAK` —— 识别可信度太低（**已废弃**）

> **2026-11-01 起废弃。** 原厂云端（`aios-home.hivoice.cn`）已停止服务、域名不解析，
> 云端恒回 `609`，本地语法只能硬套命令词且分数恒低于 `-20`，这条通道产不出可用文本。
> 语音输入改走 §3.3 的自建 ASR，详见 `docs/24-self-hosted-asr.md`。
> L2 在 `asrEngine=self` 时**直接忽略**本事件。

| extra | 类型 | 说明 |
|---|---|---|
| `type` | String | `ASR_WEAK` |
| `text` | String | 原厂本地语法硬套出来的命令（只用于日志） |
| `score` | double | 原厂置信度；低于 `-20` 视为「没听清」 |

历史触发场景：原厂云端 NLU 返回错误（例如 `uniCarRet.returnCode=609`）时，本地命令语法会
把听不清的话硬套成某个命令（实测「今天天气」→「暂停」，score `-23.47`）。

### 3.3 `ASR_AUDIO` —— 自建 ASR 的录音文件（**主通道**）

| extra | 类型 | 说明 |
|---|---|---|
| `type` | String | `ASR_AUDIO` |
| `path` | String | L1 写好的 WAV 文件绝对路径（16k/16bit/单声道），**必须已放开到 L2 可读** |

L1 在原厂识别会话内（`openAudioIn` → `closeAudioIn`）从 `com.unisound.common.e.readData`
tee 出 PCM，边读边写 WAV，会话结束时封口并广播本事件。

> 权限是协议的一部分：文件写在 L1 自己的 `cacheDir` 下（目录 0700、文件 0600），
> L2 是另一个 UID，不 chmod 就一定是 `EACCES`。L1 在录音开始时调 `Bridge.shareForL2`
> 把数据目录与 cache 目录置 `o+x`、wav 置 `o+r`。违反这一条的后果是每句话都被当成「没听清」。
L2 读到 400KB 上限（约 10 秒）后 `POST {asrUrl}/v1/asr/transcribe`（multipart `file` + `X-API-Key`）整段上传，拿到文字再走正常对话；`GET /usage` 读用量不算次数，`QUOTA_EXCEEDED`(429) 播报「本月额度已用完」并停。启用条件是设置里「语音识别来源 = 自建 ASR」、填了网关地址和令牌；未启用时 L2 直接忽略该事件。

### 3.3b `ASR_START` —— 这一轮开始录了

| extra | 类型 | 说明 |
|---|---|---|
| `type` | String | `ASR_START` |
| `path` | String | 同一个 WAV 路径（文件刚创建，正在被追加） |

`Bridge.startTeeLocked()` 里发一次，位置在创建文件之后、写入数据之前。
L2 收到后给音乐「让路」（暂停播放中的音乐），**不再开流式会话**——按次数计费下统一等
`ASR_AUDIO` 到达后整段上传，每句话只计一次；旧版的 FunASR 流式会话已移除。

### 3.3c `WAKE` 事件 —— 唤醒词命中，还没开始录命令

| 字段 | 类型 | 值 |
|---|---|---|
| `type` | String | `WAKE` |

唤醒词刚被原厂引擎识别时立刻发出（`NLUDispatcher.onWakeupResult` 入口），比 `ASR_START` 早几十毫秒。
L2 收到要**马上停掉音乐 / TTS**：实测播放中开麦拿到的 PCM 是数字静音（`peak=74 rms=3`，满量程 32767），
不停播放这一轮必然识别为空。L1 会在 `openAudioIn` 入口再等 250ms 才真正开麦，给暂停留出生效时间。

### 3.3d `ASR_CANCEL` 事件 —— 这一轮不成立

| 字段 | 类型 | 值 |
|---|---|---|
| `type` | String | `ASR_CANCEL` |
| `reason` | String | `tts-tail` / `wake-window` / `too-short:<字节数>` |

录音太短（< 0.5 秒）、开麦太贴近播报结尾、超出唤醒窗口时发出。L2 收到必须立刻释放流式会话，
否则要等 30 秒超时才恢复，这期间再唤醒没人接——用户看到的就是「灯亮一下就没反应」。

### 3.3e `engine` 指令 —— 唤醒引擎软恢复

L2 连续两轮「唤醒后既没有录音也没有取消」时，发 `cmd=engine` + `method=enterWakeup`，
由 L1 通过反射让原厂引擎重进唤醒态。用于引擎停在 `engine_stop` 后不再恢复的场景
（实测此时每 5 分钟抛一次 unisound 异常），比重启整个进程温和。

### 3.3f `TTS_DONE` —— L2 托 L1 念的那句念完了（v3）

| extra | 类型 | 说明 |
|---|---|---|
| `type` | String | `TTS_DONE` |
| `id` | int | L2 在 `speak` 指令里给的序号，原样带回 |
| `ok` | boolean | `false` 表示没念出来（引擎未注入 / `playTTS` 抛异常） |

原厂 TTS 没有播完回调，L1 用 `isTTSPlaying()` 轮询判断念完。L2 只认序号对得上的那一条
（被新一轮覆盖的旧回执直接丢弃），据此续听或结束这一轮（恢复音乐、灯环回常亮）。
`ok=false` 时 L2 退到系统 TTS，保证语音指令一定有声音。`speak` 里 `id=0` 表示不要回执。

### 3.4 `L1_HELLO` —— 心跳

每 30 秒一条，只有 `v` / `type` / `ts`。v3 起心跳的 `v` 是 L1 的真实协议版本。
L2 用它维护 `l1LastSeen`，`GET /api/v1/status` 的 `l1` 字段在 90 秒窗口内为 `true`，
`l1Version` 字段就是这个 `v`。**`l1Version < 3` 时 L2 不自动续听**（老 L1 的续听录到静音）。

### 3.5 `LIGHT_RESULT` —— 灯环回执

| extra | 类型 | 说明 |
|---|---|---|
| `type` | String | `LIGHT_RESULT` |
| `mode` | String | `off` / `loading` / `wakeup` / `dormant` / `net` |
| `ok` | boolean | L1 是否执行成功 |

L2 存到 `L1Bridge`，`GET /api/v1/l1/light/state` 可读最近一次结果。

---

## 4. L2 → L1 命令

### 4.1 灯环

```java
Intent it = new Intent("com.r1.bridge.cmd");
it.setPackage("com.phicomm.speaker.device");
it.putExtra("cmd", "light");
it.putExtra("mode", "loading");
sendBroadcast(it);
```

`mode` 只能是原厂 `PhicommLightController` 提供的预置效果（没有任意 RGB 接口）。
这些取值在 L2 侧由 `LightEffect` 枚举统一管理（`value()` 就是协议值），
新增或改名必须同步改 L1 的 `Bridge#applyLight` 并重新打包 L1。

| mode | 调用的原厂方法 | 效果 |
|---|---|---|
| `off` | `turnOffALLWakeupLight()` + `turnOffLoadingLight()` | 全灭 |
| `loading` | `turnOnLoadingLight()` | 呼吸/转圈（思考中） |
| `wakeup` | `turnOnWakeupLastLight()` | 回到最近一次唤醒的颜色 |
| `dormant` | `turnOnDormantLight()` | 休眠微光 |
| `net` | `turnOnNetDisconnectedLight()` | 断网提示 |

L1 用**反射**调这些方法（`Bridge` 只依赖 `android.*`，独立编译），
调用失败只记日志并回 `LIGHT_RESULT{ok:false}`，不影响唤醒与会话。

### 4.2 开机提示音音量

```java
it.putExtra("cmd", "beep");
it.putExtra("cap", 0.4f);   // 0~1，播放器增益上限
```

原厂 `UniMediaPlayer.playBeepSound()` 把播放器增益写死成 `1.0f`，开机那声
`res/raw/bootloader_completed.mp3` 的响度就完全由系统媒体音量决定，听起来比平时播歌响。
`l1/build.ps1` 4.9 把这一处换成 `Bridge.beepGain()`：取
`min(当前媒体音量比例, cap)`，正常开机就跟着用户音量走，音量还没恢复时也不会超过 `cap`。
`cap` 存进 L1 自己的 `r1bridge` 偏好里，重启后仍生效。

调试可以直接发广播，不用重装 L1：

```powershell
adb -s <IP>:5555 shell "am broadcast -a com.r1.bridge.cmd -p com.phicomm.speaker.device --es cmd beep --ef cap 0.25"
```

### 4.3 续听 `listen`（v3 改了实现）

v2 调原厂 `enterASR()`，实测录到的是静音（10 秒、单声道、peak≈170），不是 4 麦 HAL 的通路。
v3 不再调引擎：原厂停在唤醒态时一直在 `readData` 做唤醒检测，L1 只打上「下一段要录」的标记，
由 `onAudioData` 懒启动写盘并**自己判停**（原厂此时不会 `closeAudioIn`）：

| 参数 | 值 | 说明 |
|---|---|---|
| 开始收音 | 收到指令后 700ms | 避开喇叭尾音 |
| 判停 | 说满 0.4 秒后静音 1 秒 | 收尾并发 `ASR_AUDIO` |
| 没人说话 | 6 秒 | 发 `ASR_CANCEL(follow-silence)` |
| 看门狗 | 13 秒 | 引擎没送音频 → `ASR_CANCEL(follow-timeout)`；还在录 → 强制收尾 |

续听途中用户喊了唤醒词：丢掉续听这段，按唤醒轮重录。

### 4.4 判停开关 `endpoint`（v3）

```java
it.putExtra("cmd", "endpoint");
it.putExtra("on", true);   // 唤醒轮说完静音 1.2 秒就收尾，不等原厂 VAD
```

L2 设置页「提前判停」是唯一真相，启动、保存设置、L1 版本变化、每 2 分钟心跳重发时下发；L1 只放内存。
判停时**起点在唤醒后 1.2 秒内的声音段不算数**（那是唤醒词尾巴，2026-11-04 21:50 旧判停就是被它触发），
说满 0.6 秒、静音 1.2 秒才收尾。默认关：还没在真机上标定过。

---

## 5. 时序

```
用户: "小讯小讯，今天天气怎么样"
  │
  ├─ L1 唤醒（原厂链路，未改动）
  ├─ L1 本地 ASR → Bridge.onAsrResult → 广播 ASR_RESULT{text}
  ├─ L2 意图路由（命中就走本地）
  ├─ L2 TTS 合成（HTTP /v1/audio/speech）→ MediaPlayer 播
  └─ L2 只播自己的声音；原厂闲聊播报已在 L1 里被抑制

一轮语音的灯环与音乐（v3，L2 的 VoiceSession 负责）:
  WAKE       → L2 暂停音乐（记「语音让路」）   灯：原厂唤醒灯
  听懂文本   →                                 灯：loading（思考中）
  开始播报   →                                 灯：回到常亮效果
  播完/取消  → 恢复被让路的音乐                灯：常亮效果
  （要续听则不恢复音乐，发 listen 等下一句）

灯环（网页/语音）:
  L2 → 广播 cmd{light, mode} → L1 反射调 PhicommLightController → 广播 LIGHT_RESULT
  用户设定的效果记为「常亮效果」，一轮对话结束后灯环回到它。未知 mode：L2 回 400，L1 回执 ok=false。
```

---

## 6. 版本与兼容

- `v` 当前是 `3`（v1 只有 `ASR_RESULT`，v2 增加心跳与灯环，v3 增加 `TTS_DONE`、不靠 `enterASR` 的续听、`endpoint` 判停开关）。
- 新增字段：老版本忽略即可，不要「字段缺失就报错」。
- 新增 `type`：老版本记一条日志后丢弃。

---

## 7. 已知限制（不是待办，是结论）

| 限制 | 原因 |
|---|---|
| 广播没有发送方 uid 校验 | Android 5.1 的 `BroadcastReceiver` 拿不到 `getCallingUid()`；真要拦只能靠签名级权限，但两边签名不同 |
| 灯环没有任意 RGB | 原厂只暴露 5 种预置效果，没有颜色参数接口 |
| 没有 SPEAK 命令 | L2 用 HTTP TTS + `MediaPlayer` 直接播，全链路自己控，不借原厂 TTS（避免 audio 锁） |
| 没有 SYS_MODE / VOICE / WAKE | 这三个要动 `ISysPrivate`，收益低、风险高，本期不做 |
| 播放进度不跨包 | 播放由 L2 自己的 `MediaPlayer` 负责；原厂/蓝牙/DLNA 播放不受我们控制 |
