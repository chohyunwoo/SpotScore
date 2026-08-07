package com.spotscore.controller;

import com.spotscore.dto.StoreItemResponse;
import com.spotscore.query.StoreQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Store", description = "지역x업종 개별 업소(지도 마커) 조회 - 출처: 소상공인시장진흥공단 상가(상권)정보")
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private static final Logger log = LoggerFactory.getLogger(StoreController.class);

    private final StoreQueryService storeQueryService;

    public StoreController(StoreQueryService storeQueryService) {
        this.storeQueryService = storeQueryService;
    }

    @Operation(summary = "지역x업종 개별 업소 목록 조회",
            description = "상세 패널을 열었을 때만 호출하는 용도 - 전체 랭킹 지도에서는 호출하지 않는다(성능 고려). " +
                    "데이터가 없으면 빈 배열을 200으로 반환한다.")
    @GetMapping
    public List<StoreItemResponse> getStores(@RequestParam String regionCode, @RequestParam String industryCode,
                                              HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}, regionCode: {}, industryCode: {}",
                request.getRequestURI(), regionCode, industryCode);
        return storeQueryService.getStores(regionCode, industryCode);
    }
}
