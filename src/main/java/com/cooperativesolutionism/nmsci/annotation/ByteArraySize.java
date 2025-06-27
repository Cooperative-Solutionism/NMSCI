package com.cooperativesolutionism.nmsci.annotation;

import com.cooperativesolutionism.nmsci.validator.ByteArraySizeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ByteArraySizeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ByteArraySize {
    String message() default "字节数组长度必须为 {value} 字节";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int value(); // 目标长度
}
