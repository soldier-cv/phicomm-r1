# L1 桥构建脚本
#
# 用途：把 l1/src 下我们自己写的 bridge 类，注入原厂 com.phicomm.speaker.device 包，
#       再用 Janus 套壳，产出一个「原厂签名 + 我们的代码」的 APK。
#
# 原理（见 docs/19-r1-architecture-and-l1-build.md）：
#   · 安装：PackageManager 只校验 zip，签名来自原厂 APK → 能覆盖系统应用
#   · 运行：ART 只看文件开头的 dex 魔数 → 执行我们前置的完整 DEX
#   · 因此前置 DEX 必须包含「原厂全部类 + 我们的类」，zip 部分一个字节都不能动
#
# 用法：
#   pwsh -File .\l1\build.ps1                 # 用缓存里的底包构建
#   pwsh -File .\l1\build.ps1 -PullBase       # 额外与设备上的原厂包比对 SHA-256
#
# @author xudong.hua,deepseek
# @since 2026-10-30 21:55 星期五

[CmdletBinding()]
param(
    [string]$Device = "192.168.1.100:5555",
    [string]$Adb = "",
    [switch]$PullBase,
    [switch]$SkipVerify
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Cache = Join-Path $Root ".cache"
$Build = Join-Path $Root "build"
$Tools = Join-Path $Root "tools\jars"
$Vendor = Join-Path $Root "vendor\Unisound_original.apk"

# 底包指纹：原厂 V3.2.5.2-，24,936,387 字节。
# 与 XiaoZhi 逆向报告里记录的原厂 SHA-256 前 32 位一致，可作为真伪判据。
$VendorSha256 = "a0548242414666d27df9b099e85ab69697cc5c1599f019ce34dd2460f8841bd7"

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "    $msg" -ForegroundColor Green }

# ---------------------------------------------------------------- 工具定位
if (-not $Adb) {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
    $Adb = Join-Path $sdk "platform-tools\adb.exe"
    if (-not (Test-Path $Adb)) { $Adb = "adb" }
}
$Sdk = Split-Path -Parent (Split-Path -Parent $Adb)
$AndroidJar = Join-Path $Sdk "platforms\android-34\android.jar"
$D8Jar = Join-Path $Sdk "build-tools\36.0.0\lib\d8.jar"

$BaksmaliCp = @("baksmali-2.5.2.jar", "dexlib2-2.5.2.jar", "util-2.5.2.jar",
    "guava-27.1-android.jar", "jcommander-1.64.jar") |
    ForEach-Object { Join-Path $Tools $_ }
$SmaliCp = @("smali-2.5.2.jar", "dexlib2-2.5.2.jar", "util-2.5.2.jar",
    "guava-27.1-android.jar", "jcommander-1.64.jar", "antlr-runtime-3.5.2.jar") |
    ForEach-Object { Join-Path $Tools $_ }

foreach ($p in @($AndroidJar, $D8Jar) + $BaksmaliCp + $SmaliCp) {
    if (-not (Test-Path $p)) { throw "缺少工具：$p（见 l1/README.md 的『准备工具』）" }
}

New-Item -ItemType Directory -Force -Path $Cache, $Build | Out-Null

# ---------------------------------------------------------------- 1. 底包
Write-Step "1/7 校验底包（vendor/Unisound_original.apk）"
if (-not (Test-Path $Vendor)) { throw "缺少底包：$Vendor" }
$hash = (Get-FileHash $Vendor -Algorithm SHA256).Hash.ToLower()
if ($hash -ne $VendorSha256) {
    throw "底包 SHA-256 不符：`n  实际 $hash`n  期望 $VendorSha256"
}
$BaseApk = $Vendor
Write-Ok "底包 $((Get-Item $Vendor).Length) 字节，SHA-256 校验通过"

if ($PullBase) {
    Write-Host "    与设备上的原厂包比对 ..."
    $tmp = Join-Path $Cache "device_Unisound.apk"
    & $Adb -s $Device pull /system/app/Unisound/Unisound.apk $tmp 2>$null | Out-Null
    if (Test-Path $tmp) {
        $h2 = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
        if ($h2 -eq $VendorSha256) { Write-Ok "设备上的原厂包与 vendor 一致" }
        else { Write-Host "    [WARN] 设备上的原厂包与 vendor 不一致：$h2" -ForegroundColor Yellow }
    }
}

