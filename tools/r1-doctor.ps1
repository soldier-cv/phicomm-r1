<#
.SYNOPSIS
    斐讯 R1 音箱体检：连接、系统信息、端口、进程、安装记录。

.PARAMETER Ip
    音箱局域网 IP（不传则交互提示用户输入）。

.PARAMETER Adb
    adb.exe 路径，不填则自动查找。
#>
[CmdletBinding()]
param(
    [string]$Ip = "",
    [string]$Adb
)

while ([string]::IsNullOrWhiteSpace($Ip)) {
    $Ip = (Read-Host "请输入斐讯 R1 音箱的局域网 IP 地址").Trim()
}
$Ip = $Ip -replace ':5555$', ''
$Dev = "$Ip`:5555"
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not $Adb) {
    $bundled = Join-Path $repoRoot "一键安装包\adb.exe"
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    $Adb = if (Test-Path $bundled) { $bundled } elseif ($cmd) { $cmd.Source } else { "" }
}
if (-not (Test-Path $Adb)) { throw "找不到 adb.exe" }

function Say($title) {
    Write-Host ""
    Write-Host ("==== {0} ====" -f $title) -ForegroundColor Cyan
}

function Invoke-Shell([string]$script) {
    for ($i = 1; $i -le 3; $i++) {
        $out = & $Adb -s $Dev shell $script 2>&1 | Out-String
        if ($out -notmatch 'error: closed') { return $out.Trim() }
        & $Adb disconnect $Dev *> $null
        Start-Sleep -Milliseconds 800
        & $Adb connect $Dev *> $null
        Start-Sleep -Milliseconds 500
    }
    return "ERROR: closed"
}

Say "连接"
& $Adb connect $Dev | Out-Null
$state = (& $Adb -s $Dev get-state 2>&1 | Out-String).Trim()
Write-Host "状态：$state"
if ($state -ne 'device') { throw "连不上 $Dev" }

Say "系统"
Write-Host (Invoke-Shell "getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.display.id; getprop net.hostname")

Say "运行时间 / 时间"
Write-Host (Invoke-Shell "uptime; date")

Say "内存"
Write-Host (Invoke-Shell "cat /proc/meminfo | grep -E 'MemTotal|MemFree|MemAvailable'")

Say "存储（/data）"
Write-Host (Invoke-Shell "df /data")

Say "监听端口"
Write-Host (Invoke-Shell "netstat -tln | grep LISTEN")

Say "phicomm 相关进程"
Write-Host (Invoke-Shell "ps | grep -i phicomm")

Say "MOD 安装记录"
Write-Host (Invoke-Shell "dumpsys package com.phicomm.speaker.device | grep -iE 'versionName|lastUpdateTime|codePath|signatures'")

Say "配置网页（8080）探测"
try {
    $r = Invoke-WebRequest "http://${Ip}:8080/" -TimeoutSec 5 -UseBasicParsing
    $title = if ($r.Content -match '<title>(.*?)</title>') { $Matches[1] } else { '(无标题)' }
    Write-Host "HTTP $($r.StatusCode) — $title"
} catch {
    Write-Host "打不开：$_" -ForegroundColor Yellow
}

Say "设备上残留的临时文件"
Write-Host (Invoke-Shell "ls -l /data/local/tmp/")

Write-Host ""
Write-Host "体检完成" -ForegroundColor Green
