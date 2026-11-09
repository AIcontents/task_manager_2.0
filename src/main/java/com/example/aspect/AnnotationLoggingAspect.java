package com.example.aspect;

import com.example.annotation.Loggable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Второй аспект: срабатывает только на методах, явно помеченных @Loggable
 * (вместо pointcut по пакету). Сразу видно, какие методы логируются, и их легко добавить или убрать.
 */
@Aspect
@Component
public class AnnotationLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(AnnotationLoggingAspect.class);

    @Around(value = "@annotation(loggable)", argNames = "pjp,loggable")
    public Object logAnnotated(ProceedingJoinPoint pjp, Loggable loggable) throws Throwable {
        String method = pjp.getSignature().toShortString();
        String description = loggable.value().isBlank() ? method : loggable.value();
        log.info("[@Loggable] {} -> {}", description, method);
        try {
            Object result = pjp.proceed();
            log.info("[@Loggable] {} -> OK", description);
            return result;
        } catch (Throwable e) {
            log.warn("[@Loggable] {} -> ошибка: {}", description, e.toString());
            throw e;
        }
    }
}
