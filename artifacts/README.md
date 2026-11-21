# artifacts

这里放体积大的中间产物，**不进 git**（见 `.gitignore`）。

建议放的：

- `unisound_MOD_v1.0.4.apk` —— 装了的那份，留个底，方便以后核对
- `apktool-out/` —— `apktool d` 解出来的资源与 smali
- `jadx-out/` —— `jadx` 反编译出来的 java 源码（看逻辑用）
- 从设备上拉出来的 `/data/data/com.phicomm.speaker.device/` 配置目录

参考命令：

```powershell
# 解资源 + smali
apktool d unisound_MOD_v1.0.4.apk -o apktool-out

# 直接看 java
jadx -d jadx-out unisound_MOD_v1.0.4.apk

# 拉设备上的配置文件
adb -s <音箱IP>:5555 shell "run-as com.phicomm.speaker.device ls"   # 非 debuggable 会失败
```

> MOD 如果 `android:debuggable="true"`，就能 `run-as` 进去直接翻它的配置文件和 SharedPreferences，
> 那比反编译快得多 —— 优先确认这一点。
