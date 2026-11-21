# 实验报告：麦克风独占性（2026-10-29，最终结论）

> 实验方式：装一个 12KB 的临时探针 App（装完立即卸载），用标准 `AudioRecord` 试录。
> 全程可逆，结束后设备状态已逐项核对恢复。

## 结论（一句话）

> **四麦采集设备是单 substream、被原厂语音链 100% 独占的。**
> 唤醒链在跑时，任何其它 App 的 `AudioRecord` 都能初始化成功、也能 `startRecording`，
> 但 **`read()` 永远阻塞、一个采样也拿不到**。
> 把原厂链停掉，同一个 App **立刻就能正常录音**。
>
> ⇒ **这不是权限问题，不是硬件问题，是 ALSA 层的独占。**

---

## 一、硬件层证据

```
/proc/asound/card1/pcm0c/sub0/info
  id                 : ma4 codec pcm ma4-0
  stream             : CAPTURE
  subdevices_count   : 1        ← 整个 PCM 只有 1 个子流
  subdevices_avail   : 0        ← 被占用了
```

`subdevices_count = 1` 是决定性的：**这个采集口在硬件/驱动层就只有一路**，
不是「可以多人排队」，是「只有一个座位」。

## 二、原厂链是「常开」的

重启设备后立刻观察（uptime 84 秒时）：

```
state        : RUNNING
owner_pid    : 1789
trigger_time : 1516464034.09
tstamp       : 1516464080.66
```

`/proc/1789/status` → `Name: usc_record_thre`，`cmdline` → `com.phicomm.speaker.device`。

连续采样 5 次（间隔 3 秒），`subdevices_avail` 恒为 `0`，`owner_pid` 恒为 1789。
前一天还测到过一次连续 27.4 分钟的同一个会话。

⇒ **唤醒链从开机起就一直开着麦克风**，这是离线唤醒词的必然做法。

## 三、对照实验（这是关键）

### 3.1 原厂链在跑时，普通 App 录音

探针（`com.r1.probe`，只做 AudioRecord，不写文件）：

```
I/R1PROBE: --- MIC start, minBuf=4096
I/R1PROBE: MIC state=1 (1=INITIALIZED)        ← 初始化成功
I/R1PROBE: MIC recordingState=3 (3=RECORDING) ← 开始录音成功
（然后就没有然后了，卡死在 read()，8 秒后进程仍在）
```

同时 AudioFlinger 确实为它建了输入线程：

```
Input thread 0xb3ca1008:
  I/O handle: 20
  Standby: no
  Sample rate: 16000
  Channel Count: 2
  Format: pcm16
```

**能开、能 start、拿不到数据。**

### 3.2 把原厂链 force-stop 之后

```
$ am force-stop com.phicomm.speaker.device
（进程消失）
$ cat .../sub0/status      → 空（不再是 RUNNING）
$ cat .../sub0/info        → subdevices_avail: 1     ← 座位空出来了
```

同一个探针，同样的代码，立刻成功：

```
I/R1PROBE: MIC RESULT samples=16000 reads=10 maxAbs=79 nonZero=15717 costMs=1189 -> GOT_AUDIO
I/R1PROBE: VOICE_RECOGNITION RESULT samples=16000 reads=10 maxAbs=70 nonZero=15703 costMs=1171 -> GOT_AUDIO
I/R1PROBE: DONE
```

**1.2 秒拿到 16000 个采样（=1 秒 16kHz 音频），数据非全零。**
`MIC` 和 `VOICE_RECOGNITION` 两个音频源都成功。

### 3.3 恢复

```
$ am start -n com.phicomm.speaker.device/.ui.MainActivity
（进程回来 pid 2612）
state: RUNNING   owner_pid: 2762   subdevices_avail: 0
```

## 四、判定

| 假设 | 结论 |
|---|---|
| 是权限问题（缺 RECORD_AUDIO） | ❌ 排除 —— 同一 App 停掉原厂链就能录 |
| 是 SELinux 域问题 | ❌ 排除 —— 同上 |
| 是硬件坏 / 麦克风不可用 | ❌ 排除 —— 停掉后能录到真实数据 |
| **是 ALSA 单 substream 独占** | ✅ **成立** |

## 五、对架构的影响（决定性）

```
麦克风只有一个，而且原厂链会一直占着它。
```

于是「我们的 App 当耳朵」这条路在**保留原厂唤醒**的前提下**不可行**。
只有两个出路：

| | A1 顶替原厂包（VRISE 路线） | A2 停用原厂链，自己做唤醒 |
|---|---|---|
| 麦克风 | 原厂链继续拿着，我们在**同一个进程里**拿它的识别结果 | 我们独占，原厂链必须停 |
| 唤醒词 | 保留「小讯小讯」，出厂调好的 | 要自己实现（Porcupine/Snowboy/自训） |
| 四麦算法 | 原样沿用 | 走 AudioRecord 经 HAL（已证明能录） |
| 内存 | 不额外占 | **空闲仅 10MB，再跑一个唤醒引擎风险很高** |
| 是否要 Janus | 要 | 不要 |
| 迭代方式 | 每次改都要重新打包顶替 | 普通 `pm install` |
| 语音质量 | 与现在一致 | 需要重新调，大概率更差 |

**推荐 A1**，理由：唤醒质量是这类产品的命门，而 A1 能沿用出厂调好的本地唤醒 + 本地 ASR；
四麦波束也不需要重新实现。

## 六、实验的收尾核对（证明没留下东西）

| 检查项 | 结果 |
|---|---|
| 探针 App | 已 `pm uninstall`，`pm list packages` 里查不到 `com.r1.probe` |
| 设备临时文件 | 只剩原有的 `R1-Dlna.apk` / `server.apk` / `wktv.apk` |
| 第三方包列表 | `com.droidlogic.mediacenter` / `wukongtv.wkhelper` / `limitemp.interstellas` / `xiaobaifile.tv`（都是原有） |
| SELinux | Enforcing（未变） |
| `/system` 挂载 | `ro`（未变） |
| 原厂语音链 | 已恢复运行，重新占用麦克风 |
| 8080 网页 | HTTP 200 |

## 附：实验用的探针源码

保留在 `tools/probe-app/`，可复现。
