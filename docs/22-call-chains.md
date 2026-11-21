# 调用链路（说一句话之后到底发生了什么）

> 目的：**看图就能知道每一步调了谁、走不走外网、调了哪个接口、失败了会怎样。**
> 图例：`──` 设备内调用；`╌╌` 外网请求；`[接口]` HTTP 路径；`{广播}` 跨进程广播。

---

## 0. 总览（先看这张）

```mermaid
flowchart LR
  subgraph 设备内
    U[用户说话] --> L1[L1 原厂包内的桥<br/>唤醒 + ASR]
    L1 -- {com.r1.bridge<br/>ASR_RESULT} --> L2[L2 MainService]
    L2 --> IR[IntentRouter<br/>本地意图]
    IR -- 命中 --> LOCAL[本地动作<br/>音量/闹钟/灯环/点歌]
    IR -- 未命中 --> BRAIN[Brain<br/>回显 or 大模型]
    L2 --> TTS[SpeechEngine<br/>分句合成 + 播放]
    W[网页 / 手机] -- HTTP :8686 --> HS[HttpServer]
    HS --> IR
    HS --> PLAY[MusicPlayer]
    L1 -- 反射 --> LED[灯环 IPC]
  end
  BRAIN ╌╌ ▶|POST /chat/completions| LLM[(大模型服务)]
  TTS ╌╌ ▶|POST /v1/audio/speech| TS[(TTS 服务)]
  PLAY ╌╌ ▶|取直链| MU[(网易云 / QQ 音乐)]
  IR ╌╌ ▶|天气| WE[(和风 / wttr.in)]
```

一句话：**语音只能从 L1 进来，网页只能从 8686 进来；两条路最终都汇到 `IntentRouter`。**

---

## 1. 主链路：语音说一句话

```
用户：「小讯小讯，播放一首周杰伦的歌」
  │
  │ ① 唤醒（原厂链路，我们没改）
  ▼
[L1] com.phicomm.speaker.device 进程（Janus 顶替原厂包）
  │ ② 本地 ASR → NLUDispatcher.onASRResult(json)
  │    └─ Bridge.onAsrResult() 取 text 字段
  │ ③ sendBroadcast({com.r1.bridge}, type=ASR_RESULT, text=…)
  ▼  （定向广播，只发给 com.r1.brain）
[L2] MainService
  │ ④ BridgeReceiver.onReceive()
  │     ├─ noteL1()            ← 任何桥消息都刷新「L1 在线」时间戳
  │     └─ say(text)
  │ ⑤ Brain.handle() → 单线程池（同一时刻只处理一句）
  │ ⑥ IntentRouter.handle(text)  ← 意图路由，逐组匹配
  │     └─ 命中「点歌」→ playSong()
  │ ⑦ 取直链（见 §4）→ MusicPlayer.play(url, title)
  │ ⑧ 返回一句播报词：「正在为你播放周杰伦的《屋顶》。」
  │ ⑨ Brain.recordTurn() 落历史 + MainService.speak(reply)
  ▼
[L2] SpeechEngine
  │ ⑩ 分句 → 3 路并发 POST {ttsUrl}/v1/audio/speech → 落盘缓存
  │ ⑪ 按顺序 MediaPlayer 播放（首句先出，后句已在合成）
  ▼
喇叭出声
```

**关键点**

- 唤醒词永远是原厂的，L2 不监听麦克风。
- L1 只做「取文本 → 发广播」，不做业务判断。
- 一句话只会在 `Brain` 的单线程池里串行处理，不会互相打架。
- 本地意图命中就**不调大模型**（省钱、延迟从 1s 降到 10ms 级）。
- **反问 / 续听**：连续对话开着、或回复是反问（最多连问 `MAX_CLARIFY_STREAK` 次）时，播完续听一句。
  **只有 L1 v3 才续听**（`MainService.canFollowUp`）：v3 不调 `enterASR`（实测录到静音），
  而是从唤醒态麦克风流直接录、自己判停，6 秒没人说话就收场（`docs/07` §4.3）。
- **续听要等 700ms 再收音**：TTS 刚结束就开录会把自己的尾音录进去
  （实测录满 10 秒、ASR 只认出「嗯。」）。
- **一轮的收尾集中在 `VoiceSession`**：唤醒时音乐用 `pauseForVoice` 暂停并记标记；
  听懂后灯环转 `loading`；开口时灯回常亮效果；播完 / 取消 / 没听清 → `end()` 恢复音乐、灯回常亮。
  用户在这一轮里说了「暂停 / 停止 / 点歌」就清掉标记，不再自作主张恢复。90 秒兜底强制收尾。
