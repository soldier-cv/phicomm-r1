package com.r1.brain;

import android.util.Base64;

import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * 纯 Java 的 Ed25519 签名（和风天气 JWT 需要）。
 *
 * <p>为什么自己写：设备是 Android 5.1（API 22），java.security 里没有 Ed25519，
 * 也不能为了签名引第三方库。这里用 BigInteger 直接算 curve25519，
 * 一次签名几十毫秒，只在需要签发 JWT 时跑一次，够用。
 *
 * <p>正确性用 RFC 8032 的测试向量验证：自检方法会比对官方的公钥与签名。
 *
 * <p>算法要点：Edwards 曲线 x^2 + y^2 = 1 + d*x^2*y^2（符号取负），
 * 标量乘用扩展坐标（X:Y:Z:T）避免每次点加都做模逆。
 *
 * @author xudong.hua,deepseek
 * @since 2026-11-01 16:20 星期日
 */
public final class Ed25519 {

    private static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger L = BigInteger.ONE.shiftLeft(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);
    private static final BigInteger D2 = D.shiftLeft(1).mod(P);
    private static final BigInteger SQRT_M1 = TWO.modPow(
            P.subtract(BigInteger.ONE).shiftRight(2), P);

    /** 基点 B 的 y 坐标 = 4/5。 */
    private static final BigInteger BY = BigInteger.valueOf(4)
            .multiply(BigInteger.valueOf(5).modInverse(P)).mod(P);

    /** 曲线上的点（扩展坐标）。 */
    private static final class Point {
        final BigInteger x, y, z, t;

