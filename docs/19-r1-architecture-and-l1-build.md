# R1 原厂运行逻辑与 L1 实装说明

> 数据来源：对原厂底包 `l1/vendor/Unisound_original.apk`（需自行准备，不入库）的反汇编 + 真机日志实测。
> **本文只描述当前实装**；协议字段见 `docs/07-protocol.md`，构建/部署命令见 `docs/20-device-ops.md`。

---

## 1. 原厂是怎么跑的

入口只有一个：`com.phicomm.speaker.device.ui.ExampleANTEngineInitializer.onEngineInitDone()`，
它把整条 pipeline 按顺序拼起来：

```
本地 KWS「小讯小讯」
   ↓
DefaultANTLaunchHandler        唤醒后播提示音 → enterASR
   ↓
本地 ASR（云知声 JNI，独占麦克风）
   ↓
NLUDispatcher                  本地语法匹配 + 云端语义（云已死，只剩本地）
   ↓
ANTPipeline 按优先级分发（数值大的先抢，300 是默认档）
   ├─ 310+  自定义 Handler
   ├─ 300   DefaultWeather / DefaultMusic / DefaultChat / DefaultSetting / …
   └─ 兜底   DefaultUnSupportHandler
   ↓
命中者执行 → ctx.playTTS(text) → 原厂 TTS 出声 + 灯环随状态变化
```

**关键类**

| 职责 | 类 |
|---|---|
| pipeline 装配 | `com/phicomm/speaker/device/ui/ExampleANTEngineInitializer` |
| 应用入口 | `com/phicomm/speaker/device/ui/ExampleApp` |
| 唤醒入口 | `com/unisound/vui/handler/launch/DefaultANTLaunchHandler` |
| 语义分发 | `com/unisound/vui/handler/filter/NLUDispatcher` |
| 三道闸门 | `com/phicomm/speaker/device/custom/handler/PhicommInterceptHandler.onWakeupResult()` |
| 灯环 | `com/phicomm/speaker/device/custom/ipc/PhicommLightController` |
| TTS 播放 | `com/unisound/vui/engine/ApiHelper`（`interceptTTS`） |

### 1.1 三道闸门（「叫不动」的真正原因）

`onWakeupResult()` 里唤醒成功后立即检查：

```
没网                      → 播提示音 + enterWakeup(false) + return
isBinded=false            → 播「设备未绑定」+ return
升级检查不通过             → 播提示 + return
```

三个条件原本都要连已经下线的服务器才能为真，所以闸门永远不放行。

**我们的做法**：在 `Bridge.init()` 里直接把 SharedPreferences 的 `isBinded` 置为 `true`
（`defaultSp` 文件），不改原厂代码逻辑，最小侵入。

### 1.2 audio 锁

`ApiHelper.interceptTTS` 跑在 **TTS 回调线程**。在里面做任何同步等待都会持着 audio 管线锁，
**整机会卡死**（表现是叫不醒、没声音、只能断电）。

⇒ 所有网络请求必须丢到自己的线程池，回调里只做「投递」，绝不同步等结果。

---

## 2. 我们往包里注入了什么

L1 = 原厂 APK + 我们的桥 + 5 处补丁。补丁全部在 `l1/build.ps1` 的 4.x 步里，可重复执行。

| # | 位置 | 改法 | 目的 |
|---|---|---|---|
| 1 | `ExampleApp.onCreate` | 追加 `Bridge.init(this)` | 桥的入口（原厂 manifest 不能改，只能挂在已有组件上） |
| 2 | `NLUDispatcher.onASRResult(String)` / `(ASRResult)` | 先调 `Bridge.onAsrResult(...)` | 把识别文本转发给 L2 |
| 3 | `MqttTransportChannel.createChannel` | 方法体置空 | 原厂 MQTT 连不上已死服务器会崩溃，掐掉 |
| 4 | `DefaultChatHandler.eventReceived` | 改为只 `reset()`，不播音 | 抑制原厂闲聊播报，否则和 L2 的 TTS **双播** |
| 5 | `DefaultIlleageChatHandler.eventReceived` | 同上 | 同上 |

> 注入的类只有 `com/phicomm/speaker/device/bridge/Bridge.java`（新增），其余都是「改已有方法」。

