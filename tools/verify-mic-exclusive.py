#!/usr/bin/env python3
"""
验证：四麦采集设备是不是被原厂语音链独占（即我们的 App 还能不能采到音频）

全程只读 + 一次 1 秒的试录，不改任何配置、不装任何东西。

判定逻辑：
  A. 原厂语音链是不是长期占着 card1 的采集节点（RUNNING + owner_pid 恒定）
  B. 在被占用的前提下，第二个进程还能不能 open 这个节点（tinycap 试录 1 秒）
     - EBUSY / Device or resource busy  -> 独占，别的进程拿不到
     - 正常录到数据                    -> 可共享，普通 App 走 AudioRecord 没问题

用法: python verify-mic-exclusive.py [ip]
"""

import subprocess
import sys
import time
import re

IP = sys.argv[1] if len(sys.argv) > 1 else "192.168.1.100"
ADB = r"D:\BaiduNetdiskDownload\R1 MOD apk\一键安装 (20261007)\一键安装\adb.exe"
DEV = f"{IP}:5555"

STATUS = "/proc/asound/card1/pcm0c/sub0/status"
HWPARAMS = "/proc/asound/card1/pcm0c/sub0/hw_params"


def sh(cmd, tries=3):
    for _ in range(tries):
        r = subprocess.run([ADB, "-s", DEV, "shell", cmd],
                           capture_output=True, text=True, errors="replace")
        out = ((r.stdout or "") + (r.stderr or "")).strip()
        if "error: closed" not in out and "device offline" not in out:
            return out, r.returncode
        subprocess.run([ADB, "disconnect", DEV], capture_output=True)
        time.sleep(1)
        subprocess.run([ADB, "connect", DEV], capture_output=True)
        time.sleep(1)
    return "<连接失败>", -1


def parse_status(text):
    d = {}
    for line in text.splitlines():
        if ":" in line:
            k, v = line.split(":", 1)
            d[k.strip()] = v.strip()
    return d


def main():
    print("=" * 68)
    print(" 四麦采集独占性验证")
    print("=" * 68)

    subprocess.run([ADB, "connect", DEV], capture_output=True)
    st = sh("getprop ro.product.model")
    if st[0].startswith("<"):
        sys.exit("连不上设备，先确认 IP 和 adb")
    print(f"\n设备: {st[0]}")

    # ---------- A. 长期占用观察 ----------
    print("\n--- A. 原厂语音链是否长期占用采集节点 ---")
    print("（每 5 秒采一次，共 12 次 = 1 分钟）\n")
    print(f"{'时刻':>8}  {'state':<10} {'owner_pid':>10} {'已持有时长':>12}")
    print("-" * 48)

    owners = set()
    for i in range(12):
        raw, _ = sh(f"cat {STATUS}")
        d = parse_status(raw)
        state = d.get("state", "?")
        owner = d.get("owner_pid", "?")
        try:
            trig = float(d.get("trigger_time", 0))
            tst = float(d.get("tstamp", 0))
            held = f"{tst - trig:.0f}s"
        except ValueError:
            held = "?"
        print(f"{i * 5:>6}s  {state:<10} {owner:>10} {held:>12}")
        if owner not in ("?", "0"):
            owners.add(owner)
        time.sleep(5)

    print("-" * 48)
    if len(owners) == 1:
        print(f"→ 整段时间 owner_pid 恒为 {list(owners)[0]}，说明被同一个进程持续占用")
    elif len(owners) > 1:
        print(f"→ owner 变过：{owners}，说明有交接")
    else:
        print("→ 期间没有 owner，可能没在采")

    # ---------- 谁在占 ----------
    print("\n--- 谁的线程在采 ---")
    if owners:
        pid = list(owners)[0]
        name, _ = sh(f"cat /proc/{pid}/status | grep -E 'Name|Tgid'")
        cmdline, _ = sh(f"cat /proc/{pid}/cmdline")
        print(f"  线程 {pid}: {name.replace(chr(10), ' | ')}")
        print(f"  所属进程: {cmdline}")
        print(f"  （Tgid 就是进程 pid，可以和 ps 对上）")

    # ---------- 硬件参数 ----------
    print("\n--- 当前硬件参数 ---")
    hw, _ = sh(f"cat {HWPARAMS}")
    for line in hw.splitlines():
        if line.strip():
            print("  " + line.strip())

    # ---------- B. 第二个进程能否 open ----------
    print("\n--- B. 第二个进程能不能同时 open 这个节点 ---")
    has_tinycap, _ = sh("ls /system/bin/tinycap")
    if "No such" in has_tinycap or not has_tinycap:
        print("  设备上没有 tinycap，跳过实际试录")
        print("  （改用 AudioRecord 才需要装 App，这一步先不做）")
    else:
        print("  设备上有 tinycap，试录 1 秒（不会保存到设备，写到 /dev/null）")
        out, rc = sh(
            "tinycap /dev/null -D 1 -d 0 -c 2 -r 48000 -b 16 -t 1 2>&1")
        print(f"  返回码: {rc}")
        for line in out.splitlines()[:10]:
            print("    " + line)
        low = out.lower()
        if "busy" in low or "failed" in low or "error" in low:
            print("\n  → ✅ 判定：**独占**。第二个进程打不开，普通 App 也拿不到四麦。")
        elif "captur" in low:
            print("\n  → ✅ 判定：**可共享**。第二个进程能打开，普通 App 用 AudioRecord 没问题。")
        else:
            print("\n  → ⚠️ 输出不明，需要人工看上面内容。")

    # ---------- 收尾核对 ----------
    print("\n--- 收尾：确认没有留下任何改动 ---")
    after, _ = sh(f"cat {STATUS} | grep -E 'state|owner_pid'")
    print("  " + after.replace("\n", "\n  "))
    print("\n完成。")


if __name__ == "__main__":
    main()
