# R1 生态研究：四个参考项目对比（2026-10-28）

> 本节所有结论都标注了出处。**标「实测」的是我在本机 R1（<音箱IP>）上亲自验证过的，
> 标「引用」的来自参考项目的文档，标「推断」的是我基于前两者推理、尚未验证的。**

## 0. 一句话总结

这个生态里**所有**成功的项目，动作都是同一个：

> **顶替 `com.phicomm.speaker.device` 这个包的安装身份**（不是改包名——改包名无用），
> 然后要么改它的行为，要么它压根没坏、我们只需在它旁边加东西。

而顶替的手段只有两条：**Janus 漏洞** 或 **原厂签名泄露/源码**。前者已在你这台机器上被验证过可行。

---

## 1. 四个参考项目速览

| 项目 | 现在的位置 | 语言/形态 | 它解决的核心问题 | 对我们的价值 |
|---|---|---|---|---|
| **VRISE/feixun-r1** | 跑在**音箱上** | Java + Gradle，改原厂包 | 原厂云已死，怎么让唤醒+ASR还能用 | 给了完整的**原厂源码级工程结构**和构建链 |
| **shj4588/Phicomm_R1_XiaoZhi_Client** | 跑在**音箱上**（device + player 两个包） | Java + smali 补丁 + Janus | 同上，但做到了**V2.0.1 可重建**，还带 Web 控制台 | **最有价值的参考**，含逆向报告和所有坑 |
| **IoTDevice/phicomm-r1-controler** | 跑在**局域网另一台机器** | Go + gin | 用 adb 从外部遥控音箱 | 反面教材：它证明**外部遥控的天花板很低** |
| **morluto/rea** | 跑在**电脑上** | Node/TS + MCP | 让 AI 能逆向分析 App | 工具，不是方案 |

---

## 2. VRISE/feixun-r1 —— 最轻的改法：只换大脑

**做法**：保留原厂云知声唤醒 + ASR（**一个字都不改**），只在「识别出文字之后」那一步把自己插进去。

```
原厂: 唤醒 → ASR → 云端 NLU(已死) → pipeline 分发
改后: 唤醒 → ASR → 自定义 Handler(优先级 310 > 默认 300) → 抢走 → 自己处理
```

**关键文件**（`app/src/main/java/com/phicomm/speaker/device/custom/`，60+ 个 Java 文件）：

| 目录 | 作用 |
|---|---|
| `handler/PhicommChatHandler` 等 13 个 | 各意图的处理，跑在云知声 ANT SDK 的 pipeline 里 |
| `ai/OpenAIClient`、`AIConfig` | 接任意 OpenAI 兼容端点 |
| `ipc/PhicommLightController` | 控制灯环 |
| `keyevent/` | 接管按键（单击/双击/长按/三击） |
| `outputevents/` | 蓝牙输出、休眠、配网 |
| `music/` | 网易云客户端、播放器控制 |

**踩过的坑（README 明写）**：

1. **`ApiHelper.interceptTTS` 是 TTS 回调线程，任何同步等待都会持有 audio 管线锁导致整机卡死。**
   所有功能必须走 pipeline 的异步 Handler，不能在 TTS 回调里做 HTTP。
2. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` —— **外壳必须是 `device_original.apk`（原厂签名）**。
3. `Resources$NotFoundException` —— 用 Gradle 直接编译套壳会资源 ID 冲突，**必须走 smali 注入**保留原厂资源表。

> ### ★ 最关键的发现：IPC 系统能力
>
> `custom/ipc/PhicommIpcRegister.java` 里 import 了 **`android.os.MessageDispatchManager`** ——
> 这是 R1 系统里**定制的、AOSP 没有的**类。配合 `zsd.jar` 里的
> `android/os/ISysPrivate`、`android/os/IMessenger`、`android/os/SysPrivateManager`
> 构成一套 **msgcenter 总线**。
>
> 我**实测**确认这台机器上真的有这个服务：
> ```
> $ adb shell service list | grep -i sys
> 30  FXSystemPrivate: [android.os.ISysPrivate]
> ```
>
> **灯环就是通过它控制的**（`PhicommLightController`）：
> ```java
> private static final int LIGHT_WHAT = 4096;
> // 24 个唤醒灯位 + 特殊位：
> // 100 = 休眠灯    203 = 加载灯    254 = 网络断开灯
> IPC 发送: sendMessage(4096, lightIndex, 0=开/1=关, null)
> ```
> 这个能力**依赖进程身份**，普通 App 调不到。

---

## 3. shj4588/Phicomm_R1_XiaoZhi_Client —— 最完整的工程

### 3.1 结构

两个包，各管一块：

| 包 | 顶替谁 | 职责 |
|---|---|---|
| `XiaoZhi_Unisound_V2.0.1.apk` | `com.phicomm.speaker.device` | 会话控制、音频采集/上行、TTS、灯控、曲库、MCP 桥 |
| `XiaoZhi_EchoService_V2.0.1.apk` | `com.phicomm.speaker.player` | **Web 控制台 :8080**、播放控制、进度条、封面歌词、蓝牙/DLNA/AirPlay |

> 📌 **这就是你那个 8080 的来历**：它不是原厂的，是**第三方魔改的 `EchoService` 提供的**。
> 原厂 `EchoService_system.apk` 只有 1.1MB，而 MOD 的 player 包做了「三钩子 + 蓝牙 + DLNA + seek」一堆手术。

### 3.2 他们打的补丁（每个都对应一个真实毛病）

**device 包**：
| 补丁 | 治什么 |
|---|---|
| `patch_wakeword.py` | 唤醒词「小讯小讯」→「小智小智」 |
| `patch_enter_asr.py` | 唤醒后直接进 ASR，跳过原厂提示音 |
| `patch_bindgate.py` | **拆掉「未绑定/无网络/待升级」三道闸门**——云已死，闸门永真不了，所以原厂机现在叫不动 |
| `patch_light.py` / `patch_light_hold.py` | 治「底部一直闪」，让唤醒灯定格在收音方向 |
| `patch_boot_sound.py` | 关掉启动音乐和联网提示音 |

**player 包**：
| 补丁 | 治什么 |
|---|---|
| `patch_smali.py` | 三钩子：`Common.onCreate`→`Boot.attach`、`EasyPlayer.play`→`onPlay`、`playIndex`→`onIndex` |
| `patch_bt_scan.py` | 拔掉原厂两处把 scan mode 强设 NONE 的代码 |
| `patch_bt_hold.py` | **治「连上马上断」**：`bootInit()` 的 A2DP kick + `BluetoothPlayer$2.run()` 的主凶分支 |
| `patch_dlna_sync.py` | DLNA 推流同步「正在播放」 |
| `patch_seek.py` | AIDL 新增 seek 事务（拖动进度条） |

> **`patch_bindgate.py` 这条极其重要**：它说明**原厂机现在根本叫不动，不是因为坏了，而是三道人造闸门。**
> 这三道闸门是「未绑定 / 无网络 / 待升级」，都要连已死的斐讯服务器。

### 3.3 Janus 漏洞的完整分析（外部仓库 P2 的 `05-reverse-docs/diag-pandora/VERDICT-pandora-disguise.md`）

**为什么改包名没用**（他们的实测）：

```
/system/etc/permissions 里 seapp_contexts 原文：
  user=system               domain=system_app     type=system_app_data_file
  user=_app seinfo=platform domain=platform_app   type=app_data_file
  user=_app                 domain=untrusted_app  type=app_data_file
```

**没有任何 `name=` 规则** → 改包名对 SELinux 域**零影响**，永远落 `untrusted_app`。
想进特权域只有两条：`sharedUserId=android.uid.system` 或 `seinfo=platform`。

**为什么签名伪造不了**：

```
/system/etc/security/mac_permissions.xml 只有一条映射：
  signer 34:A2:D2:FE:89:96:56:AF:5D:F3:FB:F6:8E:80:54:77:B3:DF:37:08:CA:65:C8:42:BA:EC:2D:E8:E5:78:BC:33
    → seinfo=platform
