package com.cooperativesolutionism.nmsci.concurrency;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolValidationOptimizationContractTest {

    @Test
    void protocolByteAssemblyDoesNotUseArrayUtilsConcatenation() throws IOException {
        assertFalse(
                source("src/main/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilder.java")
                        .contains("ArrayUtils"),
                "ProtocolRawBytesBuilder should use fixed-size buffers instead of repeated ArrayUtils.addAll copies"
        );
        assertFalse(
                source("src/main/java/com/cooperativesolutionism/nmsci/protocol/CentralSignatureService.java")
                        .contains("ArrayUtils"),
                "CentralSignatureService should assemble raw bytes with one fixed-size buffer"
        );
    }

    @Test
    void signatureVerificationUsesCompressedPubkeyOverloadNotKeyFactoryRoundTrip() throws IOException {
        // 性能审计 H3：验签热路径直接以压缩公钥字节验签，不再经 compressedToPublicKey 的 KeyFactory/PublicKey 往返。
        assertFalse(
                source("src/main/java/com/cooperativesolutionism/nmsci/protocol/SignatureValidator.java")
                        .contains("compressedToPublicKey"),
                "SignatureValidator should verify from compressed pubkey bytes without a KeyFactory/PublicKey round-trip"
        );
    }

    @Test
    void transactionServicesUseMeasuredDifficultyLookupService() throws IOException {
        String recordService = source("src/main/java/com/cooperativesolutionism/nmsci/service/TransactionRecordMsgService.java");
        String mountService = source("src/main/java/com/cooperativesolutionism/nmsci/service/TransactionMountMsgService.java");

        assertTrue(recordService.contains("blockDifficultyService.currentTransactionDifficultyTarget()"));
        assertTrue(mountService.contains("blockDifficultyService.currentTransactionDifficultyTarget()"));
        assertFalse(recordService.contains("blockInfoRepository.findTopByOrderByHeightDesc()"));
        assertFalse(mountService.contains("blockInfoRepository.findTopByOrderByHeightDesc()"));
    }

    private String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
