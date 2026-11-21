# 实验：L2 骨架跑通（2026-10-29）

> 目的：验证「文本进 → LLM → 文本出」这条链在真机上能跑，并量出内存代价。
> 结果：链路 ✅，但挖出一个**必须先解决的坏消息** —— 这台机器没有系统 TTS 引擎。

---

## 一、做了什么

写了 6 个小文件（约 600 行），编译成 16 KB 的 apk，装到真机上跑。

```
com.r1.brain/
  MainService     前台常驻，对外 say(text)
  Brain           文本 → LLM → 文本（单线程池，不存历史）
  Config          SharedPreferences；没 Key 就回显
  Tts             系统 TTS 封装（主要用来"报告能不能用"）
  DebugReceiver   adb 广播入口（模拟"听到一句话"）
  BootReceiver    开机自启
```

编译流水线（本机没有 Gradle / AGP）：

```
javac -source 8 -target 8 → d8 --min-api 22 → aapt2 link
→ 塞 classes.dex → zipalign → apksigner
```

## 二、实测结果

### 2.1 链路通了

```
$ adb shell "am broadcast -a com.r1.debug.SAY --es text '你好'"

I/R1BRAIN: broadcast: com.r1.debug.SAY
I/R1BRAIN: === SAY: 你好
I/R1BRAIN: handle: 你好
I/R1BRAIN: echo mode, cost=2ms
I/R1BRAIN: === REPLY(27字): 收到：你好。（当前是回显模式，还没配 API Key）
W/R1BRAIN: TTS 不可用（failed(onInit status=-1)），跳过播报
```

广播 → 服务 → 业务 → 返回，**2 毫秒**。

### 2.2 ❌ 没有系统 TTS 引擎

```
E/R1BRAIN: TTS 初始化失败: onInit status=-1
```

`status=-1` 就是 `ERROR`。**这台精简 ROM 里没有装任何 TTS 引擎**（没有 Google TTS，
也没有国内厂商的）。`getEngines()` 拿不到东西。

**影响很大**：原计划「L2 用系统 TTS 播报」直接作废。

**三条出路**
1. **走 L1 的原厂 TTS**（云知声 `yzstts`）—— 最合理，音质是出厂调好的，且零延迟
2. 云端 TTS 合成 mp3，L2 用 `MediaPlayer` 播 —— 要网络，音质可换豆包音色
3. L2 内置一个轻量离线 TTS —— **不现实**，内存只剩 8MB

**结论：TTS 必须归 L1。** 这反过来又加强了「L1 是必需的」这个判断。

### 2.3 内存代价

| 时刻 | MemFree | Cached |
|---|---|---|
| L2 启动前 | 10,220 KB | 151,936 KB |
| L2 运行中 | 8,332 KB | 131,728 KB |

L2 的 RSS 约 **21 MB**，但 `MemFree` 只掉了 **1.9 MB**（大部分是 Zygote 共享页）。

**判定**：这个体量的 App 可以常驻。但**再加一个 HTTP 服务 + 一个播放器就会很危险**，
所以 O-05（常驻 vs 唤醒启动）暂时按「常驻」走，等接上 HTTP 再复测。

### 2.4 没干扰原厂语音

```
state: RUNNING     owner_pid: 2762     （未变）
com.phicomm.speaker.device  pid 2612  （仍在跑）
```

## 三、结论

| 项 | 结果 |
|---|---|
| 业务链路 | ✅ 通 |
| 构建流水线 | ✅ 可用（手工，不依赖 Gradle） |
| 部署方式 | ✅ 后台 `pm install` 绕开 `error: closed` |
| **系统 TTS** | ❌ **不存在** → TTS 必须走 L1 |
| 内存 | ⚠️ 可常驻，但没有余量再加重量级组件 |
| 对原厂影响 | ✅ 无 |

## 四、对方案的影响

**没有推翻任何架构决定**，但把 O-02（TTS 用谁的）从「先用原厂」变成了「**只能用原厂或云端**」，
而且明确了 **L2 不能自己播报**。

于是 `SPEAK` 这条桥消息的优先级提高了 —— 它不再是"可选的优化"，而是**唯一能出声的路径**。
这也意味着：

> **在 L1 做出来之前，L2 是"哑巴"的。**
> 所以第一期验收只能是「文本进 → 文本出，logcat 里看得到回答」，
> 不能是「音箱开口说话」。这一点要写进验收标准。

## 五、证据留存

| 东西 | 位置 |
|---|---|
| 源码 | `l2-app/src/com/r1/brain/` |
| 构建脚本 | `l2-app/build.ps1` |
| 产物 | `l2-app/build/r1brain.apk`（16 KB） |
| 用法 | `l2-app/README.md` |
