package com.cooperativesolutionism.nmsci.concurrency;

import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.MessagePayloadProjection;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockGenerationOptimizationContractTest {

    @Test
    void msgAbstractRepositoryUsesKeysetLimitQuery() throws NoSuchMethodException {
        Method method = MsgAbstractRepository.class.getMethod(
                "findNextNotInBlockBatch",
                Long.class,
                byte[].class,
                int.class
        );
        Query query = method.getAnnotation(Query.class);

        assertNotNull(query);
        assertTrue(query.nativeQuery());
        assertTrue(query.value().contains("confirm_timestamp, id"));
        assertTrue(query.value().toLowerCase().contains("limit"));
    }

    @Test
    void messageRepositoriesExposeRepositoryPayloadProjection() throws NoSuchMethodException {
        List<Class<?>> repositories = List.of(
                FlowNodeRegisterMsgRepository.class,
                CentralPubkeyEmpowerMsgRepository.class,
                CentralPubkeyLockedMsgRepository.class,
                FlowNodeLockedMsgRepository.class,
                TransactionRecordMsgRepository.class,
                TransactionMountMsgRepository.class
        );

        for (Class<?> repository : repositories) {
            Method method = repository.getMethod("findPayloadByIdIn", Collection.class);
            assertEquals(List.class, method.getReturnType());
            assertTrue(method.getGenericReturnType().getTypeName().contains(MessagePayloadProjection.class.getName()));
        }
    }

    @Test
    void repositoriesDoNotImportBlockPackageTypes() throws IOException {
        List<Path> repositorySources = List.of(
                Path.of("src/main/java/com/cooperativesolutionism/nmsci/repository/FlowNodeRegisterMsgRepository.java"),
                Path.of("src/main/java/com/cooperativesolutionism/nmsci/repository/CentralPubkeyEmpowerMsgRepository.java"),
                Path.of("src/main/java/com/cooperativesolutionism/nmsci/repository/CentralPubkeyLockedMsgRepository.java"),
                Path.of("src/main/java/com/cooperativesolutionism/nmsci/repository/FlowNodeLockedMsgRepository.java"),
                Path.of("src/main/java/com/cooperativesolutionism/nmsci/repository/TransactionRecordMsgRepository.java"),
                Path.of("src/main/java/com/cooperativesolutionism/nmsci/repository/TransactionMountMsgRepository.java")
        );

        for (Path repositorySource : repositorySources) {
            String source = Files.readString(repositorySource);
            assertTrue(!source.contains("com.cooperativesolutionism.nmsci.block."), repositorySource + " must not depend on block package types");
        }
    }

    @Test
    void blockAssemblerDoesNotUseArrayUtilsConcatenation() throws IOException {
        String blockAssemblerSource = Files.readString(Path.of("src/main/java/com/cooperativesolutionism/nmsci/block/BlockAssembler.java"));

        assertTrue(!blockAssemblerSource.contains("ArrayUtils"), "BlockAssembler should not use repeated ArrayUtils.addAll copies");
    }
}
