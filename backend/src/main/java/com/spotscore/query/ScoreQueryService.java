package com.spotscore.query;

import com.spotscore.domain.PopulationStat;
import com.spotscore.domain.Region;
import com.spotscore.domain.ScoreCache;
import com.spotscore.domain.StoreCount;
import com.spotscore.dto.RankingItem;
import com.spotscore.dto.ScoreDetailResponse;
import com.spotscore.exception.ResourceNotFoundException;
import com.spotscore.repository.PopulationStatRepository;
import com.spotscore.repository.RankingProjection;
import com.spotscore.repository.ScoreCacheRepository;
import com.spotscore.repository.StoreCountRepository;
import com.spotscore.scoring.AttractivenessTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SCORE_CACHE 기반 랭킹/상세 조회. 점수는 항상 "지역 x 업종" 조합에 대해서만
 * 계산 가능하므로(CLAUDE.md 핵심 원칙), 랭킹은 업종 코드로, 상세는 지역+업종
 * 조합으로 조회한다.
 */
@Service
public class ScoreQueryService {

    private static final Logger log = LoggerFactory.getLogger(ScoreQueryService.class);

    private final ScoreCacheRepository scoreCacheRepository;
    private final PopulationStatRepository populationStatRepository;
    private final StoreCountRepository storeCountRepository;

    public ScoreQueryService(ScoreCacheRepository scoreCacheRepository,
                              PopulationStatRepository populationStatRepository,
                              StoreCountRepository storeCountRepository) {
        this.scoreCacheRepository = scoreCacheRepository;
        this.populationStatRepository = populationStatRepository;
        this.storeCountRepository = storeCountRepository;
    }

    @Transactional(readOnly = true)
    public List<RankingItem> getRanking(String industryCode) {
        List<RankingProjection> rows = scoreCacheRepository.findRankingWithPercentile(industryCode);
        if (rows.isEmpty()) {
            log.info("랭킹 조회 결과 없음 - industryCode: {} (배치 미실행이거나 존재하지 않는 업종 코드일 수 있음)",
                    industryCode);
        }
        return rows.stream().map(this::toRankingItem).toList();
    }

    private RankingItem toRankingItem(RankingProjection row) {
        double percentileRank = round(row.getPercentileRank() == null ? 0.0 : row.getPercentileRank());
        AttractivenessTier tier = AttractivenessTier.fromPercentileRank(percentileRank);
        return new RankingItem(row.getRegionCode(), row.getRegionName(), row.getTotalScore(),
                row.getPopulationScore(), row.getHouseholdScore(), row.getDensityScore(),
                row.getLatitude(), row.getLongitude(), percentileRank, tier);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Transactional(readOnly = true)
    public ScoreDetailResponse getDetail(String regionCode, String industryCode) {
        ScoreCache scoreCache = scoreCacheRepository.findByRegion_RegionCodeAndIndustry_IndustryCode(regionCode, industryCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "해당 지역×업종 조합의 점수 데이터가 없습니다 - regionCode: " + regionCode + ", industryCode: " + industryCode));

        Region region = scoreCache.getRegion();

        PopulationStat populationStat = populationStatRepository.findTopByRegionOrderByYearDesc(region)
                .orElse(null);
        if (populationStat == null) {
            log.warn("상세 조회 원자료 누락 - regionCode: {}에 대한 population_stat 없음", regionCode);
        }

        StoreCount storeCount = storeCountRepository
                .findTopByRegionAndIndustryOrderBySnapshotDateDesc(region, scoreCache.getIndustry())
                .orElse(null);
        if (storeCount == null) {
            log.warn("상세 조회 원자료 누락 - regionCode: {}, industryCode: {}에 대한 store_count 없음",
                    regionCode, industryCode);
        }

        return ScoreDetailResponse.of(scoreCache, populationStat, storeCount);
    }
}
