# 代码审查与修复记录（2026-10-30）

> 范围：`l2-app/`（Java / Android 5.1 API 22）+ `web/`（控制台页面与前端引擎）+ `tools/` + 文档。
> 结论：**编译通过、页面自检通过、用本地 mock API 在 Chromium 里跑过桌面/移动 8 页的滚动与表单同步**；
> 真机未连接（按当前开发约定，不需要连音箱）。

---

## 一、修掉的真实缺陷

### 播放器

| 问题 | 修复 |
|---|---|
| `toggle()` 先异步 post 再读 `isPlaying()`，返回的“已暂停/继续播放”文案与真实动作相反 | 先读当前状态，再按同一状态决定动作和文案 |
| 播放器已失效（`mp == null` / 未 prepared）时 `toggle()` 仍提示“继续播放” | 失效时重新扫库并从头播放 |
| `onCompletion` 在 MediaPlayer 回调（主线程）里扫目录、选下一首 | 扫盘/选曲移到单线程 io executor |
| `stop()` 不清睡眠定时，到点仍会记一次“自动停止” | stop 同时移除 sleep callback、清零 deadline |
| 曲库同时扫 `/sdcard/Music` 与 `/storage/emulated/0/Music`（同一目录的软链），按绝对路径去重会重复 | 用 canonical path 去重 |
| 子目录音乐看不到 | 扫描最多两层，最多 1000 首 |
| 服务销毁时播放器/线程池不释放 | 增加 `shutdown()` / `destroy()`，MainService.onDestroy 调用 |

### TTS

| 问题 | 修复 |
|---|---|
| `stop()` 与正在 `prepare()` 的播报竞争，停止后仍可能出声 | 引入 generation；新请求/停止都会让旧任务失效，prepare 完再检查一次 |
| 连续请求会在单线程队列里排队，过一会儿突然播旧内容 | generation 检查让被取代的排队任务直接退出 |
| 缓存 key 只有文本，换 voice/model/speed 后仍播旧音频 | key 加入 URL、model、voice、speed |
| 缓存文件损坏后每次 `prepare()` 都失败 | 失败时删除缓存，下次重新下载 |
| 文本长度没有上限 | 按 `docs/03-features.md` F2 截断到 300 字并记日志 |
| 线程池关闭后 `speak()` 可能抛 RejectedExecutionException | 捕获并回调错误 |
| `/api/v1/tts` 等 15s 超时后仍返回 `ok:true` | 超时明确返回错误 `等待 TTS 首帧超时` |

### HTTP 服务 / 鉴权

| 问题 | 修复 |
|---|---|
| 文档要求 `/api/**` 未带 token 返回 401，代码却在没传 token 时默认放行 | 改为强制校验；支持 `X-Token`、`Authorization: Bearer`、`?token=`、`r1_token` Cookie、body `token` |
| `/` 跳转丢掉 `?token=` | 跳转保留 query，并在 token 正确时下发 `r1_token` Cookie |
| 没有请求级错误日志 | `send()` 对 ≥400 统一 `Log.w`，带上 `METHOD /path` 与响应摘要；POST/DELETE 记录耗时 |
| 请求体无上限，超大 body 会分配大数组 | 上限 64 KB，超出返回 413 |
| 线程池固定 4 个，慢 LLM 请求会堵住轮询 | 调整为 8 个；`stop()` 时 shutdown |
| `ServerSocket` 未设 reuseAddress，重启可能“端口被占” | 绑定前 `setReuseAddress(true)`，失败重试 5 次并记日志 |
| `/api/v1/logs` 的 logcat 解析下标错位（`substring(19)` 把 tag 前导 `/` 带进 msg） | 改为正则解析；加 4 秒 watchdog，防止 logcat 卡死 |
| `/api/v1/logs` 无超时，logcat 卡住会占住 worker | watchdog `destroy()` + finally 关闭流 |
| 历史只留 30 条，规格是 50 条 | 改为 50；读改写加锁，避免并发丢消息 |
| 静态页路径只挡 `..`，没挡反斜杠 | 同时拒绝 `\` |
| 蓝牙/音频服务为空时可能 NPE | 增加空值判断与 502 JSON |
| 配置端点接受 `file://` 等非法 URL，后续请求直接抛异常 | 只接受 http(s)，clamp ttsSpeed 0.5~2.0，Key 长度上限 |

