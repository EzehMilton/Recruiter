package com.recruiter.report;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnMissingBean(CandidateReportNarrativeService.class)
public class CandidateReportNarrativeFallbackConfiguration {

    @Bean
    CandidateReportNarrativeService candidateReportNarrativeService() {
        return new FallbackCandidateReportNarrativeService();
    }
}
