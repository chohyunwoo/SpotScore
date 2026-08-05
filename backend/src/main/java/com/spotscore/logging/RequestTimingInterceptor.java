package com.spotscore.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestTimingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingInterceptor.class);
    private static final String START_TIME_ATTRIBUTE = "spotscore.requestStartTimeNanos";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object startedAt = request.getAttribute(START_TIME_ATTRIBUTE);
        if (!(startedAt instanceof Long startNanos)) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        log.debug("응답 처리 시간 - endpoint: {}, status: {}, 소요시간: {}ms",
                request.getRequestURI(), response.getStatus(), elapsedMillis);
    }
}
