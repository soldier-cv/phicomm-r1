<#
.SYNOPSIS
    静默安装 APK 到斐讯 R1 音箱（规避 adb push 后连接被断的问题）。

.DESCRIPTION
    R1 上的 adbd 在 adb push 完成后会丢掉连接，紧接着的 adb shell 必然报
    error: closed。本脚本把 pm install 放到设备端后台执行，再轮询日志取结果，
    并带上传进度条与断线重连重试。

.PARAMETER Ip
    音箱局域网 IP（不传则交互提示用户输入）。

.PARAMETER Apk
    要安装的 APK 路径（本地，不传则默认使用 l2-app\build\r1brain.apk）。

.PARAMETER Adb
    adb.exe 路径。默认取项目内 一键安装包\adb.exe 或 PATH 中的 adb。

.PARAMETER SkipUpload
    设备上已有 /data/local/tmp/tmp.apk 时直接用它，不再上传。

.EXAMPLE
    .\install-apk.ps1
#>
[CmdletBinding()]
param(
    [string]$Ip = "",
    [string]$Apk = "",
    [string]$Adb,
    [switch]$SkipUpload
)

$ErrorActionPreference = 'Continue'
$repoRoot = Split-Path $PSScriptRoot -Parent
if ([string]::IsNullOrWhiteSpace($Apk)) {
    $Apk = Join-Path $repoRoot "l2-app\build\r1brain.apk"
}
while ([string]::IsNullOrWhiteSpace($Ip)) {
    $Ip = (Read-Host "请输入斐讯 R1 音箱的局域网 IP 地址").Trim()
}
$Ip = $Ip -replace ':5555$', ''
$Dev = "$Ip`:5555"
$RemoteApk = '/data/local/tmp/tmp.apk'
$RemoteLog = '/data/local/tmp/install.log'

function Resolve-Adb {
    if ($Adb -and (Test-Path $Adb)) { return $Adb }
    $bundled = Join-Path $repoRoot "一键安装包\adb.exe"
    if (Test-Path $bundled) { return $bundled }
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "找不到 adb.exe，请用 -Adb 指定"
}
$Adb = Resolve-Adb

function Log([string]$msg) {
    Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $msg)
}

# 跑一条 shell 命令，遇到 error: closed 就断开重连再试一次
function Invoke-Shell([string]$script, [switch]$Raw) {
    for ($try = 1; $try -le 2; $try++) {
        $out = & $Adb -s $Dev shell $script 2>&1 | Out-String
        if ($out -notmatch 'error: closed') { return $out.Trim() }
        Log "连接被断开（error: closed），重连后重试（第 $try 次）"
        & $Adb disconnect $Dev *> $null
        Start-Sleep -Seconds 1
        & $Adb connect $Dev *> $null
        Start-Sleep -Milliseconds 800
    }
    throw "命令反复被断开：$script"
}

# ---- 0. 前置检查 ----
if (-not (Test-Path $Apk)) { throw "APK 不存在：$Apk" }
$apkItem = Get-Item $Apk
Log ("目标 APK: {0} ({1:N0} 字节)" -f $apkItem.Name, $apkItem.Length)

Log "连接 $Dev"
& $Adb connect $Dev | Out-Null
$state = (& $Adb -s $Dev get-state 2>&1 | Out-String).Trim()
if ($state -ne 'device') { throw "连不上音箱（状态：$state）。检查 IP 和 Wi-Fi。" }

$model = Invoke-Shell 'getprop ro.product.model'
Log "已连接：$model"

# ---- 1. 上传（可跳过） ----
$needUpload = $true
$existing = Invoke-Shell "ls -l $RemoteApk"
$sizeOnDevice = if ($existing -match '(\d+)\s+\d{4}-\d\d-\d\d') { [long]$Matches[1] } else { 0 }
if ($sizeOnDevice -eq $apkItem.Length) {
    if ($SkipUpload) {
        Log "设备上已有同尺寸的 $RemoteApk，按 -SkipUpload 直接使用"
        $needUpload = $false
    } else {
        Log "设备上已有同尺寸的 $RemoteApk（上次上传残留），本次仍重新上传；要省流量可加 -SkipUpload"
    }
}

if ($needUpload) {
    $total = $apkItem.Length
    Log ("开始上传，{0:N0} 字节（无线约 0.2 MB/s，预计 3 分钟）" -f $total)
    & $Adb disconnect $Dev *> $null
    Start-Sleep -Milliseconds 500
    & $Adb connect $Dev | Out-Null

    $sw = [Diagnostics.Stopwatch]::StartNew()
    $job = Start-Job -ScriptBlock {
        param($adb, $dev, $apk, $remote)
        & $adb -s $dev push $apk $remote 2>&1
    } -ArgumentList $Adb, $Dev, $apkItem.FullName, $RemoteApk

    while ($job.State -eq 'Running') {
        Start-Sleep -Seconds 2
        $size = 0
        try {
            $raw = & $Adb -s $Dev shell "ls -l $RemoteApk" 2>&1 | Out-String
            if ($raw -match '(\d+)\s+\d{4}-\d\d-\d\d') { $size = [long]$Matches[1] }
        } catch { }
        $pct = [Math]::Min(100, [int]($size * 100 / $total))
        $mb = [Math]::Round($size / 1MB, 1)
        $totMb = [Math]::Round($total / 1MB, 1)
        $speed = if ($sw.Elapsed.TotalSeconds -gt 0) { [Math]::Round($size / 1MB / $sw.Elapsed.TotalSeconds, 2) } else { 0 }
        $barLen = [int]($pct / 4)
        $bar = ('#' * $barLen).PadRight(25, '-')
        Write-Host ("`r  [{0}] {1,3}%  {2}/{3} MB  {4} MB/s  ({5}s)" -f $bar, $pct, $mb, $totMb, $speed, [int]$sw.Elapsed.TotalSeconds) -NoNewline
    }
    Write-Host ""
    $pushOut = Receive-Job $job 2>&1 | Out-String
    Remove-Job $job -Force
    if ($pushOut -notmatch '1 file pushed') {
        Log "上传可能失败，输出：$pushOut"
        throw "上传失败"
    }
    Log "上传完成（$([int]$sw.Elapsed.TotalSeconds) 秒）"
}

# ---- 2. 设备端后台安装 ----
Log "在设备端后台执行 pm install"
& $Adb disconnect $Dev *> $null
& $Adb connect $Dev | Out-Null
Start-Sleep -Milliseconds 500
Invoke-Shell "rm -f $RemoteLog" | Out-Null
Invoke-Shell "cd /data/local/tmp && nohup sh -c 'pm install -r $RemoteApk > $RemoteLog 2>&1' >/dev/null 2>&1 &" | Out-Null

# ---- 3. 轮询结果 ----
$result = ""
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $result = Invoke-Shell "cat $RemoteLog"
        if ($result -match 'Success|Failure|INSTALL_FAILED') { break }
    } catch {
        Log "读取安装日志时连接断开，继续重试"
    }
}

Write-Host ""
$result
Write-Host ""

if ($result -match 'Success') {
    Log "安装成功"
    Invoke-Shell "rm -f $RemoteLog $RemoteApk" | Out-Null
    if ($apkItem.Name -match 'r1brain') {
        Invoke-Shell "am startservice -n com.r1.brain/.MainService" | Out-Null
        Log "已启动 R1 智脑控制台服务：http://$Ip`:8686"
    }
} elseif ($result -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
    Log "签名冲突：请先卸载旧版本再安装"
    throw "签名冲突"
} else {
    Log "安装失败，原始输出见上"
    throw "安装失败"
}
