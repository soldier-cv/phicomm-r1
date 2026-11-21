# 代码审查记录（2026-10-31）

> 范围：`l1/`、`l2-app/`、`web/` 全部源码。目标不是「挑刺」，是把**会反复踩的坑**变成规范。
> 每条都写清：问题 → 为什么算问题 → 怎么处理 → 验证方式。**没修的要写清楚为什么不修。**

---

## 一、已修（P0 / P1）

| # | 位置 | 问题 | 处理 |
|---|---|---|---|
| 1 | `DebugReceiver` | `exported=true` 且 `CONFIG` 能改 `baseUrl`/`apiKey`/`ttsUrl`：同机任意应用可把大模型请求劫持到自己的服务器，拿到全部对话与 Key | 设过访问令牌后，`CONFIG`/`SAY` 必须带同一令牌；规则与 HTTP 接口一致（见 `DebugReceiver#authorized`） |
| 2 | 5 处 HTTP 客户端 | 网易云 / QQ / HA / 天气 / 大模型各写一份 `HttpURLConnection`，超时 4~20 秒不等、响应上限 512KB~2MB 不等，改一处漏四处 | 新增 `Http.java`，统一超时、字符集、错误流、体积上限；五处全部改用它。TTS 音频**故意保留**流式落盘（整段读内存会 OOM） |
| 3 | `WeatherService` | 降级逻辑写死在 if 里；wttr 的 `format=%C...` 未编码，`java.net.URL` 直接抛 `Invalid % sequence`，**免费源其实一直没通过** | 拆成 `WeatherProvider` + `QWeatherProvider` + `FreeWeatherProvider`，`CHAIN` 数组决定顺序；修复 URL 编码 |
| 4 | 同上 | Android 5.1 不认 Let's Encrypt 根证书，wttr HTTPS 握手失败 | 先 HTTPS，失败退回 HTTP（公开天气数据，可接受），日志注明原因 |
| 5 | `IntentRouter` 等 | 用中文文案做类型判断（`"网易云".equals(source)`） | 抽 `MusicSource` 枚举：`id()` 协议值 / `label()` 文案；四个抽象方法由各源实现 |
| 6 | `L1Bridge` | 灯环模式是裸字符串，语音话术与协议值混在一起 | 抽 `LightEffect` 枚举：`value()` / `label()` / `match(原话)` |
| 7 | `MusicPlayer` | 循环模式是裸字符串，前端拼错就静默退回列表循环 | 抽 `LoopMode` 枚举 + `of()` 默认值 |
| 8 | `BridgeReceiver` / `L1Bridge` | 桥协议 action/type 字面量散落两处 | L2 抽 `BridgeProtocol`，L1 侧对应常量集中定义并注明「与 L2 逐字一致」 |
| 9 | `MainService` | 方法体里写 `android.os.SystemClock.xxx` 全限定名 | 改为 import（AGENTS 明令禁止正文写全限定名） |
| 10 | `HttpServer` 等 | 未使用的 import（`FileOutputStream`、`Map`） | 清理 |
| 11 | `AlarmScheduler` | 一次性闹钟（「20 分钟后提醒我」）响过不关闭，第二天同一时刻还会响 | 触发后置 `enabled=false` 并落盘 |
| 12 | 音乐点歌 | 歌名搜索被翻唱占满；按歌手点歌会把「容祖儿/周杰伦」当成周杰伦 | 主唱匹配 + 歌名打分 + 逐个候选尝试取地址（详见 `IntentRouter#playSong`） |
| 13 | `web/src/*-pages.js`、`web/assets/*.js` | 脚本 `SyntaxError` 或悬空调用 → `R1` 未定义，**页面能看、按钮全点不动、恒显「离线」**（2026-10-31 内联转义；2026-11-15 外链 JS `parts.join('` 误插换行，及 `maybeHintOpenApi()` 只调用未定义、异常被 `.catch` 吞掉打回 `online=false`） | 修源码；`verify-pages.js` 构建期校验内联脚本与 `assets/*.js` 语法；装包后用真实浏览器逐页确认 `online=true`、console 零错误 |
| 14 | 移动端设置 | 缺「和风专属 API Host」「自动播报」「语速」三项，手机用户配不了新账号必需的 Host | 补齐三处 + 状态同步；顺手删掉重复的初始填表代码 |

## 二、按时修掉的功能缺口（同期）

- **TTS 长文本断档**：分句 + 3 路并发合成 + 严格按序播放，整段上限 300 → 2000 字。
- **多轮上下文**：带最近 3 轮（6 条）回请求，语音与网页共用一份历史。
- **点歌失败不甩锅**：会员曲目取不到地址时自动换下一首，全部失败才提示登录。

