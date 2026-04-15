package com.recruiter.report;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnMissingBean(ReportNarrativeService.class)
public class ReportNarrativeFallbackConfiguration {

    @Bean
    ReportNarrativeService reportNarrativeService() {
        return new FallbackReportNarrativeService();
    }
}
