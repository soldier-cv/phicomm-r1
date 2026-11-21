# 系统应用「空壳替换」构建脚本
#
# 用途：把斐讯遗留的系统应用（OTA / 上报 / 运维）替换成同名的空壳实现。
#
# 为什么是替换而不是删除：
#   · 删包会让任何 startService / bindService / 广播调用抛出 NameNotFoundException；
#   · 替换成空壳后包还在、组件还在，契约不破，但内部什么都不做。
#
# 原理（与 L1 相同）：原 APK 的 zip 一字节不动（保住平台签名），在文件头前置一个
# 只含空壳类的 DEX。安装时 PackageManager 认签名，运行时 ART 加载前置 DEX。
#
# 用法：
#   pwsh -File .\sysapps\build.ps1 -App otaservice
#
# @author xudong.hua,deepseek
# @since 2026-10-31 00:55 星期六

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$App,
    [switch]$SkipVerify
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Repo = Split-Path -Parent $Root
$Build = Join-Path $Root "build"
$SrcDir = Join-Path $Root "src\$App"
$VendorDir = Join-Path $Repo "artifacts\removed-apps"
$PkgName = "com.phicomm.speaker.$App"
$VendorApk = Join-Path $VendorDir "$PkgName.apk"

function Write-Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Write-Ok($m) { Write-Host "    $m" -ForegroundColor Green }

if (-not (Test-Path $SrcDir)) { throw "找不到空壳源码目录：$SrcDir" }
if (-not (Test-Path $VendorApk)) { throw "找不到原厂 APK：$VendorApk" }

# ---- 工具 ----
$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
$AndroidJar = Join-Path $Sdk "platforms\android-34\android.jar"
$D8Jar = Join-Path $Sdk "build-tools\36.0.0\lib\d8.jar"
foreach ($p in @($AndroidJar, $D8Jar)) { if (-not (Test-Path $p)) { throw "缺少工具：$p" } }

New-Item -ItemType Directory -Force -Path $Build | Out-Null
$Classes = Join-Path $Build "classes-$App"
if (Test-Path $Classes) { Remove-Item $Classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Classes | Out-Null

# ---- 1. 编译空壳 ----
Write-Step "1/5 编译空壳类（$App）"
$srcs = Get-ChildItem $SrcDir -Recurse -Filter *.java | ForEach-Object FullName
javac -source 8 -target 8 -encoding UTF-8 -nowarn -cp "$AndroidJar" -d $Classes $srcs
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }
$classFiles = Get-ChildItem $Classes -Recurse -Filter *.class | ForEach-Object FullName
Write-Ok "$($classFiles.Count) 个 class"

# ---- 2. 生成 dex ----
Write-Step "2/5 生成 DEX"
$DexDir = Join-Path $Build "dex-$App"
if (Test-Path $DexDir) { Remove-Item $DexDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $DexDir | Out-Null
java -cp "$D8Jar" com.android.tools.r8.D8 --lib "$AndroidJar" --min-api 22 --output $DexDir $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 失败" }
$PayloadDex = Join-Path $DexDir "classes.dex"
Write-Ok "classes.dex $((Get-Item $PayloadDex).Length) 字节"

# ---- 3. Janus 套壳 ----
Write-Step "3/5 Janus 套壳（原 APK 不动，前置我们的 DEX）"
$OutApk = Join-Path $Build "$PkgName-stub.apk"
python (Join-Path $Repo "l1\tools\build_janus.py") $VendorApk $PayloadDex $OutApk
if ($LASTEXITCODE -ne 0) { throw "Janus 构建失败" }

# ---- 4. 自检 ----
if (-not $SkipVerify) {
    Write-Step "4/5 自检"
    python (Join-Path $Repo "l1\tools\verify_janus.py") $OutApk $VendorApk
    if ($LASTEXITCODE -ne 0) { throw "自检失败" }
} else {
    Write-Step "4/5 跳过自检"
}

# ---- 5. 汇总 ----
Write-Step "5/5 完成"
$size = (Get-Item $OutApk).Length
Write-Host ""
Write-Host "包名：$PkgName" -ForegroundColor Yellow
Write-Host "产物：$OutApk（$size 字节）" -ForegroundColor Yellow
Write-Host "安装：adb install -r `"$OutApk`"" -ForegroundColor Yellow
Write-Host "回滚：adb shell pm uninstall $PkgName   （回落系统原版）" -ForegroundColor Yellow