## 三、看了但**没改**（附理由）

| 项 | 现状 | 为什么不改 |
|---|---|---|
| `HttpServer` 1479 行、`route()` 是巨型方法 | 可读性差 | 拆分的收益是结构，代价是**鉴权顺序**风险：令牌校验在 `route()` 最外层，拆成多个 handler 后任何一处漏校验就是越权。当前没有因此出过 bug，先不为了好看动它。真要拆，先补一组越权回归用例再动手 |
| `r1-engine.js` 1158 行单文件 | 无打包工具 | 页面是 `node web/build-pages.js` 直接拼内联脚本，拆模块要么上 bundler（设备端不跑，只影响构建），要么手写加载顺序；收益低于成本，保持现状 |
| `catch (Throwable ignored) {}` 较多 | 防御式 | 这是 Android 老设备上的「不让一个次要失败拖死主流程」策略，但**必须**伴随日志或明确注释；纯吞不记的地方已逐个补日志 |
| `SpeechEngine` 自建 HTTP | 未走 `Http` | 音频要边读边写盘，走统一客户端必须整段进内存，这台机器只有几十 MB 可用，会 OOM。已在类注释写明原因 |
| `java.lang.Process` 全限定名 | `HttpServer` 里 | 文件已 `import android.os.Process`，简单名冲突，属于 AGENTS 允许的唯一豁免，已加行内注释 |
| `sysapps/` 空壳工程 | 已验证是死路 | 留作证据（`docs/18`），不是待清理代码 |

## 四、验证（都在真机上跑过）

```
/api/v1/status            → ver=0.4.0  l1=True
/api/v1/weather?city=杭州  → 【杭州实时天气】Smoky haze +24°C 湿度50% 风向←13km/h。
/api/v1/music/search      → ok=true 20 条
/api/v1/l1/light?mode=loading → {"ok":true,"mode":"loading","l1":true}
logcat "FATAL EXCEPTION"  → 0
```

**运维发现（不是代码问题，但会误判）**：`pm install -r` 在应用进程还在跑时可能返回 `Success`
却没有真正替换 APK。本次就因为这一点，连续两次「装完还是旧页面」。
之后的部署流程固定为：`am force-stop` → `pm install` → **核对 base.apk 时间戳或页面内容**。
已写入 `AGENTS.md`、`docs/20-device-ops.md` 与开发技能。

## 五、这次沉淀出的规范

见 `AGENTS.md`「四、代码组织与设计约定」与 `.agents/skills/r1-dev/SKILL.md`。
一句话总结：**协议值只出现在枚举里，文案只出现在 `label()` 里，网络只走 `Http`，
新增实现靠「加一个类 + 注册一行」，而不是改调用方的 if。**

---

# 语音链路复盘（2026-11-03）

> 现象：说「播放周杰伦的歌」回「我没听清」，连续多次。一条链路上三个坑，其中两个是方法问题。

| 坑 | 现象 | 根因 | 性质 |
|---|---|---|---|
| 1 | `EACCES` | L1 录音在自家 `cacheDir`（0700/0600），L2 另一个 UID 读不到 | 代码缺陷 |
| 2 | 权限修好仍识别成「嗯嗯」 | `docs/24` 说「缓冲天然单声道」是**推断**，4 麦 HAL 实际是双声道交织 | 方法：把推断当事实 |
| 3 | 提了「原厂文本优先」方案 | 原厂识别确实还活着，但语音只用自建 ASR 是产品决策 | 方法：方向变更没先问 |

**教训（一句话）**：先拉原始数据再改代码；发现绕过既有设计的捷径时，摆证据、问用户，不要自己拍板。

**当晚一起修掉的三个同类问题**（都是"先取证据"之后 5 分钟内定案的）：

- 点歌「播放周杰伦的歌。」：ASR 带句号 → 后缀匹配失败 → 按歌名搜到翻唱。
- 点歌无声：mediaserver 拉不动网易云 CDN（无 TCP 连接），改由 L2 中转（`MusicRelay`）。
- 7:55 天气闹钟只念四个字：闹钟没写 `action`，且天气查询在主线程（`NetworkOnMainThreadException`，message 为 null）。

制度性改动写在 `AGENTS.md`（开工流程、证据分级、已废弃通道）与 `r1-dev` 技能（§0 方法论、§9 语音专项）。

**自检三问**：①我看到的是数据还是推断？②字节数算的时长和人耳对得上吗？③这个改法是用户要的还是我顺手发现的？

## 2026-11-05 播放中唤醒：灯亮一下就没反应

