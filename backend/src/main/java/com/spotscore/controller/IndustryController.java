package com.spotscore.controller;

import com.spotscore.dto.IndustryResponse;
import com.spotscore.query.IndustryQueryService;
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

@Tag(name = "Industry", description = "업종 코드 목록 조회")
@RestController
@RequestMapping("/api/v1/industries")
public class IndustryController {

    private static final Logger log = LoggerFactory.getLogger(IndustryController.class);

    private final IndustryQueryService industryQueryService;

    public IndustryController(IndustryQueryService industryQueryService) {
        this.industryQueryService = industryQueryService;
    }

    @Operation(summary = "업종 목록 조회",
            description = "기본값은 추천 업종(featured=true)만 내려준다. all=true를 주면 전체 업종을 내려준다.")
    @GetMapping
    public List<IndustryResponse> getIndustries(@RequestParam(defaultValue = "false") boolean all,
                                                 HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}, all: {}", request.getRequestURI(), all);
        return industryQueryService.getIndustries(all);
    }
}
