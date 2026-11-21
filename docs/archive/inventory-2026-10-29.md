# 设备上装了什么、占多少（2026-10-29 实测）

> 方法：`pm list packages -f` 拿到 apk 路径，再逐个 `ls -l` 取大小。
> 数据目录（`/data/data/*`）在无 root 下读不到，所以这里只算 apk 体积。

## 总览

| 项 | 数值 |
|---|---|
| 已安装包 | **32 个** |
| apk 合计 | **76.1 MB** |
| `/data` | 988 MB，已用 279.1 MB，**剩 708.9 MB** |
| `/system` | 752 MB，已用 385.7 MB，剩 366.5 MB |

> 空间完全不是瓶颈。**瓶颈是内存**（490 MB 总，见下面的"僵尸 App"一节）。

---

## 一、按体积排序（完整清单）

| 大小 | 包名 | 位置 | 备注 |
|---:|---|---|---|
| 32.3 MB | `com.phicomm.speaker.device` | data | **现役 MOD**（Janus，33,064,967 B） |
| 17.9 MB | `android` | system/framework | 系统框架资源 |
| 9.3 MB | `com.phicomm.speaker.productiontest` | system/app | **产测工具**，正常使用用不到 |
| 6.0 MB | `com.xiaobaifile.tv` | data | ⚠️ **前房主装的电视应用** |
| 3.9 MB | `com.droidlogic.mediacenter` | data | ⚠️ 前房主的媒体中心 |
| 2.8 MB | `com.limitemp.interstellas` | data | ⚠️ 前房主的应用 |
| 1.4 MB | `com.wukongtv.wkhelper` | data | ⚠️ 前房主的悟空遥控 |
| 1.1 MB | `com.phicomm.speaker.player` | data | 播放器（原厂 0.2.5） |
| 652 KB | `com.android.bluetooth` | system | 蓝牙栈 |
| 492 KB | `com.android.packageinstaller` | system | 装包器 |
| 421 KB | `com.phicomm.speaker.otaservice` | system | OTA（**可能会把 MOD 覆盖回原厂**） |
| 373 KB | `com.android.providers.downloads` | system | 下载 |
| 212 KB | `com.android.server.telecom` | system | 电话 |
| 199 KB | `com.android.certinstaller` | system | 证书安装 |
| 190 KB | `com.android.inputdevices` | system | 输入设备 |
| 171 KB | `com.phicomm.speaker.netctl` | system | **配网控制** |
| 159 KB | `com.phicomm.speaker.ijetty` | system | **Jetty Web 服务器**（见下） |
| 122 KB | `com.android.providers.telephony` | system | 电话簿 |
| 101 KB | `com.android.providers.settings` | system | 设置存储 |
| 82 KB | `com.android.keychain` | system | 密钥链 |
| 69 KB | `com.android.vpndialogs` | system | VPN 对话框 |
| 55 KB | `com.android.shell` | system | shell |
| 19 KB | `com.android.externalstorage` | system | 外置存储 |
| **约 269 KB** | **`com.r1.brain`** | data | **我们的 L2（含运维中枢/免Key天气/Web控制台）** |
| 13 KB | `com.phicomm.speaker.exceptionreporter` | system | 异常上报 |
| 13 KB | `com.android.defcontainer` | system | 容器服务 |
| 12 KB | `com.android.location.fused` | system | 定位 |
| 10 KB | `com.phicomm.speaker.launcher` | system | 桌面（几乎空的） |
| 7 KB | `com.phicomm.speaker.systemtool` | system | 系统工具 |
| 7 KB | `com.phicomm.speaker.airskill` | system | **AirPlay 辅助** |
| 4 KB | `com.android.proxyhandler` | system | 代理 |
| 3 KB | `com.phicomm.speaker.bugreport` | system | 缺陷上报 |

---

## 二、⚠️ 最大的发现：前房主的电视 App 还在后台跑

这五个进程体积不大，但**全都在常驻吃内存**：

