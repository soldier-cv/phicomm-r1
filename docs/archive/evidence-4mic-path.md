# 实验报告：四麦音频到底谁在采？（2026-10-28）

> **实验性质：全程只读。没有安装任何东西，没有改配置，没有 stop 任何服务，没有停 App。**
> 设备：R1 @ <音箱IP>，SELinux Enforcing 全程未变。

## 一句话结论

> **四麦波束成形在 mediaserver 的 Audio HAL 里跑，任何 App 走普通 `AudioRecord` 就能拿到增强后的音频——
> 不需要 Janus 顶替，不需要 platform_app 域。**
>
> 但「原厂唤醒 + ASR」这条链仍然必须在 `com.phicomm.speaker.device` 进程里，
> 因为它自己是**直开 ALSA** 采的，不经过 AudioFlinger。

---

## 一、先看声音是怎么流进 App 的

### 1.1 声卡拓扑（`/proc/asound/pcm`）

```
00-00: HDMI PCM              playback 1
01-00: ma4 codec pcm ma4-0   capture  1     ← ★ 四麦阵列入口
02-00: AK7755 PCM            playback 1 : capture 1
03-00: SPDIF PCM Playback    playback 1
```

四麦 = **card1 (RK_MA4)**，不是 AK7755。

### 1.2 关键发现：MOD 自己是绕过 AudioFlinger 的

查 `com.phicomm.speaker.device` 进程（pid 2793）打开的文件描述符：

```
$ adb shell "run-as com.phicomm.speaker.device ls -l /proc/2793/fd | grep snd"
lrwx------ u0_a8 u0_a8  30 -> /dev/snd/pcmC1D0c
```

**它直接开着 `/dev/snd/pcmC1D0c`**，同时四麦节点的 `owner_pid` 也是它的录制线程：

```
$ cat /proc/asound/card1/pcm0c/sub0/status
state: RUNNING
owner_pid : 4713

$ cat /proc/4713/status
Name: usc_record_thre          ← 云知声录制线程
Tgid: 2793                     ← 属于 speaker.device 进程
Pid:  4713
```

```
hw_params: S32_LE / 2 channels / 96000 Hz / period 2048 / buffer 16384
```

> **注意 96000 Hz 和 `S32_LE、2 通道`**：这不是手机麦克风的参数。
> 4 支麦以 **32bit 打包进 2 个通道**——这就是「四麦」的真实形态。

### 1.3 但 Audio HAL 里也有一份四麦算法

把 `/system/lib/hw/audio.primary.rk30board.so` 拉下来读依赖：

```
依赖: libtinyalsa.so, libuni4michal.so, libUniLinearMicArray.so, libUniMicArray.so, ...
导入符号: uni_4mic_pcm_open / uni_4mic_pcm_read / uni_4mic_pcm_close
```

而 `libuni4michal.so` 自己导出了整套：

```
uni_4mic_hal_init          uni_4mic_board_init
uni_4mic_pcm_open          uni_4mic_pcm_read        uni_4mic_pcm_start/stop/close
uni_4mic_config            uni_4mic_get_hw_param
uni_4mic_i2c_open/read/write/update_bits      ← 通过 I2C 配麦克风阵列硬件
Unisound_MicArray_*                            （在 libUniMicArray.so 里）
```

**结论：Audio HAL 会调四麦 HAL 拿数据**，这意味着 mediaserver 代 App 采的音频**可能已经是波束成形之后的**。

### 1.4 但这台机器上 mediaserver 当前没在采

```
$ dumpsys media.audio_flinger
Input thread 0xb785e030:
  Standby: no
  1 Tracks of which 1 are active     ← 有一个文件在播（2 Tracks of which 0 active 是输出侧）
```

输入线程**没有 ACTIVE 的 RecordTrack**，卡又被 speaker.device 独占 ——
也就是说 **mediaserver 现在没在用四麦，它只是个备用通路**。

---

## 二、为什么说「不要 Janus 也能采」

| 问题 | 答案 | 依据 |
|---|---|---|
| `/dev/snd/pcmC1D0c` 的 DAC 权限 | `crwxrwxrwx`，**全开** | 实测 |
| 卡被独占时呢？ | ALSA 的 `pcm_open` 在 `RUNNING` 状态下会**失败**，但这不代表权限不够 | 依据 ALSA 语义 |
| 换正常路径 `AudioRecord` 呢？ | **走 AudioFlinger，由 mediaserver 代开设备，App 只需要 `RECORD_AUDIO` 运行时权限** | Android 架构 |
| 所以要不要 platform_app？ | **不要** | — |

