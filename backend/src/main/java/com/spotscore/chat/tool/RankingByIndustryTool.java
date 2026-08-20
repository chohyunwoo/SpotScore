package com.spotscore.chat.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotscore.dto.RankingItem;
import com.spotscore.query.ScoreQueryService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 업종 코드로 지역 랭킹을 조회한다. ScoreCacheRepository.findRankingWithPercentile이
 * 이미 total_score DESC로 정렬해 반환하므로 index 0이 최상위 지역이다 - 토큰 비용을
 * 줄이기 위해 상위 20개로 제한한다(전체 지역 수는 업종에 따라 400개를 넘을 수 있음).
 */
@Component
public class RankingByIndustryTool implements ChatTool {

    private static final int MAX_RESULTS = 20;

    private final ScoreQueryService scoreQueryService;
    private final ObjectMapper objectMapper;

    public RankingByIndustryTool(ScoreQueryService scoreQueryService, ObjectMapper objectMapper) {
        this.scoreQueryService = scoreQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "get_ranking_by_industry";
    }

    @Override
    public String description() {
        return "지정한 업종 코드(industryCode)의 지역별 창업 매력도 랭킹을 총점 내림차순 상위 " + MAX_RESULTS
                + "개까지 반환한다(index 0이 가장 높은 점수). 배치 데이터가 없으면 빈 목록을 반환한다.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("industryCode")
                .put("type", "string")
                .put("description", "get_featured_industries로 확인한 업종 코드");
        ArrayNode required = schema.putArray("required");
        required.add("industryCode");
        return schema;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        JsonNode args = objectMapper.readTree(argumentsJson);
        String industryCode = args.path("industryCode").asText(null);
        if (industryCode == null || industryCode.isBlank()) {
            return "{\"error\":\"industryCode가 필요합니다\"}";
        }

        List<RankingItem> ranking = scoreQueryService.getRanking(industryCode);
        if (ranking.isEmpty()) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("industryCode", industryCode);
            result.putArray("regions");
            result.put("message", "아직 이 업종에 대한 배치 데이터가 없습니다");
            return objectMapper.writeValueAsString(result);
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("industryCode", industryCode);
        ArrayNode regions = result.putArray("regions");
        ranking.stream().limit(MAX_RESULTS).forEach(item -> {
            ObjectNode node = regions.addObject();
            node.put("regionCode", item.regionCode());
            node.put("regionName", item.regionName());
            node.put("totalScore", item.totalScore());
            node.put("percentileRank", item.percentileRank());
            node.put("attractivenessTier", item.attractivenessTier().name());
        });
        return objectMapper.writeValueAsString(result);
    }
}
