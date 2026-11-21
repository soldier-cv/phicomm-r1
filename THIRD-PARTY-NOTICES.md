# 第三方材料说明

本仓库自己的代码是 **MIT**（见 `LICENSE`），且**不包含任何第三方项目的源代码**。
下面说明原厂底包的来源，以及运行时会访问的外部服务。

---

## 1. 原厂底包（不随仓库分发）

| 项 | 说明 |
|---|---|
| 文件 | `l1/vendor/Unisound_original.apk`（需自行准备，已在 `.gitignore`，不进入本仓库） |
| 版权 | 归原厂 / 云知声所有，**我们不对它主张任何权利** |
| 用途 | L1 的构建底包：顶替 `com.phicomm.speaker.device` 必须以它为底，`l1/build.ps1` 会校验 SHA-256（`a0548242…f8841bd7`） |
| 获取方式 | 从你自己的设备拉取：`adb pull /system/app/Unisound/Unisound.apk l1/vendor/Unisound_original.apk` |
| 发布产物 | `l1/build/r1brain-l1.apk` 是「原厂包 + 我们注入的桥」的组合包，原始部分仍归原厂所有，仅限在你自己的设备上使用 |

---

## 2. 运行时会访问的外部服务

以下服务只在用户配置后访问，本仓库不包含它们的任何代码或数据：

大模型（OpenAI 兼容接口）、TTS（OpenAI 兼容 `/v1/audio/speech`）、
网易云音乐、QQ 音乐、和风天气、wttr.in、Home Assistant（用户自己的实例）。

各服务的使用受其自身条款约束；本项目只做请求转发，不缓存内容、不二次分发。

---

## 3. 构建工具链

Android SDK（`adb` / `d8` / `aapt2` / `zipalign` / `apksigner`）与 Node.js 仅用于构建和部署，
按各自许可使用，不随产品分发。
