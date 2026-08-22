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
 * /api/v1/admin/**은 AdminApiKeyInterceptor가 X-Admin-Api-Key 헤더로 보호한다(WebConfig 참고).
 * 프론트가 "점수 근거 공개" 문구에 쓰는 읽기 전용 조회는 이 admin 엔드포인트가 아니라
 * 공개 엔드포인트 GET /api/v1/scores/weights(ScoreController)를 쓴다 - 조회는 비밀
 * 정보가 아니므로 값 변경(이 컨트롤러)만 인증 대상으로 남긴다(이슈 #17).
 */
@Tag(name = "ScoreWeightAdmin", description = "AHP 가중치 설정 조회/수정 (X-Admin-Api-Key 필요, 공개 조회는 GET /api/v1/scores/weights 참고)")
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
