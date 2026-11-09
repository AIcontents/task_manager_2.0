package com.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Маркер для методов, вызовы которых нужно логировать (см. AnnotationLoggingAspect).
 * RUNTIME - чтобы аннотация была видна Spring AOP во время работы приложения.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Loggable {

    /** Необязательное описание операции для лога. */
    String value() default "";
}
