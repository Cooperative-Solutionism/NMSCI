package com.cooperativesolutionism.nmsci.util;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

public class Secp256k1EncryptUtil {

    private static final X9ECParameters SECP256k1_PARAMS = SECNamedCurves.getByName("secp256k1");
    private static final ECParameterSpec EC_SPEC = new ECParameterSpec(
            SECP256k1_PARAMS.getCurve(),
            SECP256k1_PARAMS.getG(),
            SECP256k1_PARAMS.getN(),
            SECP256k1_PARAMS.getH()
    );

    private static final BigInteger CURVE_ORDER = EC_SPEC.getN();

    private static final BigInteger HALF_CURVE_ORDER = CURVE_ORDER.shiftRight(1);

    /**
     * 将DER签名转换为rs格式签名
     *
     * @param derSignature DER格式的签名字节数组
     * @return rs格式的签名字节数组，长度为64字节
     * @throws IOException 如果解析DER签名失败
     */
    public static byte[] derToRs(byte[] derSignature) throws IOException {
//        直接从derSignature字节中解析出r和s
        ASN1Primitive asn1Primitive = ASN1Primitive.fromByteArray(derSignature);
        if (!(asn1Primitive instanceof ASN1Sequence sequence)) {
            throw new IOException("Invalid DER signature format");
        }

        if (sequence.size() != 2) {
            throw new IOException("DER signature must contain exactly two elements");
        }

        ASN1Integer r = ASN1Integer.getInstance(sequence.getObjectAt(0));
        ASN1Integer s = ASN1Integer.getInstance(sequence.getObjectAt(1));

        byte[] rBytes = r.getValue().toByteArray();
        byte[] sBytes = s.getValue().toByteArray();

        // 确保r和s都是32字节长，不足时前面补0
        byte[] rsSignature = new byte[64];
        System.arraycopy(rBytes, Math.max(0, rBytes.length - 32), rsSignature, 0, 32);
        System.arraycopy(sBytes, Math.max(0, sBytes.length - 32), rsSignature, 32, 32);

        return rsSignature;
    }

    /**
     * 将rs格式的签名转换为DER格式
     *
     * @param rsSignature rs格式的签名字节数组，长度为64字节
     * @return DER格式的签名字节数组
     * @throws IOException 如果转换失败
     */
    public static byte[] rsToDer(byte[] rsSignature) throws IOException {
        if (rsSignature.length != 64) {
            throw new IllegalArgumentException("rsSignature must be 64 bytes long");
        }

        // 分离r和s
        byte[] rBytes = Arrays.copyOfRange(rsSignature, 0, 32);
        byte[] sBytes = Arrays.copyOfRange(rsSignature, 32, 64);

        ASN1Integer r = new ASN1Integer(new BigInteger(1, rBytes));
        ASN1Integer s = new ASN1Integer(new BigInteger(1, sBytes));

        DERSequence sequence = new DERSequence(new ASN1Primitive[]{r, s});
        return sequence.getEncoded();
    }

    /**
     * 将PublicKey格式的公钥转换为33字节压缩格式的公钥
     *
     * @param publicKey 公钥
     * @return 压缩格式的公钥字节数组，长度为33字节
     */
    public static byte[] publicKeyToCompressed(PublicKey publicKey) {
        if (!(publicKey instanceof ECPublicKey ecPublicKey)) {
            throw new IllegalArgumentException("PublicKey must be an instance of ECPublicKey");
        }

        return ecPublicKey.getQ().getEncoded(true);
    }

    /**
     * 将33字节压缩格式的公钥转换为PublicKey格式
     *
     * @param compressedPubKey 压缩格式的公钥字节数组，长度为33字节
     * @return PublicKey对象
     * @throws Exception 如果转换失败
     */
    public static PublicKey compressedToPublicKey(byte[] compressedPubKey) throws Exception {
        if (compressedPubKey.length != 33) {
            throw new IllegalArgumentException("Compressed public key must be 33 bytes long");
        }

        KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");

        return keyFactory.generatePublic(new ECPublicKeySpec(
                SECP256k1_PARAMS.getCurve().decodePoint(compressedPubKey),
                EC_SPEC
        ));
    }

