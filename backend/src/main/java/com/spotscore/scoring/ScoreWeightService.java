package com.spotscore.scoring;

import com.spotscore.repository.ScoreWeightConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SCORE_WEIGHT_CONFIG에서 하이브리드 AHP 쌍대비교 값을 조회해 리프 가중치를
 * 계산한다. CLAUDE.md 가중치 산출 방법(v2)/연령 구성 지표(v3) 섹션: 최종 가중치는
 * DEMAND_WEIGHT/SUPPLY_WEIGHT/POPULATION_RATIO/HOUSEHOLD_RATIO 4개(COMMON, 두 그룹
 * 공유)로부터 계산하고, DIRECTIONAL 그룹(연령적합도를 쓰는 POSITIVE/NEGATIVE 업종)은
 * 여기에 CORE_WEIGHT를 곱한 뒤 AGE_WEIGHT를 리프로 추가한다. 숫자는 절대
 * 코드에 하드코딩하지 않고 이 클래스에서 매번 DB 조회로 계산한다.
 */
@Service
public class ScoreWeightService {

    private static final Logger log = LoggerFactory.getLogger(ScoreWeightService.class);

    private final ScoreWeightConfigRepository scoreWeightConfigRepository;

    public ScoreWeightService(ScoreWeightConfigRepository scoreWeightConfigRepository) {
        this.scoreWeightConfigRepository = scoreWeightConfigRepository;
    }

    public LeafWeights loadLeafWeights(WeightGroup group) {
        double demandWeight = requireWeight("DEMAND_WEIGHT");
        double supplyWeight = requireWeight("SUPPLY_WEIGHT");
        double populationRatio = requireWeight("POPULATION_RATIO");
        double householdRatio = requireWeight("HOUSEHOLD_RATIO");

        if (group == WeightGroup.NEUTRAL) {
            double populationWeight = demandWeight * populationRatio;
            double householdWeight = demandWeight * householdRatio;
            double competitionWeight = supplyWeight;

            log.debug("가중치 계산 결과(NEUTRAL) - demandWeight: {}, supplyWeight: {}, populationRatio: {}, " +
                            "householdRatio: {} -> populationWeight: {}, householdWeight: {}, competitionWeight: {}",
                    demandWeight, supplyWeight, populationRatio, householdRatio,
                    populationWeight, householdWeight, competitionWeight);

            return new LeafWeights(populationWeight, householdWeight, competitionWeight, null);
        }

        double coreWeight = requireWeight("CORE_WEIGHT");
        double ageWeight = requireWeight("AGE_WEIGHT");

        double populationWeight = coreWeight * demandWeight * populationRatio;
        double householdWeight = coreWeight * demandWeight * householdRatio;
        double competitionWeight = coreWeight * supplyWeight;

        log.debug("가중치 계산 결과(DIRECTIONAL) - demandWeight: {}, supplyWeight: {}, populationRatio: {}, " +
                        "householdRatio: {}, coreWeight: {}, ageWeight: {} -> populationWeight: {}, householdWeight: {}, " +
                        "competitionWeight: {}",
                demandWeight, supplyWeight, populationRatio, householdRatio, coreWeight, ageWeight,
                populationWeight, householdWeight, competitionWeight);

        return new LeafWeights(populationWeight, householdWeight, competitionWeight, ageWeight);
    }

    private double requireWeight(String weightKey) {
        return scoreWeightConfigRepository.findByWeightKey(weightKey)
                .map(config -> config.getWeightValue().doubleValue())
                .orElseThrow(() -> {
                    log.error("가중치 설정 누락 - weight_key: {} (SCORE_WEIGHT_CONFIG 시드 데이터 확인 필요, V11 마이그레이션 참고)",
                            weightKey);
                    return new IllegalStateException("가중치 설정 누락: " + weightKey);
                });
    }
}
