package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공개(무인증) 챗봇 엔드포인트(/api/v1/chat)의 IP당 요청 레이트리밋 설정. 숫자를 코드에
 * 하드코딩하지 않고 profile별 설정에서 주입한다(CLAUDE.md 확장성 원칙). 토큰 버킷
 * (bucket4j): {@code refillPeriodSeconds}마다 버킷이 {@code capacity}만큼 다시 찬다.
 *
 * @param capacity            IP당 시간창 내 허용 요청 수(버킷 용량)
 * @param refillPeriodSeconds 버킷이 가득 다시 차는 주기(초)
 */
@ConfigurationProperties(prefix = "spotscore.chat.rate-limit")
public record ChatRateLimitProperties(int capacity, int refillPeriodSeconds) {

    public ChatRateLimitProperties {
        if (capacity <= 0) {
            capacity = 20;
        }
        if (refillPeriodSeconds <= 0) {
            refillPeriodSeconds = 60;
        }
    }
}
