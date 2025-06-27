package com.cooperativesolutionism.nmsci.validator;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ByteArraySizeValidator implements ConstraintValidator<ByteArraySize, byte[]> {
    private int expectedLength;

    @Override
    public void initialize(ByteArraySize constraintAnnotation) {
        this.expectedLength = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(byte[] value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        return value.length == expectedLength;
    }
}