```

这把证书 subject 是 `CN=R1, O=Company, E=phicomm@phicomm.com`，**自签 2017-11-29**，不是 AOSP 测试键。
私有钥在斐讯手里。

**Janus（CVE-2017-13156）怎么绕过**：

- APK 的 **v1(JAR) 签名只覆盖 ZIP 每个条目的内容，不覆盖 ZIP 结构和偏移**。
- 构造 `[自定义 DEX][原厂签名 APK]`，把 ZIP 里所有偏移 += DEX 长度：
  - **PackageManager 从文件尾读 ZIP** → 条目没变 → **签名 = 原厂签名** → 装得上
  - **ART 从 offset 0 读** → 看到 `dex\n035` → **跑我们的代码**
- 结果：**代码是我们的，身份是原厂的**，共享 uid / seinfo 全按原厂算。

**这台机器上可利用性——我实测补充了一条铁证**：

| 条件 | 值 | 来源 |
|---|---|---|
| 安卓版本 | 5.1.1 (API 22) | 实测 |
| 安全补丁 | 2016-04-01 | 引用（在 Janus 区间内） |
| root | 无（`adb root` 无效，uid 恒为 2000） | 引用 |
| **现役 device 包文件头** | **`dex\n035\0`** | **实测** |
| **dex.header.file_size** | **33,064,967 = 整个文件长度**（被改写） | **实测** |
| **dex data 结束位置** | **8,128,580，恰好等于 ZIP 起点** | **实测** |
| 现役包大小 | 33,064,967 = 8,128,580（前置 DEX）+ 24,936,387（ZIP 部分） | 实测 |

> ⚠️ **这意味着你现在装的这个 MOD 本身就是一个 Janus 包。**
> 我此前算出「MOD 签名 `[38b25ad4]` ≠ 出厂 `[105ffce5]`」，当时推测是**重签**；
> **这个推测是错的**。真相是：文件头是前置 DEX，PackageManager 读到的是尾部原厂 APK，
> 签名应该仍是原厂的——两者显示不同指纹，说明**尾壳用的是别一份原厂签名版本**（可能是 Pandora.apk 而非 Unisound.apk）。
> 这条要在 `docs/` 里更正，不要再用「重签」这个说法。

---

## 4. IoTDevice/phicomm-r1-controler —— 外部遥控的天花板

**做法**：Go 写的，**不装在音箱上**，跑在局域网另一台机器（PC/NAS/路由器），通过 adb 遥控音箱。
配套手机 App「云易连」（OpenIoTHub）。

**它提供的 HTTP 接口**（`config/models.go`）：

| 接口 | 底层命令 | 能干什么 |
|---|---|---|
| `GET /get-image` | `screencap -p` + `pull` | 截屏 |
| `GET /do-cmd?cmd=` | `adb shell <任意>` | **任意 shell** |
| `GET /do-adb-cmd?cmd=` | `adb <任意>` | **任意 adb** |
| `POST /install-apk` | `push` + `pm install -t` | 装包 |
| `GET /led-color` | **空接口** | ❌ 作者没实现 |
| `GET /input-keyevent?key=` | `input keyevent` | 模拟按键（音量等） |
| `GET /media-dispatch?key=` | `media dispatch` | 媒体控制（播放/暂停/上一首） |
| `GET /list-packages` | `pm list packages` | 列应用 |

**它的天花板在哪**：

- ❌ **灯环完全没做成**（`/led-color` 是空的）——因为灯环走 `ISysPrivate`，
  外部 adb 拿不到。
- ❌ 播放只是 `media dispatch` 按键级，**看不到当前放什么、拖不了进度**。
- ❌ 蓝牙、DLNA、AirPlay 一概碰不到（那些在 `player` 包里）。
- ⚠️ `/do-cmd` 和 `/do-adb-cmd` 是**裸的任意命令执行**，没有任何鉴权。

> **结论：外部 adb 遥控只能做到「按键 + 装包 + 截屏」。**
> 一切需要进程身份的东西（灯、音频、语义）都做不到。这条路可以当**辅助通道**，不能当主架构。

---

## 5. 关键能力分层（这是设计新 App 的依据）

| 能力 | 谁能拿到 | 依据 |
|---|---|---|
| 说「小讯小讯」唤醒 | 原厂云知声 SDK（device 包内） | 引用 |
| 唤醒后拿到识别文字 | pipeline Handler（device 包内，优先级 310 抢 300） | 引用 |
| 灯环 24 位控制 | `ISysPrivate` msgcenter（**需进程身份**） | 实测服务存在 + 引用代码 |
| 四麦阵列原始/增强音频 | 走 `AudioRecord`：**可能不用特权**（HAL 里就编了四麦算法，mediaserver 代为开卡）<br>走 `Uni4micHalJNI` 直开 `/dev/snd`：**需 platform_app** | 引用（§四/§五） |
| 播放控制 / DLNA / AirPlay | `com.phicomm.speaker.player` 包 | 引用 |
| 改音量曲线 / 系统模式 | `ISysPrivate.setSysMode`（**需进程身份**） | 实测 `service list` |
| 按键（单击/长按/三击） | `keyevent` 域 + `/dev/input` | 引用 |
| 任意 shell | adb 5555（局域网内谁都能用） | 实测 |

---

## 6. 由此推导出的三个设计选项

> 详细架构见 `archive/architecture-v0.md`，这里只放取舍。

| 选项 | 做法 | 风险 | 能拿到 | 拿不到 |
|---|---|---|---|---|
| **A. 寄生** | 保持原厂 device 包不动，我们另做 App，靠反射/绑定去蹭 | 低（随时卸载） | 「看和听」：读状态、发指令、放自己的音频 | 灯环、原厂唤醒链、语义劫持 |
| **B. 顶替 device** | Janus 换掉 `com.phicomm.speaker.device` | 高 | 全部，包括唤醒、语义、灯环、四麦 | 无（但要自己接管整条链） |
| **C. A + B 混合** | 先用 A 打通业务，B 只做薄薄一层「壳」 | 中 | 全部 | — |

**C 的关键洞察**（这是我认为最合理的方向）：
Janus 只能锁死包名给「外壳」，**但外壳里跑什么是由我们自己定的**。
所以可以把 B 做成一个**极薄的桥**（几百行）：只负责把原厂能力暴露出来，
**真正复杂的业务全放在 A 的独立 App 里**，随时能改、能卸载、能升级，不用每次重新 Janus 打包。

---

## 7. 待验证清单（写进 backlog）

- [ ] `patch_bindgate` 说的三道闸门，在**当前这个 MOD** 里还在不在？（决定要不要动）
- [ ] 普通 App 用 `AudioRecord` 拿到的到底是不是四麦增强音频？（决定要不要为了四麦去做 B）
- [ ] `ISysPrivate` 的域名/消息格式是什么？能不能从 `run-as`（shell，非破坏）下摸清
- [ ] `com.phicomm.speaker.player` 现在是谁？（原厂 1.1MB 还是 MOD 版）
- [ ] 原厂 `device` 包 `debuggable=true` → `run-as` 可用，能借到 `platform_app` 域做只读取证

---

## 附：参考项目在本地的位置

```
D:\dev\agent-repos\feixun-r1                    (VRISE/feixun-r1)
D:\dev\agent-repos\Phicomm_R1_XiaoZhi_Client    (shj4588/...，含 05-reverse-docs 全部逆向报告)
D:\dev\agent-repos\phicomm-r1-controler         (IoTDevice/...)
D:\dev\agent-repos\rea                          (morluto/rea)
```
