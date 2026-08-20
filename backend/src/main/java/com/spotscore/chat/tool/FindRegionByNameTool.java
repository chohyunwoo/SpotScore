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
 * 지역명으로 지역 코드를 찾는다. 점수는 항상 "지역x업종" 조합에 대해서만 계산
 * 가능하므로(CLAUDE.md 핵심 원칙), 별도 지역 전체 검색 리포지토리를 새로 만들지
 * 않고 프론트 RegionSearchBox.tsx와 동일하게 특정 업종의 랭킹 결과를 이름으로
 * 필터링한다.
 */
@Component
public class FindRegionByNameTool implements ChatTool {

    private static final int MAX_RESULTS = 10;

    private final ScoreQueryService scoreQueryService;
    private final ObjectMapper objectMapper;

    public FindRegionByNameTool(ScoreQueryService scoreQueryService, ObjectMapper objectMapper) {
        this.scoreQueryService = scoreQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "find_region_by_name";
    }

    @Override
    public String description() {
        return "지역 이름 일부(nameQuery)로 해당 업종(industryCode)의 랭킹 안에서 일치하는 지역 코드를 찾는다. "
                + "regionCode를 모르고 지역 이름만 알 때 get_region_detail 호출 전에 사용한다.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("industryCode")
                .put("type", "string")
                .put("description", "검색 대상 업종 코드");
        properties.putObject("nameQuery")
                .put("type", "string")
                .put("description", "찾고자 하는 지역명의 일부 문자열 (예: \"역삼\")");
        ArrayNode required = schema.putArray("required");
        required.add("industryCode");
        required.add("nameQuery");
        return schema;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        JsonNode args = objectMapper.readTree(argumentsJson);
        String industryCode = args.path("industryCode").asText(null);
        String nameQuery = args.path("nameQuery").asText(null);
        if (industryCode == null || nameQuery == null || industryCode.isBlank() || nameQuery.isBlank()) {
            return "{\"error\":\"industryCode와 nameQuery가 모두 필요합니다\"}";
        }

        List<RankingItem> matches = scoreQueryService.getRanking(industryCode).stream()
                .filter(item -> item.regionName().contains(nameQuery))
                .limit(MAX_RESULTS)
                .toList();

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode regions = result.putArray("regions");
        matches.forEach(item -> {
            ObjectNode node = regions.addObject();
            node.put("regionCode", item.regionCode());
            node.put("regionName", item.regionName());
            node.put("totalScore", item.totalScore());
        });
        if (matches.isEmpty()) {
            result.put("message", "일치하는 지역을 찾지 못했습니다 - 해당 업종의 랭킹 안에 이 이름이 없습니다");
        }
        return objectMapper.writeValueAsString(result);
    }
}
