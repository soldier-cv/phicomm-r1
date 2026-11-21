#!/usr/bin/env python3
"""
R1 设备能力矩阵 —— 不安装任何东西，只用 adb 探测。

对每个候选能力回答三个问题：
  1. 相关系统服务/属性是否存在
  2. 当前 shell 身份能不能读
  3. 那个能力背后挂的是哪个系统包

用法：python r1-capability-matrix.py [ip]
"""

import subprocess
import sys
import re

IP = sys.argv[1] if len(sys.argv) > 1 else "192.168.1.100"
ADB = r"D:\BaiduNetdiskDownload\R1 MOD apk\一键安装 (20261007)\一键安装\adb.exe"
DEV = f"{IP}:5555"


def sh(cmd, tries=3):
    """跑设备端命令，遇到 error: closed 就重连重试。"""
    for _ in range(tries):
        r = subprocess.run([ADB, "-s", DEV, "shell", cmd],
                           capture_output=True, text=True, errors="replace")
        out = (r.stdout or "") + (r.stderr or "")
        if "error: closed" not in out:
            return out.strip()
        subprocess.run([ADB, "disconnect", DEV], capture_output=True)
        subprocess.run([ADB, "connect", DEV], capture_output=True)
    return "<连接反复断开>"


def probe(label, cmd, key=None, note=""):
    """跑一条探测命令，返回 (label, 原始输出, 判定)"""
    out = sh(cmd)
    ok = bool(out) and "not found" not in out and "Permission denied" not in out and "denied" not in out.lower()
    if key and ok:
        ok = key in out
    return label, out, ok, note


def show(section, probes):
    print(f"\n{'=' * 66}")
    print(f" {section}")
    print('=' * 66)
    for label, out, ok, note in probes:
        mark = "OK  " if ok else "NO  "
        print(f" [{mark}] {label}")
        if note:
            print(f"          {note}")
        if out:
            for line in out.splitlines()[:8]:
                print(f"          {line.strip()[:110]}")


def connect():
    subprocess.run([ADB, "connect", DEV], capture_output=True)
    state = subprocess.run([ADB, "-s", DEV, "get-state"],
                           capture_output=True, text=True).stdout.strip()
    if state != "device":
        sys.exit(f"连不上 {DEV}（状态：{state}）")
    print(f"已连接 {DEV}")


def main():
    connect()

    # ---------- 1. 屏幕与画面 ----------
    show("1. 屏幕 / 画面输出", [
        probe("屏幕分辨率与密度", "wm size; wm density"),
        probe("是否有显示活动", "dumpsys SurfaceFlinger --list | grep -i -E 'surface|layer' | head -5"),
        probe("亮屏状态", "dumpsys power | grep -E 'mWakefulness=|Display Power'"),
    ])

    # ---------- 2. 灯环 ----------
    show("2. 灯环（LED）", [
        probe("LED 内核节点", "ls /sys/class/leds/ 2>/dev/null"),
        probe("LED JNI 是否在位", "ls /system/lib/libledjni.so /vendor/lib/libledjni.so 2>/dev/null"),
        probe("原厂灯光控制包", "pm list packages | grep -i -E 'light|led'"),
    ])

    # ---------- 3. 按键 / 触摸 ----------
    show("3. 按键 / 触摸", [
        probe("输入设备清单", "ls /dev/input/"),
        probe("输入设备名称", "cat /proc/bus/input/devices | grep -E 'Name=|Handlers='"),
        probe("按键事件节点", "getevent -pl 2>/dev/null | grep -E 'KEY_|ABS_|name:' | head -20"),
    ])

    # ---------- 4. 音频通路 ----------
    show("4. 音频", [
        probe("音频 HAL / 服务", "dumpsys media.audio_flinger | head -20"),
        probe("当前输出设备", "dumpsys media.audio_policy | grep -E 'Device|active' | head -10"),
        probe("媒体会话", "dumpsys media_session | grep -E 'package|state' | head -10"),
        probe("音量流", "settings list system | grep -i volume"),
    ])

    # ---------- 5. 麦克风 ----------
    show("5. 麦克风 / 录音", [
        probe("录音设备节点", "ls -l /dev/snd/ 2>/dev/null"),
        probe("录音权限归属组", "cat /proc/bus/input/devices | grep -i -A1 -B3 mic"),
        probe("占着麦克风的进程", "dumpsys media.audio_flinger | grep -i -E 'input|record' | head -8"),
    ])

    # ---------- 6. 系统私有服务 ----------
    show("6. 系统私有服务（拿到就能改底层）", [
        probe("FXSystemPrivate 是否存在", "service list | grep -i -E 'FXSystemPrivate|IMessenger'"),
        probe("IMessenger 是否存在", "service list | grep -i -E 'IMessenger'"),
        probe("能读 boot prop 吗", "getprop | grep -i -E 'boot|serialno' | head -8"),
    ])

    # ---------- 7. 蓝牙 ----------
    show("7. 蓝牙", [
        probe("蓝牙是否开启", "dumpsys bluetooth_manager | grep -E 'enabled|State:' | head -4"),
        probe("已配对设备", "dumpsys bluetooth_manager | grep -E 'Address|Name:' | head -8"),
    ])

    # ---------- 8. 网络 ----------
    show("8. 网络", [
        probe("无线网卡", "ip addr show wlan0 2>/dev/null | grep inet"),
        probe("局域网里还有谁", "ip neigh 2>/dev/null"),
        probe("开机自启配置", "ls /system/etc/init.d/ /system/bin/*.sh 2>/dev/null"),
        probe("已装第三方应用", "pm list packages -3"),
    ])

    # ---------- 9. 我们的执行身份 ----------
    show("9. 我们的身份与权限", [
        probe("shell 身份", "id"),
        probe("是否 root", "which su; su -c id 2>&1 | head -2"),
        probe("能否写系统分区", "mount | grep -E ' /system | /data ' "),
        probe("selinux 状态", "getenforce"),
    ])

    print(f"\n{'=' * 66}")
    print(" 探测完成")
    print('=' * 66)


if __name__ == "__main__":
    main()
