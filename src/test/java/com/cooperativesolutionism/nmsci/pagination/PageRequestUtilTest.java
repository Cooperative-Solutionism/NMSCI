package com.cooperativesolutionism.nmsci.pagination;

import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRequestUtilTest {

    @Test
    void createsPageRequestWithRequestedPageSizeAndSort() {
        Sort sort = Sort.by(Sort.Order.desc("confirmTimestamp"), Sort.Order.desc("id"));

        Pageable pageable = PageRequestUtil.of(1, 50, sort);

        assertEquals(1, pageable.getPageNumber());
        assertEquals(50, pageable.getPageSize());
        assertEquals(sort, pageable.getSort());
    }

    @Test
    void rejectsNegativePage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PageRequestUtil.of(-1, 50, Sort.unsorted())
        );

        assertEquals("分页页码不能小于0", exception.getMessage());
    }

    @Test
    void rejectsSizeGreaterThanMaximum() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PageRequestUtil.of(0, 201, Sort.unsorted())
        );

        assertEquals("分页大小不能超过200", exception.getMessage());
    }
}
