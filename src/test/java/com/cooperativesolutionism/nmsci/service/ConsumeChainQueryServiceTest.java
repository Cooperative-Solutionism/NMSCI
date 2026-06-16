package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumeChainQueryServiceTest {

    private static final Sort EDGE_SORT = Sort.by(
            Sort.Order.desc("relatedTransactionMountTimestamp"),
            Sort.Order.desc("id")
    );

    @Test
    void edgeTargetQueryRequestsOneExtraRowAndReportsHasNext() {
        UUID targetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ConsumeChainEdgeRepository repository = mock(ConsumeChainEdgeRepository.class);
        when(repository.findConsumeChainEdgesByTarget(targetId, (short) 1, 0L, Long.MAX_VALUE, 3, 0L))
                .thenReturn(List.of(
                        edge("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        edge("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                        edge("cccccccc-cccc-cccc-cccc-cccccccccccc")
                ));

        ConsumeChainQueryService service = new ConsumeChainQueryService();
        ReflectionTestUtils.setField(service, "consumeChainEdgeRepository", repository);
        Pageable pageable = PageRequest.of(0, 2, EDGE_SORT);

        Slice<ConsumeChainEdge> result = service.getConsumeChainEdgesById(null, targetId, (short) 1, 0L, Long.MAX_VALUE, pageable);

        assertEquals(2, result.getNumberOfElements());
        assertTrue(result.hasNext());
        assertEquals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), result.getContent().get(0).getId());
        assertEquals(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), result.getContent().get(1).getId());
        verify(repository).findConsumeChainEdgesByTarget(targetId, (short) 1, 0L, Long.MAX_VALUE, 3, 0L);
    }

    @Test
    void edgeSourceTargetQueryPassesPageOffsetToRepository() {
        UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ConsumeChainEdgeRepository repository = mock(ConsumeChainEdgeRepository.class);
        when(repository.findConsumeChainEdges(sourceId, targetId, (short) 1, 100L, 200L, 3, 2L))
                .thenReturn(List.of(edge("dddddddd-dddd-dddd-dddd-dddddddddddd")));

        ConsumeChainQueryService service = new ConsumeChainQueryService();
        ReflectionTestUtils.setField(service, "consumeChainEdgeRepository", repository);
        Pageable pageable = PageRequest.of(1, 2, EDGE_SORT);

        Slice<ConsumeChainEdge> result = service.getConsumeChainEdgesById(sourceId, targetId, (short) 1, 100L, 200L, pageable);

        assertEquals(1, result.getNumberOfElements());
        verify(repository).findConsumeChainEdges(sourceId, targetId, (short) 1, 100L, 200L, 3, 2L);
    }

    @Test
    void edgeQueryLimitFailsFastWhenPageSizeWouldOverflowExtraRow() {
        UUID targetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ConsumeChainQueryService service = new ConsumeChainQueryService();

        assertThrows(ArithmeticException.class, () -> service.getConsumeChainEdgesById(
                null,
                targetId,
                (short) 1,
                0L,
                Long.MAX_VALUE,
                PageRequest.of(0, Integer.MAX_VALUE, EDGE_SORT)
        ));
    }

    private ConsumeChainEdge edge(String id) {
        ConsumeChainEdge edge = new ConsumeChainEdge();
        edge.setId(UUID.fromString(id));
        return edge;
    }
}
