# 参考项目系统性比对与选型结论

> 目的：把手上四个参考项目放到同一张尺子上量，明确「各自怎么实现 / 对我们有什么用 / 我们最终怎么做」。
> 数据来源：本地克隆的仓库源码 + 我们自己在设备上的实测（见 `docs/experiment-*.md`）。
> 更新：2026-10-29

---

## 一、评估对象与评估方法

### 1.1 五个对象（四个是参考，一个是我们正在跑的）

| # | 项目 | 本地路径 | 定位 |
|---|---|---|---|
| P1 | `VRISE/feixun-r1` | `D:\dev\agent-repos\feixun-r1` | 改原厂包，最轻的「换脑子」方案 |
| P2 | `shj4588/Phicomm_R1_XiaoZhi_Client` | `D:\dev\agent-repos\Phicomm_R1_XiaoZhi_Client` | 双包（device + player）完整工程 + 逆向资料 |
| P3 | `IoTDevice/phicomm-r1-controler` | `D:\dev\agent-repos\phicomm-r1-controler` | 设备外部的 adb 遥控器 |
| P4 | `morluto/rea` | `D:\dev\agent-repos\rea` | 逆向分析工具（不是方案） |
| P5 | **我们装的 `unisound_MOD_v1.0.4`** | 设备上 | 现役第三方 MOD，来源未知、闭源 |

> ⚠️ **P5 是个容易被忽略的事实**：我们现在跑的这套 **不属于上面任何一个项目**。
> 它的网页写着「斐讯R1配置 1.0.4」+ 公众号「波波弱电日志」，是另一个作者的作品。
> 也就是说这个生态里至少有三条独立实现路线。

### 1.2 评估维度（为什么选这几个）

选型的真正约束不是「哪个功能多」，而是下面这五条，所以按它们量：

| 维度 | 为什么重要 |
|---|---|
| **① 麦克风怎么拿** | 实测已确认：单 substream，被原厂链独占（`docs/archive/evidence-mic-exclusivity.md`）。这一条直接淘汰一半方案。 |
| **② 是否保留原厂唤醒** | 唤醒质量是这类产品的命门；自己调在 490MB 内存机器上风险高。 |
| **③ 能不能控制灯环** | 只有拿到 `ISysPrivate` 身份才行（实测 `FXSystemPrivate` 服务存在）。 |
| **④ 迭代成本** | 我们要反复改功能，如果每次都要重打包顶替，工程效率会崩。 |
| **⑤ 回退安全** | 弄坏了必须能一键回原厂。 |

---

## 二、逐个剖析

### P1 · VRISE/feixun-r1 —— 「只换脑子」

**怎么实现的**

```
原厂:  唤醒 → ASR → 云端 NLU（已死）→ pipeline 分发
改后:  唤醒 → ASR → 自定义 Handler（优先级 310 > 默认 300，抢占消费）
                                    ↓
                          命中意图 → 异步取数据 → ctx.playTTS() → reset()
```

- **手段**：`build_and_deploy.sh` 全流程 = `javac` → `dx` → `baksmali` 反编译原厂 APK →
  把 `custom/` 编译出的 class 转成 smali 注入 `original_v2/smali/` → `smali` 重打包 →
  **`build_janus_v2.py` 做 Janus 顶壳**（源码里写明 `CVE-2017-13156`，并修正
  `DEX file_size` 以通过 dalvik 校验 —— 和我在这台设备上实测的文件布局完全一致）。
- **只注入这些**：`custom/` 整个目录 + `ExampleANTEngineInitializer` + `NLUDispatcher*` + `MqttTransportChannel*`。
- **代码规模**：`custom/` 下 60+ 个 Java 文件，按职责分：
  `handler/`（13 个意图处理器）、`ai/`（OpenAI 客户端 + 对话历史）、`ipc/`（灯控）、
  `keyevent/`（单击/双击/长按/三击）、`music/`、`outputevents/`、`persona/`、`teaser/`。

**关键收获（这条对所有人都是必经之路）**

> `ApiHelper.interceptTTS` 是 TTS 回调线程，**任何同步等待都会持有 audio 管线锁导致整机卡死**。
> 所有功能必须走 pipeline 异步 Handler。

**它的短板**

