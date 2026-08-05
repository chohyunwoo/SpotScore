package com.spotscore.scoring;

import com.spotscore.repository.ScoreWeightConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SCORE_WEIGHT_CONFIG에서 하이브리드 AHP 쌍대비교 값을 조회해 리프 가중치를
 * 계산한다. CLAUDE.md 5.2.1절: 최종 가중치 = 수요 가중치 x 하위 비율(인구 규모/
 * 가구 구조), 공급 가중치는 그대로(경쟁 밀집도). 숫자는 절대 코드에 하드코딩하지
 * 않고 이 클래스에서 매번 DB 조회로 계산한다.
 */
@Service
public class ScoreWeightService {

    private static final Logger log = LoggerFactory.getLogger(ScoreWeightService.class);

    private final ScoreWeightConfigRepository scoreWeightConfigRepository;

    public ScoreWeightService(ScoreWeightConfigRepository scoreWeightConfigRepository) {
        this.scoreWeightConfigRepository = scoreWeightConfigRepository;
    }

    public LeafWeights loadLeafWeights() {
        double demandWeight = requireWeight("DEMAND_WEIGHT");
        double supplyWeight = requireWeight("SUPPLY_WEIGHT");
        double demandPopulationRatio = requireWeight("DEMAND_POPULATION_RATIO");
        double demandHouseholdRatio = requireWeight("DEMAND_HOUSEHOLD_RATIO");

        double populationWeight = demandWeight * demandPopulationRatio;
        double householdWeight = demandWeight * demandHouseholdRatio;
        double competitionWeight = supplyWeight;

        log.debug("가중치 계산 결과 - demandWeight: {}, supplyWeight: {}, demandPopulationRatio: {}, " +
                        "demandHouseholdRatio: {} -> populationWeight: {}, householdWeight: {}, competitionWeight: {}",
                demandWeight, supplyWeight, demandPopulationRatio, demandHouseholdRatio,
                populationWeight, householdWeight, competitionWeight);

        return new LeafWeights(populationWeight, householdWeight, competitionWeight);
    }

    private double requireWeight(String weightKey) {
        return scoreWeightConfigRepository.findByWeightKey(weightKey)
                .map(config -> config.getWeightValue().doubleValue())
                .orElseThrow(() -> {
                    log.error("가중치 설정 누락 - weight_key: {} (SCORE_WEIGHT_CONFIG 시드 데이터 확인 필요, V4 마이그레이션 참고)",
                            weightKey);
                    return new IllegalStateException("가중치 설정 누락: " + weightKey);
                });
    }
}
