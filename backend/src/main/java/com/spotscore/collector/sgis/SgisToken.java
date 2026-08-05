package com.spotscore.collector.sgis;

import java.time.Duration;
import java.time.Instant;

record SgisToken(String accessToken, Instant expiresAt) {

    // 요청 도중 만료되는 상황을 피하기 위해 만료 60초 전에 미리 재발급한다
    private static final Duration EXPIRY_BUFFER = Duration.ofSeconds(60);

    static SgisToken of(String accessToken, String accessTimeoutEpochMillis) {
        Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(accessTimeoutEpochMillis));
        return new SgisToken(accessToken, expiresAt);
    }

    boolean isValid() {
        return Instant.now().isBefore(expiresAt.minus(EXPIRY_BUFFER));
    }
}
