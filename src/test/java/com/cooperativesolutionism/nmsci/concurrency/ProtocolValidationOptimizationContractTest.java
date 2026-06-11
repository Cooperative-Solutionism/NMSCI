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
    void transactionServicesUseMeasuredDifficultyLookupService() throws IOException {
        String recordService = source("src/main/java/com/cooperativesolutionism/nmsci/service/impl/TransactionRecordMsgServiceImpl.java");
        String mountService = source("src/main/java/com/cooperativesolutionism/nmsci/service/impl/TransactionMountMsgServiceImpl.java");

        assertTrue(recordService.contains("blockDifficultyService.currentTransactionDifficultyTarget()"));
        assertTrue(mountService.contains("blockDifficultyService.currentTransactionDifficultyTarget()"));
        assertFalse(recordService.contains("blockInfoRepository.findTopByOrderByHeightDesc()"));
        assertFalse(mountService.contains("blockInfoRepository.findTopByOrderByHeightDesc()"));
    }

    private String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