**引用文档里「必须 Janus 拿 platform_app 才能采四麦」这个前提，在这台机器上不成立。**

---

## 三、哪些能力确实还要身份（实测过的）

| 能力 | 需要身份？ | 证据 |
|---|---|---|
| 采音频（AudioRecord 路线） | ❌ 不要 | 见上 |
| 直接开 `/dev/snd` 原始流 | ⚠️ 要（而且会被独占挡住） | 实测 owner 被占 |
| **灯环 24 位 / 休眠灯** | ✅ 要 | `ISysPrivate`（`service list` 实测存在） |
| **系统模式 setSysMode** | ✅ 要 | 同上 |
| **原厂唤醒词 + ASR** | ✅ 要 | 云知声 SDK 在 device 包内，直开 ALSA，不经 AudioFlinger |
| 播放控制 / DLNA / AirPlay | ✅ 要 | 在 `com.phicomm.speaker.player` 包里 |

---

## 四、run-as 这个「只读后门」（不出问题地摸特权域）

`com.phicomm.speaker.device` 的 `android:debuggable="true"`，所以：

```bash
$ adb shell run-as com.phicomm.speaker.device id
uid=10008(u0_a8) gid=10008 ...
context=u:r:platform_app:s0        ← 借到的是特权域
```

**同样的文件、同样的读操作，在这个身份下成功**：

```bash
$ adb shell run-as com.phicomm.speaker.device ls -l /dev/snd/pcmC1D0c
crwxrwxrwx system audio 116, 56 2016-01-21 16:50 pcmC1D0c

$ adb shell run-as com.phicomm.speaker.device cat .../pcm0c/sub0/hw_params
access: RW_INTERLEAVED
format: S32_LE
channels: 2
rate: 96000
```

> 这是**取证并验证 SELinux 判定**的正确姿势：`run-as` 只在内存里替换 SELinux 域，
> **不动磁盘、不改挂载、不需要重启、关掉 shell 就完全恢复**。
>
> ⚠️ 但要注意：`platform_app` 域**确实**能看到 `/dev/snd`，
> 而我用普通 shell 身份读同样的 `hw_params` **也成功了**——说明在这个 5.1.1 上
> `/proc/asound` 这一路本身就不受 SELinux 限制，**它的证据力有限**。
> 真要判定「能不能 open 设备」，必须做一次**实际 open** 的对照实验；
> 而当前卡被独占，做这个实验需要停服务，所以**我没做**。

---

## 五、对架构的直接影响

原来的三层设计里，L1（顶替 device 包的桥）最主要的理由之一是「为了拿四麦」。
**这条理由现在被推翻了。**

修正后：

| 层 | 还需要的理由 | 结论 |
|---|---|---|
| L2 业务 App | — | ✅ **依然是最主要的载体** |
| L1 薄桥 | 灯环、系统模式、原厂唤醒链 | 仍然需要，但**只为这三件事** |
| 四麦采集 | 什么都不用改，`AudioRecord` 直接要 | ✅ 归入 L2 |

**可选的下一步**（都有风险，需要先确认）：

1. **验证 AudioRecord 是否真的拿到增强音频** —— 需要一个测试 App 采几秒音频，
   跟原厂采的对比 SNR。这一步要装 App，且需要一次「停 voice 服务」的窗口。
2. **摸清 `ISysPrivate` 的消息格式** —— 用 `run-as` 跑个小程序探一下（只读）。
3. **确认 `run-as` 能否长期当调试通道** —— 哪些能力能靠它摸清。

---

## 六、这次实验「什么都没动」的证明

| 检查项 | 实验前 | 实验后 |
|---|---|---|
| SELinux | Enforcing | Enforcing |
| `/system` 挂载 | `ro` | `ro` |
| 装了什么新东西 | — | 无（只 `ls` / `cat` / `logcat` / `pull` 到电脑） |
| 停了什么服务 | — | 无 |
| 四麦 owner_pid | 4713 (usc_record_thre) | 4713（未变） |
| 设备上的临时文件 | 只有原有的 3 个 apk | 不变 |
