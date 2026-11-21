# 斐讯 R1 智脑项目 (Phicomm R1) — Agent 工作规约

## 0. 项目定位
- 一句话说明：斐讯 R1 音箱（rk322x-box Android 5.1.1）重做语音助手（L1 硬件桥底座 + L2 业务 App `com.r1.brain` + L3 静态 Web 控制台）。
- 仓库地址：`https://github.com/soldier-cv/phicomm-r1`
- 默认分支：`main`

## 1. 运行环境事实
- **硬件与系统**：Rockchip RK3229 (armv7 / 32-bit)，Android 5.1.1 (API 22)；物理内存 512MB（可用仅约 10MB ~ 25MB）。
- **端口事实**：L2 内嵌轻量 HTTP 服务端口 `8686`（提供 REST API 与 Web 控制台）；设备调试 adbd 端口 `5555`。
- **路径与文件**：
  - L2 应用包名：`com.r1.brain`，数据目录 `/data/data/com.r1.brain/`。
  - 本地配置存储：`/data/data/com.r1.brain/shared_prefs/r1_config.xml`。
  - 原厂包路径：`/system/app/Unisound/`（底包只读，禁止修改）。
- **依赖真源**：
  - 麦克风与硬件桥：L1 Janus 桥接包（顶替原厂 `com.phicomm.speaker.device`）。
  - 语音识别 ASR：自建 ASR 语音识别网关（默认端口 8710，如 `http://<网关IP>:8710`），原厂云知声云端已下线。
  - 天气服务：和风专属 Host 优先，`wttr.in` HTTP 免 Key 兜底。
  - 控制台资源：静态页面位于 `web/`，构建后自动打包进 L2 APK assets。
- **生产入口**：
  - 控制台桌面端：`http://<音箱IP>:8686/desktop/home.html`（黑金机架式布局）。
  - 控制台移动端：`http://<音箱IP>:8686/mobile/home.html`（黑金紧凑底栏布局）。
  - 客户交付：`一键安装包/一键安装.bat`（Windows 自动化交付包）。

## 2. 硬规则（带可执行验证方式）
1. **界面文本纪律（§0.2）**：严禁在控制台 UI 堆砌开发需求说明、设计思路或「本功能实现/为了满足需求」等叙述，界面文案仅允许呈现用户所需的状态、操作与错误说明。
   - 验证命令：`grep -rn "本功能实现\|为了满足需求" web/` 必须 0 命中。
2. **麦克风独占性与喇叭播报闭环**：物理单麦克风被原厂 HAL 100% 独占，L2 严禁尝试自行采集麦克风；语音指令回复必须经过喇叭发声，不可仅用网页文字替代播报。
   - 验证命令：`grep -rn "AudioRecord" l2-app/src/` 必须 0 命中（仅 L1 允许桥接）。
3. **禁止破坏性生产操作**：禁止使用 `adb install`（低版本 Android 会因包管理器组合故障而卡死），必须使用 `adb push` + 远程 `pm install -r`；禁止向设备 `/system` 写入任何改动。
   - 验证命令：`grep -rn "adb install" tools/ 一键安装包/` 必须 0 命中。
4. **配置统一走原子接口，禁止手写字段映射**：应用配置修改一律走 `Config.update(ctx, mutator)` 进程锁内原子读改写，新增配置项必须在 `Config.applyJson` 与 `Config.toJson` 同步维护。
   - 验证命令：`grep -rn "\.edit().*commit()" l2-app/src/com/r1/brain/` 仅允许在 `Config.java` 内部出现。
5. **严禁引入重型第三方框架**：设备空闲内存仅 20MB 左右，L2 严禁引入 Spring Boot、Netty、OkHttp、Jackson、Gson 等外部重库，保持纯原生 Java SDK。
   - 验证命令：`grep -rn "import org.springframework\|import okhttp3\|import com.fasterxml" l2-app/src/` 必须 0 命中。
6. **测试音量安全红线**：严禁在测试中将音量调至 100%（夜间 15%、白天 20% 封顶），避免高分贝喇叭震损与噪声扰民。
   - 验证命令：检查调试请求中音量参数 `volume <= 20`。

## 3. 开发与验证流程
- **开工准备**：
  ```bash
  git fetch origin main && git pull --ff-only origin main
  git status
  ```
- **Web 控制台构建与校验**：
  ```bash
  node web/build-pages.js    # 生成 8 个静态页面与主题 CSS，并同步至 l2-app/assets
  node web/verify-pages.js   # 校验全部 8 个页面路由与结构完整性
  node web/verify-css.js     # 校验 CSS 类与图标可用性
  ```
- **L2 APK 编译（PowerShell 环境）**：
  ```powershell
  pwsh -NoProfile -ExecutionPolicy Bypass -File .\l2-app\build.ps1
  ```
  产物：`l2-app/build/r1brain.apk`（约 260KB），自动同步到 `一键安装包/apps/r1brain.apk`。
- **真机冒烟验证（每次部署后）**：
  - 检查状态：`curl -s http://<音箱IP>:8686/api/v1/status`（期望 `l1=true`）
  - 检查图标：`curl -sI http://<音箱IP>:8686/favicon.ico`（期望 200）
  - 检查对话：`curl -s -X POST http://<音箱IP>:8686/api/v1/chat -d '{"text":"现在几点"}' -H "Content-Type: application/json"`
- **收工检查**：
  ```bash
  git rev-list --left-right --count HEAD...origin/main   # 确保结果为 0 0
  ```

## 4. 已知坑
- **低版本 Android HTTPS 根证书缺失**：Android 5.1 根证书不认 Let's Encrypt，`wttr.in` 必须降级为 HTTP 协议访问。
- **设备端静默安装假成功**：`pm install` 成功前如果旧进程还在运行，可能并未卸载旧 APK 内存镜像；安装前必须显式 `am force-stop com.r1.brain`。
- **场景性排障手册索引**：详细硬件排障、语音调用链路图与完整测试矩阵下沉至以下文档，不在此赘述：
  - 排障手册：`.agents/skills/r1-dev/SKILL.md`
  - 部署与瘦身：`docs/20-device-ops.md`
  - 调用链路表：`docs/22-call-chains.md`
  - 测试矩阵：`docs/23-testing.md`

## 5. 需求台账 / 决策日志
- `2026-10-31`：完成 L1/L2/L3 三层解耦架构落地，确定 8686 端口与黑金微光视觉风格。
- `2026-11-03`：废弃原厂云知声云端 ASR 识别（恒回 609），确立全自建 ASR 语音网关架构。
- `2026-11-06`：引入 LLM Tools 工具链机制（`Tools.java`），实现本地播报意图扩展。
- `2026-11-16`：完成 Web 控制台桌面端与移动端双布局重构，支持 UA 智能分流。
- `2026-11-17`：对齐项目仓库标准，补齐黑金微光 favicon 与图标链，重构 AGENTS.md 规范。
