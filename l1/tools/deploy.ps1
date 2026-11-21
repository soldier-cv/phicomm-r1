# L1 部署脚本：分片推送 → 设备端拼装 → 本地安装
#
# 为什么不用 adb install / adb push 一次性传 31MB：
#   这台音箱的 adbd 在长连接上不稳定（docs/11-troubleshooting.md §1），
#   31MB @ 0.2MB/s 要传 2~7 分钟，中途一断宿主端就无限 "waiting for device"。
#   所以改成：4MB 一片，每片最多重试 3 次，断了大不了重传那一片。
#
# 用法：
#   pwsh -File .\l1\tools\deploy.ps1
#   pwsh -File .\l1\tools\deploy.ps1 -Apk .\l1\build\r1brain-l1.apk -ChunkMB 3
#
# @author xudong.hua,deepseek
# @since 2026-10-30 23:10 星期五

[CmdletBinding()]
param(
    [string]$Apk = "",
    [string]$Device = "192.168.1.100:5555",
    [int]$ChunkMB = 4,
    [int]$Retries = 3
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if (-not $Apk) { $Apk = Join-Path $Root "build\r1brain-l1.apk" }
if (-not (Test-Path $Apk)) { throw "找不到 APK：$Apk" }

$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
$Adb = Join-Path $Sdk "platform-tools\adb.exe"
if (-not (Test-Path $Adb)) { $Adb = "adb" }

function Say($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m) { Write-Host "    $m" -ForegroundColor Green }

# ---------------------------------------------------------------- 连接
Say "连接设备 $Device"
$connected = $false
for ($i = 1; $i -le 10; $i++) {
    & $Adb disconnect $Device 2>$null | Out-Null
    Start-Sleep 1
    & $Adb connect $Device 2>$null | Out-Null
    Start-Sleep 2
    $state = (& $Adb -s $Device get-state 2>&1) -join ""
    if ($state -match "device$") { $connected = $true; break }
    Write-Host "    第 $i 次未就绪（$state），2 秒后重试"
    Start-Sleep 2
}
if (-not $connected) {
    throw "adb 连不上。若 5555 端口能连通但一直 offline，说明 adbd 已卡死——只能给音箱断电重插。"
}
Ok "已连接"

# ---------------------------------------------------------------- 分片推送
$size = (Get-Item $Apk).Length
$localMd5 = (Get-FileHash $Apk -Algorithm MD5).Hash.ToLower()
Say "分片推送 $size 字节（每片 ${ChunkMB}MB，md5=$localMd5）"

$remoteDir = "/data/local/tmp/l1chunks"
& $Adb -s $Device shell "rm -rf $remoteDir && mkdir -p $remoteDir" | Out-Null

$fs = [System.IO.File]::OpenRead($Apk)
$chunkBytes = $ChunkMB * 1MB
$total = [math]::Ceiling($size / $chunkBytes)
$tmp = Join-Path $env:TEMP "l1chunk.bin"

try {
    for ($i = 0; $i -lt $total; $i++) {
        $offset = [int64]$i * $chunkBytes
        $len = [math]::Min($chunkBytes, $size - $offset)
        $chunk = New-Object byte[] $len
        $fs.Position = $offset
        $read = 0
        while ($read -lt $len) {
            $n = $fs.Read($chunk, $read, $len - $read)
            if ($n -le 0) { break }
            $read += $n
        }
        [System.IO.File]::WriteAllBytes($tmp, $chunk)

        $name = "part{0:d2}" -f $i
        $remote = "$remoteDir/$name"
        $done = $false
        for ($try = 1; $try -le $Retries; $try++) {
            $sw = [Diagnostics.Stopwatch]::StartNew()
            & $Adb -s $Device push $tmp $remote 2>&1 | Out-Null
            $sw.Stop()

            $remoteLen = (& $Adb -s $Device shell "busybox wc -c < $remote 2>/dev/null" 2>&1) -join ""
            $remoteLen = ($remoteLen -replace '[^0-9]', '')
            if ($remoteLen -eq "$len") {
                Ok "片 $($i+1)/$total  $len 字节  $([math]::Round($sw.Elapsed.TotalSeconds,1))s"
                $done = $true
                break
            }
            Write-Host "    片 $($i+1) 第 $try 次校验不符（远端 $remoteLen / 期望 $len），重试" -ForegroundColor Yellow
            & $Adb disconnect $Device 2>$null | Out-Null
            & $Adb connect $Device 2>$null | Out-Null
            Start-Sleep 2
        }
        if (-not $done) { throw "片 $($i+1) 连续 $Retries 次失败，中止（已传的片会保留，重跑可续）" }
    }
}
finally {
    $fs.Close()
    Remove-Item $tmp -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------- 拼装 + 校验
Say "设备端拼装"
& $Adb -s $Device shell "cd $remoteDir && cat part* > /data/local/tmp/l1.apk && busybox md5sum /data/local/tmp/l1.apk" 2>&1 | ForEach-Object { Ok $_ }
$verify = (& $Adb -s $Device shell "busybox md5sum /data/local/tmp/l1.apk" 2>&1) -join ""
if ($verify -notmatch $localMd5) { throw "md5 不匹配：$verify" }
Ok "md5 一致"

# ---------------------------------------------------------------- 安装
Say "本机安装（后台执行，避免 adb 断线影响）"
& $Adb -s $Device shell "cd /data/local/tmp && rm -f install.log && nohup sh -c 'pm install -r /data/local/tmp/l1.apk > /data/local/tmp/install.log 2>&1' >/dev/null 2>&1 &" | Out-Null

$result = ""
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep 5
    $result = (& $Adb -s $Device shell "cat /data/local/tmp/install.log 2>/dev/null" 2>&1) -join "`n"
    if ($result -match "Success|Failure|Error") { break }
    Write-Host "    等待安装完成…（$([math]::Round(($i+1)*5))s）"
}
if ($result -notmatch "Success") { throw "安装失败：$result" }
Ok "安装成功"