- **TTS 三级兜底**：HTTP TTS 无论续不续听都挂回调，失败走原厂 TTS（经 L1，v3 有 `TTS_DONE` 回执），
  原厂也失败再走系统 TTS。以前不续听时回调为 null，HTTP TTS 一挂就整句没声音。
- **模型不许声称执行过操作**：`Brain` 在可配置的 systemPrompt 之后追加一段硬规则
  （开关连续对话/放歌/闹钟/音量都只能由本地程序做，没执行就不能说"已帮你…"）。
  语音侧的填充词（「嗯/啊/哦」）也不送模型，直接当没听清——否则模型会把它当"同意"。
- **播报可被打断**：播报期间 `onAudioData` 不写盘，只算能量；持续 300ms 高于
  「自适应底噪 ×4 且 >1200」就 `stopOriginalTts()` 并立即转为录音。
  日志每 2 秒留一条 `播报中监听 rms=… ema=…`，用于现场标定阈值。
- **提前判停**（设置页开关，默认关）：唤醒后 1.2 秒内开始的声音段不算（唤醒词尾巴），
  之后说满 0.6 秒、静音 1.2 秒就收尾（日志前缀 `[自己判停]`）。实测原厂会把 2 秒的话录成 5062ms。
- **录音增益逐块自适应**（`VoiceDsp.Agc`）：只在像人声的块上往上调、超标立刻降、tanh 软限幅。
  以前按开头 0.6 秒估一次再硬削，开头是停顿时会估成 12 倍；离线对照 `tools/VoiceDspProbe.java`
  （先停顿再贴近说话：旧算法削波 6273 点，新算法 0 点）。

---

## 1.5 本地意图没命中：交给模型 + 工具

```
ASR 文本 → IntentRouter 未命中
        → Brain.callLlm：带 tools（音量/音乐/灯环/闹钟/天气/状态/时间/停止播报）
        → 模型返回 tool_calls（finish_reason=tool_calls）
        → Tools.call() 拼一句本地意图听得懂的话，交给 IntentRouter 执行
        → 直接把工具返回的固定文案作为播报内容（不再回模型改写）
```

要点：
- **工具执行复用本地意图**：不重写业务，保证「模型调的」和「用户说的」行为一致。
- **不再二次请求模型**：工具返回的就是成品文案（如「音量已调到 30%。」），既快又不会编。
- 上游不认 `tools`（400）时自动去掉重发，退回纯对话。
- 模型只在本地意图没命中时介入：固定说法仍然是 0.1~0.3 秒的本地路径。
- 工具清单与参数在 `Tools.schema()`；新增工具时同时补 schema 与 `Tools.call` 分支。

## 2. 网页/手机发指令

```
浏览器 R1.sendCommand(text)
  │ POST /api/v1/chat   {text, source}
  ▼
HttpServer（8 线程池，令牌校验在最外层）
  │ ① checkToken()：X-Token / Bearer / ?token= / Cookie / body.token
  │ ② Brain.request(cfg, text)  ← 与语音完全同一条路（IntentRouter → LLM）
  │ ③ 若 autoSpeak=true → MainService.speak(reply)（跟随「合成」设置：原厂走 L1 / 自建走 HTTP TTS）
  ▼
返回 JSON：{ok, reply, costMs, time, echoMode}
```

两条路的唯一区别：**语音会把回复念出来，网页只在 `autoSpeak` 打开时才念。**

---

## 3. 意图路由：什么时候不调大模型

`IntentRouter.handle()` 按顺序尝试，命中即返回：

| 顺序 | 分类 | 例子 | 实际动作 | 外网？ |
|---|---|---|---|---|
| 1 | 打断播报 | 别说了 / 安静点 | `SpeechEngine.stop()` | 否 |
| 2 | 闹钟 | 二十分钟后提醒我 / 明天七点叫我 / 取消闹钟 | 写 `Config.alarmsJson` | 否 |
| 3 | 音乐播控 | 暂停 / 下一首 / 30 分钟后停止 | `MusicPlayer` | 否 |
| 3 | **点歌** | 放一首七里香 / 播放一首周杰伦的歌 / 换一首 | 见 §4 | **是** |
| 4 | 场景 | 晚安 / 早上好 | 定时 + 音量 + 天气 | 早上好会查天气 |
| 5 | 报时/日期 | 现在几点 / 今天几号 | 本地时间 | 否 |
| 5 | 天气 | 杭州天气 / 今天多少度 | 见 §5 | **是** |
| 6 | 运维 | 重启音箱 / 清理缓存 / 系统体检 | `DeviceOps` | 否 |
| 7 | 音量 | 音量调到 40% / 大声点 / 静音 | `AudioManager` | 否 |
| 8 | 灯环 | 开灯 / 关灯 / 呼吸灯 | 见 §6 | 否（跨进程） |
| 9 | 智能家居 | 打开客厅灯（需配 HA） | 见 §7 | **是**（局域网） |
| 10 | 其余 | 随便聊点什么 | `Brain.callLlm()` | **是** |