- 现象：放着音乐喊唤醒词，灯环亮一次就灭，没有任何回复。
- 根因：播放中桥接层拿到的 PCM 是数字静音（`peak=74 rms=3`，满量程 32767）；原厂随后
  `onASREventCancel startRecognize:0` 取消这一轮，录音只有 1280B（< 0.5 秒阈值）被丢弃。
- 改动：L1 新增 `WAKE`/`ASR_CANCEL` 事件、开麦前等 250ms；L2 唤醒即暂停、收到取消即释放会话，
  并对「连续两轮零录音」做 `enterWakeup` 软恢复。
- 教训：**「录音开始后」才让路已经晚了一轮**，让路必须发生在开麦之前。

## 2026-11-06 投屏时语音不可用 / AirSkill 无法禁用（类别：未知问题）

- 投屏（DLNA/AirPlay）播放时喊唤醒词仍是「没听清」：原厂播放器（com.phicomm.speaker.player）**没有注册 MediaSession**（dumpsys media_session 只有 telecom），也没有对外导出的控制组件（只有 EchoReceiver 处理 BOOT_COMPLETED/PAIRING_REQUEST），L2 无法让它暂停；播放中麦克风拿到的是数字静音。
- AirSkill（uid 1000，/system/app/AirSkill）无法禁用：shell 执行 pm disable-user 被 SecurityException 拒；force-stop 只到下次开机；L2 走回环 adb 执行 am 会 15 秒超时不生效（同命令用电脑 adb 有效）。root 被 ro.secure=1 挡回。
- 结论：这两项都需要 root 或原厂控制通道，应用层到此为止。

## 2026-11-08 自建 DLNA 投屏接收端（类别：设计取舍）

- 原厂「打开投屏」在本地包里根本没有实现（23.8MB 的 APK 里连「投屏」两个字都没有，也没有任何 DLNA/SSDP/UPnP），接收端在系统签名的 `com.phicomm.speaker.player` 里，且只由「播放→停止」的状态迁移触发，开机永远不会进入。改不动 → 自己做一个。
- 直接移植 MIT 许可的 `mokso/uvp-dlna-renderer`（约 45KB 纯 Java，SSDP + AVTransport + RenderingControl + ConnectionManager + GENA），没有自己造协议层。改动只有：包名、把前台 Service 换成 `DlnaRenderer` 组件、播放器接上本地音乐的「让路」。
- 端口 49494（`/desc.xml`、`/ctl/*`、`/evt/*`），SSDP 占用 UDP 1900；8686 有令牌校验，控制点不会带，所以必须另开端口。
- 实测（PC 侧用 async-upnp-client，HA 用的同一套库）：SSDP 单播 M-SEARCH 有 200 OK；三个 SCPD 严格解析通过；SetAVTransportURI + Play 后 `TransportState=PLAYING`、进度 1s→3s；Stop 回 `STOPPED`；GENA 收到 LastChange。
- 性能实测（投屏常开）：进程 CPU 0.1%（空闲）/ 0.5%（播放中，解码在 mediaserver），RSS 32MB 前后不变，线程 25→31（SSDP 监听/广播、HTTP accept、4 个请求工作线程）。关掉投屏线程回到 25。
- WAV error 100 的真因是**源服务器**：静态站不支持 Range、Content-Type 发 text/plain，NuPlayer 的 WAV 解析要 seek 就失败。换成 Range + audio/wav 的服务器后 WAV 正常 PLAYING（时长/进度都对）。失败现在会记进 `/api/v1/cast` 的 `playError`（如 "播放出错 100/0"），状态回 STOPPED，手机侧能看到失败而不是卡在连接中。

## 2026-11-08 语音回复精简 + 逐条核对本地意图（类别：设计取舍 + 判断失误）

- 回复统一压到一句：本地意图文案全部改短（投屏/打电话/灯环/音量/点歌…），模型默认提示词改成「一句话、不超过 20 字」，除非用户要求展开。
- 「打开投屏」以前直接回「在手机上发起」，是**假设**接收端开着。现在真发一次 SSDP 探测（只认本机回包），没启动就回「投屏没打开」。
- 实测抓到的 bug：「音量小一点」被中文数字正则抓成「一」→ 音量变 1%；「继续播放/清理一下系统」匹配不到本地意图而绕去大模型。都已修并逐条跑过。
- 「投屏」仍然起不来：L1 能拿到原厂 `CommonPlayer` 并 `stop()`（反射方法包级可见，`getMethod` 找不到，已改 `getDeclaredMethod`），但原厂只在**播放→停止**的状态迁移里 `startServer()`，空闲时 stop 不触发迁移。

## 2026-11-08 等待语音输入的灯环 + 投屏接收端未启动（类别：设计取舍 + 未知问题）

