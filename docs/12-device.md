# 设备档案（当前状态，2026-10-31 实测）

> 这里只写**现在**这台机器是什么样。历史形态（第三方 MOD 时期的 8080 配置页、
> 原厂 26 个私有接口）见 `archive/device-mod-era.md` 与 `archive/original-capabilities.md`。
> 数据全部来自 `adb shell` 与 `/api/v1/status`，不是网上抄的。

---

## 1. 硬件 / 系统

| 项 | 值 | 怎么确认 |
|---|---|---|
| 型号 | `rk322x-box`（斐讯 R1） | `getprop ro.product.model` |
| 芯片 | Rockchip RK3229，armv7 32 位 | —— |
| Android | `5.1.1`（API 22） | `getprop ro.build.version.release` |
| 构建 | `LMY49F release-keys` | `getprop ro.build.display.id` |
| 主机名 | `Phicomm_R1_E235` | `getprop net.hostname` |
| MAC | `<音箱MAC>` | 路由器客户端列表 |
| 内存 | 总 **490 MB**（501,444 kB），空闲 **7~10 MB** | `cat /proc/meminfo` |
| 存储 | `/data` 约 708 MB 可用 | `df /data` |
| ABI | 只有 `armeabi-v7a` | APK 只带这一个 |

> 内存是这台机器最硬的约束：**任何「整段读进内存」的写法都可能直接 OOM。**
> 所以音频边读边写盘、HTTP 响应体有 2MB 上限（见 `docs/21-code-review.md`）。

---

## 2. 现在装了什么

| 包 | 角色 | 说明 |
|---|---|---|
| `com.phicomm.speaker.device` | **L1** | Janus 顶替原厂包；负责唤醒、ASR、灯环，把识别文本转发给 L2 |
| `com.r1.brain` | **L2** | 我们自己的业务 App：对话、TTS、音乐、闹钟、8686 控制台 |
| `com.phicomm.speaker.player` | 原厂播放器 | **没动**，DLNA / AirPlay / 蓝牙播放仍归它 |
| `com.phicomm.speaker.launcher` / `netctl` / `airskill` | 原厂 | 桌面、配网、AirPlay 投屏 |
| `com.phicomm.speaker.ijetty` | 原厂（159 KB） | 小服务，未动 |

**已卸载的系统垃圾**（用 `pm uninstall --user 0 -k`，可从 `/system/app` 恢复）：

`productiontest`（产测）、`otaservice`（OTA）、`systemtool`、`bugreport`、`exceptionreporter`。
完整清单与恢复命令见 `docs/20-device-ops.md` §C。

---

## 3. 监听端口（当前）

| 端口 | 谁 | 说明 |
|---|---|---|
| **8686** | L2 控制台 | HTTP + REST，唯一对外入口 |
| 5555 | adbd | 调试用，**极易假死**，见 `docs/11-troubleshooting.md` |
| 5353/udp、1900/udp | 系统 | mDNS / SSDP（DLNA 发现） |
| 其余系统端口 | 系统 | 不需要关心 |

> 第三方 MOD 时期的 **8080 配置页已经不存在**（那是 MOD 自己注入的 HTTP 服务，
> 顶替原厂包之后就没了）。现在只有 8686 一个入口。

---

## 4. 关键路径

| 东西 | 路径 |
|---|---|
| 原厂底包（L1 的底） | 需自行从设备拉取到 `l1/vendor/Unisound_original.apk`（不入库），SHA-256 `a0548242414666d27df9b099e85ab69697cc5c1599f019ce34dd2460f8841bd7` |
| 现役 L1 | `/data/app/com.phicomm.speaker.device-*/base.apk`（Janus，约 31 MB） |
| 现役 L2 | `/data/app/com.r1.brain-*/base.apk`（约 290 KB） |
| 原厂播放器 | `/system/app/EchoService/EchoService.apk` |
| 四麦配置 | `/system/usr/uni_4mic_config/` |
| 临时目录 | `/data/local/tmp/`（**别在这留大文件**） |
| L2 缓存（TTS） | `/data/data/com.r1.brain/cache/tts`（LRU 40 条） |
| 音乐目录 | `/sdcard/Music`（网页音乐页扫描这里） |

---

## 5. 不能碰的

| 禁止 | 原因 |
|---|---|
| 改 `/system` | 没有 root，硬改会变砖；系统包只能用 `pm uninstall --user 0 -k` 卸载（可恢复） |
| 升级 Android / 刷固件 | ADB 是唯一入口，刷坏没救 |
| `adb root` | 实测无效（user 版 adbd 没编 `ALLOW_ADBD_ROOT`） |
| `adb install` | 这台机器必卡死，用 `push` + 设备端 `pm install` |
| 连续轰炸 `pm` 命令 | 会把 adbd 搞挂（已发生多次） |

---

## 7. 应用清单与瘦身记录（2026-10-29 实测）

> 操作步骤与恢复命令见 `docs/20-device-ops.md` §C；这里只记「当时量到了什么」。

**总体**：32 个包、apk 合计 76.1 MB；`/data` 剩 708.9 MB，`/system` 剩 366.5 MB。
**空间从来不是瓶颈，内存才是。**

### 7.1 前房主留下的电视 App（已清）

| 进程 | RSS | 是什么 |
|---|---:|---|
| `com.wukongtv.wkhelper` | 33.5 MB | 悟空遥控器（电视盒子用） |
| `com.droidlogic.mediacenter` | 32.4 MB | 晶晨媒体中心 |
| `com.xiaobaifile.tv` | 28.5 MB | 小白文件管理器 |
| `com.xiaobaifile.xbpush` | 26.6 MB | 同包另一个进程 |
| `aria2c`（数据目录里） | 1.5 MB | 还在跑的下载守护进程 |

R1 没有屏幕，这些对我们毫无用处。

### 7.2 三条实测结论

1. `am force-stop` 只是临时：MemFree 8,148 KB → 16,232 KB，重启又回来。
2. `pm disable` **走不通**：`SecurityException: attempt to change component state from uid=2000`。
3. 真正有效的只有 `pm uninstall --user 0 -k <包>`（保留 `/data`）：MemFree **5,924 KB → 25,648 KB**。

> ⚠️ `-k` 只保留数据目录，**APK 会被删**，恢复要重新 `pm install`。
> `com.wukongtv.wkhelper` 当年没备份就被删了，这是操作失误，记在这里当教训。

### 7.3 两个值得警惕的系统包

| 包 | 大小 | 注意点 |
|---|---:|---|
| `com.phicomm.speaker.otaservice` | 421 KB | OTA 服务仍在跑，理论上把版本改回原厂；本机已卸载。若某天我们的包「自己消失」，先怀疑它 |
| `com.phicomm.speaker.ijetty` | 159 KB | 系统自带 Jetty Web 服务器（说明 ROM 原本就打算跑 Web 服务）；未动 |

---

## 8. 历史资料（不再维护，但要查证据时看）

| 文档 | 里面有什么 |
|---|---|
| `archive/device-mod-era.md` | 第三方 MOD 时期的完整设备快照：8080 配置页 6 个标签、签名变化、启动日志噪音 |
| `archive/original-capabilities.md` | 原厂 26 个私有接口盘点与可行性判断（L1 设计依据） |
| `archive/mod-apk-analysis.md` | MOD 的 Janus 结构拆解、可借鉴点 |
| `archive/open-questions-2026-10-29.md` | 当时的 11 个待决问题，现在基本都已被实现回答 |
| `archive/code-review-2026-10-30.md` | 第一轮代码审查记录（后续见 `docs/21`） |