> 天气/灯环/点歌都带「失败样子」：取不到就明说原因，不会静默什么都不做。

---

## 4. 点歌链路（最容易误解的一条）

```
IntentRouter.playSong(keyword, artistMode)
  │
  ├─ MusicSource.firstWithResults(cfg, keyword)
  │     ├─ 先网易云：GET music.163.com/api/search/get?s=…&type=1
  │     └─ 没结果再 QQ：GET c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?key=…
  │
  ├─ 选哪一首
  │     ├─ 按歌手点歌：只认「第一顺位主唱」，再从中随机取
  │     └─ 按歌名点歌：按「原名 > 带版本后缀 > 翻唱/现场」打分排序
  │
  ├─ 逐个候选取直链（最多 8 首，会员曲目取不到就换下一首）
  │     ├─ 网易云：GET /api/song/enhance/player/url?ids=[id]&br=320000
  │     └─ QQ：POST u.y.qq.com/cgi-bin/musics.fcg?sign=zzc…（vkey.GetVkeyServer）
  │
  └─ 拿到直链 → MusicPlayer.play(url, title)
        └─ MusicRelay.localUrlFor(url) 换成 http://127.0.0.1:<port>/audio.mp3?u=…
              └─ IO 线程里 MediaPlayer.setDataSource(回环地址) → prepare → start
```

> **为什么非要中转一层**：这台机器上 mediaserver 的老 HTTP 栈拉不动网易云 CDN ——
> 实测（2026-11-03 22:49/22:50/22:51）日志恒定是
> `NuCachedSource2: source returned error -1` 每 3 秒重试一次、10 次后
> `(1/-2147483648)`，期间 `/proc/net/tcp` 里根本没有到 CDN 的连接；
> 同一个 URL 用 L2 自己的 `Http` 去拉却是好的（能读到 `ID3` 头）。
> 所以取字节的事交给 L2 做（`MusicRelay`），mediaserver 只连 127.0.0.1。
> 取舍：中转只监听回环、单连接、支持 Range 透传（拖动进度要用），
> 全程 8KB 流式转发，不整首进内存。

**网页点歌**走的是同一套能力，只是入口不同：

```
GET  /api/v1/music/search?source=netease&q=晴天   → MusicSource.search()
POST /api/v1/music/play    {source, id, title}    → MusicSource.songUrl() → MusicPlayer.play()
```

**登录**：`POST /api/v1/music/cookie {source, cookie}` → 写进 `Config`（只存设备本地）。
未登录时搜到的热门曲目多为翻唱，会员曲目取不到直链——这是数据源行为，不是 bug。

---

**音源优先级（2026-11-04）**：`MusicSource.SEARCH_CHAIN` = `本地曲库 → 网易云 → QQ`。
本地库靠 `Config.songloftUrl` + `songloftToken` 激活（任一为空自动跳过，没部署的用户无感）；
某一源命中但取不到播放地址时继续降级到下一个源，见 `IntentRouter.playSong()` 的 `allWithResults` 循环。
## 5. 天气链路

```
WeatherService.query(cfg, city)
  │ for provider in [QWeatherProvider, FreeWeatherProvider]:
  │     ├─ isAvailable()?  和风要有 Key
  │     ├─ QWeatherProvider
  │     │     ├─ GET {host}/geo/v2/city/lookup   → 拿 location id
  │     │     └─ GET {host}/v7/weather/now       → 拼播报词
  │     └─ FreeWeatherProvider
  │           └─ GET wttr.in/{city}?format=…     （HTTPS 握手失败自动降级 HTTP）
  └─ 全部失败 → 「当前网络较忙，暂时无法获取实时天气数据」
```

入口：语音「杭州天气」、网页「测试天气」、`GET /api/v1/weather?city=杭州`。

---

## 6. 灯环链路（唯一一条 L2 → L1 的命令）