### 对话 / 闹钟 / 服务

| 问题 | 修复 |
|---|---|
| LLM 连接异常时 `HttpURLConnection` 不保证 disconnect | try/finally 统一 disconnect |
| 大模型返回超大响应可能 OOM | 上限 2 MB |
| 响应 `content` 为 JSON null 时 `optString` 行为不稳 | 显式 `isNull` 判断 |
| `tryLocalCommand` 吞掉所有异常，没有日志 | `Log.w` 记录 |
| 同一分钟多个闹钟只触发第一个 | 收集所有匹配项合并播报，按 `id:time` 去重 |
| 通知在 Android 8+ 没有 Channel | 按 SDK 创建 `r1brain` Channel |
| 服务销毁不释放 SpeechEngine / Brain / MusicPlayer | 全部 shutdown/destroy |
| Manifest 缺 `READ_EXTERNAL_STORAGE`，Android 5.1 上扫不到 `/sdcard` | 补权限 |
| DebugReceiver 在 Android 8+ 后台 `startService` 可能抛异常 | 捕获并记录 |

### 前端

| 问题 | 修复 |
|---|---|
| 聊天页每秒重写整个 `innerHTML` 并强制 `scrollTop = scrollHeight`，用户无法向上翻看 | 只在历史签名变化时重建；仅在“原本就在底部”时自动滚到底 |
| 移动端聊天同样问题 | 同上 |
| 设置页日志每秒重写并强制滚到底 | 签名去重 + “靠近底部才跟随” |
| 曲库每秒重建，滚动位置被打回顶部 | 列表签名去重 + 重建时保存/恢复 scrollTop |
| 闹钟列表每秒重建 | 按 alarms JSON 签名去重 |
| 页面首次加载时设备配置还没到，表单是本地默认值；用户点保存会把设备配置覆盖成空 | 表单增加 dirty 保护；设备配置到达且用户未编辑时自动回填 |
| 聊天“复制/重播”把文本塞进 inline onclick，单引号/换行会破坏 JS，也有注入风险 | 改为 `data-idx` + 事件委托，从 state 取原文 |
| 曲库“移除音频流”同样把 URL 塞进 inline onclick | 改为 `data-remove-stream` + 事件委托 |
| 离线时 `sendCommand` 报“音箱暂未连接”，但 API 报错时也这么说 | 区分“未连接 / 需要令牌 / 已连接但执行失败” |
| `fetch` 没有超时，音箱掉线会堆积请求 | AbortController 超时（普通 8s、聊天 30s、TTS 25s）；同步请求加 in-flight 锁 |
| 401 后页面只会一直显示离线，没有入口填令牌 | 401 自动提示一次；设置页增加“设置访问令牌”；`R1.setToken()` |
| 日志把设备 logcat 和本地控制台日志互相覆盖 | 设备日志标 `source=device`，只替换设备部分，保留本地记录 |
| `escapeHtml` 不转单引号，放进属性不安全 | 补 `&#39;` |
| 老浏览器缺 `matchAll/padStart/find/findIndex/Object.assign` | 加兼容垫片；`fetch` 缺失时给出明确提示 |
| 页面请求 `/favicon.ico` 404 | 加 `data:` 图标 |

### 稳定性、资源回收与健壮性审查（本次深化 Review 修复）