# ---------------------------------------------------------------- 2. 原厂 smali
Write-Step "2/7 反汇编原厂 classes.dex"
$FactoryDex = Join-Path $Cache "factory.dex"
$FactorySmali = Join-Path $Cache "factory-smali"
if (-not (Test-Path $FactoryDex)) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($BaseApk)
    $entry = $zip.Entries | Where-Object { $_.FullName -eq "classes.dex" }
    if (-not $entry) { throw "底包里没有 classes.dex" }
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $FactoryDex, $true)
    $zip.Dispose()
}
if (-not (Test-Path $FactorySmali)) {
    java -Xmx2g -cp ($BaksmaliCp -join ";") org.jf.baksmali.Main d -o $FactorySmali $FactoryDex
    if ($LASTEXITCODE -ne 0) { throw "baksmali 失败" }
}
$factoryCount = (Get-ChildItem $FactorySmali -Recurse -Filter *.smali | Measure-Object).Count
Write-Ok "原厂 smali $factoryCount 个类"

# ---------------------------------------------------------------- 3. 我们的类
Write-Step "3/7 编译 bridge"
$Classes = Join-Path $Build "classes"
$BridgeDexDir = Join-Path $Build "bridge-dex"
$BridgeSmali = Join-Path $Build "bridge-smali"
foreach ($d in @($Classes, $BridgeDexDir, $BridgeSmali)) {
    if (Test-Path $d) { Remove-Item -Recurse -Force $d }
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}
$srcs = Get-ChildItem (Join-Path $Root "src") -Recurse -Filter *.java | ForEach-Object FullName
javac -source 8 -target 8 -encoding UTF-8 -nowarn -cp "$AndroidJar" -d $Classes $srcs
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }
$classFiles = Get-ChildItem $Classes -Recurse -Filter *.class | ForEach-Object FullName
java -cp "$D8Jar" com.android.tools.r8.D8 --lib "$AndroidJar" --min-api 22 --output $BridgeDexDir $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 失败" }
java -Xmx512m -cp ($BaksmaliCp -join ";") org.jf.baksmali.Main d -o $BridgeSmali (Join-Path $BridgeDexDir "classes.dex")
if ($LASTEXITCODE -ne 0) { throw "baksmali(bridge) 失败" }
Write-Ok "$($classFiles.Count) 个 class"

# ---------------------------------------------------------------- 4. 合并 + 打补丁
Write-Step "4/7 合并 smali 并插入调用"
$Merged = Join-Path $Build "merged-smali"
if (Test-Path $Merged) { Remove-Item -Recurse -Force $Merged }
Copy-Item $FactorySmali $Merged -Recurse

$BridgePkg = Join-Path $Merged "com\phicomm\speaker\device\bridge"
New-Item -ItemType Directory -Force -Path $BridgePkg | Out-Null
Copy-Item (Join-Path $BridgeSmali "com\phicomm\speaker\device\bridge\*") $BridgePkg -Recurse -Force

# 4.1 ExampleApp.onCreate：init() 之后挂 Bridge.init
$appSmali = Join-Path $Merged "com\phicomm\speaker\device\ExampleApp.smali"
$text = Get-Content $appSmali -Raw
if ($text -notmatch 'bridge/Bridge;->init') {
    $anchor = "invoke-direct {p0}, Lcom/phicomm/speaker/device/ExampleApp;->init()V"
    if ($text -notmatch [regex]::Escape($anchor)) { throw "ExampleApp 里找不到 init() 调用点" }
    $inject = "`r`n`r`n    invoke-static {p0}, Lcom/phicomm/speaker/device/bridge/Bridge;->init(Landroid/content/Context;)V"
    $text = $text.Replace($anchor, $anchor + $inject)
    [System.IO.File]::WriteAllText($appSmali, $text, [System.Text.UTF8Encoding]::new($false))
    Write-Ok "ExampleApp.onCreate ← Bridge.init"
} else {
    Write-Ok "ExampleApp 已打过补丁，跳过"
}

