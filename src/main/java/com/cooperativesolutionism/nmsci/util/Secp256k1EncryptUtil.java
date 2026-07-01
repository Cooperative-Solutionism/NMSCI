package com.cooperativesolutionism.nmsci.util;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RAW_PRIVATE_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RS_SIGNATURE_BYTES;

import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.base.Sha256Hash;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
    private static final int RS_COMPONENT_BYTES = RS_SIGNATURE_BYTES / 2;

    // 确保 BC provider 已注册后再构造可复用的 KeyFactory（静态初始化按文本顺序执行，本块先于下方字段）。
    // 与 NmsciApplication / ChainVerifier 的注册保持幂等一致，令本工具类在任意上下文（含单测）自足可用。
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // 复用 KeyFactory 实例，避免每次 compressedToPublicKey/rawToPrivateKey 都做一次 JCA provider 查找（性能审计 H3）。
    // KeyFactory 取得后其 generatePublic/generatePrivate 可安全跨线程复用（只读不可变 KeySpec，无 per-call 可变状态）。
    private static final KeyFactory EC_KEY_FACTORY = createEcKeyFactory();

    private static KeyFactory createEcKeyFactory() {
        try {
            return KeyFactory.getInstance("ECDSA", "BC");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * 将DER签名转换为rs格式签名
     *
     * @param derSignature DER格式的签名字节数组
     * @return rs格式的签名字节数组，长度为64字节
     * @throws IOException 如果解析DER签名失败
     */
    public static byte[] derToRs(byte[] derSignature) throws IOException {
        if (derSignature == null) {
            throw new IOException("Invalid DER signature format");
        }

        ASN1Primitive primitive;
        try {
            primitive = ASN1Primitive.fromByteArray(derSignature);
        } catch (IOException e) {
            throw new IOException("Invalid DER signature format", e);
        }

        if (!(primitive instanceof ASN1Sequence seq) || seq.size() != 2) {
            throw new IOException("Invalid DER signature format");
        }
        if (!(seq.getObjectAt(0) instanceof ASN1Integer rInteger)
                || !(seq.getObjectAt(1) instanceof ASN1Integer sInteger)) {
            throw new IOException("Invalid DER signature format");
        }

        BigInteger r = rInteger.getValue();
        BigInteger s = sInteger.getValue();
        validateSignatureScalar(r, "r");
        validateSignatureScalar(s, "s");

        byte[] rs = new byte[RS_SIGNATURE_BYTES];
        byte[] rBytes = toFixed32Bytes(r);
        byte[] sBytes = toFixed32Bytes(s);
        System.arraycopy(rBytes, 0, rs, 0, RS_COMPONENT_BYTES);
        System.arraycopy(sBytes, 0, rs, RS_COMPONENT_BYTES, RS_COMPONENT_BYTES);

        return rs;
    }

    private static void validateSignatureScalar(BigInteger value, String name) throws IOException {
        if (value.compareTo(BigInteger.ZERO) <= 0 || value.compareTo(CURVE_ORDER) >= 0) {
            throw new IOException("Invalid DER signature " + name + " value");
        }
    }

    private static byte[] toFixed32Bytes(BigInteger value) throws IOException {
        byte[] valueBytes = value.toByteArray();
        if (valueBytes.length == RS_COMPONENT_BYTES + 1 && valueBytes[0] == 0) {
            valueBytes = Arrays.copyOfRange(valueBytes, 1, RS_COMPONENT_BYTES + 1);
        }
        if (valueBytes.length > RS_COMPONENT_BYTES) {
            throw new IOException("Invalid DER signature scalar length");
        }

        byte[] fixed = new byte[RS_COMPONENT_BYTES];
        System.arraycopy(valueBytes, 0, fixed, RS_COMPONENT_BYTES - valueBytes.length, valueBytes.length);
        return fixed;
    }

    /**
     * 将rs格式的签名转换为DER格式
     *
     * @param rsSignature rs格式的签名字节数组，长度为64字节
     * @return DER格式的签名字节数组
     * @throws IOException 如果转换失败
     */
    public static byte[] rsToDer(byte[] rsSignature) throws IOException {
        validateRsSignature(rsSignature);

        // 分离r和s
        byte[] rBytes = Arrays.copyOfRange(rsSignature, 0, RS_COMPONENT_BYTES);
        byte[] sBytes = Arrays.copyOfRange(rsSignature, RS_COMPONENT_BYTES, RS_SIGNATURE_BYTES);

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
        if (compressedPubKey == null || compressedPubKey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("Compressed public key must be 33 bytes long");
        }

        return EC_KEY_FACTORY.generatePublic(new ECPublicKeySpec(
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
        if (rawPrivateKey.length < RAW_PRIVATE_KEY_BYTES) {
            byte[] paddedPrivateKey = new byte[RAW_PRIVATE_KEY_BYTES];
            System.arraycopy(rawPrivateKey, 0, paddedPrivateKey, RAW_PRIVATE_KEY_BYTES - rawPrivateKey.length, rawPrivateKey.length);
            return paddedPrivateKey;
        } else if (rawPrivateKey.length > RAW_PRIVATE_KEY_BYTES) {
            return Arrays.copyOfRange(rawPrivateKey, rawPrivateKey.length - RAW_PRIVATE_KEY_BYTES, rawPrivateKey.length);
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
        BigInteger privateKeyValue = validateRawPrivateKey(rawPrivateKey);

        ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privateKeyValue, EC_SPEC);

        return EC_KEY_FACTORY.generatePrivate(privateKeySpec);
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
        byte[] rawPrivateKey = privateKeyToRaw(privateKey);
        return signData(data, ECKey.fromPrivate(new BigInteger(1, rawPrivateKey)));
    }

    /**
     * 使用已构造的 ECKey 对数据进行签名(Low-S签名)。
     * 供固定中心密钥等场景缓存 ECKey 复用，避免每次签名都由私钥重新派生公钥点。
     *
     * @param data  待签名的数据
     * @param ecKey 已构造的签名密钥
     * @return 签名(DER格式)
     */
    public static byte[] signData(byte[] data, ECKey ecKey) {
        byte[] dataDblDigest = Sha256Util.doubleDigest(data);
        Sha256Hash hash = Sha256Hash.wrap(dataDblDigest);
        ECKey.ECDSASignature ecdsaSignature = ecKey.sign(hash);
        return ecdsaSignature.encodeToDER();
    }

    /**
     * 将32字节原始私钥转换为可复用的 ECKey（范围校验与 {@link #rawToPrivateKey} 一致）。
     */
    public static ECKey rawToECKey(byte[] rawPrivateKey) {
        return ECKey.fromPrivate(validateRawPrivateKey(rawPrivateKey));
    }

    private static void validateRsSignature(byte[] rsSignature) {
        if (rsSignature == null || rsSignature.length != RS_SIGNATURE_BYTES) {
            throw new IllegalArgumentException("rsSignature must be 64 bytes long");
        }
    }

    private static BigInteger validateRawPrivateKey(byte[] rawPrivateKey) {
        if (rawPrivateKey == null || rawPrivateKey.length != RAW_PRIVATE_KEY_BYTES) {
            throw new IllegalArgumentException("Raw private key must be 32 bytes long");
        }

        BigInteger privateKeyValue = new BigInteger(1, rawPrivateKey);
        if (privateKeyValue.compareTo(BigInteger.ZERO) <= 0 || privateKeyValue.compareTo(CURVE_ORDER) >= 0) {
            throw new IllegalArgumentException("Invalid private key value");
        }
        return privateKeyValue;
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
        return verifySignature(data, rsSignature, publicKeyToCompressed(publicKey));
    }

    /**
     * 直接以33字节压缩公钥验证 SECP256K1 签名，避免 KeyFactory/PublicKey 往返（性能审计 H3）。
     * 校验链上验证（{@code ChainVerifier}）与写入摄入（{@code SignatureValidator}）的每消息热路径均走此重载：
     * 输入本就是压缩公钥字节，bitcoinj 的 {@code ECKey.fromPublicOnly} 可直接消费，无需再经 JCE KeyFactory
     * 生成 {@code PublicKey} 又立即转回压缩字节。
     *
     * @param data             原始数据
     * @param rsSignature      rs 格式签名
     * @param compressedPubkey 33 字节压缩公钥
     * @return 签名有效返回 true
     * @throws Exception 如果公钥/签名字节解析失败（长度、离曲线点、DER 解码等）
     */
    public static boolean verifySignature(byte[] data, byte[] rsSignature, byte[] compressedPubkey) throws Exception {
        if (compressedPubkey == null || compressedPubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("Compressed public key must be 33 bytes long");
        }
        // 显式校验曲线点合法性（等价于原 compressedToPublicKey 的 decodePoint 守卫），拒绝离曲线/畸形公钥
        SECP256k1_PARAMS.getCurve().decodePoint(compressedPubkey);
        ECKey ecKey = ECKey.fromPublicOnly(compressedPubkey);

        byte[] dataDblDigest = Sha256Util.doubleDigest(data);
        Sha256Hash hash = Sha256Hash.wrap(dataDblDigest);

        byte[] derSignature = rsToDer(rsSignature);
        ECKey.ECDSASignature ecdsaSignature = ECKey.ECDSASignature.decodeFromDER(derSignature);

        return ecKey.verify(hash, ecdsaSignature);
    }

    /**
     * 签名是否为较小签名Low-S
     *
     * @param rsSignature 签名的字节数组
     * @return 如果签名的s值小于等于曲线阶的一半，则返回 true，否则返回 false
     * @throws IOException 如果解析签名失败
     */
    public static boolean isNotLowS(byte[] rsSignature) throws IOException {
        validateRsSignature(rsSignature);
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(rsSignature, RS_COMPONENT_BYTES, RS_SIGNATURE_BYTES));
        return s.compareTo(HALF_CURVE_ORDER) > 0;
    }
}
