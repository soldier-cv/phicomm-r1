# 测试与自检（怎么证明改动是对的）

> 这台机器没有单元测试框架，也不需要：**四层自检**覆盖面比写测试更实际。
> 原则：能在本地/构建期发现的问题，绝不留到设备上；上机检查只看「必须上机才能看到」的东西。

---

## 一、四层自检矩阵

| 层 | 跑什么 | 花多久 | 能发现什么 |
|---|---|---|---|
| ① 静态 | `node web/verify-css.js`、`node web/verify-pages.js`、`tools/HttpProbe.java` | 1~2 秒 | 页面结构 + 纯函数（状态行 / 分块解码 / 分句 / 播放源 / 配置收口） |
| ② 构建 | `l2-app/build.ps1`、`l1/build.ps1` | 10 秒 / 90 秒 | 编译错误、dex 打包失败、签名/对齐问题；L1 还有五项自检 |
| ③ 接口 | `curl` 打 8686 的关键接口 | 10 秒 | 路由、鉴权、JSON 结构、外网连通（音乐/天气）、灯环广播 |
| ④ 设备 | `logcat` + `/api/v1/status` + 安装后核对 | 30 秒 | 崩溃、L1 心跳、内存、包是否真的换上了 |

**不需要上机的部分**：JSON 拼装、路由分支、意图正则、协议常量——这些在 ③ 就能验完。

---

## 二、每层的具体命令与期望

### ① 静态

```powershell
node web/build-pages.js     # 生成 8 个页面 + CSS，并同步到 l2-app/assets
node web/verify-css.js      # 期望：OK
node web/verify-pages.js    # 期望：verify-pages: OK (8 pages)
```

服务端纯函数（不依赖设备，也不依赖 org.json）：

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$dir = "$env:TEMP\r1probe"; New-Item -ItemType Directory -Force -Path $dir | Out-Null
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$sdk\platforms\android-36\android.jar" `
  -cp l2-app\build\classes -d $dir tools\HttpProbe.java
java -cp "l2-app\build\classes;$dir;$sdk\platforms\android-36\android.jar" `
  com.r1.brain.HttpProbe "$dir\http.txt"
Get-Content "$dir\http.txt" -Encoding UTF8   # 期望：通过 20 / 20
```

> android.jar 里的 `org.json` 是 stub（调用会抛 `Stub!`），所以 `Config.toJson/applyJson`
> 这种依赖 JSON 的行为不在电脑上验，改到设备上用 ③ 的 `curl -i` + `config/export` 检查字段。

`verify-pages.js` 现在会拦四类问题：

1. `getElementById` 引用了页面里不存在的 id；
2. 同一页面重复 id；
3. 本地 `src`/`href` 不存在，或出现外链（本项目零 CDN）；
4. **内联脚本语法错**（`new Function` 只解析不执行）与多转义出来的 `\``。

### ② 构建

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\l2-app\build.ps1   # 期望末尾打印 APK 大小
pwsh -NoProfile -ExecutionPolicy Bypass -File .\l1\build.ps1       # 期望五项 [ok]
```

L1 的五项自检（少一项都不算成功）：dex 文件头、file_size 一致、Adler32/SHA-1、
zip 可解析 134 个条目、条目安装后字节一致。

### ③ 接口冒烟（改了就测对应那条）

```powershell
$base = "http://<IP>:8686"

