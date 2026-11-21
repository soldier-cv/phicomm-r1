# play-probe —— 出声 / 定时探针

只为一件事存在：在真机上回答「普通 App 到底能不能出声、定时准不准」，
也就是 `docs/03-features.md` 里的 V1 和 V2。

**它不碰原厂、不碰 L2**：包名 `com.r1.playprobe`，装坏了 `pm uninstall com.r1.playprobe` 就没了。

## 结论（2026-10-29 实测）

| 项 | 结果 |
|---|---|
| V1 `MediaPlayer` 出声 | ✅ `onPrepared duration=4148ms`，`START→COMPLETED=4168ms`，人耳确认听到 |
| V2 `setExact` 精度 | ✅ 120 秒间隔 `drift=5ms`，到点响了 |
| 音量 | 默认 6/15 就能听清；**不要再替用户拉满** |

## 用法

```powershell
.\build.ps1            # 只编译
.\build.ps1 -Install   # 编译 + 装到 <音箱IP>
```

```powershell
$adb = "D:\BaiduNetdiskDownload\R1 MOD apk\一键安装 (20261007)\一键安装\adb.exe"
$dev = "<音箱IP>:5555"

# 1) 播一段 wav（音量保持设备当前值，不动）
& $adb -s $dev shell "am start -n com.r1.playprobe/.PlayProbe"
& $adb -s $dev logcat -d -s R1PLAY

# 2) 只调音量，不播
& $adb -s $dev shell "am start -n com.r1.playprobe/.PlayProbe --ei vol 4 --ez play false"

# 3) 设 N 秒后的精确闹钟
& $adb -s $dev shell "am start -n com.r1.playprobe/.AlarmProbe --ei sec 120"
& $adb -s $dev logcat -d -s R1ALARM
```

日志 tag：播放 `R1PLAY`，定时 `R1ALARM`（`SCHEDULED` / `FIRED drift=…ms` / `RING START|DONE`）。

## 别踩的坑

**`/proc/asound/card2/pcm0p/sub0/status` 的 `RUNNING` 不能当判据。**
AudioFlinger 常驻持有这个 substream，**没放声音时 card0（HDMI）和 card2（AK7755）
也同时显示 RUNNING**。第一版验证就是被它误导的。

能用的客观判据只有：`onPrepared` 的 `duration` 与 `START→COMPLETED` 的时长吻合。
「喇叭有没有真响」最终仍要人耳确认一次。

## 其他

- 播放用的 `assets/probe.wav` 是 Windows SAPI（Huihui 中文）生成的 4.15 秒测试音，
  22.05kHz 立体声。`tinyplay` 那条路要求 ≥2 声道、且 48kHz 会报
  `Error playing sample`，只有 22.05kHz 能过——但 `tinyplay` 绕过 AudioFlinger，
  它出不出声不代表 App 能出声，只是硬件通路的一个参考。
- 闹钟接收者用的是 `STREAM_MUSIC`，不是 `STREAM_ALARM`——避免测试音比用户设的音量大。
