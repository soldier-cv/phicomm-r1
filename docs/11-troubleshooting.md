# 运维与排障手册（出问题了看这里）

> 这个项目最大的敌人不是技术难点，是**设备时不时就联系不上了**。
> 这里把已经踩过的坑和恢复办法写全。
>
> 本文合并了原「adb 四个坑」与「运维手册」（连不上 / 装坏 / 回退）两篇；
> 部署与系统瘦身见 `docs/20-device-ops.md`，两者不重复。

---

## 第 1 节 · adb 连不上

### 症状 A：`failed to connect` / `device offline`，但网页能打开

**这是 adbd 挂死了**（不是网络问题，不是权限问题）。

判断依据：

```powershell
# 1) 设备本身活着吗（8080 是音箱上的网页）
curl.exe -s -o NUL -w "%{http_code}`n" http://<IP>:8080/
# 如果返回 200 → 设备活着，只是 adb 挂了

# 2) 5555 端口 TCP 能连上吗
Test-NetConnection <IP> -Port 5555

# 3) 裸读握手包（adb 正常时应该立刻收到 "CNXN"）
#    连上但 3 秒收不到任何数据 = adbd 卡死
```

**恢复办法：给音箱断电重插。** 没有别的办法。

> 已经发生过两次（2026-10-28、10-29），每次都是大量连续 adb 命令之后。

**预防**
- 一次只跑一条 `adb shell`，脚本里加 `disconnect → connect → 重试`。
- 尤其别连续跑 `pm` 命令 —— `pm list packages`、`pm path` 特别容易把连接掐断。

### 症状 B：`error: closed`

**命令发出去了，但连接被设备掐断，结果丢了。** 不是失败，是不知道成功没。

**这是 R1 的常态**，尤其在这两种场景：
1. `adb push` 完成后的**第一条** `adb shell`
2. 任何 `pm` 开头的命令

**通用绕法**：把操作丢到设备端后台，结果写文件，过几秒再读。

```bash
# 装包
adb shell "cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/x.apk > /data/local/tmp/i.log 2>&1' >/dev/null 2>&1 &"
sleep 12 && adb shell "cat /data/local/tmp/i.log"

# 查包列表
adb shell "cd /data/local/tmp && nohup sh -c 'pm list packages > /data/local/tmp/p.txt 2>&1' >/dev/null 2>&1 &"
sleep 8 && adb shell "grep 关键字 /data/local/tmp/p.txt"

# 卸载
adb shell "cd /data/local/tmp && nohup sh -c 'pm uninstall 包名 > /data/local/tmp/u.log 2>&1' >/dev/null 2>&1 &"
sleep 10 && adb shell "cat /data/local/tmp/u.log"
```

> 注意 `cd /data/local/tmp &&` 不能省，否则 nohup 起不来。

### 症状 C：IP 变了

音箱是 DHCP，租约可能变；电脑的 IP 也会变（实测从 `.27` 变成过 `.110`）。

```powershell
# 找音箱：扫 5555 或 8080
1..254 | ForEach-Object { $ip="<你的网段>.$_"; $c=New-Object Net.Sockets.TcpClient; $c.ConnectAsync($ip,8686) } 
# 或者直接看路由器后台的客户端列表，找 Phicomm_R1
```

**根治**：在路由器上把 MAC `<音箱MAC>` 绑定成固定 IP。

---

### 症状 D：`adb install` 永远卡在 `waiting for device`（2026-10-30 实测）

**现象**：`adb install -r xxx.apk` 刷出几千行 `- waiting for device -`，直到超时；但设备本身活着（ping、8080、8686 都正常）。

**根因**：`adb install` 是「推送 + 触发安装 + 等结果」的长连接操作。这台机器的 adbd 在长连接上会挂，宿主机就进入无限重连；**文件其实已经传完了**（实测 `/data/local/tmp/r1brain-l1.apk` 31,013,767 字节完整）。

**正确姿势**：永远不要用 `adb install`，改成两步：

```powershell
# 1) 推送（慢，但能断点重试）
adb -s $dev push x.apk /data/local/tmp/x.apk
# 2) 设备端后台安装，本地只轮询日志
adb -s $dev shell "cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/x.apk > /data/local/tmp/install.log 2>&1' >/dev/null 2>&1 &"
sleep 30; adb -s $dev shell "cat /data/local/tmp/install.log"
```

`l1/tools/deploy.ps1`（L1）已经把这套封起来了：分片 4MB、每片校验、失败重试、设备端拼装 + md5 校验、后台安装轮询。

### 症状 E：adbd 假死（端口通但 `offline`）

```
Test-NetConnection <IP> -Port 5555   → True
adb connect <IP>:5555                → failed to connect
adb -s ... get-state                           → error: device offline
```

端口能连、握手没响应 = adbd 卡死。**唯一恢复办法：给音箱断电重插。**

> 2026-10-30 又发生两次，都发生在连续大文件传输之后。规律：**别做长连接的大传输**。

---

## 第 2 节 · 设备上的东西被改坏了

### 2.1 语音助手叫不醒了

```bash
# 看进程在不在
adb shell "ps | grep speaker.device"

