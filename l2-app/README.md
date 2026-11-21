# l2-app —— L2 业务 App

> 不碰麦克风、不碰 L1、不碰音频采集。负责网页控制台、LLM 对话、TTS 播报、
> 本地音乐播放、北京时间闹钟与设备状态遥测。

## 现在能做什么

- **网页控制台**：桌面端 / 移动端各 4 页（首页、对话、音乐、设置），黑金风格，零外链。
- **对话**：`/api/v1/chat`，没配 Key 走回显模式；配了走 OpenAI 兼容接口。
- **本地硬件意图**：音量、静音、播放/暂停、上一首/下一首、睡眠定时、报时、系统体检。
- **TTS**：OpenAI 兼容 `/v1/audio/speech`，音频落盘缓存，`MediaPlayer` 播报；未配置时回退系统 TTS。
- **音乐**：扫描 `/sdcard/Music`、`/sdcard/Download`（最多两层），也支持 HTTP(S) 音频流与同名 `.lrc` 歌词。
- **闹钟**：每 15 秒按北京时间检查，到点通过音箱播报；同一分钟多个闹钟合并播报。
- **遥测 / 日志**：`/api/v1/status`、`/api/v1/logs`（logcat 最近 50 行）。
- **开机自启**：`BootReceiver` → 前台服务 `MainService`。

## 访问

浏览器直接打开：

```
http://<音箱IP>:8686/
```

首次使用**不需要令牌**——启动日志会打印「控制台未设令牌：首次使用开放访问」。
如需鉴权，到控制台设置页「访问令牌」自行设置（至少 4 位，留空即关闭鉴权）。
设置后所有 `/api/**` 未带令牌返回 **401**；用
`http://<音箱IP>:8686/?token=<你设置的令牌>` 打开一次，服务端下发 `r1_token`
Cookie，之后页面内的 API 请求自动带鉴权。

## 接口（v0.2.0 实际契约）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 按 UA 跳转 `/desktop/home.html` 或 `/mobile/home.html` |
| GET | `/desktop/*.html`、`/mobile/*.html` | 8 个控制台页面 |
| GET | `/assets/*` | 字体 / 样式 / 前端引擎 |
| GET | `/api/v1/status` | 在线、运行时长、内存、温度、蓝牙、音量、TTS 状态 |
| GET / DELETE | `/api/v1/history` | 读取 / 清空最近 50 条对话 |
| POST | `/api/v1/chat` | `{"text":"..."}` → `{"reply":"...","costMs":n}`，单条 ≤500 字 |
| GET / POST | `/api/v1/config` | 读 / 写配置（Key 只回 `hasKey`，不回明文） |
| GET | `/api/v1/music/list` | 本地曲库（5 秒缓存） |
| GET | `/api/v1/music/lyrics?path=` | 同名 `.lrc` |
| GET | `/api/v1/player` | 播放器状态 |
| POST | `/api/v1/player/{play,toggle,pause,stop,next,prev,seek,mode,sleep}` | 播放控制 |
| POST | `/api/v1/bluetooth` | `{"enabled":true|false}` |
| GET | `/api/v1/logs` | logcat 最近 50 行（带超时保护） |
| POST | `/api/v1/tts`、`/api/v1/tts/stop` | 播报 / 停止（未配置 409） |
| GET | `/api/v1/tts/status` | TTS 状态 |
| GET / POST | `/api/v1/volume` | 主音量（`{"pct":0-100}` 或 `{"value":n}`） |

设置令牌后，鉴权令牌可放在：`X-Token` 头、`Authorization: Bearer`、`?token=`、`r1_token` Cookie、
JSON body 的 `token` 字段。

```bash
curl "http://<音箱IP>:8686/api/v1/status?token=xxx"
curl -X POST "http://<音箱IP>:8686/api/v1/chat?token=xxx" \
     -H "Content-Type: application/json" --data-raw '{"text":"你好"}'
```

## 编译

不依赖 Gradle（设备 API 22，本机没有 AGP）：

```powershell
cd D:\dev\agent-repos\phicomm-r1\l2-app
.\build.ps1              # 生成网页 → 编译 → 签名
.\build.ps1 -Install     # 编译 + 装到音箱
```

流水线：`web/build-pages.js → javac → d8 → aapt2 link → 塞 classes.dex → zipalign → apksigner`
产物：`build/r1brain.apk`。

网页源码在 `../web/`；`l2-app/assets/` 是构建时生成的 APK 资源副本，**不进 git**。
`build.ps1` 在有 Node 时会先跑 `web/build-pages.js`，没有 Node 时使用 `web/` 下已有产物。

> ⚠️ 签名密钥 `r1brain.jks` 不进 git，第一次构建自动生成。别删；换钥匙重装会报
> `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

## 调试广播

| 广播 | 作用 |
|---|---|
| `am broadcast -a com.r1.debug.START` | 拉起服务 |
| `am broadcast -a com.r1.debug.PING` | 打印 pid / TTS 状态 / 配置 |
| `am broadcast -a com.r1.debug.SAY --es text "…"` | 模拟听到一句话 |
| `am broadcast -a com.r1.debug.CONFIG --es apiKey xxx --es ttsUrl …` | 写配置 |

## 文件

| 文件 | 职责 |
|---|---|
| `MainService.java` | 前台常驻；持有 Brain / Tts / SpeechEngine / HttpServer / AlarmScheduler |
| `HttpServer.java` | 8686 HTTP 服务：静态页 + REST API + 鉴权 + 错误日志 |
| `Brain.java` | 本地意图 → 回显 / LLM；单线程池 |
| `Config.java` | SharedPreferences 配置；没 Key 就回显 |
| `SpeechEngine.java` | HTTP TTS：缓存、打断旧播报、首帧计时 |
| `Tts.java` | 系统 TTS 兜底（本机无引擎，仅报告状态） |
| `MusicPlayer.java` | MediaPlayer 播放本地/网络音频；扫盘在 io 线程 |
| `AlarmScheduler.java` | 北京时区闹钟，15 秒 tick |
| `DebugReceiver.java` | adb 广播入口（调试用） |
| `BootReceiver.java` | 开机自启 |

## 已知限制

- 设备没有系统 TTS 引擎；未配置 HTTP TTS 时只能看文字。
- L1（唤醒 / ASR / 灯环）尚未接入，`/status` 的 `l1` 固定为 `false`。
- 和风天气 / Home Assistant 目前只有配置项与存储，业务调用尚未实现。
- `DebugReceiver` 是 `exported=true` 且无权限保护，正式版应关闭或加签名校验。
