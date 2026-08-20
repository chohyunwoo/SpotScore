package com.spotscore.chat.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotscore.dto.RankingItem;
import com.spotscore.dto.ScoreDetailResponse;
import com.spotscore.exception.ResourceNotFoundException;
import com.spotscore.query.ScoreQueryService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 지역x업종 조합 하나의 상세 브레이크다운(인구/가구/경쟁여유도/연령적합도)을 반환한다.
 * ScoreDetailResponse에는 percentileRank/attractivenessTier가 없어(RankingItem에만
 * 존재) 랭킹을 한 번 더 조회해 병합한다. 데이터가 없는 조합은 404 예외 대신
 * "found: false"로 표현해 챗봇이 자연스럽게 "데이터가 없다"고 답할 수 있게 한다.
 */
@Component
public class RegionDetailTool implements ChatTool {

    private final ScoreQueryService scoreQueryService;
    private final ObjectMapper objectMapper;

    public RegionDetailTool(ScoreQueryService scoreQueryService, ObjectMapper objectMapper) {
        this.scoreQueryService = scoreQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "get_region_detail";
    }

    @Override
    public String description() {
        return "지정한 지역 코드(regionCode)와 업종 코드(industryCode) 조합의 상세 점수 브레이크다운"
                + "(인구 규모, 가구 구조, 경쟁 여유도, 해당하면 연령적합도)과 원자료 값을 반환한다. "
                + "\"왜 이 점수인지\" 설명할 때 사용한다.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("regionCode")
                .put("type", "string")
                .put("description", "get_ranking_by_industry 또는 find_region_by_name으로 확인한 지역 코드");
        properties.putObject("industryCode")
                .put("type", "string")
                .put("description", "업종 코드");
        ArrayNode required = schema.putArray("required");
        required.add("regionCode");
        required.add("industryCode");
        return schema;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        JsonNode args = objectMapper.readTree(argumentsJson);
        String regionCode = args.path("regionCode").asText(null);
        String industryCode = args.path("industryCode").asText(null);
        if (regionCode == null || industryCode == null || regionCode.isBlank() || industryCode.isBlank()) {
            return "{\"error\":\"regionCode와 industryCode가 모두 필요합니다\"}";
        }

        ScoreDetailResponse detail;
        try {
            detail = scoreQueryService.getDetail(regionCode, industryCode);
        } catch (ResourceNotFoundException ex) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("found", false);
            result.put("message", ex.getMessage());
            return objectMapper.writeValueAsString(result);
        }

        Optional<RankingItem> rankingMatch = scoreQueryService.getRanking(industryCode).stream()
                .filter(item -> item.regionCode().equals(regionCode))
                .findFirst();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("found", true);
        result.put("regionName", detail.regionName());
        result.put("industryName", detail.industryName());
        result.put("totalScore", detail.totalScore());
        result.put("populationScore", detail.populationScore());
        result.put("householdScore", detail.householdScore());
        result.put("densityScore", detail.densityScore());
        rankingMatch.ifPresent(item -> {
            result.put("percentileRank", item.percentileRank());
            result.put("attractivenessTier", item.attractivenessTier().name());
        });

        if (detail.populationStat() != null) {
            ObjectNode populationStat = result.putObject("populationStat");
            populationStat.put("totalPopulation", detail.populationStat().totalPopulation());
            populationStat.put("householdCount", detail.populationStat().householdCount());
            populationStat.put("avgHouseholdSize", detail.populationStat().avgHouseholdSize());
        }
        if (detail.competitionStat() != null) {
            ObjectNode competitionStat = result.putObject("competitionStat");
            competitionStat.put("storeCount", detail.competitionStat().storeCount());
            competitionStat.put("storeCountPerCapita", detail.competitionStat().storeCountPerCapita());
        }
        if (detail.ageStat() != null && detail.ageStat().ageScore() != null) {
            ObjectNode ageStat = result.putObject("ageStat");
            ageStat.put("ageRatioPercent", detail.ageStat().ageRatioPercent());
            ageStat.put("ageScore", detail.ageStat().ageScore());
            ageStat.put("direction", detail.ageStat().direction().name());
            // 시스템 프롬프트만 믿지 않는 이중 방어 - tool 결과 자체에 통계 기준 차이
            // 문구를 심어 모델이 놓치지 않게 한다(CLAUDE.md 연령 구성 지표 섹션).
            ageStat.put("ageScoreCaveat",
                    "이 지표는 SGIS 추계인구가 아닌 KOSIS 주민등록인구 통계를 기준으로 계산되어 "
                            + "다른 인구 지표(populationStat)와 통계 기준이 다릅니다.");
        }

        return objectMapper.writeValueAsString(result);
    }
}
