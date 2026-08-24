package com.spotscore.query;

import com.spotscore.collector.KakaoLocalClient;
import com.spotscore.domain.Store;
import com.spotscore.dto.StoreItemResponse;
import com.spotscore.dto.StorePlaceLinkResponse;
import com.spotscore.exception.ResourceNotFoundException;
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
    private final KakaoLocalClient kakaoLocalClient;

    public StoreQueryService(StoreRepository storeRepository, KakaoLocalClient kakaoLocalClient) {
        this.storeRepository = storeRepository;
        this.kakaoLocalClient = kakaoLocalClient;
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

    /**
     * 가게 상세 모달의 "카카오맵에서 보기"용 장소 상세 URL을 조회한다. 저장된 가게명+좌표로
     * Kakao Local 검색해 실제 등록 장소의 place_url을 찾는다(이슈 #34). 키 미설정/결과
     * 없음이면 placeUrl=null - 프론트가 이름 검색 링크로 폴백한다.
     */
    @Transactional(readOnly = true)
    public StorePlaceLinkResponse getPlaceLink(String bizesId) {
        Store store = storeRepository.findById(bizesId)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 업소입니다: " + bizesId));
        String placeUrl = kakaoLocalClient.findPlaceUrl(store.getBizesNm(), store.getLon(), store.getLat());
        return new StorePlaceLinkResponse(placeUrl);
    }
}