- 唤醒词改起来需要重新构建（`patch_wakeword` 那类做法要改 smali）。
- 没有 Web 控制台，配置只能改设备上的 ini（`ai_config.local.ini` 推到 `/data/data/.../files/`）。
- 构建链要 **JDK 11**（Gradle 6.5 + AGP 4.1.1 不支持 21），且要自建 `key.jks`。
- 授权 **GPL**：如果抄它的代码，衍生作品也要开源。

**对我们有什么用**

- ✅ **L1 桥的最佳范本**：Handler 抢占、异步 TTS、灯控 `sendMessage(4096, …)` 全都现成。
- ✅ 配置下发模式（ini 推送到 `/data/data/<pkg>/files/`）可以直接借用。
- ✅ Janus 构造脚本 `build_janus_v2.py` 是现成可用的（60 行，逻辑清晰）。

---

### P2 · shj4588/Phicomm_R1_XiaoZhi_Client —— 「双包 + 完整逆向」

**怎么实现的**

两个 Janus 包，各管一块，靠 `AIDL` 互联：

| 包 | 顶替谁 | 里面有什么（实测目录） |
|---|---|---|
| device | `com.phicomm.speaker.device` | `AudioCapture`、`KwsEngine`、`UscTap`（抓云知声）、`WakeupBridge`、`SessionController`、`TtsPlayer`、`OpusCodec`、`R1Lights`、`XiaoZhiWs`、`WebConsole`、`UiModern` |
| player | `com.phicomm.speaker.player` | `WebSrv`（Web 控制台）、`DlnaHook`、`Bt`、`PCtl`、`Proxy`、`Svc`、`Sys` |

- **手段**：和 P1 一样是 Janus，但**规模大得多** —— 有 `janus_poc.py`、`libr1opus.so`（自建 so 宿主）、
  R8 混淆配置、自建签名 `r1-pure.jks`。
- **补丁清单**（每个都对应一个真实毛病，价值很高）：

| 补丁 | 治什么 |
|---|---|
| `patch_bindgate.py` | **拆掉「未绑定/无网络/待升级」三道闸门**（云已死，闸门永真不了） |
| `patch_wakeword.py` | 唤醒词替换 |
| `patch_enter_asr.py` | 唤醒后直接进 ASR，跳过原厂提示音 |
| `patch_light.py` / `patch_light_hold.py` | 治「底部一直闪」，让唤醒灯定格收音方向 |
| `patch_boot_sound.py` | 关启动音乐和联网提示音 |
| `patch_bt_scan.py` / `patch_bt_hold.py` | 治蓝牙「连上马上断」 |
| `patch_dlna_sync.py` / `patch_seek.py` | DLNA 同步「正在播放」、拖动进度条 |

- **文档资产**：`05-reverse-docs/` 下有 Janus 分析、SELinux 分域实证、
  四麦音频链路取证、AEC 方案、真机日志（`dump_device.txt` 1.8MB、`dmesg` 450KB）。
- **架构观**：把音频 PCM 用 Opus 编码后经 WebSocket 推到「小智」服务端，语音识别和对话在服务器侧。
  这解释了为什么它要 `OpusCodec` + `XiaoZhiWs`。

**它的短板**

- ❌ **没有 LICENSE 文件** → 默认「保留所有权利」，代码不能直接抄，只能作为**证据和思路参考**。
- ❌ 双包 + Janus + 自建 so，构建链极重（要 JDK 17、R8、D8、smali、jarsigner 全套）。
- ❌ 它把「Web 控制台」放在 player 包，而**我们设备上的 8080 在 device 包**，说明两者实现不同源，别照抄。

**对我们有什么用**

- ✅✅ **最重要的证据来源**：Janus 可行性、SELinux 分域、四麦链路、蓝牙坑，全都有实证。
- ✅ **补丁思路清单**：尤其 `patch_bindgate`（为什么原厂机叫不动）和 `patch_bt_hold`（蓝牙秒断）。
- ✅ **「L1 采 PCM → 服务器 ASR」的备选架构**：如果嫌云知声 ASR 不够好，这是升级路径。
- ⚠️ 代码不能直接复用（无授权），**只能重写**。

---

### P3 · IoTDevice/phicomm-r1-controler —— 「外部遥控器」

**怎么实现的**

Go + gin，**跑在局域网另一台机器上**，用 adb 遥控音箱。配套手机 App「云易连」（OpenIoTHub）。

它暴露的 HTTP 接口（`config/models.go` 实测）：

