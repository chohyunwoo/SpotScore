package com.spotscore.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI spotScoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SpotScore API")
                        .description("공공데이터 기반 창업 입지 추천 대시보드 API")
                        .version("v1"));
    }
}
