# 24 · 自建 ASR 接入（语音识别的唯一通道）

> **结论先行：原厂云知声那套云端识别已经停止服务，语音识别只能走自建 ASR。**
> 本文件是这条链路的唯一说明。改 L1/L2 的语音输入路径前**必须先读本文**，
> 不要再去接原厂的 `ASR_RESULT` / `ASR_WEAK`，那条路已经死了（证据见 §1）。

> **2026-11-16 迁移：识别后端已从「本机 SenseVoice」换成「整合 ASR 网关」**（账号池 + 腾讯云按次数计费 + 配额自动计数）。
> 新契约：`asrEngine=self`、`asrUrl=http://<网关IP>:8720`（局域网；`<跳板IP>:8720` 仅 Tailscale 可机器用，设备走局域网）、
> `asrToken` = 网关发放的调用方令牌（走 `X-API-Key`，明文只存本机、页面只回 `hasAsrToken`）。
> L2 改走 `POST /v1/asr/transcribe`（multipart `file`），不再用旧 `/asr` 裸 WAV；
> 读用量走 `GET /usage`（不算次数），收到 `QUOTA_EXCEEDED`（429）播报「本月额度已用完」并停止。
> **按次数计费下禁用流式**：网关 WS 从 ready 帧就计一次、反复开关更烧额度，统一整段上传每句只计一次。
> 配额的「按时长/按次数、上限可配、按月重置」由网关侧负责，L2 不重复实现。
> 网关对接文档：`../asr-gateway/docs/INTEGRATION.md`（仓库 `D:/dev/agent-repos/asr-gateway`，当前版本 v0.7.0）。
> 令牌在网关控制台「调用令牌」页发放：`POST /admin/api/login` 登录后 `POST /admin/api/tokens`，
> 拿到的明文填入本项目的「网关令牌」；`GET /usage` 用同一令牌读取本月点数，不计次数。
> 下方 §0~§9 是迁移前的 SenseVoice 链路，留作历史证据，**接口细节以本段为准**。

---

## 0. 一句话链路

```
用户说话
  → 原厂引擎开始识别会话（openAudioIn）
  → L1 从 readData 的 PCM 里 tee 一份（16k/16bit/单声道）
  → 边读边写 WAV，会话结束（closeAudioIn）时封口
  → 广播 ASR_AUDIO{path}
  → L2 MainService.onVoiceAudio(path) → AsrClient.transcribe
  → POST {asrUrl}/asr  →  {text}
  → 拿到文字走正常对话（大模型 + TTS）
```

---

## 1. 原厂为什么废了（2026-11-01 实测证据）

云端 NLU 走 `aios-home.hivoice.cn`，**这个域名已经不解析**：

| 侧 | 证据 |
|---|---|
| 设备 | `E/HttpUtils: http post error : Unable to resolve host "aios-home.hivoice.cn": No address associated with hostname` |
| 电脑 | `Resolve-DnsName aios-home.hivoice.cn` → 解析失败（`hivoice.cn`、`asrv3.hivoice.cn` 还能解析，但只回 `609 http post request error`） |

于是每说一句话都走同一条死路：

```
ASR ignored (原厂错误回包): {"returnCode":0,"uniCarRet":{"result":{},"returnCode":609,"message":"http post reuqest error"}}
  ↓ 约 1.5 秒后
ASR weak (本地硬匹配 score=-70.05): 暂停
  ↓
L2 播报「我没听清，请再说一遍」
```

补充两条关键事实：

1. **本地语法（`local_asr`）不能当识别用**：它只会把任意语音硬套成固定命令词
   （实测把无关的话套成「收藏这个」「问题反馈」「最低音量」），分数恒在 `-20` 以下。
2. 因此 `ASR_RESULT`（云端文本）和 `ASR_WEAK`（本地硬猜）两条路都**不再有任何价值**，
   L2 不得再依赖它们。

---

## 2. 自建 ASR 服务契约（外部服务，不在本仓库）

