package com.spotscore.controller;

import com.spotscore.admin.ScoreWeightAdminService;
import com.spotscore.config.WebConfig;
import com.spotscore.dto.RankingItem;
import com.spotscore.dto.ScoreWeightConfigResponse;
import com.spotscore.exception.ResourceNotFoundException;
import com.spotscore.query.ScoreQueryService;
import com.spotscore.scoring.AttractivenessTier;
import com.spotscore.security.AdminApiKeyInterceptor;
import com.spotscore.logging.RequestTimingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ScoreController의 웹 계층(요청 매핑/파라미터 바인딩/JSON 직렬화/상태 코드)만 격리해
 * 검증하는 슬라이스 테스트. 서비스는 목킹하고, MVC 인터셉터(WebConfig)와 Security
 * 필터는 배제해 컨트롤러 계약 자체에 집중한다.
 *
 * 확정 응답 계약(CLAUDE.md): 랭킹은 flat 배열이며 데이터 없으면 빈 배열을 200으로,
 * 상세는 없으면 404를 반환한다.
 */
@WebMvcTest(controllers = ScoreController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class))
@AutoConfigureMockMvc(addFilters = false)
class ScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScoreQueryService scoreQueryService;

    @MockBean
    private ScoreWeightAdminService scoreWeightAdminService;

    // WebConfig를 배제해도 HandlerInterceptor 빈이 슬라이스 스캔에 잡힐 수 있어,
    // 실 의존성(AdminSecurityProperties 등) 없이 컨텍스트가 뜨도록 목으로 대체한다.
    @MockBean
    private AdminApiKeyInterceptor adminApiKeyInterceptor;

    @MockBean
    private RequestTimingInterceptor requestTimingInterceptor;

    @Test
    void rankingReturnsFlatArray() throws Exception {
        RankingItem item = new RankingItem("11680640", "역삼1동",
                new BigDecimal("73.65"), new BigDecimal("80.0"), new BigDecimal("60.0"),
                new BigDecimal("70.0"), 37.5, 127.05, 5.0, AttractivenessTier.ATTRACTIVE);
        when(scoreQueryService.getRanking(eq("I2"))).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/scores/ranking").param("industryCode", "I2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].regionCode").value("11680640"))
                .andExpect(jsonPath("$[0].regionName").value("역삼1동"))
                .andExpect(jsonPath("$[0].totalScore").value(73.65))
                .andExpect(jsonPath("$[0].attractivenessTier").value("ATTRACTIVE"))
                .andExpect(jsonPath("$[0].latitude").value(37.5));
    }

    @Test
    void rankingReturnsEmptyArrayWithOkWhenNoData() throws Exception {
        when(scoreQueryService.getRanking(eq("NONE"))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/scores/ranking").param("industryCode", "NONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rankingWithoutRequiredParamReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/scores/ranking"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailReturns404WhenCombinationMissing() throws Exception {
        when(scoreQueryService.getDetail(eq("11680640"), eq("I2")))
                .thenThrow(new ResourceNotFoundException("없음"));

        mockMvc.perform(get("/api/v1/scores/detail")
                        .param("regionCode", "11680640")
                        .param("industryCode", "I2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void weightsReturnsPublicConfigArray() throws Exception {
        when(scoreWeightAdminService.getAllWeights()).thenReturn(List.of(
                new ScoreWeightConfigResponse("DEMAND_WEIGHT", new BigDecimal("0.5"), "TOP"),
                new ScoreWeightConfigResponse("SUPPLY_WEIGHT", new BigDecimal("0.5"), "TOP")));

        mockMvc.perform(get("/api/v1/scores/weights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weightKey").value("DEMAND_WEIGHT"))
                .andExpect(jsonPath("$[0].weightValue").value(0.5))
                .andExpect(jsonPath("$[0].weightGroup").value("TOP"));
    }
}