# 4.2 NLUDispatcher：两条 ASR 结果入口各插一行
$nluSmali = Join-Path $Merged "com\unisound\vui\handler\filter\NLUDispatcher.smali"
$text = Get-Content $nluSmali -Raw
if ($text -notmatch 'bridge/Bridge;->onAsrResult') {
    $pattern = '(?s)(\.method protected onASRResult(?:Local|Net)\(Lcom/unisound/vui/engine/ANTHandlerContext;Ljava/lang/String;\)Z.*?\.prologue)'
    $patched = 0
    $text = [regex]::Replace($text, $pattern, {
            param($m)
            $script:patched++
            $m.Value + "`r`n`r`n    invoke-static {p2}, Lcom/phicomm/speaker/device/bridge/Bridge;->onAsrResult(Ljava/lang/String;)V"
        })
    if ($patched -lt 2) { throw "NLUDispatcher 补丁只命中了 $patched 处（应为 2）" }
    [System.IO.File]::WriteAllText($nluSmali, $text, [System.Text.UTF8Encoding]::new($false))
    Write-Ok "NLUDispatcher.onASRResult* ← Bridge.onAsrResult（$patched 处）"
} else {
    Write-Ok "NLUDispatcher 已打过补丁，跳过"
}

# 4.3 MqttTransportChannel：原厂 MQTT 连的是已下线的斐讯云，
#     连不上时 Paho 的静态初始化会抛 MissingResourceException，
#     在 HandlerThread 上变成 FATAL 把整个进程打死。我们不需要这条通道，直接置空。
$mqttSmali = Join-Path $Merged "com\unisound\ant\device\mqtt\MqttTransportChannel.smali"
$text = Get-Content $mqttSmali -Raw
$mqttMethods = @(
    @{ Sig = ".method public createChannel(Landroid/content/Context;Lcom/unisound/ant/device/mqtt/OnMQTTStatusChangeListener;)V"; Regs = 3 },
    @{ Sig = ".method public createChannel(Landroid/content/Context;Lcom/unisound/ant/device/mqtt/bean/ChannelParams;Lcom/unisound/ant/device/mqtt/OnMQTTStatusChangeListener;)V"; Regs = 4 }
)
$neutralized = 0
foreach ($m in $mqttMethods) {
    if ($text -notmatch [regex]::Escape($m.Sig)) { continue }
    $pattern = '(?s)' + [regex]::Escape($m.Sig) + '.*?\.end method'
    $body = $m.Sig + "`r`n    .registers $($m.Regs)`r`n`r`n    return-void`r`n.end method"
    $text = [regex]::Replace($text, $pattern, { param($x) $body }, 1)
    $neutralized++
}
if ($neutralized -eq 0) { throw "MqttTransportChannel.createChannel 未找到" }
[System.IO.File]::WriteAllText($mqttSmali, $text, [System.Text.UTF8Encoding]::new($false))
Write-Ok "MqttTransportChannel.createChannel 置空（$neutralized 处）"

# 4.4 【已决策：保留定位，不关闭】#     云知声 SDK 在 DeviceCenterHandler 里 new AutoLocationReceiver，
#     其构造函数创建 BaiduLocationStrategy，拉起独立的 :remote 进程（实测 58MB）。
#     但原厂天气靠它拿城市（日志里的"苏州"就是它解析的），用户明确要求保留定位能力，
#     因此这里**不做任何修改**。若将来确认 L2 的 IP 定位可以完全替代，再启用下面这段。
#
# $locSmali = Join-Path $Merged "com\unisound\ant\device\receiver\AutoLocationReceiver.smali"
# ...（原置空逻辑已删除）