| 接口 | 底层 | 能力 |
|---|---|---|
| `GET /get-image` | `screencap` + `pull` | 截屏 |
| `GET /do-cmd?cmd=` | `adb shell <任意>` | **任意 shell** |
| `GET /do-adb-cmd?cmd=` | `adb <任意>` | **任意 adb** |
| `POST /install-apk` | `push` + `pm install -t` | 装包 |
| `GET /input-keyevent?key=` | `input keyevent` | 模拟按键（音量等） |
| `GET /media-dispatch?key=` | `media dispatch` | 播放/暂停/上一首 |
| `GET /list-packages` | `pm list packages` | 列应用 |
| `GET /led-color` | —— | **空接口，没实现** |

**它的天花板在哪（这是最有价值的一条结论）**

- ❌ **灯环没做成**（`/led-color` 是空函数）—— 因为灯走 `ISysPrivate`，外部 adb 拿不到身份。
- ❌ 播放只有按键级，看不到播什么、拖不了进度。
- ❌ 蓝牙 / DLNA / AirPlay 一概碰不到（在 player 包里）。
- ⚠️ `/do-cmd` 是裸的任意命令执行，**无鉴权**。

**结论**：外部 adb 只能做「按键 + 装包 + 截屏」。
**一切需要进程身份的能力（灯、音频、语义）都做不到。** 可当辅助通道，不能当主架构。

**对我们有什么用**

- ✅ **反面教材 + 能力边界证明**：这条路的上限已经探明。
- ✅ 它的 HTTP 接口设计（GET + query）可以借用一下风格，但我们的控制面要加鉴权。
- ⚠️ 别指望它能控制灯效。

---

### P4 · morluto/rea —— 工具，不是方案

MCP server，把 JADX / Apktool / Ghidra / adb 封给 AI agent 用。
（Android lane 需要它指定的 `jadx-headless-mcp 0.7.1` JAR，我这边下载失败，没跑起来。）

**对我们有什么用**

- 🔧 后期如果要深挖 dex / so，它能把反编译流程自动化。
- ❌ 现在不是瓶颈 —— 我们已经用 `aapt2` + Python 读 dex + adb 把要查的都查出来了。

---

### P5 · 现役 `unisound_MOD_v1.0.4`（我们装的这个）

**它是什么**

- **Janus 包**：`[前置 DEX 8,128,580 B][原厂 Unisound.apk 24,936,387 B]`，总计 33,064,967 B。
- 尾部壳与原厂 APK **逐字节只差 405 字节**（全是中央目录偏移字段），签名仍是原厂。
- 前置 DEX 里加了：`8080` 网页配置面板、`NanoHTTPD` 风格的服务器、音乐源接入、AI 大模型接入、
  灯效开关、定时任务、语音音色切换。

**它的能力清单（来自 8080 的 22 个接口）**

`/save_config`（22 参数）、`/set_voice`、`/set_wakeup`、`/set_tts`、`/toggle_dormant`、
`/toggle_bass_boost`、`/toggle_ambient_light`、`/toggle_bluetooth`、`/alarm_add|del|edit|toggle`、
`/voice_del`、`/get_qr_key|img`、`/check_qr`、`/qw_pubkey`、`/alarm_list`

**它的短板**

- 无鉴权、无 CSRF、裸 GET；`/qw_pubkey?gen=1` 能覆盖私钥文件。
- 闭源，不能改（要改只能反编译它的前置 DEX 再重签 —— 但那样就失去"原厂签名"了）。
- 主页是青色测试页（Pandora），不是给人看的界面。

**对我们的用处**

- ✅ **它证明了 Janus 在这台机器上活着**，而且已经把「灯、音色、唤醒词、音乐源、AI」都调通了。
- ✅ 它的 22 个接口是**现成的能力清单**——告诉我们这个包**能做到什么**。
- ⚠️ 它是别人的闭源作品，我们的新架构**不基于它**，只作为「可行性参照」。

---

## 三、横向对比矩阵

### 3.1 实现手段