| 问题 | 修复 |
|---|---|
| `WeatherService` 网络请求发生异常或执行完毕后未调用 `disconnect()` 及在 `finally` 中关闭输入流，且响应读取未设上限 | `queryFreeWeather` 与 `fetchJson` 均增加 `try...finally` 块强制关闭 `in` 输入流并调用 `conn.disconnect()`，并加入 64KB/256KB 大小上限截断，彻底避免低运存环境下的 Socket / FD 泄漏与潜在 OOM |
| `DeviceOps.execShell` 降级调用 `Runtime.exec` 时未合并 stderr，输出缓冲区满会导致进程死锁挂起，且 `waitFor()` 无超时保护 | 将命令包裹为 `(cmd) 2>&1`，合并 stderr 到 stdout 杜绝死锁；增加 15s 看门狗线程守护 (`watchdog.destroy()`)，并在 `finally` 块中彻底关闭各管道流与释放底层 Process |
| `SpeechEngine.fetch` 下载 TTS 音频落盘时，若遇异常未能保证关闭 `is` 与 `fos` 文件句柄 | 重构嵌套 `try...finally` 结构，确保临时文件流和网络输入流在任何异常分支均能立即释放 |
| `HttpServer.logsJson` 读取 logcat 进程未主动关闭子进程的未用流（err/out） | 在 `finally` 块补齐 `proc.getErrorStream().close()` 与 `proc.getOutputStream().close()`，加速 Linux 句柄回收 |
| `BootReceiver.java` 缺失规范的 Javadoc 文档注释（`@author`、`@since`） | 补全标准 Javadoc 注释，符合 `@author xudong.hua,gemini` 与精确到分钟并带中文星期的规范 |

---

## 二、目录与构建整理

- `web/` 成为**唯一前端源码**；`web/src` 是源，`web/desktop|mobile|assets` 是生成产物。
- `l2-app/assets/` 明确为打包产物，加入 `.gitignore`；`build.ps1` 会先跑 `web/build-pages.js`
  再复制，避免源码与 APK 内页面两套不一致。
- 删除已失效的 `prototype/` 引用（原文件已删除），文档统一指向 `web/`。
- `web/package.json` 提供 `npm run build` / `npm run check`（无第三方依赖）。
- 新增 `web/verify-pages.js`：检查 `getElementById` 引用的 id、重复 id、本地资源是否存在、是否出现外链。
- `.gitignore` 收紧：只忽略仓库根目录的临时 png（不再吞掉 `web/assets` 里的图片），
  忽略 `l2-app/assets/`、`一键安装包/` 等本地产物。

---

## 三、验证结果

| 项 | 结果 |
|---|---|
| `javac -Xlint:all` | 通过；仅剩 API 22 预期的 deprecation（蓝牙、MediaPlayer、Notification.Builder、系统 TTS） |
| `l2-app/build.ps1` | 通过，产出 `build/r1brain.apk`（约 257 KB），网页资源已进 APK |
| `node --check` 全部前端源 | 通过 |
| `node web/build-pages.js` | 通过，8 页 + CSS 生成并同步 |
| `node web/verify-css.js` | 通过：385 个 class 无缺失，0 个非法图标 |
| `node web/verify-pages.js` | 通过：8 页无重复 id、无缺失 id、无缺失资源、无外链 |
| Chromium + 本地 mock API | 桌面/移动 8 页无 JS 错误；聊天/日志/曲库滚动 5 秒后位置不变；设置页异步回填设备配置；日志合并 52 条 |

> 真机未测：本次没有连接 `<音箱IP>`。装包后用
> `http://<ip>:8686/?token=<你设置的令牌>` 做一次冒烟即可。

---

## 四、仍待处理的已知限制（不是本次引入）

1. **L1 未接入**：唤醒、ASR、灯环仍为空；`/status` 的 `l1=false`。
2. **系统 TTS 不存在**：不配置 HTTP TTS 时只能看文字。
3. **天气服务已打通**：支持免 Key 开放源与和风天气双通道；Home Assistant 目前仅保存配置。
4. **音乐源**：已支持音箱本地目录 (/sdcard/Music 等) 递归扫描与 HTTP 直链流播放，未集成第三方云音乐登录。
5. **多轮上下文**：LLM 请求当前带系统提示词 + 当前指令，未附带长期多轮历史以节省 Token 与保护设备传输。
6. **DebugReceiver**：`exported=true` 便于 ADB 命令行远程调试。
7. **前端浏览器要求**：基于现代浏览器标准设计，建议使用最新 Chrome / Edge / Safari 打开控制台。