# 4.5 抑制原厂闲聊播报：chat / illegal 两个处理器只收会话、不出声。
#     否则配好 L2 的 TTS 之后，同一句话会被原厂（"抱歉，我还在努力成长"）和 L2 各播一遍。
#     注意：设备类意图（音乐/音量/设置/闹钟）仍由原厂处理，只掐这两个闲聊分支。
$chatPatches = @(
    @{ File = "com\unisound\vui\handler\session\chat\DefaultChatHandler.smali";
       Cls  = "Lcom/unisound/vui/handler/session/chat/DefaultChatHandler;" },
    @{ File = "com\unisound\ant\platform\illeagechat\DefaultIlleageChatHandler.smali";
       Cls  = "Lcom/unisound/ant/platform/illeagechat/DefaultIlleageChatHandler;" }
)
$sig = ".method protected eventReceived(Lnluparser/scheme/NLU;Lcom/unisound/vui/engine/ANTHandlerContext;)V"
foreach ($p in $chatPatches) {
    $sf = Join-Path $Merged $p.File
    if (-not (Test-Path $sf)) {
        Write-Host "    [WARN] 找不到 $($p.File)，跳过" -ForegroundColor Yellow
        continue
    }
    $text = Get-Content $sf -Raw
    if ($text -notmatch [regex]::Escape($sig)) {
        Write-Host "    [WARN] $($p.File) 里没有 eventReceived，跳过" -ForegroundColor Yellow
        continue
    }
    $pattern = '(?s)' + [regex]::Escape($sig) + '.*?\.end method'
    $body = $sig + "`r`n    .registers 3`r`n`r`n" +
        "    invoke-super {p0, p1, p2}, Lcom/unisound/vui/handler/SimpleUserEventInboundHandler;->eventReceived(Ljava/lang/Object;Lcom/unisound/vui/engine/ANTHandlerContext;)V`r`n`r`n" +
        "    invoke-virtual {p0}, " + $p.Cls + "->reset()V`r`n`r`n" +
        "    return-void`r`n.end method"
    $text = [regex]::Replace($text, $pattern, { param($x) $body }, 1)
    [System.IO.File]::WriteAllText($sf, $text, [System.Text.UTF8Encoding]::new($false))
    Write-Ok "已抑制闲聊播报: $([System.IO.Path]::GetFileNameWithoutExtension($p.File))"
}

# 4.6 ExampleANTEngineInitializer.onEngineInitDone：把原厂引擎实例交给 Bridge，
#     用于「连续对话」时重新进入 ASR（Bridge.requestListen → engine.enterASR()）。
$engineSmali = Join-Path $Merged "com\phicomm\speaker\device\ui\ExampleANTEngineInitializer.smali"
if (-not (Test-Path $engineSmali)) {
    Write-Host "    [WARN] 找不到 ExampleANTEngineInitializer，跳过连续对话补丁" -ForegroundColor Yellow
} else {
    $text = Get-Content $engineSmali -Raw
    if ($text -notmatch 'bridge/Bridge;->setEngine') {
        $pattern = '(?s)(\.method protected onEngineInitDone\(Lcom/unisound/vui/engine/ANTEngine;\)V.*?)(\r?\n\s*return-void\r?\n\.end method)'
        $inject = "`r`n`r`n    invoke-static {p1}, Lcom/phicomm/speaker/device/bridge/Bridge;->setEngine(Ljava/lang/Object;)V"
        $newText = [regex]::Replace($text, $pattern, ('${1}' + $inject + '${2}'), 1)
        if ($newText -eq $text) {
            Write-Host "    [WARN] onEngineInitDone 补丁未命中" -ForegroundColor Yellow
        } else {
            [System.IO.File]::WriteAllText($engineSmali, $newText, [System.Text.UTF8Encoding]::new($false))
            Write-Ok "ExampleANTEngineInitializer.onEngineInitDone ← Bridge.setEngine"
        }
    } else {
        Write-Ok "engine 注入已存在，跳过"
    }
}

# 4.7 NLUDispatcher.onWakeupResult：唤醒成功后告诉 Bridge「接下来这段要录」
#     原厂每几秒就开合一次麦克风做唤醒轮询，不标记的话会把环境音全录下来。
$nluSmali = Join-Path $Merged "com\unisound\vui\handler\filter\NLUDispatcher.smali"
$text = Get-Content $nluSmali -Raw
if ($text -notmatch 'bridge/Bridge;->onWakeup') {
    $wakeSig = '.method protected onWakeupResult(Lcom/unisound/vui/engine/ANTHandlerContext;Ljava/lang/String;)Z'
    if ($text -notmatch [regex]::Escape($wakeSig)) {
        throw "NLUDispatcher.onWakeupResult 未找到"
    }
    $wakePat = '(?s)(' + [regex]::Escape($wakeSig) + '.*?\.prologue)'
    $patched = [regex]::Replace($text, $wakePat, {
            param($m)
            $m.Value + "`r`n`r`n    invoke-static {}, Lcom/phicomm/speaker/device/bridge/Bridge;->onWakeup()V"
        }, 1)
    if ($patched -eq $text) { throw "onWakeupResult 注入未生效" }
    [System.IO.File]::WriteAllText($nluSmali, $patched, [System.Text.UTF8Encoding]::new($false))
    Write-Ok "NLUDispatcher.onWakeupResult ← Bridge.onWakeup"
} else {
    Write-Ok "onWakeupResult 注入已存在，跳过"
}

