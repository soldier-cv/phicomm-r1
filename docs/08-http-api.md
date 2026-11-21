# 接口与远程操控设计

> 回答一个问题：**远程操控怎么做？音响自带的能力和我们自己的功能，怎么统一成一套接口？**
> 结论先说：你的理解是对的 —— **远程只访问我们程序（L2），它内部再去调音响的能力（L1）。**
> 但有几个地方要补清楚，否则会踩坑。

---

## 零、当前接口清单（v0.4.0，以代码为准）

所有 `/api/` 请求都要带访问令牌（首次未设置时开放）：
`X-Token` 头 / `Authorization: Bearer` / `?token=` / 登录后下发的 `r1_token` Cookie / JSON body 的 `token` 字段。

| 方法 | 路径 | 作用 |
|---|---|---|
| GET | `/api/v1/status` | 硬件遥测、音量、蓝牙、网络、L1 在线状态 |
| GET | `/api/v1/history` | 最近 50 条对话 |
| DELETE | `/api/v1/history` | 清空对话历史 |
| POST | `/api/v1/chat` | 发送文本指令 / 问答 |
| GET / POST | `/api/v1/config` | 读取 / 保存配置（密钥自动脱敏） |
| POST | `/api/v1/token` | 设置访问令牌（设置后强制校验） |
| GET | `/api/v1/config/export` | 导出完整配置（含密钥、Cookie、ASR、Songloft、JWT，用于换机） |
| POST | `/api/v1/config/import` | 合并导入配置（令牌不导入；字段与 export 对称） |
| GET | `/api/v1/music/list` | 扫描本机 `/sdcard/Music` 曲库 |
| GET | `/api/v1/music/lyrics` | 读取同名 `.lrc` 歌词 |
| GET | `/api/v1/music/search` | 云音乐搜索（`source=netease\|qq\|songloft`） |
| POST | `/api/v1/music/play` | 取直链并播放（`source=netease\|qq\|songloft`） |
| POST | `/api/v1/music/cookie` | 保存网易云 / QQ 音乐登录 Cookie |
| POST | `/api/v1/music/songloft/test` | 测试本地曲库连通性（地址 + 令牌），返回 `{ok,total}` 或 `{ok:false,error}` |
| GET | `/api/v1/player` | 播放器状态 |
| POST | `/api/v1/player/{play,toggle,pause,stop,next,prev,seek,mode,sleep}` | 播放控制 |
| GET / POST | `/api/v1/volume` | 读取 / 设置主音量 |
| GET / POST | `/api/v1/receivers` | **统一接收端开关**（DLNA / AirPlay / 蓝牙含可发现）。这是开关，不是推流。GET 查状态与错误；POST `{dlna?,airplay?,bluetooth?,discoverable?}` |
| GET / POST | `/api/v1/cast` | 自建 DLNA 开关（`POST {on}`），与 `/api/v1/receivers` 的 `dlna` 同一实现 |
| POST | `/api/v1/bluetooth` | 开 / 关蓝牙适配器，可带 `{enabled,discoverable}`，走同一套 `Receivers` 实现 |
| POST | `/api/v1/tts` | 触发一次语音播报 |
| POST | `/api/v1/tts/stop` | 停止播报 |
| GET | `/api/v1/tts/status` | TTS 引擎状态 |
| GET | `/api/v1/weather` | 城市天气（和风 → wttr 兜底） |
| GET | `/api/v1/l1/light` | 灯环效果（`mode=off\|loading\|wakeup\|dormant\|net\|scan`，未知值 400）；设定值记为常亮效果，对话结束后回到它 |
| GET | `/api/v1/l1/light/state` | 最近一次灯环回执 |
| GET | `/api/v1/logs` | 本应用 logcat |
| GET | `/api/v1/apps` | 已安装应用与运行状态 |
| POST | `/api/v1/apps/install` | 分块上传 APK 并静默安装 |
| POST | `/api/v1/apps/uninstall` | 静默卸载 |
| POST | `/api/v1/apps/stop` | 强行停止 |
| POST | `/api/v1/system/reboot` | 重启音箱 |
| POST | `/api/v1/system/clean` | 清理临时文件与缓存 |