---

## 3. Bridge 做什么

只依赖 `android.*`，编译时不需要厂商 jar，也不需要原厂那 6000 多个 smali。

```java
Bridge.init(ctx)          // 置 isBinded；注册命令接收器；起心跳线程（30 秒）
Bridge.onAsrResult(json)  // 从原厂 JSON 里取 text，转 send()
Bridge.send(text)         // 广播 {com.r1.bridge}, type=ASR_RESULT
Bridge.applyLight(mode)   // 反射调原厂 PhicommLightController，回 LIGHT_RESULT
```

**它不做的事**：不解析业务、不发网络请求、不播 TTS、不接管唤醒。

灯环的 5 个预置效果（`off` / `loading` / `wakeup` / `dormant` / `net`）通过反射调用原厂的
`turnOffALLWakeupLight`、`turnOnLoadingLight`、`turnOnWakeupLastLight`、`turnOnDormantLight`、
`turnOnNetDisconnectedLight`。原厂**没有**任意 RGB 接口。

---

## 4. 构建流程

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\l1\build.ps1
```

七步，产物 `l1/build/r1brain-l1.apk`（约 31 MB）：

```
1. 校验底包           l1/vendor/Unisound_original.apk（需自行准备）的 SHA-256 必须匹配
2. 反汇编原厂 dex      baksmali → smali 树
3. 编译我们的桥        javac → d8 → 我们自己的 classes
4. 合并 + 打补丁       把桥的 smali 并进树里，执行上表 5 处补丁
5. 组装 dex           smali → payload.dex（约 6.0 MB）
6. Janus 套壳         l1/tools/build_janus.py：dex 前缀 + 原 zip 追加，重算偏移与校验
7. 五项自检           文件头 / file_size / Adler32+SHA-1 / 134 个 zip 条目 / 安装字节一致
```

底包固定为原厂 APK（24,936,387 B，证书指纹 `34A2D2FE…78BC33`，见 `07-protocol.md`）：
它带原厂平台签名，能直接覆盖 `/system/app/Unisound`；代码也是原厂自己的，没有夹带任何第三方业务。

**Janus 原理**（`build_janus.py` 自研实现，74 行）：

- 文件头写 `dex\n035` → 安装时 ART 把它当 DEX 加载，执行我们的代码；
- 尾部仍是合法 zip → PackageManager 校验 v1 签名时读到的是原厂内容，签名依然有效；
- 关键是把 zip 中央目录的偏移整体前推 dex 长度，并改写 DEX 头的 `file_size`、
  Adler32 与 SHA-1。

---

## 5. 安装与回退

```powershell
# 装（不要用 adb install，会卡死）
adb -s <IP>:5555 push .\l1\build\r1brain-l1.apk /data/local/tmp/r1brain-l1.apk
adb -s <IP>:5555 shell "cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/r1brain-l1.apk > l1.log 2>&1' >/dev/null 2>&1 &"
# 等 45 秒
adb -s <IP>:5555 shell "cat /data/local/tmp/l1.log"     # 期望 Success
```

回退：`pm uninstall com.phicomm.speaker.device` → 系统自动回落到 `/system/app/Unisound`。
注意回退后**语音助手叫不动**（原厂闸门），这是原厂状态，不是弄坏了。

换一台没被动过的原厂机时，可能报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（签名不同）：
先 `pm uninstall` 再装，见 `docs/11-troubleshooting.md` §8.4。

---

## 6. 限制（不是待办，是结论）

| 限制 | 原因 |
|---|---|
| 不能加 `<receiver>` / `<service>` / `<permission>` | 改 manifest 会让 Janus 的 v1 摘要失配 |
| 所有接收器只能运行时注册 | 同上；`Bridge.init()` 里 `registerReceiver` |
| 灯环只有 5 种预置效果 | 原厂没暴露颜色参数 |
| 唤醒词不能改 | 唤醒是原厂本地 KWS，词表在包内 |
| L1 的改动成本高 | 每次改都要重打包 31 MB + 推送约 100 秒 + 安装 45 秒；**能放 L2 就别放 L1** |
| L1 不播 TTS | 播报统一由 L2 的 HTTP TTS 负责，避免两套声音打架 |
