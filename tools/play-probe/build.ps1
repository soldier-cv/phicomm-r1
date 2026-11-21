<#
.SYNOPSIS
    编译 + 签名「MediaPlayer 出声探针」（不依赖 Gradle）。

.DESCRIPTION
    只为一件事服务：在真机上验证普通 App 的 MediaPlayer 能不能出声（V1 / O-14）。
    装坏了 pm uninstall com.r1.playprobe 就没了，不碰原厂、不碰 L2。

.EXAMPLE
    .\build.ps1 -Install
#>
[CmdletBinding()]
param(
    [switch]$Install,
    [string]$Ip = ""
)

$ErrorActionPreference = 'Stop'

$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt  = "$sdk\build-tools\36.0.0"
$jar = "$sdk\platforms\android-36\android.jar"
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$bundledAdb = Join-Path $repoRoot "一键安装包\adb.exe"
$cmdAdb = Get-Command adb.exe -ErrorAction SilentlyContinue
$adb = if (Test-Path $bundledAdb) { $bundledAdb } elseif ($cmdAdb) { $cmdAdb.Source } else { "$sdk\platform-tools\adb.exe" }

foreach ($p in @("$bt\d8.bat", "$bt\aapt2.exe", "$bt\zipalign.exe", "$bt\apksigner.bat", $jar)) {
    if (-not (Test-Path $p)) { throw "缺少构建工具: $p" }
}

$root = $PSScriptRoot
$out  = Join-Path $root "build"
$cls  = Join-Path $out "classes"
$assets = Join-Path $root "assets"

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $cls | Out-Null

if (-not (Test-Path "$assets\probe.wav")) { throw "缺少 assets/probe.wav" }

Write-Host "[1/6] javac" -ForegroundColor Cyan
$sources = Get-ChildItem -Path $root -Filter *.java -File | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $jar -d $cls $sources 2>&1 |
    ForEach-Object { Write-Host "  $_" }
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

Write-Host "[2/6] d8 -> classes.dex" -ForegroundColor Cyan
$classFiles = Get-ChildItem $cls -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --min-api 22 --lib $jar --output $out $classFiles 2>&1 | ForEach-Object { Write-Host "  $_" }
if (-not (Test-Path "$out\classes.dex")) { throw "d8 没产出 classes.dex" }

Write-Host "[3/6] aapt2 link" -ForegroundColor Cyan
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar `
    -A $assets `
    --manifest (Join-Path $root "AndroidManifest.xml") `
    --min-sdk-version 22 --target-sdk-version 22 2>&1 | ForEach-Object { Write-Host "  $_" }
if (-not (Test-Path "$out\base.apk")) { throw "aapt2 失败" }

Write-Host "[4/6] 塞入 classes.dex" -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\base.apk", 'Update')
$entry = $zip.GetEntry("classes.dex")
if ($entry) { $entry.Delete() }
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\classes.dex", "classes.dex") | Out-Null
$zip.Dispose()

Write-Host "[5/6] zipalign" -ForegroundColor Cyan
& "$bt\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign 失败" }

Write-Host "[6/6] 签名" -ForegroundColor Cyan
$ks = Join-Path (Split-Path $root -Parent) "..\l2-app\r1brain.jks"
$ks = [System.IO.Path]::GetFullPath($ks)
if (-not (Test-Path $ks)) { throw "缺少密钥库: $ks" }
$apk = Join-Path $out "playprobe.apk"
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --min-sdk-version 22 --out $apk "$out\aligned.apk"
if (-not (Test-Path $apk)) { throw "签名失败" }

$size = [math]::Round((Get-Item $apk).Length / 1KB, 1)
Write-Host ""
Write-Host "产物: $apk  ($size KB)" -ForegroundColor Green

if ($Install) {
    while ([string]::IsNullOrWhiteSpace($Ip)) {
        $Ip = (Read-Host "请输入斐讯 R1 音箱的局域网 IP 地址").Trim()
    }
    $Ip = $Ip -replace ':5555$', ''
    Write-Host ""
    Write-Host "安装到 $Ip ..." -ForegroundColor Cyan
    $dev = "$Ip`:5555"
    & $adb connect $dev | Out-Null
    Start-Sleep -Seconds 1
    & $adb -s $dev push $apk /data/local/tmp/playprobe.apk 2>&1 | Select-Object -Last 1
    & $adb disconnect $dev | Out-Null
    Start-Sleep -Milliseconds 800
    & $adb connect $dev | Out-Null
    Start-Sleep -Milliseconds 800
    & $adb -s $dev shell "cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/playprobe.apk > /data/local/tmp/i.log 2>&1' >/dev/null 2>&1 &" | Out-Null
    Start-Sleep -Seconds 12
    $logText = ((& $adb -s $dev shell "cat /data/local/tmp/i.log") -join "`n")
    Write-Host $logText
    if ($logText -notmatch 'Success') { throw "安装失败，看上面输出" }
    Write-Host "安装成功" -ForegroundColor Green
}
