package com.spotscore.controller;

import com.spotscore.dto.ScoreRecalculationResponse;
import com.spotscore.scoring.ScoreCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * SCORE_WEIGHT_CONFIG이 바뀌었을 때 SCORE_CACHE만 다시 계산한다. 원자료
 * (population_stat/store_count)는 그대로 두고 가중치만 새로 적용하는 것이므로,
 * 원자료를 다시 수집하는 MonthlyDataCollectionBatchJob(POST /api/v1/admin/batch/run)
 * 전체를 재실행할 필요가 없다 - 그건 SGIS/상권정보 실외부 API를 다시 호출해 불필요한
 * 트래픽을 발생시킨다. year/snapshotDate는 재계산 대상 원자료가 실제로 적재된
 * 값과 일치해야 한다(다르면 ScoreCalculationService가 WARN 로그 후 0건 처리하고 끝난다).
 *
 * /api/v1/admin/**은 AdminApiKeyInterceptor가 X-Admin-Api-Key 헤더로 보호한다(WebConfig 참고).
 */
@Tag(name = "ScoreRecalculation", description = "가중치 변경 후 SCORE_CACHE만 재계산 (원자료 재수집 없음, X-Admin-Api-Key 필요)")
@RestController
public class ScoreRecalculationController {

    private static final Logger log = LoggerFactory.getLogger(ScoreRecalculationController.class);

    private final ScoreCalculationService scoreCalculationService;

    public ScoreRecalculationController(ScoreCalculationService scoreCalculationService) {
        this.scoreCalculationService = scoreCalculationService;
    }

    @Operation(summary = "점수 재계산 수동 트리거",
            description = "year/snapshotDate에 해당하는 population_stat/store_count로 SCORE_WEIGHT_CONFIG " +
                    "최신값을 적용해 SCORE_CACHE를 다시 계산한다. 생략 시 year=올해, snapshotDate=오늘.")
    @PostMapping("/api/v1/admin/scores/recalculate")
    public ScoreRecalculationResponse recalculate(@RequestParam(required = false) Integer year,
                                                   @RequestParam(required = false) String snapshotDate,
                                                   HttpServletRequest request) {
        LocalDate targetDate = snapshotDate != null ? LocalDate.parse(snapshotDate) : LocalDate.now();
        int targetYear = year != null ? year : targetDate.getYear();
        log.info("요청 수신 - endpoint: {}, year: {}, snapshotDate: {}", request.getRequestURI(), targetYear, targetDate);

        int saved = scoreCalculationService.recalculateAll(targetYear, targetDate);
        return new ScoreRecalculationResponse(targetYear, targetDate, saved);
    }
}
