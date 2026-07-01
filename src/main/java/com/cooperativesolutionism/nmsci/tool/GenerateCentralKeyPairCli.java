package com.cooperativesolutionism.nmsci.tool;

import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.Security;
import java.util.Base64;

/**
 * 独立生成一对 secp256k1 中心密钥（33 字节压缩公钥 + 32 字节私钥，均 Base64 输出），用于填充
 * {@code CENTRAL_KEY_PAIR_PUBKEY} / {@code CENTRAL_KEY_PAIR_PRIKEY}，或在中心公钥轮换时铸造新密钥对。
 * 不需要数据库或运行中的服务。输出格式与 {@code .env} / {@code application-*.properties} 直接对应。
 *
 * <p>运行（已构建 jar 后）：
 * <pre>
 * java -Dloader.main=com.cooperativesolutionism.nmsci.tool.GenerateCentralKeyPairCli \
 *      -cp target/nmsci-*.jar org.springframework.boot.loader.launch.PropertiesLauncher
 * </pre>
 *
 * <p>私钥为敏感材料：仅在可信环境运行，妥善保存，切勿提交进 git 或写入日志。
 */
public final class GenerateCentralKeyPairCli {

    private GenerateCentralKeyPairCli() {
    }

    public static void main(String[] args) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPair keyPair = Secp256k1EncryptUtil.generateKeyPair();
        byte[] rawPrivateKey = Secp256k1EncryptUtil.privateKeyToRaw(keyPair.getPrivate());
        byte[] compressedPublicKey = Secp256k1EncryptUtil.rawToECKey(rawPrivateKey).getPubKey();

        Base64.Encoder encoder = Base64.getEncoder();
        System.out.println("CENTRAL_KEY_PAIR_PUBKEY=" + encoder.encodeToString(compressedPublicKey));
        System.out.println("CENTRAL_KEY_PAIR_PRIKEY=" + encoder.encodeToString(rawPrivateKey));
    }
}