```
语音「开灯」/ 网页 R1.setLight('wakeup') / GET /api/v1/l1/light?mode=wakeup
  │
  ▼
[L2] IntentRouter.light() 或 HttpServer
  │ ① MainService.l1Alive()？90 秒内收到过 L1 心跳才继续
  │ ② L1Bridge.sendLight(ctx, LightEffect)
  │     └─ sendBroadcast({com.r1.bridge.cmd}, cmd=light, mode=wakeup)
  ▼
[L1] Bridge.LightCommandReceiver.onReceive()
  │ ③ 反射 new PhicommLightController(ctx)
  │ ④ 按 mode 调 turnOnLoadingLight / turnOnWakeupLastLight / turnOffALLWakeupLight …
  │     └─ 原厂走 IPC 交给下层守护进程 → 灯环变化
  │ ⑤ 回广播 {com.r1.bridge}, type=LIGHT_RESULT, mode, ok
  ▼
[L2] BridgeReceiver → L1Bridge.onLightResult()   （GET /api/v1/l1/light/state 可查）
```

注意：HTTP 接口在 ② 之后就返回 `{"ok":true}`，**不等灯环真正亮**；
真实结果看回执或 `light/state`。

---

## 7. 闹钟到点链路

```
设置：语音「二十分钟后提醒我」 / 网页设置页新增闹钟
  │ → IntentRouter.addAlarm() 或 POST /api/v1/config {alarms:[…]}
  │ → 写 Config.alarmsJson（JSON 数组，最多 50 条）
  ▼
[L2] AlarmScheduler：Handler 每 15 秒 tick 一次
  │ ① 取北京时间 HH:mm
  │ ② 遍历启用的闹钟，与 time 比对（同一分钟只触发一次）
  │ ③ 一次性闹钟（once=true）触发后置 enabled=false 并落盘
  │ ④ action=weather 的闹钟：**在后台线程**查天气再播
  │    主线程里查会抛 NetworkOnMainThreadException（message 是 null，日志只显示「失败：null」）
  │ ⑤ MainService.speak("北京时间 08:00，闹钟时间到。……")
  ▼
SpeechEngine → 喇叭
```

闹钟的 `action` 决定播什么，**建闹钟时必须写进去**：

| action | 触发时念什么 |
|---|---|
| `speak`（缺省） | 备注文本，例如「北京时间 07:55，闹钟时间到。播报天气」 |
| `weather` | 先调 `WeatherService.query()`，再念「…闹钟时间到。【城市天气】当前…」 |

> 2026-11-03 踩过：网页端 `R1.addAlarm()` 当时没写 `action`，服务端按 `speak` 处理，
> 于是每天 7:55 只念了四个字「播报天气」。现在桌面端和手机端都有「提醒 / 天气」选择，
> 引擎的 `addAlarm` 也会带上 `action`。

**没有用 `AlarmManager`**：设备常年通电、L2 是前台服务，15 秒轮询更简单可靠，
代价是最多晚 15 秒响（详见 `06-scheduler.md` 顶部说明）。

---

## 8. TTS 播报内部
```
MainService.speak(text)
  │ ① SpeechEngine.isConfigured()？没配就退回系统 TTS（本机基本不可用）
  │ ② split()：按 。！？； 切句，单句 >60 字再按逗号切；整段上限 2000 字
  │ ③ 首句立即合成，其余最多 3 路并发
  │      └─ POST {ttsUrl}/v1/audio/speech  {model, voice, input, speed}
  │      └─ 音频落盘到 cacheDir/tts（LRU 40 条）
  │ ④ 严格按原顺序播放：播第 N 句时第 N+1 句已在合成
  │ ⑤ 新的播报会打断旧的（generation 递增，旧任务自动放弃）
  ▼
MediaPlayer 逐句播放
```

---

## 9. 语音识别（ASR）只有一条路：自建 ASR

> **2026-11-01 起原厂通道作废。** 云端 `aios-home.hivoice.cn` 域名不解析、恒回 `609`，
> 本地语法只会硬套命令词。详见 `docs/24-self-hosted-asr.md`。

```
用户说话 → 原厂引擎开识别会话（openAudioIn）
  → L1 在 com.unisound.common.e.readData 上 tee PCM（16k/16bit/单声道，无需重采样）
  → 边读边写 WAV（≤10 秒），closeAudioIn 时封口
  → 广播 ASR_AUDIO{path}
  → MainService.onVoiceAudio(path)
      ├─ 读文件（≤400KB）
      ├─ AsrClient.transcribe(cfg, wav)
      │    └─ POST {asrUrl}/v1/asr/transcribe（multipart file + X-API-Key）
      │         → {text, duration_seconds, provider_type}
      └─ 拿到 text 后走 MainService.say(text, true)
```