        Point(BigInteger x, BigInteger y, BigInteger z, BigInteger t) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.t = t;
        }
    }

    private static final Point BASE = basePoint();
    private static final Point IDENTITY = new Point(BigInteger.ZERO, BigInteger.ONE,
            BigInteger.ONE, BigInteger.ZERO);

    private Ed25519() {
    }

    /**
     * 用 32 字节种子私钥对消息签名。
     *
     * @param seed    32 字节私钥种子（PKCS#8 私钥里取出来的那 32 字节）
     * @param message 待签名内容
     * @return 64 字节签名（R || S）
     */
    public static byte[] sign(byte[] seed, byte[] message) {
        if (seed == null || seed.length != 32) {
            throw new IllegalArgumentException("Ed25519 私钥必须是 32 字节种子，实际 "
                    + (seed == null ? 0 : seed.length));
        }
        byte[] h = sha512(seed);
        BigInteger a = leToInt(clamp(h));
        byte[] prefix = new byte[32];
        System.arraycopy(h, 32, prefix, 0, 32);

        byte[] publicKey = encode(mul(BASE, a));

        BigInteger r = leToInt(sha512(concat(prefix, message))).mod(L);
        byte[] rPoint = encode(mul(BASE, r));

        BigInteger k = leToInt(sha512(concat(concat(rPoint, publicKey), message))).mod(L);
        BigInteger sOut = r.add(k.multiply(a)).mod(L);

        byte[] sig = new byte[64];
        System.arraycopy(rPoint, 0, sig, 0, 32);
        System.arraycopy(intToLe(sOut, 32), 0, sig, 32, 32);
        return sig;
    }

    /**
     * 从 32 字节种子推导公钥（自检与调试用）。
     *
     * @param seed 32 字节种子
     * @return 32 字节公钥
     */
    public static byte[] publicKey(byte[] seed) {
        byte[] h = sha512(seed);
        return encode(mul(BASE, leToInt(clamp(h))));
    }

    /**
     * 从和风控制台给的私钥文本里取出 32 字节种子。
     *
     * <p>控制台下载的凭据里 privateKey 通常是 PKCS#8 的 base64，可能带 PEM 头与换行。
     * 这里只认两种：32 字节裸种子，或 PKCS#8 DER（取最后 32 字节）。
     *
     * @param raw 私钥文本
     * @return 32 字节种子
     */
    public static byte[] seedFromPkcs8(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("私钥为空");
        }
        String s = raw.replace("PRIVATE KEY-----", "")
                .replace("-----BEGIN", "")
                .replace("-----END", "")
                .replace("\\n", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.decode(s, Base64.DEFAULT);
        if (der.length == 32) return der;
        if (der.length >= 32 && der[0] == 0x30) {
            byte[] seed = new byte[32];
            System.arraycopy(der, der.length - 32, seed, 0, 32);
            return seed;
        }
        throw new IllegalArgumentException("认不出这个私钥格式（期望 PKCS#8 或 32 字节种子）");
    }

    /**
     * 生成一个新的 32 字节私钥种子（SecureRandom）。
     *
     * @return 32 字节种子
     */
    public static byte[] randomSeed() {
        byte[] s = new byte[32];
        new java.security.SecureRandom().nextBytes(s);
        return s;
    }

    /**
     * 把 32 字节种子导成 OpenSSL 风格的 PKCS#8 base64（可选加 PEM 头）。
     *
     * @param seed 32 字节种子
     * @return base64 文本
     */
    public static String privateKeyBase64(byte[] seed) {
        byte[] der = new byte[48];
        byte[] prefix = new byte[]{0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03,
                0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20};
        System.arraycopy(prefix, 0, der, 0, prefix.length);
        System.arraycopy(seed, 0, der, prefix.length, 32);
        return Base64.encodeToString(der, Base64.NO_WRAP);
    }

    /**
     * 把公钥导成 PEM（SPKI DER + BEGIN/END 头），和风控制台认这种格式。
     *
     * @param publicKey 32 字节公钥
     * @return 带换行的 PEM
     */
    public static String publicKeyPem(byte[] publicKey) {
        byte[] prefix = new byte[]{0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70,
                0x03, 0x21, 0x00};
        byte[] der = new byte[prefix.length + publicKey.length];
        System.arraycopy(prefix, 0, der, 0, prefix.length);
        System.arraycopy(publicKey, 0, der, prefix.length, publicKey.length);
        String body = Base64.encodeToString(der, Base64.NO_WRAP);
        StringBuilder sb = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < body.length(); i += 64) {
            sb.append(body, i, Math.min(body.length(), i + 64)).append('\n');
        }
        sb.append("-----END PUBLIC KEY-----\n");
        return sb.toString();
    }

    /** 公钥的裸 base64（44 字符），有些平台只要这一段。 */
    public static String publicKeyBase64(byte[] publicKey) {
        return Base64.encodeToString(publicKey, Base64.NO_WRAP);
    }

    /**
     * 自检：跑 RFC 8032 第 7.1 节的两个测试向量。
     *
     * @return 通过返回空串，失败返回原因
     */
    public static String selfTest() {
        try {
            byte[] seed1 = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
            String wantPub1 = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a";
            String wantSig1 = "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                    + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b";
            if (!hex(publicKey(seed1)).equals(wantPub1)) return "公钥向量不匹配";
            if (!hex(sign(seed1, new byte[0])).equals(wantSig1)) return "空消息签名不匹配";

            byte[] seed2 = hex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb");
            byte[] msg2 = new byte[]{0x72};
            String wantPub2 = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c";
            String wantSig2 = "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da"
                    + "085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00";
            if (!hex(publicKey(seed2)).equals(wantPub2)) return "第二组公钥不匹配";
            if (!hex(sign(seed2, msg2)).equals(wantSig2)) return "第二组签名不匹配";
            return "";
        } catch (Throwable t) {
            return "自检异常: " + t;
        }
    }

    // ------------------------------------------------------------------ 曲线运算

    private static byte[] clamp(byte[] h) {
        byte[] a = new byte[32];
        System.arraycopy(h, 0, a, 0, 32);
        a[0] &= (byte) 248;
        a[31] &= 127;
        a[31] |= 64;
        return a;
    }

    private static Point basePoint() {
        BigInteger y = BY;
        BigInteger x = recoverX(y);
        return new Point(x, y, BigInteger.ONE, x.multiply(y).mod(P));
    }

    /** 由 y 求满足曲线方程的 x（取偶数那个）。 */
    private static BigInteger recoverX(BigInteger y) {
        BigInteger y2 = y.multiply(y).mod(P);
        BigInteger num = y2.subtract(BigInteger.ONE).mod(P);
        BigInteger den = D.multiply(y2).add(BigInteger.ONE).mod(P);
        BigInteger x2 = num.multiply(den.modInverse(P)).mod(P);
        BigInteger x = x2.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P);
        if (!x.multiply(x).mod(P).equals(x2)) {
            x = x.multiply(SQRT_M1).mod(P);
        }
        if (!x.multiply(x).mod(P).equals(x2)) {
            throw new IllegalStateException("这个 y 不在曲线上");
        }
        if (x.testBit(0)) {
            x = P.subtract(x);
        }
        return x;
    }

    private static Point add(Point p, Point q) {
        BigInteger a = p.y.subtract(p.x).multiply(q.y.subtract(q.x)).mod(P);
        BigInteger b = p.y.add(p.x).multiply(q.y.add(q.x)).mod(P);
        BigInteger c = p.t.multiply(D2).multiply(q.t).mod(P);
        BigInteger d = p.z.multiply(TWO).multiply(q.z).mod(P);
        BigInteger e = b.subtract(a);
        BigInteger f = d.subtract(c);
        BigInteger g = d.add(c);
        BigInteger h = b.add(a);
        return new Point(e.multiply(f).mod(P), g.multiply(h).mod(P),
                f.multiply(g).mod(P), e.multiply(h).mod(P));
    }

    private static Point mul(Point p, BigInteger n) {
        Point result = IDENTITY;
        Point addend = p;
        int bits = n.bitLength();
        for (int i = 0; i < bits; i++) {
            if (n.testBit(i)) result = add(result, addend);
            addend = add(addend, addend);
        }
        return result;
    }

    private static byte[] encode(Point p) {
        BigInteger zInv = p.z.modInverse(P);
        BigInteger x = p.x.multiply(zInv).mod(P);
        BigInteger y = p.y.multiply(zInv).mod(P);
        byte[] out = intToLe(y, 32);
        if (x.testBit(0)) out[31] |= (byte) 0x80;
        return out;
    }

    private static BigInteger leToInt(byte[] b) {
        byte[] be = new byte[b.length];
        for (int i = 0; i < b.length; i++) be[i] = b[b.length - 1 - i];
        return new BigInteger(1, be);
    }

    private static byte[] intToLe(BigInteger v, int len) {
        byte[] be = v.toByteArray();
        byte[] out = new byte[len];
        int copy = Math.min(be.length, len);
        for (int i = 0; i < copy; i++) {
            out[i] = be[be.length - 1 - i];
        }
        return out;
    }

    private static byte[] sha512(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(data);
        } catch (Throwable t) {
            throw new IllegalStateException("SHA-512 不可用: " + t);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xf, 16));
            sb.append(Character.forDigit(x & 0xf, 16));
        }
        return sb.toString();
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
