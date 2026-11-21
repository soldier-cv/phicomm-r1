# 麦克风探针（实验用，装完即卸）

用途：验证「原厂语音链在跑时，普通 App 的 AudioRecord 能不能录到数据」。

## 结论摘要

- 原厂链在跑：`state=1`(初始化成功) → `recordingState=3`(开始录音) → **`read()` 永远阻塞，0 采样**
- 原厂链停掉：1.2 秒拿到 16000 采样，`maxAbs≈70~79`，数据真实

## 文件

| 文件 | 说明 |
|---|---|
| `Probe.java` | 探针源码，两个音频源各录 1 秒，结果打 `logcat` tag `R1PROBE` |
| `AndroidManifest.xml` | 最小清单，只有 `RECORD_AUDIO` |
| `probe.apk` | 已签好的产物（12KB，debug 签名） |

## 构建（如果要改）

```powershell
$sdk="$env:LOCALAPPDATA\Android\Sdk"
$bt="$sdk\build-tools\36.0.0"
$jar="$sdk\platforms\android-36\android.jar"

javac -source 8 -target 8 -bootclasspath $jar -d classes Probe.java
& "$bt\d8.bat" --min-api 22 --lib $jar --output . (Get-ChildItem classes -Recurse -Filter *.class).FullName
& "$bt\aapt2.exe" link -o base.apk -I $jar --manifest AndroidManifest.xml --min-sdk-version 22 --target-sdk-version 22
# 把 classes.dex 加进 base.apk，然后
& "$bt\zipalign.exe" -f 4 base.apk aligned.apk
& "$bt\apksigner.bat" sign --ks probe.jks --ks-pass pass:android --key-pass pass:android --min-sdk-version 22 --out probe.apk aligned.apk
```

## 运行

```bash
# 注意：R1 上 pm 命令会把 adb 连接掐掉，必须走后台 + 读文件
adb push probe.apk /data/local/tmp/probe.apk
adb shell "cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/probe.apk > /data/local/tmp/i.log 2>&1' >/dev/null 2>&1 &"
sleep 10 && adb shell "cat /data/local/tmp/i.log"

adb logcat -c
adb shell "am start -n com.r1.probe/.Probe"
sleep 8 && adb logcat -d -s R1PROBE

# 卸载
adb shell "cd /data/local/tmp && nohup sh -c 'pm uninstall com.r1.probe > /data/local/tmp/u.log 2>&1' >/dev/null 2>&1 &"
sleep 10 && adb shell "cat /data/local/tmp/u.log"
```

## 警告

跑之前一定要确认原厂语音链在不在跑：

```bash
adb shell "cat /proc/asound/card1/pcm0c/sub0/status | grep -E 'state|owner_pid'"
adb shell "cat /proc/asound/card1/pcm0c/sub0/info | grep subdevices_avail"
```

`subdevices_avail: 0` 表示被占，此时探针会卡死（正常现象，`am force-stop com.r1.probe` 即可）。
要测「能录」的场景，必须先 `am force-stop com.phicomm.speaker.device`，
测完用 `am start -n com.phicomm.speaker.device/.ui.MainActivity` 恢复。