# 看是不是被 force-stop 了
adb shell "am start -n com.phicomm.speaker.device/.ui.MainActivity"

# 看麦克风有没有被占（正常应该 owner_pid 在、avail=0）
adb shell "cat /proc/asound/card1/pcm0c/sub0/status"
adb shell "cat /proc/asound/card1/pcm0c/sub0/info | grep subdevices_avail"
```

### 2.2 想彻底回到原厂

```bash
# 卸载掉 data 分区里的覆盖版本，系统会自动回落到 /system/app/Unisound
adb shell "cd /data/local/tmp && nohup sh -c 'pm uninstall com.phicomm.speaker.device > /data/local/tmp/u.log 2>&1' >/dev/null 2>&1 &"
sleep 10 && adb shell "cat /data/local/tmp/u.log"

# 重启
adb shell reboot
```

**注意**：回退后语音助手**叫不动了**（原厂三道闸门：未绑定/无网络/待升级，都要连已死的服务器）。
这是原厂状态，不是弄坏了。

### 2.3 我们自己的 L2 App 装坏了

```bash
adb shell "cd /data/local/tmp && nohup sh -c 'pm uninstall com.r1.brain > /data/local/tmp/u.log 2>&1' >/dev/null 2>&1 &"
```

**L2 怎么坏都不会影响原厂语音功能**，这是分层的意义。

---

## 第 3 节 · 常用命令速查

```powershell
$adb = "D:\dev\agent-repos\phicomm-r1\一键安装包\adb.exe"   # 或 PATH 里的 adb
$dev = "<IP>:5555"

& $adb connect $dev
& $adb -s $dev get-state                     # device = 正常
& $adb -s $dev shell "cat /proc/uptime"      # 开机多久
& $adb -s $dev shell "cat /proc/meminfo | grep MemFree"
& $adb -s $dev shell "netstat -tln | grep LISTEN"
& $adb -s $dev shell "ps | grep phicomm"
& $adb -s $dev logcat -d -s R1PROBE          # 我们的日志 tag
```

**仓库自带脚本**

```powershell
pwsh .\tools\r1-doctor.ps1 -Ip <IP>          # 一键体检
pwsh .\tools\install-apk.ps1 -Apk <路径> -Ip <IP>   # 静默装包（带进度条）
python .\tools\verify-mic-exclusive.py <IP>  # 验证麦克风独占
```

---

## 第 4 节 · 关键路径

| 东西 | 路径 |
|---|---|
| 原厂底包 | `/system/app/Unisound/Unisound.apk`（24,936,387 B，被 L1 顶替但仍在） |
| 现役 L1 | `/data/app/com.phicomm.speaker.device-*/base.apk`（Janus，约 31 MB） |
| 原厂播放器 | `/system/app/EchoService/EchoService.apk` |
| 四麦配置 | `/system/usr/uni_4mic_config/` |
| 临时目录 | `/data/local/tmp/`（**别在这里留垃圾**） |
| 语音链数据 | `/data/data/com.phicomm.speaker.device/files/`（`run-as` 可读） |

---

## 第 5 节 · 不许做的事

| 禁止 | 为什么 |
|---|---|
| ❌ 改 `/system` | 没有 root，改不了；硬改会变砖 |
| ❌ `adb root` | 实测无效（user 版 adbd 没编 `ALLOW_ADBD_ROOT`） |
| ❌ 刷固件 / 动 recovery | ADB 是唯一入口，刷坏了没救 |
| ❌ 连续轰炸 `pm` 命令 | 会把 adbd 搞挂（已发生两次） |
| ❌ 在 `/data/local/tmp` 留大文件 | `/data` 只剩 708MB |
| ❌ 用裸 `pm uninstall` 删系统包 | 会破坏开机流程；只能用 `pm uninstall --user 0 -k`（见 docs/20 §C） |

---

## 第 6 节 · 出了事怎么定位

**第一步永远是拿 logcat**：

```powershell
& $adb -s $dev logcat -d > r1-logcat.txt
```

**关注的 tag**

| tag | 来源 |
|---|---|
| `R1PROBE` | 我们的探针 App |
| `R1BRIDGE` | 将来的桥（约定用这个 tag） |
| `ExampleApp` | 现役 MOD 的主进程日志 |
| `AndroidRuntime` | 崩溃 |
| `UNI_4MIC` | 四麦 HAL（mediaserver） |

**关键文件**

```bash
adbfs cat /proc/asound/card1/pcm0c/sub0/status   # 麦克风状态
adb shell dmesg | busybox tail -50                # 内核（没有 tail，用 busybox）
adb shell "dumpsys package <包名>"                # 安装信息
```

---

## 第 7 节 · 免 ADB 线连日常运维：Web 控制台空中 OTA 升级与应用管理

> 鉴于 adbd 极易挂死（第 1 节），日常开发与使用中**强烈建议优先使用 Web 控制台内置的运维中枢**，彻底摆脱物理断电重插与脆弱的 ADB 连接。

### 7.1 空中 OTA 自我升级（免连电脑）
1. 电脑端完成编译打包：`pwsh -NoProfile -ExecutionPolicy Bypass -File .\l2-app\build.ps1`
2. 浏览器打开控制台 `http://<音箱IP>:8686/desktop/settings.html`（或手机端设置页）。
3. 切换至 **「05 运维与应用」** 面板。
4. 将编译产物 `l2-app/build/r1brain.apk` 直接拖入「APK 文件流式上传区」（或点击选择文件）。
5. 后端采用 **8KB 循环缓冲纯流式直写** `/data/local/tmp/upload_install.apk`（内存开销 < 64KB），并通过本机回环 ADB (`127.0.0.1:5555`) 穿透执行 `pm install -r`。
6. 检测到属于自我升级后，后台会自动重启 `MainService` 服务，约 2 秒后直接刷新网页控制台即可完成升级！