服务地址：`http://<网关IP>:8720`（局域网；远程访问需另配跳板，设备走局域网）。
调用方令牌存 `Config.asrToken`（X-API-Key，明文只存本机）。读用量走 `GET /usage`（不算次数）。
按次数计费下**不用流式**：整段上传每句只计一次；旧的 FunASR `/asr/stream` 已移除。

已废弃：`ASR_RESULT`（云端文本）与 `ASR_WEAK`（本地硬猜）。`asrEngine=self` 时 L2 直接忽略。

失败处理：网关不可达 / 空文本 → 语音「没听清」；`QUOTA_EXCEEDED`(429) → 语音「本月额度已用完」并停止，不重试、不换账号。
测试入口：`GET /api/v1/asr/health?url=&token=`、`POST /api/v1/asr/test {url, audio(base64 WAV)}`、`GET /api/v1/asr/quota`。

---

## 10. 页面轮询：谁多久调一次什么
| 页面行为 | 接口 | 频率 |
|---|---|---|
| 所有页面（引擎） | `GET /api/v1/status` + `GET /api/v1/config` | 每 **10 秒**（页面隐藏时停） |
| 播放器状态 | `GET /api/v1/player` | 每 **3 秒**，另加本地每秒插值进度 |
| 对话页 | `GET /api/v1/history` | 页面加载 + 每次发消息后 |
| 发指令 | `POST /api/v1/chat` | 手动 |
| 音乐页搜索 | `GET /api/v1/music/search` | 手动 |
| 音乐页点播 | `POST /api/v1/music/play` | 手动 |
| 设置页保存 | `POST /api/v1/config` | 手动 |
| 日志页 | `GET /api/v1/logs` | 手动刷新 |

---

## 11. 线程与超时（排查卡顿时看这张）

| 执行体 | 线程模型 | 超时 |
|---|---|---|
| HTTP 服务 | 固定 8 线程池 | 读请求头/体有上限（body ≤ 64KB） |
| `Brain`（对话） | 单线程池 | 大模型 连接 5s / 读 20s |
| `SpeechEngine` 播放 | 单线程（保证顺序） | 单句播放最长 20s |
| `SpeechEngine` 合成 | 固定 3 线程 | 连接 5s / 读 30s |
| `AlarmScheduler` | 主线程 Handler，15s tick | —— |
| `MusicPlayer` | 单线程 IO 池 | 由 MediaPlayer 自己决定 |
| L1 心跳 | 独立守护线程 | 每 30s 一次 |
| 所有外网请求 | 走 `Http`（默认 连接 5s / 读 15s） | 响应体上限 2MB |

---

## 12. 失败时发生什么

| 环节 | 失败表现 | 用户看到/听到 |
|---|---|---|
| L1 未运行 | `/status` 的 `l1=false`，90 秒无心跳 | 语音没反应（唤醒本身由原厂负责） |
| ASR 没出文本 | 桥不发消息 | 无反应；原厂自己会提示 |
| 大模型不通 | `Brain` 抛错 | 播「抱歉，我这边出错了」；网页显示原始错误 |
| 未配 Key | `echoMode=true` | 回显模式：复述一遍并提示去配 Key |
| TTS 未配 | 退回系统 TTS（不可用） | 只有网页文字，没有声音 |
| 直链取不到 | 逐个换候选，全失败才提示 | 「这几首都要登录/无版权」 |
| 天气两源都挂 | 抛错被门面吞掉并降级 | 「网络较忙，稍后再试」 |
| L1 不在线时点灯环 | 发广播前就被拦下 | 「灯环要我这边在线才能控制」 |
| 网页令牌错 | 401 | 页面提示补令牌 |

---

## 13. 想自己验一遍

```powershell
$base = "http://<IP>:8686"

# 主链路（不用嘴，直接打接口）
Invoke-WebRequest "$base/api/v1/chat" -Method POST -ContentType "application/json" `
  -Body '{"text":"现在几点"}'                      # 期望：本地意图，costMs 个位数

# 外部链路
Invoke-WebRequest "$base/api/v1/music/search?source=netease&q=晴天"
Invoke-WebRequest "$base/api/v1/weather?city=杭州"
Invoke-WebRequest "$base/api/v1/l1/light?mode=loading"

# 看每一步的日志
adb -s <IP>:5555 logcat -s R1BRAIN:V R1BRIDGE:V
```

日志里能对上本文的每个编号：`=== L1 ASR -> …`（④）、`点歌跳过…`（§4）、
`TTS 第 N/M 句播完`（§8）。