**测试 / 诊断类接口**（都用「页面上还没保存」的值，便于设置页先测后存）：

| 方法 | 路径 | 作用 |
|---|---|---|
| GET | `/api/v1/llm/models?baseUrl=&apiKey=` | 拉取 OpenAI 兼容模型列表 |
| POST | `/api/v1/llm/test` | 发一条极短请求测「地址 + Key + 模型」 |
| POST | `/api/v1/ha/test` | Home Assistant 连通性 |
| POST | `/api/v1/weather/test` | 天气双源测试（可覆盖 Key/Host/JWT） |
| GET | `/api/v1/asr/health?url=&token=` | 整合 ASR 网关用量（GET /usage，不算次数；token 可省略则用已保存的） |
| POST | `/api/v1/asr/test` | 整合网关转写测试（可带 WAV base64） |
| GET | `/api/v1/asr/quota` | 显示「本月已用/剩余/上限/重置日」 |
| POST | `/api/v1/weather/keygen` | 生成和风 Ed25519 密钥对 |
| POST | `/api/v1/weather/pubkey` | 由私钥推导公钥 |
| POST | `/api/v1/weather/jwt` | 只签发不请求，确认 JWT 凭据配套 |
| GET | `/api/v1/cast/status` | 仅探测端口是否在听（兼容旧控制台）；新逻辑请用 `/api/v1/receivers` |
| POST | `/api/v1/cast/wake` | 唤醒原厂播放器主服务后探测（不含开关） |

> 下面几节是当时的**设计讨论**，保留原因与取舍；接口以本表为准。

---

## 〇-附、接收端开关（不是推流）

语音工具、控制台徽章、小程序首页都走同一套 L2 接口：`GET/POST /api/v1/receivers`。

- **DLNA**：自建 `DlnaRenderer`（SSDP 1900 + HTTP 49494），可开可关，默认开机打开。
- **蓝牙**：适配器开关 + `setScanMode` 进入可发现/配对（默认 120 秒）。打开蓝牙时默认同时进入可发现。
- **AirPlay**：打开或关闭原厂接收端。L1 向 `com.phicomm.speaker.player` 发原厂 `msgcenter` 消息 `type=0x40, sub=1/2`（与 `PhicommXController.open/closeBlueToothStatus` 同一条），进入「等待外部连接」后会 `bootAllExternalConnectPlayer()`，Shairport 监听 **TCP 5002** 并广播 `_raop._tcp`（设备名 `PhicommAirPlay`）。不要再用 `PLAYER/stop` 或 `am startservice AirPlayerService`。成功与否以 5002 是否在听为准。原厂等待态约 90 秒无客户端会退出，L2 在偏好仍为打开时每 20 秒检查并续期。等待期间灯环会切到原厂效果 `0xcc`；同时会拉起原厂 DLNA 与蓝牙 A2DP 接收（不影响自建 DLNA）。接口本身很快返回（约 2 秒内），不再串行尝试 20 秒以上。失败时 JSON 里的 `attempts` / `error` / `l1` 会写明路径，不假装做成。
- 本接口**不会**从音箱向外推流，也不做 AirPlay/蓝牙发送端。


---

## 一、先把「接口」这个词拆开（这是最容易混的地方）

我们说的「接口」其实有三种，性质完全不同：

| 类型 | 例子 | 谁能调 | 有没有网络形态 |
|---|---|---|---|
| **A. 网络接口** | 第三方 MOD 8080 的 `/toggle_ambient_light` | 局域网任何人 | 有（HTTP） |
| **B. 进程内 Java API** | `PhicommLightController.sendMessage(4096,…)`、`ctx.playTTS()` | 只有那个包自己 | **没有**，只能在自己的进程里调 |
| **C. 系统服务（Binder）** | `ISysPrivate`、`AudioFlinger`、`MediaSession` | 有身份/权限的进程 | 有（Binder），但不是 HTTP |

**关键点**：音响「自带的」能力，绝大多数是 **B 类**（进程内 Java API），
它们**本来就没有网络形态**。

