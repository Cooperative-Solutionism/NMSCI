package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * 按 id 查询实体的公共助手，收敛各服务重复的「id 非空校验 + findById().orElseThrow」样板，
 * 并赋予正确的错误语义：id 为空抛 {@link BadRequestException}(400)，目标不存在抛 {@link NotFoundException}(404)。
 */
public final class EntityLookup {

    private EntityLookup() {
    }

    /**
     * @param id     主键
     * @param label  实体中文名（用于拼装错误信息，如「流转节点注册信息」）
     * @param finder 按 id 查询的方法引用，通常为 {@code repository::findById}
     */
    public static <T> T requireById(UUID id, String label, Function<UUID, Optional<T>> finder) {
        if (id == null) {
            throw new BadRequestException(label + "id不能为空");
        }
        return finder.apply(id)
                .orElseThrow(() -> new NotFoundException(label + "id(" + id + ")不存在"));
    }
}