| 维度 | P1 VRISE | P2 XiaoZhi | P3 controler | P5 现役 MOD |
|---|---|---|---|---|
| 运行位置 | 设备上 | 设备上（+服务端） | **设备外** | 设备上 |
| 改原厂包 | 是（Janus） | 是（Janus，两个包） | 否 | 是（Janus） |
| 保留原厂唤醒 | ✅ | ✅（KwsEngine） | ❌ | ✅ |
| ASR 在哪 | 原厂本地 | 服务端（PCM 上行） | 无 | 原厂本地 |
| 麦克风怎么拿 | 不碰（用原厂 ASR 结果） | **进程内直采**（`AudioCapture` + `Uni4micHalJNI`） | 拿不到 | 不碰 |
| 灯环 | ✅ `PhicommLightController` | ✅ `R1Lights` | ❌ 未实现 | ✅ |
| 播放/DLNA | 部分 | ✅ 完整（player 包） | 只有按键 | ✅ |
| Web 控制台 | ❌（ini 文件） | ✅（在 player 包） | ✅（但只是 adb 包装） | ✅（在 device 包） |
| 构建依赖 | JDK 11 + smali + Janus | JDK 17 + R8 + D8 + smali + Janus + so 宿主 | Go 一条命令 | 无（闭源） |
| 授权 | GPL | **无授权声明** | MIT | 闭源 |

### 3.2 对我们五条约束的满足度

| 约束 | P1 | P2 | P3 | P5 | **我们要的** |
|---|---|---|---|---|---|
| ① 麦克风 | 用原厂（不碰） | 进程内直采 | ✗ | 用原厂 | 用原厂 / L1 内 |
| ② 唤醒质量 | ★★★（原厂） | ★★★（原厂 KWS） | ✗ | ★★★（原厂） | ★★★ |
| ③ 灯环 | ✓ | ✓ | ✗ | ✓ | ✓ |
| ④ 迭代成本 | ✗ 每次重打包 | ✗ 更重 | ✓ 但能力弱 | ✗ 不可改 | **必须低** |
| ⑤ 回退 | ✓ uninstall | ✓ uninstall | — | ✓ uninstall | ✓ |
| **综合** | 能用但重 | 最强但最重 | 能力不足 | 不可改 | **要「重的那部分尽量不动」** |

### 3.3 一句话评价

| 项目 | 一句话 |
|---|---|
| P1 | **最像我们要的**，但把配置做成了文件，没有控制面 |
| P2 | **资料最全、能力最强**，但重到不适合日常迭代，且无授权 |
| P3 | **只能按键**，证明了外部 adb 的天花板 |
| P5 | **能跑但不归我们**，只能当「能做到」的证据 |
| P4 | 工具，留着以后用 |

---

## 四、需求 → 能力 映射

我们要的东西，分别从哪个项目能拿到：

| 我们要的能力 | 来源 | 怎么拿 |
|---|---|---|
| Janus 构造 | P1 `build_janus_v2.py`（GPL，可改） | 直接改它的脚本 |
| 语义层接管 | P1 `custom/handler/*` | 照它的 pipeline 抢占写法 |
| 拆三道闸门 | P2 `patch_bindgate.py` 的思路 | **重写**（无授权） |
| 灯环控制 | P1 `PhicommLightController`（`sendMessage(4096,…)`） | 直接借鉴 |
| 异步 TTS（不卡死） | P1 README 的教训 | 照做 |
| 蓝牙秒断修复 | P2 `patch_bt_hold.py` 思路 | 需要时重写 |
| 进程内直采四麦 | P2 `AudioCapture` + `Uni4micHalJNI` | 需要时重写 |
| 能力边界（什么做不到） | P3 | 记住结论即可 |
| 现成能力清单 | P5 的 22 个接口 | 当 checklist |
| 反编译自动化 | P4 | 后期再说 |

---

## 五、最终架构（与上面结论一致）

```
┌──────────────────────────────────────────────────────────────┐
│ L3  控制面（PC / NAS / 手机浏览器）                            │
│     配置、日志、部署、升级 —— 不装在音箱上                      │
└──────────────────────────────────────────────────────────────┘
                    ▲ HTTP（带鉴权）
┌──────────────────────────────────────────────────────────────┐
│ L2  业务 App（自己的包名 com.r1.brain）                        │
│     ★ 全部业务逻辑：LLM / 意图 / 音乐 / 消息 / HA / 记忆         │
│     ★ 普通 APK —— 随时 pm install，随时 pm uninstall           │
│     ★ 不碰麦克风（碰不到，也不需要）                            │
└──────────────────────────────────────────────────────────────┘
                    ▲ 广播 / socket（7 条消息）
┌──────────────────────────────────────────────────────────────┐
│ L1  桥（Janus 顶替 com.phicomm.speaker.device，保持极薄）       │
│     ① 原厂唤醒 + 本地 ASR（唯一能拿麦的进程）                   │
│     ② ASR 文本 → L2                                          │
│     ③ L2 回的文本 → 原厂 TTS 播                               │
│     ④ 灯环 / 系统模式（ISysPrivate）                           │
│     ⑤ 拆三道闸门                                              │
│     ⚠️ 只在桥协议变化时才重新打包                               │
└──────────────────────────────────────────────────────────────┘
```