    /**
     * 将PrivateKey格式的私钥转换为原始格式
     *
     * @param privateKey 私钥
     * @return 原始格式的私钥字节数组，长度为32字节
     */
    public static byte[] privateKeyToRaw(PrivateKey privateKey) {
        if (!(privateKey instanceof ECPrivateKey ecPrivateKey)) {
            throw new IllegalArgumentException("PrivateKey must be an instance of ECPrivateKey");
        }

        BigInteger d = ecPrivateKey.getD();
        byte[] rawPrivateKey = d.toByteArray();

        // 确保私钥长度为32字节，不足时前面补0
        if (rawPrivateKey.length < 32) {
            byte[] paddedPrivateKey = new byte[32];
            System.arraycopy(rawPrivateKey, 0, paddedPrivateKey, 32 - rawPrivateKey.length, rawPrivateKey.length);
            return paddedPrivateKey;
        } else if (rawPrivateKey.length > 32) {
            return Arrays.copyOfRange(rawPrivateKey, rawPrivateKey.length - 32, rawPrivateKey.length);
        } else {
            return rawPrivateKey;
        }
    }

    /**
     * 将32字节原始格式的私钥转换为PrivateKey格式
     *
     * @param rawPrivateKey 原始格式的私钥字节数组，长度为32字节
     * @return PrivateKey对象
     * @throws Exception 如果转换失败
     */
    public static PrivateKey rawToPrivateKey(byte[] rawPrivateKey) throws Exception {
        if (rawPrivateKey.length != 32) {
            throw new IllegalArgumentException("Raw private key must be 32 bytes long");
        }

        BigInteger privateKeyValue = new BigInteger(1, rawPrivateKey);
        if (privateKeyValue.compareTo(BigInteger.ZERO) <= 0 || privateKeyValue.compareTo(CURVE_ORDER) >= 0) {
            throw new IllegalArgumentException("Invalid private key value");
        }

        KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");
        ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(1, rawPrivateKey), EC_SPEC);

        return keyFactory.generatePrivate(privateKeySpec);
    }

    /**
     * 生成 SECP256K1 曲线的密钥对
     *
     * @return KeyPair 包含公钥和私钥
     * @throws Exception 如果生成密钥对失败
     */
    public static KeyPair generateKeyPair() throws Exception {
        // 指定算法为 ECDSA，曲线名称为 SECP256k1
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", "BC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
        keyPairGenerator.initialize(ecSpec);

        return keyPairGenerator.generateKeyPair();
    }

    /**
     * 使用 SECP256K1 曲线对数据进行签名(Low-S签名)
     *
     * @param data       待签名的数据
     * @param privateKey 用于签名的私钥
     * @return 签名(DER格式)
     * @throws Exception 如果签名失败
     */
    public static byte[] signData(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA", "BC");
        signature.initSign(privateKey);
        signature.update(data);
        byte[] derSignature = signature.sign();
        byte[] rsSignature = derToRs(derSignature);
        if (!isLowS(rsSignature)) {
            // 如果签名的s值不小于等于曲线阶的一半，则需要调整s值
            BigInteger r = new BigInteger(1, Arrays.copyOfRange(rsSignature, 0, 32));
            BigInteger s = new BigInteger(1, Arrays.copyOfRange(rsSignature, 32, 64));
            s = CURVE_ORDER.subtract(s);
            byte[] adjustedSignature = new byte[64];
            System.arraycopy(r.toByteArray(), Math.max(0, r.toByteArray().length - 32), adjustedSignature, 0, 32);
            System.arraycopy(s.toByteArray(), Math.max(0, s.toByteArray().length - 32), adjustedSignature, 32, 32);
            return rsToDer(adjustedSignature);
        }
        return derSignature;
    }

    /**
     * 验证 SECP256K1 曲线签名
     *
     * @param data        原始数据
     * @param rsSignature 签名
     * @param publicKey   用于验证的公钥
     * @return 如果签名有效返回 true，否则返回 false
     * @throws Exception 如果验证失败
     */
    public static boolean verifySignature(byte[] data, byte[] rsSignature, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA", "BC");
        signature.initVerify(publicKey);
        signature.update(data);
        byte[] derSignature = rsToDer(rsSignature);
        return signature.verify(derSignature);
    }

    /**
     * 签名是否为较小签名Low-S
     *
     * @param rsSignature 签名的字节数组
     * @return 如果签名的s值小于等于曲线阶的一半，则返回 true，否则返回 false
     * @throws IOException 如果解析签名失败
     */
    public static boolean isLowS(byte[] rsSignature) throws IOException {
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(rsSignature, 32, 64));
        return s.compareTo(HALF_CURVE_ORDER) <= 0;
    }
}
