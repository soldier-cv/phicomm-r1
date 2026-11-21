#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Janus 包自检。

校验五件事：
    1. 文件头是 dex\\n035
    2. DEX 头里的 file_size 等于整个文件长度
    3. Adler32 与 SHA-1 与内容一致
    4. 把偏移还原后，zip 能被正常解包
    5. 还原后的 134 个条目与底包逐条目 md5 全等（证明签名区域没被动过）

用法：
    python verify_janus.py <产物.apk> <原厂.apk>

@author xudong.hua,deepseek
@since 2026-10-30 21:50 星期五
"""

import hashlib
import struct
import sys
import zipfile
import zlib
from io import BytesIO

EOCD_SIG = b"PK\x05\x06"
CD_SIG = b"PK\x01\x02"
LFH_SIG = b"PK\x03\x04"


def fail(msg: str) -> None:
    print("    [FAIL] " + msg)
    sys.exit(1)


def unjanus(data: bytes) -> bytes:
    """把中央目录里的偏移减回去，得到可直接解包的普通 APK。"""
    zip_start = data.find(LFH_SIG)
    if zip_start < 0:
        fail("找不到 zip 起始位置")
    tail = bytearray(data[zip_start:])
    eocd = tail.rfind(EOCD_SIG)
    if eocd < 0:
        fail("找不到 EOCD")

    cd_off = struct.unpack("<I", tail[eocd + 16:eocd + 20])[0]
    tail[eocd + 16:eocd + 20] = struct.pack("<I", cd_off - zip_start)

    count = struct.unpack("<H", tail[eocd + 10:eocd + 12])[0]
    pos = cd_off - zip_start
    for _ in range(count):
        if tail[pos:pos + 4] != CD_SIG:
            break
        name_len = struct.unpack("<H", tail[pos + 28:pos + 30])[0]
        extra_len = struct.unpack("<H", tail[pos + 30:pos + 32])[0]
        comment_len = struct.unpack("<H", tail[pos + 32:pos + 34])[0]
        old = struct.unpack("<I", tail[pos + 42:pos + 46])[0]
        if old >= zip_start:
            tail[pos + 42:pos + 46] = struct.pack("<I", old - zip_start)
        pos += 46 + name_len + extra_len + comment_len
    return bytes(tail)


def main(apk_path: str, base_path: str) -> None:
    with open(apk_path, "rb") as f:
        data = f.read()
    with open(base_path, "rb") as f:
        base = f.read()

    # 1. 文件头
    if data[:4] != b"dex\n":
        fail("文件头不是 dex\\n035")
    print("    [ok] 文件头 dex\\n035")

    # 2. file_size
    file_size = struct.unpack("<I", data[32:36])[0]
    if file_size != len(data):
        fail("file_size=%d 与实际长度 %d 不符" % (file_size, len(data)))
    print("    [ok] file_size=%d" % file_size)

    # 3. 校验和
    checksum = struct.unpack("<I", data[8:12])[0]
    if (zlib.adler32(data[12:]) & 0xFFFFFFFF) != checksum:
        fail("Adler32 不匹配")
    if hashlib.sha1(data[32:]).digest() != data[12:32]:
        fail("SHA-1 不匹配")
    print("    [ok] Adler32 + SHA-1")

    # 4. zip 可解包
    shell = unjanus(data)
    try:
        zs = zipfile.ZipFile(BytesIO(shell))
    except Exception as exc:  # noqa: BLE001
        fail("还原后无法作为 zip 打开：%s" % exc)
    print("    [ok] zip 可解包，%d 个条目" % len(zs.infolist()))

    # 5. 逐条目 md5 对比
    zb = zipfile.ZipFile(BytesIO(base))
    a = {i.filename: hashlib.md5(zs.read(i.filename)).hexdigest() for i in zs.infolist()}
    b = {i.filename: hashlib.md5(zb.read(i.filename)).hexdigest() for i in zb.infolist()}
    only_a = set(a) - set(b)
    only_b = set(b) - set(a)
    diff = [k for k in a if k in b and a[k] != b[k]]
    if only_a or only_b or diff:
        fail("条目与底包不一致：多出 %d，缺少 %d，内容不同 %d"
             % (len(only_a), len(only_b), len(diff)))
    print("    [ok] %d 个条目与底包逐字节一致（签名区域未改动）" % len(a))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], sys.argv[2])
