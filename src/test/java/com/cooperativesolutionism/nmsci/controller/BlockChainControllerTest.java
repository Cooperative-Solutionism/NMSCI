package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockChainControllerTest {

    private BlockChainController controllerWith(BlockChainService service) {
        return new BlockChainController(service);
    }

    @Test
    void latestThrowsNotFoundWhenNoBlockExists() {
        BlockChainService service = mock(BlockChainService.class);
        when(service.getLastBlock()).thenReturn(null);

        assertThrows(NotFoundException.class, () -> controllerWith(service).getLastBlock());
    }

    @Test
    void latestReturnsBlockWhenPresent() {
        BlockChainService service = mock(BlockChainService.class);
        BlockInfo block = mock(BlockInfo.class);
        when(service.getLastBlock()).thenReturn(block);

        assertSame(block, controllerWith(service).getLastBlock().getData());
    }
}