| 项 | 值 |
|---|---|
| 接口文档 | `http://<跳板IP>:8088/sites/asr-api/README.md` |
| 服务地址 | `http://<网关IP>:8710`（局域网）／`http://<跳板IP>:8710`（ZeroTier） |
| 引擎 | SenseVoiceSmall + fsmn-vad，GPU |
| 加载方式 | 懒加载，空闲 1 小时卸载；**冷启动约 11 秒** |
| 鉴权 | 无 |

接口：

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/asr` | body 直接是 WAV 二进制，返回 `{text, confidence, duration_ms, engine}`。**L2 优先走这个**（不用拼 multipart） |
| `POST` | `/v1/audio/transcriptions` | multipart 字段 `file`，返回 `{text}`。`/asr` 返回 404/405 时兜底 |
| `GET` | `/health` | `{ok, model, sample_rate, max_seconds, lang, host}` |

音频硬要求：**16000Hz / 16bit / 单声道 PCM-WAV，单次 1~10 秒**。

两个坑：

- `confidence` 目前是 **`null`**（SenseVoice 不提供句级置信度）——**不要拿它做任何判定**，
  这正是原厂那套 `score < -20` 逻辑踩过的地方。
- 首次调用超时要给 **≥30 秒**（含模型冷启动），别把冷启动误判成服务挂了。

---

## 3. 分工

### 3.1 L1（`com.phicomm.speaker.device`）

tee 点选在原厂音频源 `com.unisound.common.e` 的三个方法上（由 `l1/build.ps1` 打 smali 补丁）：

| 方法 | 我们做什么 |
|---|---|
| `openAudioIn()I` | 开始新一轮录音（清空缓冲） |
| `readData([BI)I` | 把刚读到的字节追加进 WAV |
| `closeAudioIn()V` | 封 WAV 头、广播 `ASR_AUDIO{path}` |

为什么是这里：

- `readData` 是原厂引擎**唯一的取音入口**，无论走 4 麦 HAL（`Uni4micHalJNI.readData`）
  还是 `AudioRecord.read`，都从这里出。
- 采样率与位深直接可用：`e.<clinit>` 里 `h=0x3e80`(16000Hz)、`audioFormat=0x2`(16bit)，
  **不需要重采样**。
- ⚠️ **但声道数不一定是 1**：那三个常量只描述 `AudioRecord` 那条兜底路。
  4 麦 HAL 实际吐的是**双声道交织**（两个麦克风的样点交替排列）。
  实测 2026-11-03 22:18 那段 235244 字节的录音：同帧左右声道平均差 44.9、
  同声道相邻样点差 7.4 —— 按单声道直接写盘就是一段变速噪声，SenseVoice 只听到
  「悟空悟空」，同一段音频按双声道下混后立刻得到「播放周杰伦的歌」。
  所以 `Bridge` 会先攒 0.6 秒判定输入声道（`detectChannels`），再折叠成单声道写盘。
- **不能自己另开 AudioRecord**：麦克风被原厂引擎独占
  （`/proc/asound/card1/pcm0c/sub0/status` → `avail=0`）。

实现约束：

- **边读边写文件**，不要攒在内存里——设备空闲内存只有 8MB 上下。
- 单轮上限 10 秒，超了直接截断（服务端也限 10 秒）。
- 文件放 `getCacheDir()` 下，**每轮一个独立文件名**（`r1asr-<时间戳>.wav`）。
  早期每轮覆盖同名文件，实测 2026-11-04 21:29 出现「同一轮 online 出 10 字、终稿只剩 2 字」——
  下一轮 `setLength(0)` 把还在被 L2 读的文件截断了，两轮音频串在一起。
  2 分钟前的旧文件在下一轮开始时清掉。
- 写盘失败只记日志，不能影响原厂识别（它是同一条线程）。

### 3.2 L2（`com.r1.brain`）

- `MainService.onVoiceAudio(path)` → `AsrClient.transcribe` → 正常对话。
- **流式（2026-11-04 起）**：L1 广播 `ASR_START{path}` → `MainService.onVoiceStart` 开
  `ws://{asrUrl}/asr/stream`（FunASR 2pass），`AsrStream` 跟着文件读、每 60ms 推 1920 字节；
  收到 `ASR_AUDIO` 就发 `{"is_speaking":false}` 等终稿（最多 4 秒）。
  任何一步失败都回落到整段 `POST /asr`——流式只是加速，不是新依赖。
  实测（电脑端同协议）：3.67 秒音频推完 **300ms** 出终稿；整段上传时代同一段要等录音结束再算。
- 设置项：`asrEngine=self`、`asrUrl=http://<网关IP>:8710`。
- `asrEngine=self` 时**忽略** `ASR_RESULT` / `ASR_WEAK`，避免两套结果打架。

---

## 4. 当前实现状态（2026-11-01）

| 环节 | 状态 |
|---|---|
| ASR 服务（8710） | ✅ 已上线，局域网可达 |
| L2 `AsrClient` + `/api/v1/asr/health` + 设置页 | ✅ 已完成 |
| L2 `onVoiceAudio` 接收 `ASR_AUDIO` | ✅ 已完成 |
| **L1 录音转发（PCM tee → WAV → ASR_AUDIO）** | ⬜ 本次实现 |

> 历史坑：曾经出现过「设置里还是 `asrEngine=native`、`asrUrl` 为空」的状态，
> 导致看起来像又退回原厂了。**排查语音问题时第一件事就是看 `GET /api/v1/config`
> 里的 `asrEngine` 和 `asrUrl`。**

---

## 5. 验证清单

```powershell
# 1. 配置是否指向自建 ASR
(Invoke-WebRequest "http://<IP>:8686/api/v1/config").Content   # asrEngine=self、asrUrl 非空

# 2. 服务是否活着
(Invoke-WebRequest "http://<网关IP>:8710/health").Content  # ok=true

# 3. 对着音箱说一句话，看 L1 有没有产出录音
adb -s <IP>:5555 logcat -d -s R1BRIDGE:V | Select-String "ASR_AUDIO"

# 4. 看 L2 有没有拿到文字
adb -s <IP>:5555 logcat -d -s R1BRAIN:V | Select-String "自建 ASR"

# 5. 录到的音频对不对（最直接）
adb -s <IP>:5555 shell "ls -l /data/data/com.r1.brain/cache/*.wav"
# 时长应接近你说这句话的时长；文件大小 ≈ 秒数 × 32000 字节
```

---

## 6. 已知坑

| 现象 | 原因 / 对策 |
|---|---|
| 每句话都回「我没听清，请再说一遍」，`/api/v1/logs` 里有 `自建 ASR 失败: ... EACCES (Permission denied)` | **L1 的录音在它自己的私有目录里，L2 是另一个 UID**：目录 0700、文件 0600，`open` 一定 `EACCES`。2026-11-03 20:29 / 20:32 连续两轮实测如此（「播放周杰伦的歌」两次都被当成没听清）。Bridge 已在录音开始时把数据目录与 cache 目录置 `o+x`、wav 置 `o+r`（`Bridge.shareForL2`）；如果还报这条，说明装的是没带该修复的 L1 |
| 识别成完全不相干的话（「悟空悟空」/「嗯嗯」），但录音时长正常 | **双声道交织数据被当成单声道写盘**。看 L1 日志里的 `录音输入声道 = N`：N=2 表示已按 4 麦 HAL 的双声道折叠过；若日志显示 1 而结果依旧乱，说明判定阈值被噪声带偏，对照 `detectChannels` 的两次实测值（同帧差 44.9 / 相邻差 7.4）调整 |
| 说完没反应，日志里 `ASR_AUDIO` 都没有 | L1 的 smali 补丁没生效：确认 `l1/build.ps1` 里 `com/unisound/common/e` 的三处注入都成功（构建末尾自检会报） |
| 有 `ASR_AUDIO` 但 L2 不处理 | `asrEngine` 不是 `self`，L2 会直接忽略该事件 |
| 第一次说话要等十几秒 | ASR 服务冷启动约 11 秒，属正常；连着说第二次就快了 |
| 识别成乱码/极短文字 | 录到的不是 16k 单声道，或只录到几十毫秒（检查 tee 是否在 `openAudioIn` 就开始了） |
| 说完一遍后第二次没录音 | `closeAudioIn` 没被调用（原厂会话没正常结束），需要看 `USC` 日志的会话时序 |
