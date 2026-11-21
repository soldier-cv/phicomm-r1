# 定时任务 / 闹钟 —— 设计与实测

> 你问的"R1 本身有闹钟吗"——**有，但不能用**。下面是实测依据和我们的方案。

> **实现现状（v0.4.0，以代码为准）**：最终没有用 `AlarmManager`，而是
> `AlarmScheduler` 在 MainService 里每 **15 秒**比对一次北京时间的 `HH:mm`。
> 原因：这台机器的 L2 是常驻前台服务、全年通电，轮询实现最简单且不受系统省电策略影响；
> `AlarmManager` 的收益（精确唤醒）在这里没有意义，反而要多处理重启重建与 Doze 兼容。
> 代价是**最多晚 15 秒触发**，对"叫我起床"完全够用。下面的 AlarmManager 讨论保留作背景。

---

## 一、R1 原生到底有什么（实测）

### 1.1 没有独立闹钟 App，也没有铃声资源

```
pm list packages | grep -iE 'alarm|clock|memo|remind'   → 空
ls /system/media/audio/alarms/                          → 目录不存在
```

**整个 ROM 里没有任何铃声文件**（`/system/media/audio` 都没有）。
这意味着 `RingtoneManager` 拿不到东西——**闹钟要响，声音必须我们自己带**。

### 1.2 但云知声有一套备忘/提醒

```
$ dumpsys activity services com.phicomm.speaker.device
  ServiceRecord{... com.unisound.vui.handler.session.memo.ring.MemoRingingService}   ← 在跑

$ run-as ... ls databases/
  MemoDB.db   24,576 B
```

把 `MemoDB.db` 拉下来看表结构（`localmemo`）：

```sql
CREATE TABLE localmemo (
  id, memoid, memocontent, memotype,          -- 内容 / 类型（'alarm'）
  memoyear, memomonth, memoday, memohour, memominute, memosecond,
  isenabled, isrepeat, repeattype,            -- 'OFF' / 每天 / 工作日…
  issnoozed, snoozehour, snoozeminute, snoozeseconds,
  ringing, memotone,                          -- 铃声名 / content:// URI
  countdown, timegap, executecount, memocreatetime
)
```

里面只有 **2 条 2025 年的旧记录**（前房主设的），而且都是 `isenabled=0`、`repeattype=OFF`。

**所以：能力是完整的**（内容、重复、贪睡、铃声都支持），只是没在用。

### 1.3 系统的 AlarmManager 是健康的

```
$ dumpsys alarm
RTC_WAKEUP #2: Alarm{... com.phicomm.speaker.bugreport}
RTC_WAKEUP #1: Alarm{... com.phicomm.speaker.systemtool}
nowRTC=1793252874633  nowELAPSED=+6h9m16s
```

系统自己的服务在用 `RTC_WAKEUP` 定时唤醒，说明**这条路是通的**。
而且 Android 5.1 **没有 Doze**（Doze 是 6.0 才有的），`setExact` 到点就响，不会被省电策略推迟。

---

## 二、结论：原生的不能用，我们自己做

| 方案 | 判断 | 原因 |
|---|---|---|
| 用云知声的 `MemoRingingService` | ❌ | 入口是云知声 **NLU 意图**（云已死）。语音"提醒我"根本到不了它。 |
| 直接往 `MemoDB.db` 写记录 | ❌ | 服务在内存里缓存了状态，背后改 DB 不一定会生效；而且它的 schema 只支持"响铃"，我们要的动作（播报/放歌/开灯）它表达不了。 |
| **L2 自己实现（当前：15 秒轮询）** | ✅ | 不依赖 L1、随时能改；设备常年通电 + 前台服务常驻，轮询够用且最好排查。 |

> **决定：定时任务归 L2。** 写进 `09-decisions.md` 作为 D-13。

---

## 三、我们的方案

### 3.1 数据模型

一条任务 = **什么时候** + **做什么**。

```json
{
  "id": "t-1793252874",
  "label": "起床",
  "enabled": true,
  "time": { "hour": 7, "minute": 0 },
  "repeat": "weekdays",          // once | daily | weekdays | weekends
  "action": {
    "type": "speak",             // speak | play | scene | volume | light
    "params": { "text": "早上好，今天多云，18 到 26 度。" }
  },
  "snooze": { "enabled": true, "minutes": 5, "max": 3 },
  "createdAt": 1793252874000,
  "lastFiredAt": null
}
```

**动作类型**（这是和原厂最大的区别——原厂只会响铃）：

| type | 做什么 | 依赖 |
|---|---|---|
| `speak` | 播一段话（走 TTS） | TTS 端点 |
| `play` | 播一首歌 / 一个歌单 | 音乐源 |
| `scene` | 执行一串动作（见 F11） | 各自动作 |
| `volume` | 设音量 | 本地 |
| `light` | 灯效 | L1 |

> 注意：**每个动作都允许"响铃兜底"**——见 3.4。

### 3.2 触发

```java
AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
```

