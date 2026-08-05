package com.spotscore.controller;

import com.spotscore.admin.ScoreWeightAdminService;
import com.spotscore.dto.ScoreWeightConfigResponse;
import com.spotscore.dto.ScoreWeightUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCORE_WEIGHT_CONFIG 조회/수정용 관리 엔드포인트 - 재배포 없이 가중치를 조정할
 * 수 있게 한다. 가중치 수치 자체는 여기서 정하지 않고 DB에 이미 있는 값을 그대로
 * 조회/수정할 뿐이다(ScoreWeightAdminService 참고).
 *
 * TODO: 운영 배포 전 인증 추가 필요 - 현재는 로컬 전용으로 인증 없이 열려 있다.
 */
@Tag(name = "ScoreWeightAdmin", description = "AHP 가중치 설정 조회/수정 (인증 미적용 - 로컬 전용)")
@RestController
@RequestMapping("/api/v1/admin/score-weights")
public class ScoreWeightAdminController {

    private static final Logger log = LoggerFactory.getLogger(ScoreWeightAdminController.class);

    private final ScoreWeightAdminService scoreWeightAdminService;

    public ScoreWeightAdminController(ScoreWeightAdminService scoreWeightAdminService) {
        this.scoreWeightAdminService = scoreWeightAdminService;
    }

    @Operation(summary = "가중치 설정 전체 조회")
    @GetMapping
    public List<ScoreWeightConfigResponse> getWeights(HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}", request.getRequestURI());
        return scoreWeightAdminService.getAllWeights();
    }

    @Operation(summary = "가중치 설정 값 수정", description = "weightKey는 기존 SCORE_WEIGHT_CONFIG에 있는 값만 허용 - 없으면 404.")
    @PutMapping("/{weightKey}")
    public ScoreWeightConfigResponse updateWeight(@PathVariable String weightKey,
                                                   @RequestBody ScoreWeightUpdateRequest request,
                                                   HttpServletRequest httpRequest) {
        log.info("요청 수신 - endpoint: {}, weightKey: {}", httpRequest.getRequestURI(), weightKey);
        return scoreWeightAdminService.updateWeight(weightKey, request.weightValue());
    }
}
