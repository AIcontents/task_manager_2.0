package com.example.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Сквозное логирование и тайминг сервисного слоя. Весь код - здесь:
 * в TaskService/TaskServiceImpl, контроллере и репозитории нет ни строки про логирование.
 * <p>
 * Нужны обе аннотации: @Aspect говорит, что это аспект, а @Component делает класс Spring-бином
 * (без бина Spring аспект не увидит и ничего не перехватит).
 * <p>
 * Ограничение прокси: если метод сервиса вызывает другой метод ТОГО ЖЕ класса (this.method()),
 * вызов идёт мимо прокси и аспект не срабатывает (например, getOrThrow -> findById).
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /** Один раз описываем, куда применяются все advice - дальше везде только имя метода (DRY). */
    @Pointcut("execution(* com.example.service.TaskService.*(..))")
    public void taskServiceMethods() {
    }

    @Before("taskServiceMethods()")
    public void logBefore(JoinPoint joinPoint) {
        log.info("→ {} called with args: {}",
                joinPoint.getSignature().getName(),
                Arrays.toString(joinPoint.getArgs()));
    }

    /** @After срабатывает всегда - и при успехе, и при исключении (только при успехе - @AfterReturning). */
    @After("taskServiceMethods()")
    public void logAfter(JoinPoint joinPoint) {
        log.info("← {} finished", joinPoint.getSignature().getName());
    }

    /**
     * ProceedingJoinPoint (а не JoinPoint) и throws Throwable. Без pjp.proceed() реальный метод
     * не выполнится; исключение пробрасывается дальше, чтобы его поймал @ControllerAdvice.
     */
    @Around("taskServiceMethods()")
    public Object measureTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed(); // вызываем реальный метод
            log.info("{} completed in {}ms", pjp.getSignature().getName(), elapsedMillis(start));
            return result;
        } catch (Throwable e) {
            log.warn("{} failed after {}ms: {}", pjp.getSignature().getName(), elapsedMillis(start), e.getMessage());
            throw e; // пробрасываем - иначе @ControllerAdvice не увидит исключение
        }
    }

    /** Наблюдатель, а не обработчик: исключение не подавляется и идёт дальше к @ControllerAdvice. */
    @AfterThrowing(pointcut = "taskServiceMethods()", throwing = "ex")
    public void logException(JoinPoint joinPoint, Exception ex) {
        log.warn("✖ {}.{} threw {}: {}",
                joinPoint.getTarget().getClass().getSimpleName(),
                joinPoint.getSignature().getName(),
                ex.getClass().getSimpleName(),
                ex.getMessage());
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
