package com.spotscore.chat.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spotscore.domain.AgeDirection;
import com.spotscore.domain.IndustryAgeDirection;
import com.spotscore.repository.IndustryAgeDirectionRepository;
import com.spotscore.scoring.LeafWeights;
import com.spotscore.scoring.ScoreWeightService;
import com.spotscore.scoring.WeightGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 업종의 실제 리프 가중치(인구규모/가구구조/경쟁여유도/연령적합도)를 반환해,
 * 챗봇이 "총점이 왜 이렇게 나왔는지"를 get_region_detail의 세부 점수와 곱해
 * 실제 계산식으로 설명할 수 있게 한다. 가중치는 SCORE_WEIGHT_CONFIG에서 매번
 * 조회하며(CLAUDE.md "가중치를 매직 넘버로 넣지 말 것"), 이 tool도 코드에 숫자를
 * 하드코딩하지 않고 ScoreWeightService를 그대로 재사용한다.
 */
@Component
public class ScoreWeightsTool implements ChatTool {

    private static final Logger log = LoggerFactory.getLogger(ScoreWeightsTool.class);

    private final IndustryAgeDirectionRepository industryAgeDirectionRepository;
    private final ScoreWeightService scoreWeightService;
    private final ObjectMapper objectMapper;

    public ScoreWeightsTool(IndustryAgeDirectionRepository industryAgeDirectionRepository,
                             ScoreWeightService scoreWeightService, ObjectMapper objectMapper) {
        this.industryAgeDirectionRepository = industryAgeDirectionRepository;
        this.scoreWeightService = scoreWeightService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "get_score_weights";
    }

    @Override
    public String description() {
        return "지정한 업종(industryCode)의 실제 점수 계산 가중치(인구규모/가구구조/경쟁여유도, 해당하면 연령적합도)를 "
                + "반환한다. get_region_detail의 populationScore/householdScore/densityScore/ageScore에 이 가중치를 곱해 "
                + "더하면 totalScore가 나온다 - \"왜 이 점수인지\"를 숫자 계산으로 설명할 때 반드시 이 tool을 먼저 호출하라.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("industryCode")
                .put("type", "string")
                .put("description", "가중치를 조회할 업종 코드");
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

        // ScoreCalculationService.resolveAgeDirection과 동일한 매핑 규칙(시딩 누락 시
        // NEUTRAL로 기본 처리) - 배치가 실제로 점수를 계산할 때 쓴 것과 같은 그룹을
        // 골라야 설명이 실제 저장된 totalScore와 어긋나지 않는다.
        AgeDirection direction = industryAgeDirectionRepository.findById(industryCode)
                .map(IndustryAgeDirection::getDirection)
                .orElseGet(() -> {
                    log.warn("get_score_weights - 업종별 연령 방향성 매핑 없음, NEUTRAL로 처리 - industryCode: {}", industryCode);
                    return AgeDirection.NEUTRAL;
                });
        WeightGroup weightGroup = WeightGroup.from(direction);
        LeafWeights weights = scoreWeightService.loadLeafWeights(weightGroup);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("industryCode", industryCode);
        result.put("weightGroup", weightGroup.name());
        result.put("populationWeight", weights.populationWeight());
        result.put("householdWeight", weights.householdWeight());
        result.put("competitionWeight", weights.competitionWeight());
        if (weights.hasAgeWeight()) {
            result.put("ageWeight", weights.ageWeight());
            result.put("formula",
                    "totalScore = populationScore*populationWeight + householdScore*householdWeight "
                            + "+ densityScore*competitionWeight + ageScore*ageWeight");
        } else {
            result.put("formula",
                    "totalScore = populationScore*populationWeight + householdScore*householdWeight "
                            + "+ densityScore*competitionWeight (이 업종은 NEUTRAL이라 연령적합도 리프가 없음)");
        }
        return objectMapper.writeValueAsString(result);
    }
}