# 4.8 原厂音频源 com.unisound.common.e：把 PCM tee 一份给 Bridge（自建 ASR 的输入）
#     选这里是因为 readData 是原厂引擎唯一的取音入口，且缓冲天然是 16k/16bit/单声道
#     （该类 <clinit>: h=0x3e80 / channelConfig=0x10 / audioFormat=0x2），不用重采样。
#     麦克风被原厂引擎独占（avail=0），不能另开 AudioRecord，只能这样抄一份。
$srcSmali = Join-Path $Merged "com\unisound\common\e.smali"
if (-not (Test-Path $srcSmali)) { throw "com/unisound/common/e.smali 未找到" }
$text = Get-Content $srcSmali -Raw
if ($text -notmatch 'Bridge;->onAudioData') {
    $cls = 'Lcom/phicomm/speaker/device/bridge/Bridge;'

    # openAudioIn：录音开始
    $openPat = '(?s)(\.method public openAudioIn\(\)I\s*\r?\n\s*\.registers \d+)'
    $newText = [regex]::Replace($text, $openPat,
        ('${1}' + "`r`n`r`n    invoke-static {}, $cls->onAudioInOpen()V"), 1)
    if ($newText -eq $text) { throw "openAudioIn 注入未生效" }
    $text = $newText

    # readData：每块 PCM 抄一份（p1=缓冲区, p2=长度）
    $readPat = '(?s)(\.method public readData\(\[BI\)I.*?move-result v0)'
    $newText = [regex]::Replace($text, $readPat,
        ('${1}' + "`r`n`r`n    invoke-static {p1, p2}, $cls->onAudioData([BI)V"), 1)
    if ($newText -eq $text) { throw "readData 注入未生效" }
    $text = $newText

    # closeAudioIn：录音结束，封 WAV 并广播
    $closePat = '(?s)(\.method public closeAudioIn\(\)V\s*\r?\n\s*\.registers \d+)'
    $newText = [regex]::Replace($text, $closePat,
        ('${1}' + "`r`n`r`n    invoke-static {}, $cls->onAudioInClose()V"), 1)
    if ($newText -eq $text) { throw "closeAudioIn 注入未生效" }
    $text = $newText

    [System.IO.File]::WriteAllText($srcSmali, $text, [System.Text.UTF8Encoding]::new($false))
    Write-Ok "com.unisound.common.e ← PCM tee（open/read/close 三处）"
} else {
    Write-Ok "PCM tee 注入已存在，跳过"
}

# 4.9 开机提示音：原厂 UniMediaPlayer.playBeepSound 把播放器增益写死 1.0f，
#     于是那声 bootloader_completed.mp3 的响度完全由系统媒体音量决定，
#     比平时播歌/说话响得多。这里把增益换成 Bridge.beepGain()（跟随音量并封顶）。
#     只改这一个重载：全包里只有 PhicommInitializeHandler 调它（就是开机提示音）。
$umpSmali = Join-Path $Merged "com\unisound\vui\common\media\UniMediaPlayer.smali"
if (-not (Test-Path $umpSmali)) { throw "UniMediaPlayer.smali 未找到" }
$text = Get-Content $umpSmali -Raw
if ($text -notmatch 'Bridge;->beepGain') {
    $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $sig = '.method public playBeepSound(Ljava/lang/String;ILcom/unisound/vui/common/media/IMediaPlayerStateListener;Z)V'
    $start = $text.IndexOf($sig)
    if ($start -lt 0) { throw "playBeepSound 签名未找到" }
    $end = $text.IndexOf(".end method", $start)
    if ($end -lt 0) { throw "playBeepSound 结尾未找到" }
    $body = $text.Substring($start, $end - $start)
    $call = 'invoke-virtual {v0, v1, v2}, Landroid/media/MediaPlayer;->setVolume(FF)V'
    if ($body.IndexOf($call) -lt 0) { throw "playBeepSound 里的 setVolume(FF) 未找到" }
    $inject = "invoke-static {}, Lcom/phicomm/speaker/device/bridge/Bridge;->beepGain()F" + $nl +
        "    move-result v1" + $nl + "    move v2, v1" + $nl + "    " + $call
    $body = $body.Replace($call, $inject)
    $text = $text.Substring(0, $start) + $body + $text.Substring($end)
    [System.IO.File]::WriteAllText($umpSmali, $text, [System.Text.UTF8Encoding]::new($false))
    Write-Ok "UniMediaPlayer.playBeepSound ← Bridge.beepGain"
} else {
    Write-Ok "开机提示音增益注入已存在，跳过"
}

