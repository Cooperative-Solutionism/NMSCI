package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.exception.BadRequestException;

import java.util.function.Supplier;

final class ApiRequestBoundary {

    private ApiRequestBoundary() {
    }

    static <T> T badRequestOnIllegalArgument(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
