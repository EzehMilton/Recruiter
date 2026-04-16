package com.recruiter.ai;

import com.recruiter.config.AiModelRoutingProperties;
import com.recruiter.domain.ScreeningPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AiModelSelectionService {

    private final AiModelRoutingProperties properties;

    public String screeningModel(ScreeningPackage screeningPackage) {
        return resolveModel(properties.getRoute(screeningPackage).getScreeningModel());
    }

    public String reportingModel(ScreeningPackage screeningPackage, String scoringMode) {
        if (!isAiScoringMode(scoringMode)) {
            return defaultModel();
        }
        return resolveModel(properties.getRoute(screeningPackage).getReportingModel());
    }

    public String defaultModel() {
        return resolveModel(properties.getDefaultModel());
    }

    private String resolveModel(String configuredModel) {
        if (StringUtils.hasText(configuredModel)) {
            return configuredModel.trim();
        }
        return "gpt-5.4-mini";
    }

    private boolean isAiScoringMode(String scoringMode) {
        return scoringMode != null
                && scoringMode.trim().toLowerCase(Locale.ROOT).startsWith("ai");
    }
}
