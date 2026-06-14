package com.cooperativesolutionism.nmsci.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequestUtil {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    /**
     * 消息分页查询的统一排序：先按中心确认时间戳倒序，再按 id 倒序兜底稳定排序。
     * 供各消息分页控制器复用，避免重复定义。
     */
    public static final Sort MESSAGE_QUERY_SORT = Sort.by(Sort.Order.desc("confirmTimestamp"), Sort.Order.desc("id"));

    private PageRequestUtil() {
    }

    /**
     * 按 {@link #MESSAGE_QUERY_SORT} 构建消息分页查询的 {@link Pageable}。
     */
    public static Pageable ofMessageQuery(int page, int size) {
        return of(page, size, MESSAGE_QUERY_SORT);
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
