# 播报链路方案（设计，暂不实现）

> 依据：2026-10-29 对你提供的 TTS 服务实测。
> 本文只做方案，不写代码。\n> ⚠️ **已升级**：多种使用场景（一次性 / 持续对话）的完整设计见 `05-voice.md`，本文只覆盖单次播报。

---

## 一、已确认的事实

### 1.1 服务可用，而且就在局域网里

```
POST http://<服务IP>:8317/v1/audio/speech
Authorization: Bearer sk-***
Content-Type: application/json
{"model":"grok-tts","input":"你好，我是小讯，很高兴认识你。","voice":"nova"}
```

| 项 | 实测值 |
|---|---|
| HTTP | 200 |
| Content-Type | `audio/mpeg` |
| 体积 | 51,840 字节（约 22 个汉字） |
| **耗时** | **1.96 秒** |
| 格式 | MP3 128kbps / 24kHz / 单声道 |

> ZeroTier 地址 `<跳板IP>:8317` 和局域网地址 `<服务IP>:8317` 是同一台机器。
> **音箱能直接访问后者**（`ping` 通，29.6ms），所以**不需要任何代理或跳板**。

### 1.2 这台机器的播放通路

```
音频输出走 card2（AK7755 codec），不是 HDMI 那张卡
/proc/asound/card2/pcm0p/sub0/status   ← 播放时这里会变 RUNNING
```

这条对**验证**很关键：人不在机器跟前时，可以靠这个节点判断"声音到底有没有送出去"。

---

## 二、目标与非目标

| | 内容 |
|---|---|
| **目标** | 拿到一句文字，能在 3 秒内从音箱里说出来；失败时界面能看出原因 |
| **非目标（本期）** | 流式合成、多音色实时切换、离线 TTS、边生成边播 |

---

## 三、链路

```
L2 拿到回答文本
   │
   ├─ 查缓存（同一句话不重复请求）
   │    命中 → 直接播
   │
   ├─ 未命中 → POST /v1/audio/speech
   │            ↓ 约 2 秒
   │          MP3 字节（约 50KB）
   │            ↓
   │          写入 cache/tts/<hash>.mp3
   │            ↓
   └─ MediaPlayer.setDataSource(文件) → prepare → start
                    ↓
              播完 → onCompletion → 删文件 / 更新状态
```

---

## 四、六个关键决策

### D-1 · 播放方式：`MediaPlayer` + 临时文件

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| **MediaPlayer + 文件** | 系统自带，MP3 解码免费，代码最少 | 要先落盘（50KB） | ✅ **选它** |
| `AudioTrack` 裸播 PCM | 延迟最低 | **MP3 要自己解码**，得引第三方库 | ❌ |
| ExoPlayer | 功能全 | 多几 MB 依赖，内存吃紧 | ❌ |
| `MediaPlayer` 直接播 URL | 不落盘 | 设备端流式缓冲不稳，且无法重播 | ❌ |

**落盘位置**：`context.getCacheDir()/tts/`，播完即删；缓存的另存一份（见 D-2）。

### D-2 · 缓存：按文本 hash，LRU 20 条

- key = `md5(text + voice + speed)`
- 上限 **20 条**（约 1 MB），超了删最旧的
- 价值：常用语（"好的""稍等""网络不好"）秒回；重复问同一句不重复花 2 秒

> 这是**抵消 2 秒延迟最便宜的手段**。

### D-3 · 单实例 + 打断

- 同一时刻只允许一个播放
- 新播报到来 → 立刻 `stop()` 旧的 → 播新的
- 对外接口带 `interrupt` 参数（默认 true）

### D-4 · 音频焦点

播放前 `requestAudioFocus(STREAM_MUSIC)`，播完 `abandon`。
理由：原厂语音链、蓝牙、DLNA 都可能同时在用音频，不申请焦点会互相踩。

### D-5 · 失败降级（三级）

```
① TTS 请求失败 → ② 用缓存里最近一条"兜底短语"（内置 chime）
                → ③ 都不行 → 只显示文字 + 状态栏标记「TTS 不可用」
```

**永远不弹窗报错**——音箱在客厅，弹窗没人看。

### D-6 · 延迟优化：分句（二期）

2 秒合成 + 1 秒 LLM = 3 秒才开口，偏慢。二期做：

```
LLM 回答按句切 → 第一句先合成先播 → 后面几句并发合成
```

本期先不做，但**接口要留好**（`speak(String)` 而不是 `speak(List)`，内部再拆）。

---

## 五、对外接口

### 5.1 内部（L2 内）

```java
interface TtsPlayer {
    void speak(String text, boolean interrupt, Callback cb);  // 异步
    void stop();
    String status();     // idle | synthesizing | playing | error:xxx
}
```

### 5.2 HTTP（给网页和调试用）

```
POST /api/v1/tts        {"text":"..."}  →  {"ok":true,"cached":false,"costMs":2100}
GET  /api/v1/tts/status                  →  {"state":"playing","durationMs":3200}
```

