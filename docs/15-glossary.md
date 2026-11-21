# 术语表

> 这个项目里有一堆自造词和音译词，先对齐语义再讨论。

---

## 架构相关

| 词 | 含义 |
|---|---|
| **L1** | 桥层。Janus 顶替 `com.phicomm.speaker.device` 的那个包。持有原厂唤醒/ASR/TTS/灯环。很少改。 |
| **L2** | 业务层。我们自己的普通 App（`com.r1.brain`）。全部业务逻辑，随时 `pm install`。 |
| **L3** | 控制面。跑在电脑/NAS 上，放配置界面、日志、部署工具。**第一期可以不起**。 |
| **桥 / bridge** | L1 和 L2 之间的通信通道（广播 + JSON），见 `07-protocol.md`。 |
| **单一入口** | 所有外部请求只进 L2，L1 不监听端口。 |

## 设备与安装

| 词 | 含义 |
|---|---|
| **Janus** | CVE-2017-13156。在 APK 前面贴一段 DEX，让 ART 从 offset 0 读我们的代码，而 PackageManager 从尾部读原厂签名。**代码是我们的，身份是原厂的**。 |
| **顶替** | 用 Janus 把我们的代码塞进原厂包的身份里。包名**必须**是 `com.phicomm.speaker.device`。 |
| **前置 DEX** | Janus 文件里前面那段（本机实测 8,128,580 字节）。 |
| **原厂壳** | Janus 文件尾部那个完整的原厂 APK（24,936,387 字节）。 |
| **MOD** | 别人做的第三方改造包。我们现在装的是 `unisound_MOD_v1.0.4`。 |

## 系统与权限

| 词 | 含义 |
|---|---|
| **`ISysPrivate`** | R1 系统里定制的 Binder 服务（`FXSystemPrivate`），读属性、设系统模式。AOSP 没有这个。 |
| **`MessageDispatchManager`** | R1 定制的进程内消息总线，灯环靠它。 |
| **`seinfo` / SELinux 域** | 决定进程能干什么。`untrusted_app` 是最低权限，`platform_app` 高一级，`system_app` 最高。**由签名和 sharedUserId 决定，与包名无关**。 |
| **`mac_permissions.xml`** | 系统里把「证书」映射到「seinfo」的配置。只有一条：斐讯那把证书 → `platform`。 |
| **`run-as`** | adb 的一个能力，能以某个 App 的身份（uid + SELinux 域）执行命令。要求该 App `debuggable=true`。**只读后门，不改磁盘**。 |
| **`subdevices_avail`** | ALSA 里某个 PCM 还剩几个采集流可用。`0` = 被占满。 |

## 音频

| 词 | 含义 |
|---|---|
| **四麦 / 4mic** | R1 的 4 麦克风环形阵列，半径 3.5cm 十字排布。 |
| **`ma4 codec`** | 四麦所在的声卡（card1），`/proc/asound/card1`。 |
| **AudioFlinger** | 安卓的音频服务（mediaserver 里），App 通过它录音/放音。 |
| **Audio HAL** | `audio.primary.rk30board.so`，里面链了四麦算法。 |
| **`uni_4mic_pcm_open`** | 四麦 HAL 的打开接口。它最终调 `pcm_open(card1)`。 |
| **AEC** | 回声消除。喇叭放音时把麦克风收到的自己的声音减掉。 |
| **DOA** | Direction of Arrival，声源方向。四麦可以算这个。 |

## 云知声相关

| 词 | 含义 |
|---|---|
| **云知声 / unisound** | R1 的语音引擎供应商（`com.unisound.*`）。唤醒和 ASR 都是它的。 |
| **ANT SDK** | 云知声的语音会话框架，用 pipeline（责任链）分发事件。 |
| **pipeline Handler** | 责任链上的一环。**优先级数字大的先拿到事件**（310 > 300）。 |
| **`ApiHelper.interceptTTS`** | 原厂的 TTS 回调点。**在这里做同步等待会让整机卡死**。 |
| **闸门 / bindgate** | 原厂三道人造检查：未绑定 / 无网络 / 待升级。都要连已死的斐讯服务器，所以永远不放行。 |

## 项目内缩写

| 缩写 | 指代 |
|---|---|
| **L1** | 顶替原厂 `com.phicomm.speaker.device` 的 Janus 包（唤醒 / ASR 转发 / 灯环） |
| **L2** | `com.r1.brain`，全部业务逻辑 + 8686 控制台 |
| **L3** | 浏览器里的控制台（静态页面，不是设备上的进程） |
| **桥 / Bridge** | L1 里那个类：取文本、发广播、调灯环，不含业务 |

## 日常用语

| 词 | 含义 |
|---|---|
| **`error: closed`** | adb 连接被设备掐断。不是失败，是结果丢了。 |
| **adbd 挂死** | 设备端 adb 守护进程卡住，TCP 能连但握手不回。只能断电恢复。 |
| **体检** | 跑 `tools/r1-doctor.ps1`，看设备全貌。 |
| **探针** | `tools/probe-app`，那个 12KB 的录音测试 App。 |