- 灯环：原厂 `PhicommLightController` 只有 off/loading/wakeup/dormant/net + 24 段唤醒灯位，**没有 RGB**，颜色由固件定。选 `loading`（呼吸）作「聆听」态，与原厂 `onRecognizeStart` 一致。
- 断点在哪：续听走的是 `continueListening()`，它把灯环设回 `ambient`（默认 OFF）——所以「等你说」的时候灯是灭的。原厂引擎判停时会 `turnOffLoadingLight`，因此聆听态每 2 秒保活一次。
- 同一效果重复下发时 L1 跳过 24 段清除（那是 24 次 IPC，录音线程旁边白烧 CPU）；`off` 仍强制清。
- 播报文本：`cleanForSpeech` 把 `~` 直接删掉，「6~12℃」念成「六百一十二度」（实测 2026-11-08 10:52）。数字之间的 `~ - ～ —` 统一换成「到」。
- 投屏：`com.phicomm.speaker.player` 的 DLNA/AirPlay 接收端**没有启动**——实测只开 5555/8686 两个 TCP 端口，DLNA 描述端口与 AirPlay 7000 全关。`DlnaPlayerService`/`AirPlayerService` 是 `exported=false`，外部无法 bind。原厂只在「播放停止→等待投屏」状态迁移里调 `startServer()`（`EasyPlayer.bootAllExternalConnectPlayer`），开机时不会走到。

## 2026-11-06 大模型工具调用 + 日志/缓存上限（类别：设计取舍）

- 模型此前被提示词明确禁止操作设备（「你没有操作能力」），只能回「我没法直接调音量」。改为本地意图优先、未命中才给工具；工具执行后直接播固定文案，不再多要一轮模型。
- 工具参数统一转成「本地意图听得懂的话」（`Tools.route`），避免两份业务实现分叉。
- 空间：/api/v1/logs 限 60 行/单条 500 字、并发只允许一个 logcat；TTS 缓存 40 条且 ≤8MB；历史 50 条；录音 2 分钟清理。

## 2026-11-05 交互收尾、续听、录音增益（类别：设计取舍 + 未知问题）
- 收尾没人管：唤醒暂停的音乐从不恢复，「暂停」还会回「现在没有在播放」；思考期间灯不动；氛围灯被一轮冲掉。→ 新增 `VoiceSession`，一轮只有一个 `end()`。
- 兜底不完整：不续听时 HTTP TTS 回调为 null，失败无声。→ 回调总是挂上，三级兜底；原厂 TTS 加 `TTS_DONE` 回执。
- 续听换实现（用户拍板）：不调 `enterASR`，从唤醒态麦克风流录 + 自己判停；L1 v3 才启用。增益改逐块自适应 + 软限幅，判停忽略唤醒词段（默认仍关）。
- 教训：延时收尾必须带轮次令牌、旁路播报（网页/闹钟）不能结束语音轮、被 `stop()` 的播报也要回调——否则音乐会在用户说话时恢复或停 10 分钟。
- 自启：包被 `force-stop` 后永远收不到 `BOOT_COMPLETED`（实测重启后 8686 拒绝连接，手动 `am startservice` 才恢复）。L1 v3 启动时与每 5 分钟显式补拉 L2。
- 证据级别：DSP 是**离线合成音频实测**（`tools/VoiceDspProbe.java`），续听流里是否有声、阈值是否合适**待真机验证**。

## 2026-11-05 服务端工程化整理（非安全项）

- 闹钟：天气闹钟不再吞掉同一分钟的其它备注；去重状态落盘，进程重启不会重复播；主服务持有 `PARTIAL_WAKE_LOCK`，避免休眠让 15 秒 tick 停摆。
- HTTP：`reasonOf` 补全（500 不再写成 `HTTP/1.1 500 OK`）；支持 `Transfer-Encoding: chunked`；畸形 JSON 统一 400；静态资源 `max-age=300`；工作队列改有界，满了回 503。
- 配置：所有写入改走 `Config.update`（原子读改写），杜绝整包覆盖丢字段；`toJson/applyJson` 收口，导出补齐 asr / cookie / songloft / JWT；`asrUrl` 默认值不再被 `load()` 覆盖成空。
- 历史：语音与网页写同一种 schema，只用一把锁（`Brain.LOCK_HISTORY`）。
- TTS：坏响应不再按句子 MD5 缓存成 mp3；重复句子的并发临时文件冲突已修；`split` 的逗号切分真正生效。
- 自检：新增 `tools/HttpProbe.java`（纯函数 20 项）；依赖 `org.json` 的行为改在设备上用 `curl -i` 验证。