> 第三方 MOD 在 8080 上做的那 22 个接口，**不是音响自带的**，是 MOD 作者
> 自己在 DEX 里包了一层 HTTP（见 `archive/where-8080-comes-from.md`）。
> 也就是说：**「音响自带接口」这个说法其实不存在** —— 只有「音响自带能力」。

所以要做远程操控，必然是我们**新写一层 HTTP**，把 B 类能力包装出去。
问题只是：这一层写在哪、谁能访问。

---

## 二、单一入口原则

```
❌ 不这样：手机 → L1（直接打 8080）         ← MOD 时期的做法，无鉴权
❌ 也不这样：手机 → L1 和 L2 两个不同端口     ← 两套鉴权、两套日志、两个地址
✅ 只这样：  手机 → L2（唯一入口）→ 需要时再调 L1
```

**所有远程请求都进 L2，L1 不直接对外暴露。**

理由：

| 理由 | 说明 |
|---|---|
| **只有一个鉴权点** | L1 只认 L2 的桥消息，不认网络，少一个攻击面 |
| **L1 可以极薄** | 它只需要实现「桥」，不需要实现 HTTP、JSON、鉴权、CORS |
| **能力变更不影响外部** | 以后 L1 里换了实现，外部接口不用动 |
| **现在的 8080 就是反面教材** | 无鉴权裸 GET，`/qw_pubkey?gen=1` 能覆盖私钥 |

---

## 三、请求路径

### 3.0 阶段决定（2026-10-29）：先只做局域网

> **远程操控（ZeroTier 跳板）延后，不作为第一期目标。**
> 第一期的验收标准很简单：**同一 WiFi 下，手机/电脑能打开 `http://<音箱IP>:8686/` 并操控音箱。**

理由：
- 局域网已经覆盖了 95% 的使用场景（在家用手机控制）。
- 跳板方案涉及另一台机器的路由/NAT，属于独立的小工程，不该拖累主线。
- 先把 L2 的接口做好、鉴权做对，远程只是换个入口，不影响内部设计。

### 3.1 第一期：局域网（唯一目标）

```
手机 / 电脑浏览器（同一 WiFi）
   │  http://<音箱IP>:8686/api/v1/...
   ▼
┌──────────────┐   需要 L1 的能力时
│      L2      │ ──────────────► 广播/socket ──► L1
│  (API 网关)   │ ◄──────────────────────────────
└──────────────┘
   本地直接处理：LLM 对话、音乐、配置、日志、任务编排
```

- L2 直接在音箱上监听 **8686**，局域网内任何设备都能访问。
- **带 token 鉴权**（不能像现在的 8080 那样裸奔）。
- L3 在这一期**可选**：可以不起，配置界面直接由 L2 提供静态页；也可以起来放日志和历史。

### 3.2 第二期（延后）：远程，用一台局域网设备做跳板

> 这条已经想清楚，但**先不做**，记在这里免得下次重新论证。

拓扑：

```
手机（装 ZeroTier，在外面）
   │  http://<跳板机 ZT IP>:8686
   ▼
┌─────────────────────────┐
│  跳板机（家里的一台电脑）  │
│  · 同时连着 ZeroTier 网络 │
│  · 同时连着家里局域网      │
│  · 做反向代理 + 二次鉴权   │
└─────────────────────────┘
   │  局域网
   ▼
L2 @ <音箱IP>:8686
```

要点：
- **跳板机不需要额外硬件**，就是"一台同时接入两个网络的机器"。
- 不需要改路由器、不需要公网 IP、不做端口映射。
- L2 完全不用改 —— 它只认本地 8686，跳板机负责转发。
- 电脑上 **ZeroTier 已经装好了**（实测 `ZeroTier One` → `100.64.0.110`），
  到时候只要在跳板上做个反向代理即可。

### 3.3 为什么不把 ZeroTier 装在音箱上

音箱是 Android 5.1 / armv7 / 清理后可用约 25MB 内存，装不了也跑不动。
所以远程通道必须落在**另一台机器**上 —— 这就是"跳板"的由来。