# 服务与桥
(Invoke-WebRequest "$base/api/v1/status").Content            # l1=true，version 与本次一致
# 意图（本地，不花 token）
Invoke-WebRequest "$base/api/v1/chat" -Method POST -ContentType "application/json" -Body '{"text":"现在几点"}'
# 音乐（外网）
Invoke-WebRequest "$base/api/v1/music/search?source=netease&q=晴天"
Invoke-WebRequest "$base/api/v1/music/search?source=qq&q=jay"
# 天气（外网 + 降级）
Invoke-WebRequest "$base/api/v1/weather?city=杭州"
# 灯环（跨进程）
Invoke-WebRequest "$base/api/v1/l1/light?mode=loading"
```

期望值见 `docs/22-call-chains.md` 的 §12 与 `docs/20-device-ops.md` 的 A5.1。

### ④ 设备

```powershell
adb -s <IP>:5555 logcat -d | Select-String "FATAL EXCEPTION"     # 期望 0
adb -s <IP>:5555 logcat -d -s R1BRIDGE:V                          # L1 bridge ready, ver=2
adb -s <IP>:5555 shell "ls -l /data/app/com.r1.brain-*/base.apk"  # 时间戳是刚才
```

---

## 三、常见「假绿」（看着通过，其实没生效）

| 现象 | 真相 | 怎么破 |
|---|---|---|
| `pm install` 打印 `Success` | 应用进程还活着时，包可能没真正换掉（实测踩过两次） | 先 `am force-stop`；装完看 `base.apk` 时间戳或直接 `curl` 新页面内容 |
| 页面 HTTP 200、按钮全没反应 | 内联脚本 `SyntaxError`，HTML 只是「能看」 | 看浏览器控制台；或跑 `verify-pages.js`（已含语法检查） |
| 改完页面样式没变 | 浏览器缓存了旧 HTML/CSS | 地址加 `?v=日期`，或强制刷新 |
| 刚装完 `/status` 里 `l1=false` | L1 心跳 30 秒一次，90 秒窗口 | 等半分钟再看，不是坏了 |
| 对话「成功」但回答是复读 | 没配大模型 Key，处在回显模式 | 看返回里的 `echoMode:true` |
| 接口 JSON 看着对，状态行却是 `500 OK` / 响应头缺失 | 只看了 body，没看状态行 | `curl.exe -i` 看第一行与响应头；`HttpProbe` 也会拦 |
| 刷新页面很慢 | 静态资源每次重传约 300KB | 现在带 `Cache-Control: public, max-age=300`；改样式后强制刷新 |
| 天气回了一句「网络较忙」 | 两个源都失败时的兜底话术，**不是真天气** | 看 `logcat -s R1BRAIN:V` 里 `wttr`/`qweather` 的失败原因 |
| 点歌成功，但放的是翻唱 | 未登录网易云时搜索结果被翻唱占满 | 看返回里的歌手名；要原版得贴 Cookie |
| `curl` 看着正常但设备没声音 | TTS 未配 → 退回系统 TTS（本机没有） | 设置页配 TTS 端点；看 `/api/v1/tts/status` |

---

## 四、必须上机才能验的

这几项本地怎么测都是假的，只能看设备：

| 能力 | 为什么 |
|---|---|
| 唤醒 + 语音识别 | 麦克风在原厂链路里，只有真说话才走得到 |
| TTS 出声 / 打断 | 依赖 MediaPlayer 与喇叭 |
| 灯环效果 | 依赖原厂 IPC 与灯环硬件 |
| 蓝牙配对/播放 | 依赖系统蓝牙栈 |
| 内存与保活 | 490MB 的真实压力，模拟不出来 |
| 安装/OTA | 依赖设备端 `pm` 与回环 ADB |

---

## 五、改动类型 → 必跑项

| 你改了什么 | 至少跑 |
|---|---|
| `web/src/*`（页面/引擎） | ① 全部 + 打开对应页面看控制台 + ③ 相关接口 |
| `l2-app` 里的意图/播报 | ② + ③ `chat` 两条（本地 + 外网） + ④ FATAL |
| 音乐源 / 天气 | ② + ③ 对应接口（两个音源、两个天气源都要） |
| 灯环 / 桥协议 | ② + **L1 构建五项自检** + ④ `R1BRIDGE` 日志 + ③ `l1/light` |
| `HttpServer.send()` / 静态路由 / 缓存头 | ② + `curl.exe -i` 看状态行与 `Cache-Control` + 页面强刷一次 |
| 部署脚本 / 文档 | 不涉及设备；但文档链接要过一遍引用检查（见 `docs/README.md`） |

---

## 六、测试数据卫生

- 验证完把 `DELETE /api/v1/history` 清一下，别把自己的 `probe` 记录留给用户；
- 测试闹钟（一次性闹钟）记得清掉：`POST /api/v1/config {"alarms":[]}`；
- 设备上不要留大文件：`/data/local/tmp/*.apk` 用完删（`/data` 只有 708MB）；
- 改过音量/灯环的，验完恢复原值（音量、灯环 `off`）。

---

## 七、已知不会再做的测试

| 不做的 | 原因 |
|---|---|
| JVM 单元测试 | 所有逻辑都紧贴 Android API（AudioManager/MediaPlayer/SharedPreferences），脱离设备跑没意义 |
| 模拟器 | 没有对应镜像；音频/灯环/唤醒在模拟器上全假 |
| 自动化 UI 测试（Appium 等） | 页面是静态 HTML，`verify-pages.js` + 浏览器控制台已经覆盖主要风险，引入框架反而增加维护量 |
| 压测 | 单用户设备，峰值就是「一个人连续说十句话」 |
