# 分块上传 APK 到音箱并触发安装（不依赖 adb）
#
# 背景：PC 到音箱这条链路只有 20~50KB/s，一次性传 30MB 必然被读超时打断。
# 协议：POST /api/v1/apps/install?token=..&offset=N&total=T
#       服务端按 offset 落盘，最后一块到达后自动 pm install。
#
# 用法：
#   pwsh -File .\tools\upload-apk.ps1 -Apk .\l1\build\r1brain-l1.apk -Token 7ae2577bf4014f0a
#
# @author xudong.hua,deepseek
# @since 2026-10-30 23:30 星期五

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Target = "192.168.1.100",
    [int]$Port = 8686,
    [Parameter(Mandatory = $true)][string]$Token,
    [int]$ChunkKB = 1024,
    [int]$Retries = 6
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $Apk)) { throw "找不到文件：$Apk" }

$size = (Get-Item $Apk).Length
$chunkBytes = $ChunkKB * 1KB
$base = "http://${Target}:${Port}/api/v1/apps/install?token=$Token"

Write-Host "==> 上传 $Apk（$size 字节，每块 ${ChunkKB}KB）" -ForegroundColor Cyan

$fs = [System.IO.File]::OpenRead($Apk)
$tmp = Join-Path $env:TEMP "uploadchunk.bin"
$offset = 0
$sw = [Diagnostics.Stopwatch]::StartNew()

try {
    while ($offset -lt $size) {
        $len = [math]::Min($chunkBytes, $size - $offset)
        $buf = New-Object byte[] $len
        $fs.Position = $offset
        $read = 0
        while ($read -lt $len) {
            $n = $fs.Read($buf, $read, $len - $read)
            if ($n -le 0) { break }
            $read += $n
        }
        [System.IO.File]::WriteAllBytes($tmp, $buf)

        $url = "$base&offset=$offset&total=$size"
        $done = $false
        for ($try = 1; $try -le $Retries; $try++) {
            $resp = & curl.exe -s -X POST $url -H "Content-Type: application/octet-stream" `
                -H "Expect:" --data-binary "@$tmp" --max-time 300 2>&1
            try {
                $j = $resp | ConvertFrom-Json
            } catch {
                $j = $null
            }
            if ($j -and $j.ok -eq $false) {
                Write-Host "    块 @$offset 第 $try 次失败：$($j.error)" -ForegroundColor Yellow
            } elseif ($j -and $j.offset -ne $null) {
                $offset = [int64]$j.offset
                $pct = [math]::Round(100.0 * $offset / $size, 1)
                Write-Host ("    进度 {0}%  {1}/{2}  {3:N0}KB/s" -f $pct, $offset, $size,
                    (($offset) / 1024.0 / [math]::Max(1, $sw.Elapsed.TotalSeconds)))
                $done = $true
                if ($j.install) {
                    Write-Host "==> 安装结果" -ForegroundColor Cyan
                    Write-Host ($j.install | ConvertTo-Json -Depth 4)
                    if ($j.install.success -ne $true) { throw "安装失败" }
                }
                break
            } else {
                Write-Host "    块 @$offset 第 $try 次无有效响应" -ForegroundColor Yellow
            }
            Start-Sleep 2
        }
        if (-not $done) { throw "块 @$offset 连续 $Retries 次失败" }
    }
}
finally {
    $fs.Close()
    Remove-Item $tmp -ErrorAction SilentlyContinue
}

Write-Host ("==> 完成，用时 {0:N0}s" -f $sw.Elapsed.TotalSeconds) -ForegroundColor Green
