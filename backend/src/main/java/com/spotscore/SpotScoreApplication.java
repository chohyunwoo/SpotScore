package com.spotscore;

import com.spotscore.config.AdminSecurityProperties;
import com.spotscore.config.BatchProperties;
import com.spotscore.config.CorsProperties;
import com.spotscore.config.DiscoveryProperties;
import com.spotscore.config.FeaturedIndustryProperties;
import com.spotscore.config.IndustryAgeDirectionProperties;
import com.spotscore.config.GroqProperties;
import com.spotscore.config.KosisProperties;
import com.spotscore.config.SgisProperties;
import com.spotscore.config.StoreZoneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableConfigurationProperties({SgisProperties.class, StoreZoneProperties.class, BatchProperties.class,
        CorsProperties.class, DiscoveryProperties.class, FeaturedIndustryProperties.class, KosisProperties.class,
        IndustryAgeDirectionProperties.class, GroqProperties.class, AdminSecurityProperties.class})
@EnableScheduling
@SpringBootApplication
public class SpotScoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpotScoreApplication.class, args);
    }
}
