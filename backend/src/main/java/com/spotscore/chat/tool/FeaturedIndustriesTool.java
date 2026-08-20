package com.spotscore.chat.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotscore.dto.IndustryResponse;
import com.spotscore.query.IndustryQueryService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 드롭다운에 노출되는 추천 업종(featured=true) 목록만 반환한다 - 전체 75개가
 * 아니라 CLAUDE.md가 확정한 상위 30개 기준을 그대로 따른다(IndustryQueryService와
 * 동일 동작).
 */
@Component
public class FeaturedIndustriesTool implements ChatTool {

    private final IndustryQueryService industryQueryService;
    private final ObjectMapper objectMapper;

    public FeaturedIndustriesTool(IndustryQueryService industryQueryService, ObjectMapper objectMapper) {
        this.industryQueryService = industryQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "get_featured_industries";
    }

    @Override
    public String description() {
        return "대시보드에서 선택 가능한 추천 업종 목록(industryCode/industryName)을 반환한다. "
                + "사용자가 업종을 특정하지 않았거나, 어떤 업종 코드가 존재하는지 확인이 필요할 때 사용한다.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        schema.set("required", objectMapper.createArrayNode());
        return schema;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        List<IndustryResponse> industries = industryQueryService.getIndustries(false);
        return objectMapper.writeValueAsString(Map.of("industries", industries));
    }
}
