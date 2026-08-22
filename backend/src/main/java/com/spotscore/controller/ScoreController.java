package com.spotscore.controller;

import com.spotscore.admin.ScoreWeightAdminService;
import com.spotscore.dto.RankingItem;
import com.spotscore.dto.ScoreDetailResponse;
import com.spotscore.dto.ScoreWeightConfigResponse;
import com.spotscore.query.ScoreQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Score", description = "지역x업종 점수 랭킹/상세 조회")
@RestController
@RequestMapping("/api/v1/scores")
public class ScoreController {

    private static final Logger log = LoggerFactory.getLogger(ScoreController.class);

    private final ScoreQueryService scoreQueryService;
    private final ScoreWeightAdminService scoreWeightAdminService;

    public ScoreController(ScoreQueryService scoreQueryService, ScoreWeightAdminService scoreWeightAdminService) {
        this.scoreQueryService = scoreQueryService;
        this.scoreWeightAdminService = scoreWeightAdminService;
    }

    @Operation(summary = "업종별 지역 랭킹 조회",
            description = "배치 미실행 등으로 데이터가 없으면 빈 배열을 200으로 반환한다.")
    @GetMapping("/ranking")
    public List<RankingItem> getRanking(@RequestParam String industryCode, HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}, industryCode: {}", request.getRequestURI(), industryCode);
        return scoreQueryService.getRanking(industryCode);
    }

    @Operation(summary = "지역x업종 상세 점수 조회",
            description = "해당 조합의 점수 데이터가 없으면 404(RESOURCE_NOT_FOUND)를 반환한다.")
    @GetMapping("/detail")
    public ScoreDetailResponse getDetail(@RequestParam String regionCode, @RequestParam String industryCode,
                                          HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}, regionCode: {}, industryCode: {}",
                request.getRequestURI(), regionCode, industryCode);
        return scoreQueryService.getDetail(regionCode, industryCode);
    }

    @Operation(summary = "가중치 설정 조회(공개, 읽기 전용)",
            description = "SCORE_WEIGHT_CONFIG 값을 그대로 노출한다 - 화면의 '점수 근거 공개' 문구가 가중치 숫자를 " +
                    "하드코딩하지 않도록 조회 전용으로 공개한다. 값 변경은 여전히 " +
                    "PUT /api/v1/admin/score-weights/{weightKey}(X-Admin-Api-Key 필요)에서만 가능하다.")
    @GetMapping("/weights")
    public List<ScoreWeightConfigResponse> getWeights(HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}", request.getRequestURI());
        return scoreWeightAdminService.getAllWeights();
    }
}
