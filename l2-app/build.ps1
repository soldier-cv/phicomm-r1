<#
.SYNOPSIS
    编译 + 签名 L2 App（不依赖 Gradle），并自动更新到 一键安装包\apps\r1brain.apk。

.DESCRIPTION
    流水线：javac → d8 → aapt2 link → 塞 classes.dex → zipalign → apksigner
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt  = "$sdk\build-tools\36.0.0"
$jar = "$sdk\platforms\android-36\android.jar"
$repoRoot = Split-Path $PSScriptRoot -Parent

foreach ($p in @("$bt\d8.bat", "$bt\aapt2.exe", "$bt\zipalign.exe", "$bt\apksigner.bat", $jar)) {
    if (-not (Test-Path $p)) { throw "缺少构建工具: $p" }
}

$root = $PSScriptRoot
$out  = Join-Path $root "build"
$cls  = Join-Path $out "classes"

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $cls | Out-Null

Write-Host "[1/6] javac" -ForegroundColor Cyan
$sources = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $jar -d $cls $sources 2>&1 |
    Where-Object { $_ -notmatch '已过时|deprecat' } | ForEach-Object { Write-Host "  $_" }
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

Write-Host "[2/6] d8 -> classes.dex" -ForegroundColor Cyan
# 类一多，直接把一长串 .class 路径传给 d8 会超过 Windows 命令行长度上限
# （2026-11-04 加 AsrStream/WsClient 后就报了「The command line is too long」）。
# 先打包成 jar，再把 jar 交给 d8。
$classJar = Join-Path $out "classes.jar"
& jar --create --file $classJar -C $cls . 2>&1 | ForEach-Object { Write-Host "  $_" }
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $classJar)) { throw "jar 打包 class 失败" }
& "$bt\d8.bat" --min-api 22 --lib $jar --output $out $classJar 2>&1 | ForEach-Object { Write-Host "  $_" }
if (-not (Test-Path "$out\classes.dex")) { throw "d8 没产出 classes.dex" }

Write-Host "[3/6] aapt2 link" -ForegroundColor Cyan
# 网页源码在 web/（src 是源，desktop/mobile/assets 是 build-pages.js 的产物）。
# 这里重新生成一次，保证 APK 里的页面与 web/src 一致；没有 Node 时退回使用已有产物。
$webDir = Join-Path (Split-Path $root -Parent) "web"
if (Test-Path $webDir) {
    $node = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($node) {
        Push-Location $webDir
        try {
            & node build-pages.js
            if ($LASTEXITCODE -ne 0) { throw "web/build-pages.js 生成失败" }
        } finally {
            Pop-Location
        }
    } elseif (-not (Test-Path (Join-Path $webDir "desktop\home.html"))) {
        throw "缺少 web/desktop 页面产物，且本机没有 node；请先安装 Node 或把 web/ 产物一起带上"
    } else {
        Write-Host "  未找到 node，直接使用 web/ 下已有页面产物" -ForegroundColor Yellow
    }
}

# 把 web/ 的成品复制进 APK assets（l2-app/assets 是构建产物，已加入 .gitignore）
$assets = Join-Path $root "assets"
if (Test-Path $assets) { Remove-Item $assets -Recurse -Force }
New-Item -ItemType Directory -Path $assets | Out-Null
if (Test-Path $webDir) {
    foreach ($sub in @("assets", "desktop", "mobile")) {
        $src = Join-Path $webDir $sub
        if (-not (Test-Path $src)) { throw "缺少网页资源目录: $src" }
        Copy-Item $src (Join-Path $assets $sub) -Recurse -Force
    }
    $rootFavicon = Join-Path $webDir "favicon.ico"
    if (Test-Path $rootFavicon) { Copy-Item $rootFavicon (Join-Path $assets "favicon.ico") -Force }
    Write-Host "  已把 web/ 下的 desktop(4页) + mobile(4页) + assets 打进 APK" -ForegroundColor DarkGray
} else {
    throw "缺少 web/ 目录，无法打包网页控制台"
}
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar `
    -A $assets `
    --manifest (Join-Path $root "AndroidManifest.xml") `
    --min-sdk-version 22 --target-sdk-version 22 2>&1 | ForEach-Object { Write-Host "  $_" }
if (-not (Test-Path "$out\base.apk")) { throw "aapt2 失败" }

# aapt2 在 Windows 上会把资源条目名写成反斜杠（assets/desktop\home.html），
# 而 Android 的 AssetManager 只认正斜杠。不改名的话，设备上所有页面都会
# 落到「没有这个接口」的 404 分支。这里重写一遍条目名。
Write-Host "[3.5/6] 规范化资源条目名（反斜杠 → 正斜杠）" -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$fixTmp = "$out\base_fixed.apk"
$srcZip = [System.IO.Compression.ZipFile]::OpenRead("$out\base.apk")
$dstZip = [System.IO.Compression.ZipFile]::Open($fixTmp, 'Create')
try {
    foreach ($e in $srcZip.Entries) {
        $name = $e.FullName.Replace('\', '/')
        if ($name.EndsWith('/')) { continue }
        $ne = $dstZip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
        $si = $e.Open()
        $di = $ne.Open()
        try { $si.CopyTo($di) } finally { $di.Dispose(); $si.Dispose() }
    }
}
finally {
    $srcZip.Dispose()
    $dstZip.Dispose()
}
Remove-Item "$out\base.apk" -Force
Move-Item $fixTmp "$out\base.apk" -Force

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
$ks = Join-Path $root "r1brain.jks"
if (-not (Test-Path $ks)) {
    Write-Host "  生成密钥库（只做一次）" -ForegroundColor Yellow
    & keytool -genkeypair -keystore $ks -alias r1brain -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass android -keypass android -dname "CN=r1brain, O=r1" 2>&1 |
        Select-Object -First 1 | ForEach-Object { Write-Host "  $_" }
}
$apk = Join-Path $out "r1brain.apk"
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --min-sdk-version 22 --out $apk "$out\aligned.apk"
if (-not (Test-Path $apk)) { throw "签名失败" }

$size = [math]::Round((Get-Item $apk).Length / 1KB, 1)
Write-Host ""
Write-Host "产物: $apk  ($size KB)" -ForegroundColor Green

# 自动同步最新 APK 到 一键安装包\apps\r1brain.apk
$distApps = Join-Path $repoRoot "一键安装包\apps"
if (Test-Path (Join-Path $repoRoot "一键安装包")) {
    New-Item -ItemType Directory -Force -Path $distApps | Out-Null
    Copy-Item $apk (Join-Path $distApps "r1brain.apk") -Force
    Write-Host "已同步至一键安装包: $distApps\r1brain.apk" -ForegroundColor Cyan
}
