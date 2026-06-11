package com.cooperativesolutionism.nmsci.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequestUtil {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    private PageRequestUtil() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new IllegalArgumentException("分页页码不能小于0");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("分页大小必须大于0");
        }

        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("分页大小不能超过" + MAX_SIZE);
        }

        return PageRequest.of(page, size, sort);
    }
}
