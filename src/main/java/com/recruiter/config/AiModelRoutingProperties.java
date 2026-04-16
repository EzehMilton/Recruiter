package com.recruiter.config;

import com.recruiter.domain.ScreeningPackage;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "recruitment.ai-models")
public class AiModelRoutingProperties {

    private String defaultModel = "gpt-5.4-mini";
    private final PackageModels quickScreen = new PackageModels("gpt-5.4-mini", "gpt-5.4-mini");
    private final PackageModels standardScreen = new PackageModels("gpt-5.4-mini", "gpt-5.4");
    private final PackageModels premiumPack = new PackageModels("gpt-5.4", "gpt-5.4");

    public PackageModels getRoute(ScreeningPackage screeningPackage) {
        return switch (screeningPackage != null ? screeningPackage : ScreeningPackage.QUICK_SCREEN) {
            case QUICK_SCREEN -> quickScreen;
            case STANDARD_SCREEN -> standardScreen;
            case PREMIUM_PACK -> premiumPack;
        };
    }

    @Getter
    @Setter
    public static class PackageModels {

        private String screeningModel;
        private String reportingModel;

        public PackageModels() {
        }

        public PackageModels(String screeningModel, String reportingModel) {
            this.screeningModel = screeningModel;
            this.reportingModel = reportingModel;
        }
    }
}