### 7.2 远程清理与卸载垃圾应用（回收 512MB 内存）
- 在应用管理列表中，第三方应用（如前房主遗留的 TV 软件）会标记为「第三方」。
- 点击对应的 **「强停」** 或 **「卸载」** 按钮，系统会调用特权 `pm uninstall --user 0 -k` 彻底释放被占用的运行内存与后台进程。

### 7.3 一键深度清理与特权重启
- **一键深度清理**：点击后自动扫描并清空 `/data/local/tmp` 目录下的遗留安装包和音频缓存，并主动调用 JVM `System.gc()`。
- **重启音箱系统**：在无需拔插电源的情况下，直接点击「重启音箱系统」，系统将调用特权 `reboot` 指令完成整机安全重启。

---

## 第 8 节 · `error: closed` 的根因与四个安装坑

> 原「adb 四个坑」（2026-10-28 实测）；当时的结论今天仍然适用。

### 8.1 `adb push` 之后的第一条 `adb shell` 必定断开

现象：push 成功（字节数完整），紧接着的 `pm install` 报 `error: closed`。

已排除的可能：

| 怀疑 | 实测 | 结论 |
|---|---|---|
| Wi-Fi 抖动 | 连续 8 次 `adb shell echo alive` 全部 rc=0 | 排除 |
| `pm install` 太久被掐 | `adb shell "sleep 20; echo done"` 正常返回 | 排除 |
| 上传没传完 | 设备端与本地校验和一致 | 排除 |
| 只是偶发 | 复现 ≥4 次，位置固定在 push 后第一条 shell | **稳定现象** |

**对策**：安装丢到设备端后台（见第 1 节症状 B）。任何一条命令报 `error: closed` 时，
统一先 `adb disconnect` → `adb connect` 再重试一次，成功率接近 100%。

### 8.2 原版「一键安装.bat」会闪退

那份 bat 里 `goto run_error` / `:start_Unisound` / `:start_EchoService` / `:adbfile_not`
四个标签都不存在，cmd 遇到不存在的 `goto` 会直接终止批处理，双击就是窗口一闪。

现在用 `一键安装包/一键安装.bat`（重写过的版本）或 `tools/install-apk.ps1`。

### 8.3 无线传 30MB 要 3 分钟，且没有进度

实测 **0.2 MB/s**，31 MB 约 185 秒。进度只能靠轮询设备端文件大小推算：

```sh
adb -s <ip> shell "ls -l /data/local/tmp/r1brain-l1.apk"
```

`tools/install-apk.ps1` 就是这么算进度的，并且会先比对设备上同名包的大小，一致就跳过上传。

### 8.4 签名冲突（换一台原厂机时必现）

我们重签的 L1（`[38b25ad4]`）与出厂签名（`[105ffce5]`）不同，
在**没被动过**的机器上直接 `pm install -r` 会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：

```sh
# 先卸载 data 分区里的覆盖版本，回落到 /system 原厂包
adb shell pm uninstall com.phicomm.speaker.device
# 再装我们的 L1
adb shell pm install -r /data/local/tmp/r1brain-l1.apk
```

