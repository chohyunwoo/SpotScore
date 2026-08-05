package com.spotscore.admin;

import com.spotscore.domain.ScoreWeightConfig;
import com.spotscore.dto.ScoreWeightConfigResponse;
import com.spotscore.exception.ResourceNotFoundException;
import com.spotscore.repository.ScoreWeightConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * SCORE_WEIGHT_CONFIG 조회/수정. 가중치 산출 방법(수요/공급 2축 하이브리드 AHP)의
 * 실제 쌍대비교 수치는 팀 논의 전이라 이 클래스는 값을 임의로 정하지 않는다 -
 * 재배포 없이 팀이 최종 값을 반영할 수 있도록 조회/수정 경로만 제공한다.
 *
 * TODO: 운영 배포 전 인증 추가 필요 - 현재는 로컬 전용으로 인증 없이 열려 있다.
 */
@Service
public class ScoreWeightAdminService {

    private static final Logger log = LoggerFactory.getLogger(ScoreWeightAdminService.class);

    private final ScoreWeightConfigRepository scoreWeightConfigRepository;

    public ScoreWeightAdminService(ScoreWeightConfigRepository scoreWeightConfigRepository) {
        this.scoreWeightConfigRepository = scoreWeightConfigRepository;
    }

    public List<ScoreWeightConfigResponse> getAllWeights() {
        return scoreWeightConfigRepository.findAll().stream()
                .sorted(Comparator.comparing(ScoreWeightConfig::getWeightKey))
                .map(ScoreWeightConfigResponse::from)
                .toList();
    }

    @Transactional
    public ScoreWeightConfigResponse updateWeight(String weightKey, BigDecimal weightValue) {
        if (weightValue == null) {
            throw new IllegalArgumentException("weightValue는 필수입니다");
        }
        if (weightValue.compareTo(BigDecimal.ZERO) < 0 || weightValue.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("weightValue는 0~1 사이여야 합니다: " + weightValue);
        }

        ScoreWeightConfig config = scoreWeightConfigRepository.findByWeightKey(weightKey)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 weightKey: " + weightKey));

        BigDecimal previousValue = config.getWeightValue();
        config.updateValue(weightValue);
        log.info("가중치 설정 변경 - weightKey: {}, 이전 값: {}, 새 값: {}", weightKey, previousValue, weightValue);

        return ScoreWeightConfigResponse.from(config);
    }
}
