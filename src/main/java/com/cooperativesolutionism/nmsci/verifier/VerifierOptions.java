package com.cooperativesolutionism.nmsci.verifier;

/**
 * 验证选项。
 *
 * @param expectedCentralPubkey  期望的中心压缩公钥（33字节）；非 null 时校验每个区块头中心公钥与之一致。null 时仅校验各区块在其自身头部公钥下签名自洽。
 * @param expectedSourceHashHex  期望的源码包 SHA-256（64位十六进制）；非 null 时逐块校验区块头 sourceCodeZipHash 与之一致。
 * @param startingPreviousHash   .dat 起始区块应衔接的前区块 id（32字节锚点）；非 null 时校验首块「前区块头摘要」等于它。null 且首块高度为0、前摘要全0 时按创世处理；否则首块衔接判为跳过（无法独立判定）。
 * @param includeStatefulReplay  是否执行有状态回放（注册/授权/挂载等引用与唯一性）。
 */
public record VerifierOptions(
        byte[] expectedCentralPubkey,
        String expectedSourceHashHex,
        byte[] startingPreviousHash,
        boolean includeStatefulReplay
) {

    public static VerifierOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private byte[] expectedCentralPubkey;
        private String expectedSourceHashHex;
        private byte[] startingPreviousHash;
        private boolean includeStatefulReplay = true;

        public Builder expectedCentralPubkey(byte[] value) {
            this.expectedCentralPubkey = value;
            return this;
        }

        public Builder expectedSourceHashHex(String value) {
            this.expectedSourceHashHex = value;
            return this;
        }

        public Builder startingPreviousHash(byte[] value) {
            this.startingPreviousHash = value;
            return this;
        }

        public Builder includeStatefulReplay(boolean value) {
            this.includeStatefulReplay = value;
            return this;
        }

        public VerifierOptions build() {
            return new VerifierOptions(expectedCentralPubkey, expectedSourceHashHex, startingPreviousHash, includeStatefulReplay);
        }
    }
}
