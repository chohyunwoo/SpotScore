package com.spotscore.query;

import com.spotscore.dto.StoreItemResponse;
import com.spotscore.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * STORE(개별 업소 원본) 기반 지역x업종 업소 목록 조회 - 상세 패널의 지도 개별
 * 마커 표시용. 상세 패널을 열었을 때만 호출되는 용도라 랭킹/지도 초기 로딩과는
 * 무관하다(성능 고려로 별도 엔드포인트로 분리).
 */
@Service
public class StoreQueryService {

    private static final Logger log = LoggerFactory.getLogger(StoreQueryService.class);

    private final StoreRepository storeRepository;

    public StoreQueryService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public List<StoreItemResponse> getStores(String regionCode, String industryCode) {
        List<StoreItemResponse> stores = storeRepository
                .findByRegion_RegionCodeAndIndustry_IndustryCode(regionCode, industryCode)
                .stream()
                .map(StoreItemResponse::from)
                .toList();

        if (stores.isEmpty()) {
            log.info("업소 목록 조회 결과 없음 - regionCode: {}, industryCode: {} (배치 미실행이거나 해당 조합에 업소가 없을 수 있음)",
                    regionCode, industryCode);
        }
        return stores;
    }
}