---

## 四、接口清单（能力 → 归属）

`base = /api/v1`，全部 JSON，全部 `Authorization: Bearer <token>`。

### 4.1 L2 直接处理（不经过 L1）

| 方法 | 路径 | 干什么 |
|---|---|---|
| POST | `/chat` | **远程发一句文本，让音箱说出来**（核心） |
| POST | `/ask` | 发一句，只要回答文本，不播报 |
| GET | `/chat/history` | 最近的对话 |
| GET | `/config` | 读全部配置 |
| PATCH | `/config` | 改配置（LLM Key、音乐源、HA…） |
| GET | `/music/search?q=` | 搜歌（转给音乐源） |
| POST | `/music/play` | 播某首 |
| GET | `/music/sources` | 音乐源状态（哪个可用） |
| GET | `/device` | 聚合状态：在线、内存、网络、版本、L1 是否正常 |
| GET | `/logs?tail=` | 最近日志 |
| POST | `/ota/upload` | 上传新 apk 供自升级 |
| POST | `/l1/restart` | 让 L1 重连桥（不用重启设备） |

### 4.1.1 已实现（v0.2.0 实际契约，2026-10-30）

上表是目标形态；**下面这张才是设备上真在跑的**，客户端按这张接。

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| GET | `/status` | — | `{ok,online,version,uptimeMs,memFreeKb,memTotalKb,socTempC,pid,tts,ttsConfigured,l1,l1Version,port,model,echoMode,volume,volumeMax,volumePct,btSupported,btEnabled}` |
| GET | `/history` | — | `{ok,history:[…最近 50 条]}` |
| DELETE | `/history` | — | `{ok,history:[]}` |
| POST | `/chat` | `{text,source?}` | `{ok,reply,costMs,model,echoMode,time}`；单条 ≤500 字 |
| GET | `/config` | — | `{baseUrl,model,hasKey,echoMode,ttsUrl,ttsModel,ttsVoice,ttsSpeed,hasTtsKey,autoSpeak,haUrl,hasHaToken,hasWeatherKey,weatherCity,alarms}` |
| POST | `/config` | `{llmBaseUrl?,llmModel?,llmApiKey?,llmSystemPrompt?,ttsUrl?,ttsKey?,ttsModel?,ttsVoice?,ttsSpeed?,autoSpeak?,haUrl?,haToken?,weatherCity?,weatherKey?,alarms?}` | 同上（Key 只回 `hasKey`/`hasTtsKey`） |
| GET | `/music/list` | — | `{ok,tracks:[{id,title,subTitle,artist,album,format,spec,source,path,hasLrc}]}`（5 秒缓存） |
| GET | `/music/lyrics?path=` | — | `{ok,lrc}` |
| GET | `/player` | — | `{ok,hasTrack,source,title,playing,prepared,positionMs,durationMs,loop,sleepRemainSec,error}` |
| POST | `/player/play` | `{source,title?}` | 播放器状态 |
| POST | `/player/{toggle,pause,stop,next,prev}` | — | 播放器状态 |
| POST | `/player/seek` | `{ms}` | 播放器状态 |
| POST | `/player/mode` | `{loop:list\|single\|shuffle}` | 播放器状态 |
| POST | `/player/sleep` | `{minutes:0-1440}` | 播放器状态 |
| POST | `/bluetooth` | `{enabled}` | `{ok,btEnabled}` |
| GET | `/logs` | — | `{ok,logs:[{time,level,tag,msg}]}`（logcat 最近 50 行） |
| POST | `/tts` | `{text}` | `{ok,firstAudioMs,state}`；没配 TTS 返回 **409**；等待首帧上限 15s，超时但有后台播放时回 `{ok:true,pending:true}` |
| POST | `/tts/stop` | — | `{ok,state}` |
| GET | `/tts/status` | — | `{configured,state,lastFirstAudioMs,lastError,lastText}` |
| GET | `/volume` | — | `{ok,value,max,pct}` |
| POST | `/volume` | `{pct}` 或 `{value}` | `{ok,value,max,pct}` |
| GET | `/apps` | — | `{ok,apps:[{packageName,name,versionName,versionCode,isSystem,isRunning,sizeBytes,sizeMb}]}` |
| POST | `/apps/install` | 二进制流 (8KB 缓冲) | `{success,package,selfUpdate,output}` (流式直写 `/data/local/tmp`，回环 ADB uid=2000 静默安装，若是当前应用自动重启服务完成空中 OTA) |
| POST | `/apps/uninstall` | `{package}` | `{ok,package}` (静默卸载指定应用，自动回收空间与运行内存) |
| POST | `/apps/stop` | `{package}` | `{ok,package}` (强行停止指定应用) |
| POST | `/system/reboot` | — | `{ok,message}` (调用特权 reboot 安全重启音箱系统) |
| POST | `/system/clean` | — | `{ok,deletedFiles,freedMb}` (深度清理临时包与音频缓存并触发 JVM GC) |
| GET | `/weather?city=` | `city?` | `{ok,city,report}` (免 Key 开放源 wttr.in + 和风天气双通道自动降级) |