### 5.1 这个架构是从哪几个项目"拼"出来的

| 我们的层 | 借鉴自 | 理由 |
|---|---|---|
| L1 的 pipeline 接管 | **P1** | 唯一有完整可运行代码的 |
| L1 的 Janus 构造 | **P1** | 脚本现成、逻辑清楚 |
| L1 拆闸门 / 蓝牙修 | **P2** 的思路 | 有实证但无授权，须重写 |
| L2 的全部业务 | **我们自己** | 没有任何项目做过「独立 App + 广播桥」这个形态 |
| L3 的控制面 | **P5** 的接口清单当 checklist | 但重做，加鉴权 |
| 能力边界判断 | **P3** | 证明外部 adb 不可行 |

### 5.2 为什么 L2 必须是独立包（这是我们的原创点）

三个参考项目里，**没有一个**把业务逻辑放在独立 App 里：

- P1 全塞进被顶替的包 → 改一个字就要重打包
- P2 全塞进两个被顶替的包 → 更重
- P3 放外面 → 但拿不到能力
- P5 闭源

**我们的 L2 是把「能力」和「迭代」拆开**：
能力留在 L1（不常变），迭代放 L2（天天变）。这是这台设备上唯一能兼顾「好用」和「可维护」的切法。

### 5.3 为什么不用其他几种做法

| 做法 | 为什么不行 |
|---|---|
| 纯 L2 自己采麦（不顶替） | **实测：麦克风被原厂链 100% 独占，`read()` 永远阻塞** |
| 停用原厂链，自己实现唤醒 | 490MB 内存只剩 10MB，唤醒引擎跑不动；唤醒质量要从零调 |
| 只做外部 adb 遥控（P3 路线） | 拿不到灯、音频、语义，天花板太低 |
| 全部塞进 L1（P1/P2 路线） | 每次改功能都要重打包顶替，迭代成本不可接受 |
| 继续用 P5 那个 MOD | 闭源，改不了；而且没有鉴权、主页是测试页 |

---

## 六、落地顺序（按依赖关系排）

```
第 1 步  定协议（半天）
        L1↔L2 的 7 条消息定死：ASR_RESULT / WAKEUP / KEY_EVENT
                                  SPEAK / LIGHT / SYS_MODE / STOP

第 2 步  L2 单机跑通（1-2 周，零风险）
        - 调试入口：adb shell am broadcast 发文本 → 当作"听到了"
        - LLM / 意图 / 音乐 / TTS 全跑通
        - 配置界面（L3 网页）
        产出：证明业务层没问题

第 3 步  L1 桥（一次性，做完很少改）
        - 本地先做 Janus PoC（PC 上验，不碰设备）
        - 上机顶替，打通 7 条消息
        - 拆三道闸门
        产出：能语音对话的完整链路

第 4 步  合并调优
        - 端到端延迟（目标：说完话 2 秒内开口）
        - 灯效、按键
```

**为什么必须先做 L2**：它占 90% 工作量，且零风险；L1 是高风险的一次性投入，
只有确认业务层跑通了才值得动。

---

## 七、需要你拍板的四件事

| 问题 | 选项 | 我的建议 |
|---|---|---|
| LLM 用哪家 | 智谱 GLM-4.5-Flash / 豆包 / DeepSeek / 本地 Ollama | **GLM-4.5-Flash**（免费、VRISE 实测 0.5~1 秒；别用 4.7，27~68 秒） |
| TTS 用谁的 | 原厂云知声 / 云端（豆包音色） | 先用原厂（零成本），不满意再换云 |
| 音乐源 | Navidrome / 网易云 / QQ / 本地 | **Navidrome**（标准 Subsonic API，最省事） |
| L3 跑哪 | 这台 PC / NAS / 树莓派 | 你定，我按能长期开着的那台来 |