| 进程 | RSS | 是什么 |
|---|---:|---|
| `com.wukongtv.wkhelper` | 33.5 MB | 悟空遥控器（电视盒子用） |
| `com.droidlogic.mediacenter` | 32.4 MB | 晶晨媒体中心 |
| `com.xiaobaifile.tv` | 28.5 MB | 小白文件管理器 |
| `com.xiaobaifile.xbpush` | 26.6 MB | 小白推送服务（同包的另一个进程名，**不是独立包**） |
| `/data/data/com.xiaobaifile.tv/files/aria2c` | 1.5 MB | **还在跑的下载器守护进程** |

**R1 没有屏幕，这些 TV 应用对我们毫无用处。**

### 2.1 实测：停掉它们能省多少内存

```
am force-stop 之后    MemFree: 8,148 KB → 16,232 KB
```

### 2.2 `pm disable` 走不通（实测）

```
pm disable com.xiaobaifile.tv
→ SecurityException: attempt to change component state from uid=2000, package uid=10012
```

shell（uid 2000）**没有权限停用其他 uid 的应用**。`pm disable-user` 也是静默失败。

### 2.3 真正有效的办法：`pm uninstall --user 0 -k`

对当前用户卸载（保留数据目录），shell 有此权限：

```bash
pm uninstall --user 0 -k com.xiaobaifile.tv
pm uninstall --user 0 -k com.droidlogic.mediacenter
pm uninstall --user 0 -k com.limitemp.interstellas
```

**实测结果**：

```
卸载前  MemFree:  5,924 KB
卸载后  MemFree: 25,648 KB        ← 4.3 倍
```

进程列表里只剩系统进程 + `speaker.device` + `r1.brain`。

> **自动化落地**：上述特权卸载指令已集成在 `DeviceOps.java` 中（通过本地回环 ADB 以 uid=2000 执行）。用户可直接在 Web 控制台设置页的「05 运维中枢」一键点击「卸载」，自动回收内存，无需手动敲命令。

> ⚠️ **注意**：`-k` 只保留 `/data` 数据，**APK 会被删掉**，
> 恢复需要重新 `pm install` 那个 apk。所以事先必须备份。

### 2.4 备份与回退

三个 apk 已备份到 `artifacts/removed-apps/`：

```
com.xiaobaifile.tv.apk          5.8 MB
com.droidlogic.mediacenter.apk  3.8 MB
com.limitemp.interstellas.apk   2.7 MB
```

回退：

```bash
adb install -r artifacts/removed-apps/<包名>.apk
```

> ⚠️ **`com.wukongtv.wkhelper` 没来得及备份就被删了**（第一次试验时直接用
> `pm uninstall` 验证可行性，成功了，apk 也一起没了）。
> 需要用的时候得从应用商店重新装「悟空遥控器」。这是操作失误，记在这里。

---

## 三、两个值得注意的系统包

### `com.phicomm.speaker.ijetty`（159 KB）

**系统自带一个 Jetty Web 服务器包。** 说明这个 ROM 本来就打算跑 Web 服务。

对 MOD 的 8080 有个推测：MOD 的前置 DEX 里出现过
`http2HeadersList`、`Server-sent frames must not be masked`、`tp2Connection` ——
这是 **Netty** 的字符串，不是 NanoHTTPD。所以那个 8080 可能是 **MOD 自己打包的 Netty**，
而不是复用系统的 Jetty。这条不影响设计，记录备查。

### `com.phicomm.speaker.otaservice`（421 KB）

**OTA 服务还在跑。** 风险：它可能会检测到"版本不一致"然后把我们的包覆盖回原厂。

目前没观察到它动手（MOD 从 10-28 装上去到现在没事），但如果哪天 MOD 突然消失，
第一个怀疑对象就是它。缓解手段：`pm disable com.phicomm.speaker.otaservice`。

---

## 四、对我们设计的直接影响

1. **先清内存** —— 停掉那五个 TV App，能把可用内存从 8 MB 提到 16 MB，
   这是我们能不能塞下一个 HTTP 服务的前提。
2. **不要删系统包** —— `productiontest`（9.3 MB）虽然没用，但删了可能影响开机流程，
   而且 `/system` 是只读的，删它需要 remount（没 root 做不到）。
3. **L2 现在约 257 KB**，全部业务塞进去也到不了 1 MB，体积不是问题，**进程内存才是**。
