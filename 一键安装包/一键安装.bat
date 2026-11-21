@echo off
chcp 65001 >nul
setlocal ENABLEDELAYEDEXPANSION
title 斐讯 R1 · 黑金声学控制台 一键安装工具 v1.0
cd /d "%~dp0"

color 0B
echo ============================================================
echo        斐讯 R1 · 黑金高保真声学控制台 一键安装工具
echo ============================================================
echo.

if not exist "adb.exe" (
    color 0C
    echo [错误] 当前目录下未找到 adb.exe，请确保解压完整的一键安装包！
    pause
    exit /b 1
)

set "APK_FILE=apps\r1brain.apk"
if not exist "%APK_FILE%" (
    color 0C
    echo [错误] 未找到安装包 %APK_FILE% ！
    pause
    exit /b 1
)

:input_ip
color 0B
set "USER_IP="
set /p USER_IP=请输入斐讯 R1 音箱的局域网 IP 地址 (例如 192.168.1.100): 
set "USER_IP=!USER_IP: =!"
if "!USER_IP!"=="" (
    color 0C
    echo [提示] IP 地址不能为空，请重新输入！
    echo.
    goto input_ip
)

:: 去除可能附带的 :5555 后缀，统一提取纯 IP
for /f "tokens=1 delims=:" %%a in ("!USER_IP!") do set "RAW_IP=%%a"
set "DEV=!RAW_IP!:5555"

echo.
echo [1/4] 正在连接斐讯 R1 音箱 (!DEV!) ...
adb.exe connect !DEV! >nul 2>&1
ping 127.0.0.1 -n 2 >nul

set "CONN_STATE="
for /f "tokens=1 delims=" %%i in ('adb.exe -s !DEV! get-state 2^>nul') do set "CONN_STATE=%%i"
if /i not "!CONN_STATE!"=="device" (
    echo 首次连接未响应，正在重启 ADB 服务并重试连接...
    adb.exe kill-server >nul 2>&1
    ping 127.0.0.1 -n 2 >nul
    adb.exe connect !DEV!
    ping 127.0.0.1 -n 2 >nul
    for /f "tokens=1 delims=" %%i in ('adb.exe -s !DEV! get-state 2^>nul') do set "CONN_STATE=%%i"
)

if /i not "!CONN_STATE!"=="device" (
    color 0C
    echo.
    echo [连接失败] 无法连接到音箱 !DEV!
    echo 请检查：
    echo   1. 音箱是否已开机并连入与电脑相同的局域网 Wi-Fi
    echo   2. 输入的 IP 地址 (!RAW_IP!) 是否正确
    echo   3. 音箱 ADB 调试端口 (5555) 是否已开启
    echo.
    goto input_ip
)

echo [连接成功] 已连上斐讯 R1 音箱，正在初始化安装权限...
adb.exe -s !DEV! shell settings put secure install_non_market_apps 1 >nul 2>&1

echo.
echo [2/4] 正在上传控制台安装包 (%APK_FILE%) 到音箱，请稍候...
adb.exe -s !DEV! push "%APK_FILE%" /data/local/tmp/r1brain.apk
if errorlevel 1 (
    color 0C
    echo [上传失败] 安装包传输中断，请按任意键重试！
    pause >nul
    goto input_ip
)

echo.
echo [3/4] 上传完成，正在音箱后台执行静默安装 (约需 10~15 秒)...
:: 规避 R1 音箱 adb push 后首次 shell 易断开的问题：主动重连并后台执行 pm install
adb.exe disconnect !DEV! >nul 2>&1
ping 127.0.0.1 -n 2 >nul
adb.exe connect !DEV! >nul 2>&1
ping 127.0.0.1 -n 2 >nul

adb.exe -s !DEV! shell "rm -f /data/local/tmp/r1_install.log; cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/r1brain.apk > /data/local/tmp/r1_install.log 2>&1' >/dev/null 2>&1 &" >nul 2>&1

set "INSTALL_OK=0"
set "SIGN_CONFLICT=0"
for /l %%n in (1,1,25) do (
    if "!INSTALL_OK!"=="0" (
        ping 127.0.0.1 -n 3 >nul
        adb.exe connect !DEV! >nul 2>&1
        for /f "delims=" %%L in ('adb.exe -s !DEV! shell "cat /data/local/tmp/r1_install.log 2>/dev/null"') do (
            echo %%L | findstr /i "Success" >nul && set "INSTALL_OK=1"
            echo %%L | findstr /i "INSTALL_FAILED_UPDATE_INCOMPATIBLE signatures" >nul && set "SIGN_CONFLICT=1"
        )
    )
)

if "!SIGN_CONFLICT!"=="1" (
    echo 检测到旧版签名冲突，正在自动卸载旧版并重新安装...
    adb.exe -s !DEV! shell "pm uninstall com.r1.brain" >nul 2>&1
    adb.exe -s !DEV! shell "rm -f /data/local/tmp/r1_install.log; cd /data/local/tmp && nohup sh -c 'pm install -r /data/local/tmp/r1brain.apk > /data/local/tmp/r1_install.log 2>&1' >/dev/null 2>&1 &" >nul 2>&1
    for /l %%n in (1,1,20) do (
        if "!INSTALL_OK!"=="0" (
            ping 127.0.0.1 -n 3 >nul
            adb.exe connect !DEV! >nul 2>&1
            for /f "delims=" %%L in ('adb.exe -s !DEV! shell "cat /data/local/tmp/r1_install.log 2>/dev/null"') do (
                echo %%L | findstr /i "Success" >nul && set "INSTALL_OK=1"
            )
        )
    )
)

if not "!INSTALL_OK!"=="1" (
    color 0C
    echo [安装失败] 未检测到安装成功标记，音箱返回日志如下：
    adb.exe -s !DEV! shell "cat /data/local/tmp/r1_install.log"
    adb.exe -s !DEV! shell "rm -f /data/local/tmp/r1brain.apk /data/local/tmp/r1_install.log" >nul 2>&1
    echo.
    pause
    goto input_ip
)

echo.
echo [4/4] 正在清理临时文件并启动 R1 智脑声学服务...
adb.exe -s !DEV! shell "am startservice -n com.r1.brain/.MainService; rm -f /data/local/tmp/r1brain.apk /data/local/tmp/r1_install.log" >nul 2>&1
ping 127.0.0.1 -n 3 >nul

:: 首次使用无需令牌；如需鉴权，到控制台设置页「访问令牌」自行设置
set "OPEN_URL=http://!RAW_IP!:8686"

color 0A
echo.
echo ============================================================
echo   [安装成功] 斐讯 R1 · 黑金声学控制台已成功部署并启动！
echo ------------------------------------------------------------
echo   电脑 / 手机浏览器控制台地址:  !OPEN_URL!
echo   首次使用无需令牌；如需鉴权，请到设置页「访问令牌」设置
echo   (服务已设为开机自动启动，无需重启音箱即可直接使用)
echo ============================================================
echo.
set "OPEN_WEB=Y"
set /p OPEN_WEB=是否立即在浏览器中打开控制台页面？[Y/n]: 
if /i not "!OPEN_WEB!"=="n" (
    start "" "!OPEN_URL!"
)
echo.
echo 按任意键退出安装程序...
pause >nul
exit /b 0