**鉴权**：`/api/**` 全部要令牌（未带返回 **401**）。认 `X-Token` 头、`Authorization: Bearer`、
`?token=` 查询参数、`r1_token` Cookie、JSON body 的 `token` 字段五种。
用 `http://<ip>:8686/?token=<令牌>` 打开首页时，服务端在 302 里下发 `r1_token` Cookie，
后续页面内的 API 请求自动带鉴权。

**TTS 语义**：`POST /tts` 会等到**首帧开始播放**（最多 15 秒）后返回，所以能拿到 `firstAudioMs`；
播放本体在后台继续，同一时刻只有一路播报，新的会打断旧的。返回 409 表示没配 `ttsUrl`。
15 秒内没等到首帧但也没有错误时返回 `{ok:true,pending:true}`——**这不代表失败**，
后台仍在合成/播放，不要按失败重试（会重复播报）。

**通用约定（2026-11-05）**：

- 请求体支持 `Content-Length` 与 `Transfer-Encoding: chunked`；畸形 JSON 返回 400，
  不再出现「socket 被直接关掉、客户端看不到任何响应」。
- 状态行符合 RFC（500 = `Internal Server Error`，503 = `Service Unavailable`）。
- 缓存：`/api/**` 一律 `Cache-Control: no-store`；静态页面/资源为 `public, max-age=300`，
  改样式后最多 5 分钟旧缓存，强制刷新即可。
- 工作队列有界（8 线程 + 32 排队），满时返回 **503**；网页 `/chat` 与语音共用一把执行锁，
  并发时后到的 5 秒内拿不到锁也返回 503。
- 配置写入是「合并」语义：请求里没出现的字段保持不变；密钥字段收到 `***` 时跳过，
  不会把页面掩码写回数据库。

### 4.2 需要转给 L1（音响自有能力）



| 方法 | 路径 | L1 内部做什么 |
|---|---|---|
| POST | `/voice/wake` | 主动触发一次唤醒（等价于叫它） |
| POST | `/voice/stop` | 打断当前播报 |
| GET/PATCH | `/voice` | 音色、唤醒词、语速、音调 |
| POST | `/tts` | 指定文本让它用原厂 TTS 播 |
| GET/POST | `/light` | 灯环：`{index:1-24, on:true}` 或 `{special:"dormant\|loading\|netlost"}` |
| GET | `/light/state` | 当前灯位 |
| POST | `/mode` | 系统模式（`setSysMode`） |
| GET/POST | `/alarm` | 定时任务（闹钟/定时播放） |
| POST | `/player/{play\|pause\|next\|prev}` | 播放控制（player 包） |
| GET | `/player/now` | 当前曲目 + 进度 |
| POST | `/player/seek` | 拖动进度（需要 `patch_seek` 那类改动） |

> **实现方式**：L2 把上表右列包装成桥消息发给 L1，L1 执行后回结果。
> 对外部客户端来说，`/light` 和 `/chat` 没有任何区别 —— 都是 L2 的一个 REST 接口。

