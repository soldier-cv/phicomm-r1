# 设备档案（2026-10-28 实测）

所有数据都是通过 `adb shell` 从机器上直接读出来的，不是网上抄的。

## 硬件 / 系统

| 项 | 值 | 命令 |
|---|---|---|
| 型号 | `rk322x-box` | `getprop ro.product.model` |
| 安卓 | `5.1.1` | `getprop ro.build.version.release` |
| 构建 | `LMY49F release-keys` | `getprop ro.build.display.id` |
| 主机名 | `Phicomm_R1_E235` | `getprop net.hostname` |
| CPU 架构 | `armeabi-v7a`（APK 只带这一个 ABI） | 看 `lib/` |

## 应用清单

MOD 装上之后，系统里同时存在两套同名包裹：

| 包名 | 路径 | 状态 |
|---|---|---|
| `com.phicomm.speaker.device` | `/data/app/com.phicomm.speaker.device-1` | **MOD**，33,064,967 B |
| `com.phicomm.speaker.device` | `/system/app/Unisound` | 出厂版，31,254,519 B，被上面的覆盖 |

安装后签名从 `[105ffce5]` 变成 `[38b25ad4]` —— 说明 MOD 是重新签名过的（证书 Subject 为 `CN=R1, O=Company, E=phicomm@phicomm.com`，自签名，2017 年签发、2045 年到期）。

### 随系统启动的 phicomm 相关进程

```
com.phicomm.speaker.productiontest   （产测）
com.phicomm.speaker.bugreport
com.phicomm.speaker.otaservice       （OTA）
com.phicomm.speaker.systemtool
com.phicomm.speaker.launcher
com.phicomm.speaker.device           （主体 + :remote）
com.phicomm.speaker.netctl           （配网）
com.phicomm.speaker.airskill         （AirPlay 投屏）
com.phicomm.speaker.exceptionreporter
com.phicomm.speaker.player           （+ :NetPlayer / :DlnaPlayer / :AirPlayer）
```

注意 `player` 里已经带了 **DLNA** 和 **AirPlay** 两个子进程，说明音源协议原生就有。

## 监听端口（`netstat -tln`）

| 端口 | 判断 | 备注 |
|---|---|---|
| 5555 | adb | 对局域网开放 |
| **8080** | **R1 Mod 配置网页** | HTTP 200，标题「斐讯R1配置 1.0.4」 |
| 6800 | 未知 HTTP 服务 | 访问 `/` 返回 404 |
| 7100 | TCP 服务 | 不响应 HTTP |
| 8090 | 未知 HTTP | 访问 `/` 返回 `404 text/plain` |
| 58888 | 未知 | 有大量 127.0.0.1 往它连 CLOSE_WAIT |
| 12104 / 52244 / 52266 / 38253 / 54566 / 38520 | 未知 | 待认领 |
| 5353 (udp) | mDNS | 局域网发现 |
| 1900 (udp) | SSDP/UPnP | DLNA 广播 |

> 待办：认领这些未知端口。可对照 `adb shell "cat /proc/net/tcp"` 拿 inode，再跟 `ls -l /proc/<pid>/fd` 对上进程。

## MOD 自带能力（配置网页 8080）

页面标题「斐讯R1配置 1.0.4」，六个标签页：

| 标签 | URL 路由 | 字段/功能 |
|---|---|---|
| R1功能配置 | `/` | 低音增强、氛围灯、蓝牙模式；发音人（`FEMALE`/`SWEET`/`MALE`/`CHILDREN`/`LZL`）、唤醒词、语速 0-100、音调 0-100、休眠 |
| 音乐源 | `/2` | 网易云 `#api-url`、QQ音乐 `#qq-api-url`（含微信扫码）、Navidrome `#nd-url`/`#nd-user`/`#nd-pass`、转码码率 128/192/320 |
| 和风天气 | （3） | 天气 API Key |
| HomeAssistant | （4） | HA 地址 + Token |
| AI大模型 | `/5` | `#ai-provider`、`#ai-base-url`、`#ai-api-key`、`#ai-model`、`#ai-temperature`、`#ai-max-tokens`、`#ai-enabled` |
| 定时任务 | （6） | 闹钟 / 提醒 |

页面默认值（出厂预置）：

```
provider = openai
base-url = https://open.bigmodel.cn/api/paas/v4/chat/completions   （智谱）
model    = GLM-4.5-Flash
temperature = 0.7
max-tokens  = 2048
```

### 主界面（设备屏幕）

MOD 的 `MainActivity` 是一个青底测试页，标题 `Pandora`：

```
[ 测试用例界面 ]
[ 禁用MIC ]
[ 上一首 ] [ 播放 ] [ 下一首 ]
[ 联网 ]
```

这是作者留的调试面板，不是给终端用户看的界面 —— **优化的第一个方向就在这里**。

## 启动日志里的既有问题

```
E/CuidV266Manager: fetal error:: app files dir name is unexpectedly
                   :: /data/data/com.phicomm.speaker.device/files/com.phicomm.speaker.device
W/System.err: java.lang.UnsatisfiedLinkError: couldn't find "liblocSDK8a.so"   （百度定位 SDK）
D/SysPrivateService: get BootProp failed, e=IllegalStateException: Cannot get boot prop
V/WindowManagerService: Adding window ... WindowsService     （悬窗）
```

- `liblocSDK8a.so` 缺失 → 百度定位初始化失败，**但 MOD 只是引用了 Baidu LBS，实际用不到定位**，属于噪音，可以不管。
- `CuidV266Manager` 的 files dir 路径异常 → 可能与 `pm install -r` 保留了旧的 `data/data` 目录结构有关，不致命。
- `SysPrivateService.get BootProp` 失败 → 系统私有服务拒绝非系统签名的调用，**这可能是某些系统级功能（如改音量曲线、接管唤醒）失效的根因**，值得深挖。