- 用 `RTC_WAKEUP`：设备休眠也能醒（虽然 R1 常年插电，但保险）
- 用 `setExact` 而不是 `set`：5.1 上没有 `setExactAndAllowWhileIdle`，`setExact` 就够
- 每次只挂**最近的一条**；响完再挂下一条（避免 20 个 PendingIntent）
- **开机重建**：`BootReceiver` 里读全部任务，重新挂

### 3.3 重复与边界

| 项 | 规则 |
|---|---|
| `once` | 响完 `enabled=false` |
| `daily` | 响完挂明天同一时刻 |
| `weekdays` | 周一到周五（**按法定节假日判断**；目前未接节假日数据，按纯周一~周五处理） |
| `weekends` | 周六周日 |
| 上限 | **20 条**（内存与 UI 都够用） |
| 时间精度 | 分钟级（秒固定 0） |

### 3.4 错过与兜底（两条硬规则）

**规则一：错过不补。**

设备断电错过了 7:00 的闹钟，开机后**不补响**。
理由：半夜插电突然响一声，比漏一次闹钟更烦。

**规则二：闹钟必须有声音。**

这台机器没有系统 TTS，也没有任何铃声文件。所以：

```
内置 assets/chime.wav（一个 2 秒的提示音，约 40 KB）
      ↓
闹钟触发时：先播 chime，再执行动作
      ↓
TTS 没配 / 网络不通 / 动作失败 → 至少还有 chime
```

> 这是从实测推出来的：**不能假设"总有人能说话"**。

### 3.5 贪睡

响的时候按键（或网页点「再睡 5 分钟」）：

```
snooze.enabled = true
     ↓
挂一个 +5 分钟的临时 Alarm（不计入 20 条上限）
     ↓
最多 3 次，之后不再给贪睡
```

### 3.6 语音怎么设

"明早七点叫我" → **本地解析，不调 LLM**（要快，且 LLM 容易把时间理解错）。

第一期做规则解析：

| 说法 | 解析结果 |
|---|---|
| 七点叫我 / 7 点 | 今天 07:00，若已过则明天 |
| 明早七点 | 明天 07:00 |
| 下午三点 | 今天 15:00 |
| 每天七点 | repeat=daily |
| 工作日八点 | repeat=weekdays |
| 晚上十点半 | 今天 22:30 |

解析不出来再丢给 LLM，让它返回 JSON（兜底，不常用）。

---

## 四、UI 规划（新增「任务」页）

```
┌──────────────────────────────────────┐
│  定时任务                    [+ 新建] │
├──────────────────────────────────────┤
│  07:00   起床                        │
│          工作日 · 播报天气            │
│          ● 已启用        [开关]       │
├──────────────────────────────────────┤
│  22:30   睡前                        │
│          每天 · 音量 10 + 播白噪音    │
│          ○ 已停用        [开关]       │
└──────────────────────────────────────┘
```

新建时：

```
时间  [07] : [00]
重复  (仅一次) (每天) (工作日) (周末)
动作  [播报 ▾]  [内容……]
      或 [播放 ▾] [歌单……]
      或 [场景 ▾] [睡前……]
贪睡  [✓] 5 分钟后，最多 3 次
```

**为什么不放在首页**：定时任务是"设一次就不动"的功能，
按我们的分层原则（常用在第一屏），它属于二级页。

---

## 五、和别的东西怎么配合

| 关系 | 说明 |
|---|---|
| **与场景（F11）** | 定时任务可以触发场景，场景也可以被语音触发 |
| **与语音** | 语音设闹钟走本地解析；到点时如果有语音在播，**先播完再响**（或打断，可配） |
| **与 L1** | 动作里的 `light` 需要 L1；其它动作 L2 自己就能做 |
| **与 L3** | 任务存在 L2 的 SharedPreferences；L3 可以备份/同步（以后） |

---

## 六、实施步骤

| 步 | 做什么 | 验收 |
|---|---|---|
| 1 | `TaskStore`（SharedPreferences + JSON 数组） | 增删改查跑通 |
| 2 | `Scheduler`（setExact + 下一条链式挂载） | 设一条 1 分钟后的，准点触发 |
| 3 | `AlarmReceiver`（触发 → 播 chime → 执行动作） | 能听见声音 |
| 4 | `BootReceiver` 重建 | 重启后任务还在 |
| 5 | `/api/v1/tasks` CRUD | 网页能增删 |
| 6 | 「任务」页 UI | 能设、能开关 |
| 7 | 语音时间解析 | "明早七点"能设上 |

**第 2 步就能验证最关键的假设**：这台机器上 `setExact` 到底准不准。
建议第一次实测设 1 分钟、10 分钟、1 小时各一条，记录实际触发时刻与误差。

---

## 七、还没定的

| 问题 | 说明 |
|---|---|
| 节假日数据源 | 目前未接；要不要内置一张表（`weekdays` 现在按纯周一~周五算） |
| 闹钟响时能否被语音打断 | 需要 L1（按键/唤醒） |
| 多条同时到点 | 排队播还是一个接一个 |
| 是否要"日出日落"触发 | 需要经纬度，先不做 |