# 4.10 接管原厂 TTS：所有字符串 TTS 都走 NativeANTEngine.doPlayTTS(String)。
#      原厂 NLU 与 L2 会同时处理同一句话，以前靠「发现它在播就 cancelTTS」事后压制，
#      结果 2026-11-05 21:30:48 把唤醒提示音当成抢答取消，连带 doCancelEngine，
#      引擎停在 engine_stop，用户之后喊多少遍都没反应。
#      现在改成在出口直接丢弃「不是我们发起」的 TTS：不取消、不打断，也就不会误伤引擎。
#      唤醒提示音走 AudioFileHelper 的音频字节，不经过这里，不受影响。
$engineSmali = Join-Path $Merged "com\unisound\vui\engine\NativeANTEngine.smali"
if (-not (Test-Path $engineSmali)) { throw "NativeANTEngine.smali 未找到" }
$text = Get-Content $engineSmali -Raw
if ($text -notmatch 'Bridge;->allowEngineTts') {
    $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $pat = '(?s)(\.method protected doPlayTTS\(Ljava/lang/String;\)V.*?\.prologue\s*\r?\n)'
    $guard = '    invoke-static {}, Lcom/phicomm/speaker/device/bridge/Bridge;->allowEngineTts()Z' + $nl +
        '    move-result v0' + $nl +
        '    if-nez v0, :bridge_tts_ok' + $nl +
        '    return-void' + $nl +
        '    :bridge_tts_ok' + $nl
    $newText = [regex]::Replace($text, $pat, { param($m) $m.Groups[1].Value + $guard }, 1)
    if ($newText -eq $text) { throw "doPlayTTS 注入未生效" }
    [System.IO.File]::WriteAllText($engineSmali, $newText, [System.Text.UTF8Encoding]::new($false))
    Write-Ok "NativeANTEngine.doPlayTTS ← Bridge.allowEngineTts（接管原厂播报）"
} else {
    Write-Ok "原厂 TTS 接管注入已存在，跳过"
}

# ---------------------------------------------------------------- 5. 组装 dex
Write-Step "5/7 组装完整 DEX"
$PayloadDex = Join-Path $Build "payload.dex"
if (Test-Path $PayloadDex) { Remove-Item $PayloadDex }
java -Xmx3g -cp ($SmaliCp -join ";") org.jf.smali.Main a --api 22 -o $PayloadDex $Merged
if ($LASTEXITCODE -ne 0) { throw "smali 组装失败" }
$payloadSize = (Get-Item $PayloadDex).Length
Write-Ok "payload.dex $payloadSize 字节"

# ---------------------------------------------------------------- 6. Janus 套壳
Write-Step "6/7 Janus 套壳"
$OutApk = Join-Path $Build "r1brain-l1.apk"
$janus = Join-Path $Root "tools\build_janus.py"
python $janus $BaseApk $PayloadDex $OutApk
if ($LASTEXITCODE -ne 0) { throw "Janus 构建失败" }

# ---------------------------------------------------------------- 7. 自检
if (-not $SkipVerify) {
    Write-Step "7/7 自检"
    $verify = Join-Path $Root "tools\verify_janus.py"
    python $verify $OutApk $BaseApk
    if ($LASTEXITCODE -ne 0) { throw "自检失败" }
} else {
    Write-Step "7/7 跳过自检"
}

Write-Host ""
Write-Host "产物：$OutApk（$((Get-Item $OutApk).Length) 字节）" -ForegroundColor Yellow
