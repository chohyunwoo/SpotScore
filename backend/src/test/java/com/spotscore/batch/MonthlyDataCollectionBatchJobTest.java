package com.spotscore.batch;

import com.spotscore.batch.mapping.RegionCodeMappingValidator;
import com.spotscore.collector.KosisAgeCollector;
import com.spotscore.config.BatchProperties;
import com.spotscore.domain.Region;
import com.spotscore.repository.AgeStatRepository;
import com.spotscore.repository.IndustryCategoryRepository;
import com.spotscore.repository.PopulationStatRepository;
import com.spotscore.repository.RegionRepository;
import com.spotscore.repository.StoreCountRepository;
import com.spotscore.repository.StoreRepository;
import com.spotscore.scoring.ScoreCalculationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * spotscore.batch.target-regions가 비어있을 때(서울 전체 등 지역 수가 많아 환경변수로
 * 나열하는 게 비현실적인 경우) REGION 테이블의 sgisAdmCd 매핑 지역 전체로 대체되는지
 * 검증한다 - KOSIS 배치가 dev 대상 소수 지역에만 머물러 있던 문제(#9) 재발 방지.
 */
class MonthlyDataCollectionBatchJobTest {

    private RegionRepository regionRepository;
    private BatchProperties batchProperties;
    private MonthlyDataCollectionBatchJob job;

    private MonthlyDataCollectionBatchJob newJob(BatchProperties props) {
        return new MonthlyDataCollectionBatchJob(props,
                mock(RegionCodeMappingValidator.class),
                regionRepository,
                mock(IndustryCategoryRepository.class),
                mock(PopulationStatRepository.class),
                mock(StoreCountRepository.class),
                mock(StoreRepository.class),
                mock(AgeStatRepository.class),
                mock(KosisAgeCollector.class),
                mock(ScoreCalculationService.class));
    }

    @Test
    void fallsBackToRegionTableWhenTargetRegionsNotConfigured() {
        regionRepository = mock(RegionRepository.class);
        when(regionRepository.findAllBySgisAdmCdIsNotNull()).thenReturn(List.of(
                new Region("11680640", "역삼1동", "ADONG", "11230640"),
                new Region("11680650", "역삼2동", "ADONG", "11230650")
        ));
        batchProperties = new BatchProperties("0 0 3 1 * *", List.of(), 200);
        job = newJob(batchProperties);

        List<String> targets = job.resolveRawTargets();

        assertThat(targets).containsExactlyInAnyOrder("11230640:11680640", "11230650:11680650");
    }

    @Test
    void usesConfiguredTargetRegionsWhenPresentInsteadOfRegionTable() {
        regionRepository = mock(RegionRepository.class);
        batchProperties = new BatchProperties("0 0 3 1 * *", List.of("11230640:11680640"), 200);
        job = newJob(batchProperties);

        List<String> targets = job.resolveRawTargets();

        assertThat(targets).containsExactly("11230640:11680640");
        verifyNoInteractions(regionRepository);
    }
}