**为什么单独开一个 `/tts`**：网页上那个「试听一句」按钮要用它，
而且调试 TTS 时不用每次都走一遍 LLM。

---

## 六、状态机

```
idle ──speak()──► synthesizing ──成功──► playing ──播完──► idle
                       │                    │
                       │失败                │被打断
                       ▼                    ▼
                    fallback ──► idle      idle
```

`status()` 对外暴露的四个值：`idle` / `synthesizing` / `playing` / `error:<原因>`。

---

## 七、延迟预算（实测值推算）

| 环节 | 时间 | 备注 |
|---|---|---|
| 用户说完 → LLM 返回 | 0.5~1.0 s | GLM-4.5-Flash 实测 |
| LLM 返回 → TTS 合成完 | **~2.0 s** | 实测 51KB 用了 1.96s |
| 落盘 + MediaPlayer prepare | ~0.2 s | 本地文件 |
| **合计到"开口"** | **≈ 2.7~3.2 s** | |

**这个数偏慢**（人的耐心阈值大约 2 秒）。三个缓解手段，按性价比：

1. **缓存常用语**（D-2）：命中时 0.2 秒出声
2. **先说一句"嗯，我想想"**：立刻播缓存里的短语，掩盖后面的等待
3. **分句并发**（D-6，二期）

---

## 八、存储与清理

| 项 | 策略 |
|---|---|
| 临时文件 | 播完立即删 |
| 缓存 | 上限 20 条 / 1 MB，LRU |
| 启动时 | 清空 `cache/tts/` 下的临时文件（防崩溃残留） |
| `/data` 余量 | 708 MB，不构成约束 |

---

## 九、怎么验证"真的出声了"（人不在跟前也能判）

不能靠耳朵，所以用三条客观证据：

| # | 证据 | 怎么取 |
|---|---|---|
| **1** | `MediaPlayer` 回调 | 日志里出现 `onPrepared duration=3200` 和 `onCompletion` |
| **2** | **硬件播放节点在跑** | 播放期间 `cat /proc/asound/card2/pcm0p/sub0/status` → `state: RUNNING` |
| **3** | AudioFlinger 有活跃输出 | `dumpsys media.audio_flinger` 里 output thread 出现 active track |

> 第 2 条是最硬的：**只有真的往 codec 推了数据，这个节点才会 RUNNING**。

**验证脚本的思路**（后续写成 `tools/verify-audio.py`）：

```
1. 触发播报
2. 每 200ms 采一次 card2 的 status，记录出现 RUNNING 的区间
3. 播报结束后核对：区间长度 ≈ MediaPlayer 报的 duration
4. 三条都对上 → 判定"确实出声"
```

---

## 十、风险与备选

| 风险 | 概率 | 备选 |
|---|---|---|
| `MediaPlayer` 在这台机器上不出声 | 低（用户先按能出声推进） | 改 `AudioTrack` + 内置 MP3 解码（jsvg/其他轻量库）；或把播报交给 L1 的原厂 TTS |
| TTS 服务 2 秒太慢 | 中 | 缓存 + 预置短语 + 分句（D-6） |
| TTS 服务挂了 | 中 | 三级降级（D-5），至少 chime 能响 |
| MP3 24kHz 单声道设备不支持 | 低 | 让服务端换 44.1kHz；或本地重采样 |
| 与蓝牙/DLNA 抢音频 | 中 | 音频焦点（D-4） |

---

## 十一、实施步骤（下一步做什么）

| 步 | 内容 | 验收 |
|---|---|---|
| 1 | 把 TTS 地址/Key/音色写进设备配置（**不进 git**） | `/api/v1/config` 能读回（Key 打码） |
| 2 | `TtsPlayer`：请求 → 落盘 → 播放 | 日志有 `onPrepared` + `onCompletion` |
| 3 | 播放期间采 `card2` 状态 | 出现 `RUNNING`，时长对得上 |
| 4 | 接进对话链路（回答完自动播） | 网页发一句，设备出声 |
| 5 | 缓存 + 三级降级 | 断网时仍有 chime |
| 6 | `/api/v1/tts` + 网页「试听」按钮 | 网页点一下就能听到 |

**第 2、3 步就能判定 V1 通过与否**，之后才做 4~6。

---

## 十二、待确认

| # | 问题 | 影响 |
|---|---|---|
| Q1-a | 你的 TTS 支持哪些 `voice` / `model` 值？ | 音色选择器要列什么 |
| Q1-b | 支不支持 `speed` 参数？ | 语速调节能不能做 |
| Q1-c | 单次 `input` 上限多少字？ | 长回答要不要分句 |
| Q1-d | 并发请求有限制吗？ | 分句并发方案能不能用 |

> 现在用的是 `model=tts-1`、`voice=alloy`，能出结果；不确定服务端是否真的按这两个参数区分。
