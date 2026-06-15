package com.cooperativesolutionism.nmsci.exception;

/**
 * Conflict exception mapped by {@link GlobalExceptionHandler} to HTTP 409.
 * Used when a request conflicts with current resource state, lifecycle state,
 * or uniqueness constraints.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
