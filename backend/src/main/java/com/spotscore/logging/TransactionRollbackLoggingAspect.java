package com.spotscore.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @Transactional 메서드에서 런타임 예외가 밖으로 전파되는 시점 = Spring이 트랜잭션을
 * rollback-only로 마킹하는 시점이므로, 여기서 WARN 로그(롤백 사유)를 남기고 그대로
 * rethrow한다 (CLAUDE.md 로깅 가이드 - DB 접근: 트랜잭션 롤백).
 */
@Aspect
@Component
public class TransactionRollbackLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionRollbackLoggingAspect.class);

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object logRollbackOnException(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error ex) {
            log.warn("트랜잭션 롤백 - method: {}, 롤백 사유: {}",
                    joinPoint.getSignature().toShortString(), ex.getMessage());
            throw ex;
        }
    }
}