### 4.3 桥消息（L2 ↔ L1，内部协议）

```
L1 → L2    ASR_RESULT   { text, sessionId, ts }
           WAKEUP       { sessionId }
           KEY_EVENT    { type: click|double|long|triple }
           STATE        { light, playing, mode, ttsSpeaking }

L2 → L1    SPEAK        { text, sessionId }
           STOP         { sessionId }
           LIGHT        { index, on } | { special }
           SYS_MODE     { mode }
           VOICE        { speaker, wakeWord, speed, pitch }
           WAKE         { }            主动触发一次唤醒
           ALARM_*      { … }
```

传输用**广播**最省事（Android 5.1 隐式广播限制少），需要低延迟再换本地 socket。

---

## 五、鉴权

| 层 | 做什么 |
|---|---|
| L2 | 校验 `Authorization: Bearer <token>`，token 在 L3 网页上生成 |
| L3 | 二次校验（同一个 token），并强制 HTTPS（远程时） |
| L1 | **不做网络鉴权** —— 它根本不监听端口，只接受 L2 的桥消息 |

> 桥消息本身也要防伪造：L1 校验发送方包名/签名（`PackageManager` 查 calling uid），
> 只接受 L2 的消息。否则局域网里别的 App 也能广播命令。

---

## 六、和现在 8080 的关系

| 阶段 | 8080 | 新接口 |
|---|---|---|
| 现在 | MOD 自带，无鉴权 | —— |
| L2 起来后 | 建议**关掉或限制**（见下） | 新接口并行跑 |
| L1 上线后 | **不再存在**（我们的 L1 不实现 8080） | 唯一入口 |

**要不要现在关掉 8080？** 我的建议是：**先不动**。
理由：它是现役 MOD 的一部分，关掉可能连带影响它的其他功能；
而且它只在局域网内可用。等 L1 替换掉整个包，它自然就没了。

> 但要知道它现在是敞开的：局域网里任何人 `curl` 就能改唤醒词、改 AI Key、开关蓝牙。
> 如果家里有访客网络或智能家居设备，建议在路由器上限制这个 IP 的 8080 访问。

---

## 七、这个设计解决的一个现实问题

**L2 的升级不再依赖 adb。**

```
手机 → L3 网页 → 上传新 apk → L3 推给 L2 → L2 自安装
```

音箱的 adbd 已经挂死过两次（只能断电恢复）。把升级路径放在 HTTP 上，
就不会因为 adb 挂了而失去控制。

---

## 八、待定项

| 问题 | 选项 | 影响 |
|---|---|---|
| 端口号 | 8686 / 8080 续用 / 其他 | 建议 8686，避开现在的 8080 |
| L2 常驻方式 | 常驻服务 / 唤醒时启动 | 清理后可用约 25MB，常驻仍要谨慎（当前用前台服务常驻） |
| L3 第一期是否要起 | 起 / 不起 | **可以不起**，L2 直接供静态页；等要日志/历史再起 |
| 远程（跳板） | 做 / 不做 | **延后**，见 §3.2 |

---

## 九、第一期验收标准

做完这一期就算成了，不牵扯任何远程/跳板：

1. 同一 WiFi 下，手机浏览器打开 `http://<音箱IP>:8686/` 能看到配置界面
2. 带 token 才能访问（未带 token 返回 401）
3. 能通过网页：
   - 发一句话让音箱说出来（`POST /chat`）
   - 开关氛围灯（`POST /light`）—— 需要 L1 就绪
   - 改 LLM Key / 音乐源（`PATCH /config`）
4. 音箱断电重启后，L2 自动重新上线，网页不用改地址（IP 已绑 MAC）
> **音源优先级**：`MusicSource.SEARCH_CHAIN` 为 `本地曲库 → 网易云 → QQ`。本地曲库由
> `/api/v1/config` 的 `songloftUrl` + `songloftToken` 激活，任一为空即跳过；某个音源
> 命中但取不到播放地址时会继续问下一个音源（例如本地文件被删、或 24bit FLAC 这台机器放不了）。
