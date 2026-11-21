# 文档索引

> **新会话先读 `../START-HERE.md`。** 编号固定不重排：**缺号表示已归档**（见文末），
> 这样代码注释和旧提交里的 `docs/NN` 链接不会指向错误内容。

当前维护中的文档只有 **16 篇**，分四组。粗体是必读。

> **只读五篇就够干活**：`AGENTS.md`（禁令与已知坑）→ `.agents/skills/r1-dev/SKILL.md`
> （怎么做 + 排障下钻）→ `22-call-chains.md`（链路）→ `24-self-hosted-asr.md`（语音）→
> `21-code-review.md`（复盘：踩过的坑）。
> 其余文档按需查；**新知识优先并入上面几篇，不要新建 md**（见 `AGENTS.md`「文档纪律」）。

---

## 一、先理解（改代码前）

| 文档 | 讲什么 |
|---|---|
| **`22-call-chains.md`** | **调用链路图：说一句话/点一下按钮之后走了哪些类、调了哪个接口、走不走外网、失败怎么办** |
| `01-architecture.md` | 为什么分 L1/L2/L3，每层负责什么 |
| `07-protocol.md` | L1 ↔ L2 桥协议（`com.r1.bridge` 事件 + `com.r1.bridge.cmd` 命令，v2 实装版） |

## 二、要做什么（产品与设计）

| 文档 | 讲什么 |
|---|---|
| **`03-features.md`** | **功能规格：触发/前提/流程/失败/边界/验收，附语音指令表** |
| `04-ui.md` | 界面设计与视觉规范（实现以 `web/` 为准） |
| `05-voice.md` | 播报链路、分句并发、三种语音模式的取舍 |
| `06-scheduler.md` | 定时任务：为什么最终用 15 秒轮询、数据模型、失败处理 |
| `09-decisions.md` | 决策记录（背景 → 选项 → 决定 → 证据），要推翻某个结论先看这里 |

## 三、怎么对接（接口）

| 文档 | 讲什么 |
|---|---|
| **`08-http-api.md`** | 对外 HTTP 接口清单（30 条，以代码为准）+ 远程方案 |
| `15-glossary.md` | 术语表（L1/L2、Janus、四麦、闸门…） |

## 四、怎么上线、测试与排障

| 文档 | 讲什么 |
|---|---|
| **`20-device-ops.md`** | **部署与运维：两条用户路径、装包、配置、可卸载清单与恢复、验收清单** |
| **`23-testing.md`** | **测试与自检：四层自检矩阵、常见「假绿」、改动类型 → 必跑项、测试数据卫生** |
| `11-troubleshooting.md` | 出问题怎么办：adb 连不上/假死、`error: closed`、装坏了、回退、签名冲突 |
| `12-device.md` | 设备档案（当前）：硬件、装了什么、端口、关键路径、不能碰的 |
| `21-code-review.md` | 全量复核记录：修了什么、为什么有些没修、验证输出 |
| `19-r1-architecture-and-l1-build.md` | 原厂运行逻辑与 L1 的构建方式（要重打 L1 时看） |

> **三类坑分别在哪**：设计/实现坑 → `21`（附不修的理由）；测试坑与「假绿」→ `23`；
> 部署与设备坑 → `20` + `11`。

---

## 归档（`docs/archive/`，**过程记录与证据，不要当现状看**）

| 文档 | 为什么在归档里 |
|---|---|
| `plan-v0.md` | 最初的两阶段计划，已被实现取代 |
| `original-capabilities.md` | 原厂 26 个私有接口盘点（L1 设计依据） |
| `open-questions-2026-10-29.md` | 当时的 11 个待决问题，现基本都已实现回答 |
| `code-review-2026-10-30.md` | 第一轮代码审查（后续见 `21-code-review.md`） |
| `mod-apk-analysis.md` | 第三方 MOD 的 Janus 结构拆解 |
| `ui-design-prompt.md` | 早期「投喂 UI 生成工具」的提示词（已完成使命） |
| `device-mod-era.md` | MOD 时期的设备快照：8080 配置页、签名变化、启动日志噪音 |
| `inventory-2026-10-29.md` | 当时的 32 个包体积清单与瘦身前后内存对照（结论已并入 `12-device.md`） |
| `architecture-v0.md` | 早期架构草案，假设已被实验否定 |
| `features-v1.md` | 早期功能清单，已被 `03-features.md` 取代 |
| `project-comparison.md` | 早期同类方案的调研比对（结论已进 `09-decisions.md`） |
| `ecosystem-research.md` | 早期调研笔记 |
| `where-8080-comes-from.md` / `webui-8080-api.md` | 8080 端口归属彻查与那套接口清单 |
| `correction-janus-not-resign.md` | 「这个 MOD 不是重签」的更正 |
| `plan-tts-playback.md` | 播报方案第一版，已升级为 `05-voice.md` |
| `optimization-backlog.md` | 最初头脑风暴 |
| `evidence-*.md` | 三个关键实验的原始记录（**证据，不可删**） |

---

## 仓库结构

```
phicomm-r1/
├── START-HERE.md          新会话入口（5 分钟）
├── README.md              仓库说明
├── AGENTS.md              工程规范与坑（所有 Agent 必读）
├── .agents/skills/r1-dev/ 开发技能：构建/部署/验证/排障
├── docs/                  本文档目录
│   ├── 03/04/05/06        产品与设计
│   ├── 07/08              协议与接口
│   ├── 11/12/20           运维与设备
│   ├── 19/21/22           实现、审查、链路
│   └── archive/           过程记录与证据
├── web/                   控制台前端源码 + 生成页 + 自检
├── l2-app/                L2 应用源码（可编译）
├── l1/                    L1 注入桥 + 构建脚本（原厂底包需自行准备，不入库）
├── tools/                 脚本与探针
└── artifacts/             样本、备份（不进 git 的大件）
```
