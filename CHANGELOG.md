# 更新日志 (CHANGELOG)

本项目遵循 [Semantic Versioning (SemVer)](https://semver.org/lang/zh-CN/) 语义化版本规范。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

## [v0.5.0] - 2026-11-27

### Added
- **桌面端 Material Design 3 全面重构**：引入双主题语义 Design Token（`web/src/design-system.css`），统一五级按钮层级、M3 Switch 开关、可拖拽 Range Slider 滑块及全局键盘快捷键（`/` 聚焦、`Esc` 清空、`Ctrl+S` 审查配置）。
- **原生 `<dialog>` 模态组件引擎**：`r1-engine.js` 封装 `openModal` / `confirmModal`，全面替换连接音箱、访问令牌、云音乐 Cookie、Songloft 配置、添加音频流及危险操作的原生 `prompt()` / `confirm()`。
- **自适应桌面壳层**：支持 240px 侧栏抽屉与 76px 图标轨道（Drawer ↔ Rail）自适应与手动折叠，主内容区限宽 `1360px` 并精简顶栏冗余状态。
- 补齐控制台黑金微光风格 `favicon.ico` 与相关 Web 图标链（桌面端与移动端多路径支持）。
- 建立 GitHub Actions 静态页面与样式验证流水线。
- 新增 `.gitattributes` 与 `.editorconfig` 统一跨平台换行与缩进。

### Changed
- **桌面端四大页面布局重构**：首页收拢为单屏 7:5 工作台 + 底部单行遥测带；对话页改为全视口自适应流式布局；音乐页改为左播控歌词 / 右统一媒体库双栏；设置页改为左侧 6 组二级粘性目录 + 右侧限宽单列结构化表单（和风 JWT 凭据收纳至折叠区）。
- **清透新拟态主题修复与升级**：修复主卡片未命中浮雕规则导致的灰脏渐变、弹窗遮罩过淡、浅色白边隐身与按钮反向光源问题，为卡片与控件补充冷灰发丝兜底边界。
- **字体与可读性优化**：废弃 8px/9px 微缩中文与中文等宽滥用，补齐中文字体回退栈与 `:focus-visible` 焦点环；修复日志 `ERR`/`WARN`/`INFO` 语义分级配色。
- 强化 `.gitignore` 凭据拦截与图标例外规则。
- 按项目规范重构 `AGENTS.md`，清除模板注入并为每条硬规则增加可执行验证命令。

## [v0.4.0] - 2026-11-16

### Added
- **控制台多端重构**：分离桌面机架式（`/desktop/*.html`）与移动紧凑底栏（`/mobile/*.html`）双布局，支持 User-Agent 自动分流重定向。
- **黑金主题与清晰主题**：落地 Luxury Gold Glow 视觉设计，支持动态换肤与无损切换。
- **整合 ASR 网关**：对接自建 ASR 语音识别网关，增加令牌与配额状态提示。
- **客户一键安装包**：提供 `一键安装包/一键安装.bat` 免环境全自动交付工具。

### Changed
- 音量调节指令安全化：超出 0~100 的音量指令直接拒绝，不再强制夹断到 100。
- 优化 ASR 代码结构，彻底清除旧版 8710 流式与置信度遗留实现。

### Fixed
- 修复控制台核心脚本两处缺陷，恢复离线与局域网页面展示稳定性。
- 修复隔空播放 (AirPlay) 开关后蓝牙扫描模式未自动恢复的问题。

## [v0.3.0] - 2026-11-06

### Added
- 引入 LLM Tools 工具机制（`Tools.java`），支持大模型意图调用并在设备端固定文案播报。
- 城市天气双源链路：和风天气专属 Host + `wttr.in` HTTP 免 Key 兜底。

## [v0.1.0] - 2026-10-31

### Added
- 初始落地 L1 硬件桥（Janus 顶替原厂包）+ L2 业务 App（`com.r1.brain`）+ L3 网页控制台三层架构。
- 麦克风唤醒转发、灯环私有协议桥接与 HTTP 8686 REST 服务。
