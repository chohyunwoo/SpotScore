package com.spotscore.controller;

import com.spotscore.batch.BatchResult;
import com.spotscore.batch.MonthlyDataCollectionBatchJob;
import com.spotscore.dto.BatchTriggerResponse;
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
 * 운영용 배치 수동 트리거. 월 1회 스케줄 실행과 동일한 로직을 즉시 1회 실행한다
 * (데모/장애 재처리 목적) - CLAUDE.md API 버전 프리픽스 원칙에 따라 /api/v1 하위에 둔다.
 */
@Tag(name = "Batch", description = "데이터 수집/점수 계산 배치 수동 트리거")
@RestController
public class BatchController {

    private static final Logger log = LoggerFactory.getLogger(BatchController.class);

    private final MonthlyDataCollectionBatchJob batchJob;

    public BatchController(MonthlyDataCollectionBatchJob batchJob) {
        this.batchJob = batchJob;
    }

    @Operation(summary = "배치 수동 트리거", description = "SGIS/상권정보 수집 + 점수 재계산을 즉시 1회 실행한다.")
    @PostMapping("/api/v1/admin/batch/run")
    public BatchTriggerResponse runBatch(
            @RequestParam(required = false) String snapshotDate,
            HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}, snapshotDate: {}", request.getRequestURI(), snapshotDate);
        LocalDate targetDate = snapshotDate != null ? LocalDate.parse(snapshotDate) : LocalDate.now();
        BatchResult result = batchJob.run(targetDate);
        return BatchTriggerResponse.from(result);
    }
}
