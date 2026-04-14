package com.recruiter.config;

import com.recruiter.ai.Sector;
import com.recruiter.domain.ShortlistQuality;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Getter
@Validated
@ConfigurationProperties(prefix = "recruitment")
public class RecruitmentProperties implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentProperties.class);
    private static final double AI_WEIGHT_TOLERANCE = 0.001;

    @Min(1)
    private int shortlistCount = 3;

    @Min(1)
    private int maxJobDescriptionWords = 1000;

    @Min(1)
    private int analysisCap = 20;

    private Integer uploadProcessingCap;

    private ShortlistQuality defaultShortlistQuality = ShortlistQuality.VERY_GOOD;

    @Min(1)
    private long maxFileSizeBytes = 1 * 1024 * 1024; // 1 MB

    private double prefilterBorderlineMargin = 10.0;

    private int prefilterMaxRescue = 8;

    private String defaultSector = "generic";

    private final Scoring scoring = new Scoring();
    private final AiCost aiCost = new AiCost();

    @Override
    public void afterPropertiesSet() {
        scoring.validateAndResolve();
    }

    public void setShortlistCount(int shortlistCount) {
        this.shortlistCount = shortlistCount;
    }

    public void setMaxJobDescriptionWords(int maxJobDescriptionWords) {
        this.maxJobDescriptionWords = maxJobDescriptionWords;
    }

    public void setAnalysisCap(int analysisCap) {
        this.analysisCap = analysisCap;
    }

    public void setUploadProcessingCap(Integer uploadProcessingCap) {
        this.uploadProcessingCap = uploadProcessingCap;
    }

    public int getEffectiveUploadProcessingCap() {
        return uploadProcessingCap != null ? uploadProcessingCap : 500;
    }

    public void setDefaultShortlistQuality(ShortlistQuality defaultShortlistQuality) {
        this.defaultShortlistQuality = defaultShortlistQuality;
    }

    public double getMinimumShortlistScore() {
        return defaultShortlistQuality.getThreshold();
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public void setPrefilterBorderlineMargin(double prefilterBorderlineMargin) {
        this.prefilterBorderlineMargin = prefilterBorderlineMargin;
    }

    public void setPrefilterMaxRescue(int prefilterMaxRescue) {
        this.prefilterMaxRescue = prefilterMaxRescue;
    }

    public void setDefaultSector(String defaultSector) {
        this.defaultSector = defaultSector;
    }

    public Sector getEffectiveSector() {
        return Sector.fromString(defaultSector);
    }

    public HeuristicScoringWeights getResolvedHeuristicScoring() {
        return scoring.getResolvedHeuristic();
    }

    public AiScoringWeights getResolvedAiScoring(Sector sector) {
        return scoring.getResolvedAi(sector);
    }

    @Getter
    public static class Scoring {

        private final Heuristic heuristic = new Heuristic();
        private final Ai ai = new Ai();
        private boolean resolved;

        void validateAndResolve() {
            heuristic.validateAndResolve();
            ai.validateAndResolve();
            resolved = true;
        }

        public HeuristicScoringWeights getResolvedHeuristic() {
            ensureResolved();
            return heuristic.getResolved();
        }

        public AiScoringWeights getResolvedAi(Sector sector) {
            ensureResolved();
            return ai.getResolved(sector);
        }

        private void ensureResolved() {
            if (!resolved) {
                validateAndResolve();
            }
        }
    }

    @Getter
    public static class Heuristic {

        private static final int DEFAULT_ESSENTIAL_FIT_MAX = 40;
        private static final int DEFAULT_BROADER_SKILL_FIT_MAX = 25;
        private static final int DEFAULT_KEYWORD_SUPPORT_MAX = 15;
        private static final int DEFAULT_EXPERIENCE_FIT_MAX = 10;
        private static final int DEFAULT_GAP_PENALTY_SEVERE = -10;
        private static final int DEFAULT_GAP_PENALTY_MODERATE = -5;

        private int essentialFitMax = DEFAULT_ESSENTIAL_FIT_MAX;
        private int broaderSkillFitMax = DEFAULT_BROADER_SKILL_FIT_MAX;
        private int keywordSupportMax = DEFAULT_KEYWORD_SUPPORT_MAX;
        private int experienceFitMax = DEFAULT_EXPERIENCE_FIT_MAX;
        private int gapPenaltySevere = DEFAULT_GAP_PENALTY_SEVERE;
        private int gapPenaltyModerate = DEFAULT_GAP_PENALTY_MODERATE;

        private HeuristicScoringWeights resolved = defaults();

        void validateAndResolve() {
            int sum = essentialFitMax + broaderSkillFitMax + keywordSupportMax + experienceFitMax;
            if (sum != 90) {
                log.warn("Invalid heuristic scoring weights sum {}. Expected 90, falling back to defaults.", sum);
                resolved = defaults();
                return;
            }

            resolved = new HeuristicScoringWeights(
                    essentialFitMax,
                    broaderSkillFitMax,
                    keywordSupportMax,
                    experienceFitMax,
                    gapPenaltySevere,
                    gapPenaltyModerate
            );
        }

        private HeuristicScoringWeights getResolved() {
            return resolved;
        }

        public void setEssentialFitMax(int essentialFitMax) {
            this.essentialFitMax = essentialFitMax;
        }

        public void setBroaderSkillFitMax(int broaderSkillFitMax) {
            this.broaderSkillFitMax = broaderSkillFitMax;
        }

        public void setKeywordSupportMax(int keywordSupportMax) {
            this.keywordSupportMax = keywordSupportMax;
        }

        public void setExperienceFitMax(int experienceFitMax) {
            this.experienceFitMax = experienceFitMax;
        }

        public void setGapPenaltySevere(int gapPenaltySevere) {
            this.gapPenaltySevere = gapPenaltySevere;
        }

        public void setGapPenaltyModerate(int gapPenaltyModerate) {
            this.gapPenaltyModerate = gapPenaltyModerate;
        }

        private static HeuristicScoringWeights defaults() {
            return new HeuristicScoringWeights(
                    DEFAULT_ESSENTIAL_FIT_MAX,
                    DEFAULT_BROADER_SKILL_FIT_MAX,
                    DEFAULT_KEYWORD_SUPPORT_MAX,
                    DEFAULT_EXPERIENCE_FIT_MAX,
                    DEFAULT_GAP_PENALTY_SEVERE,
                    DEFAULT_GAP_PENALTY_MODERATE
            );
        }
    }

    @Getter
    public static class Ai {

        private static final double DEFAULT_ESSENTIAL_FIT_WEIGHT = 0.35;
        private static final double DEFAULT_EXPERIENCE_FIT_WEIGHT = 0.25;
        private static final double DEFAULT_DESIRABLE_FIT_WEIGHT = 0.15;
        private static final double DEFAULT_DOMAIN_FIT_WEIGHT = 0.15;
        private static final double DEFAULT_CREDENTIALS_FIT_WEIGHT = 0.10;

        private double essentialFitWeight = DEFAULT_ESSENTIAL_FIT_WEIGHT;
        private double experienceFitWeight = DEFAULT_EXPERIENCE_FIT_WEIGHT;
        private double desirableFitWeight = DEFAULT_DESIRABLE_FIT_WEIGHT;
        private double domainFitWeight = DEFAULT_DOMAIN_FIT_WEIGHT;
        private double credentialsFitWeight = DEFAULT_CREDENTIALS_FIT_WEIGHT;
        private Map<String, AiSectorOverride> sectorOverrides = new LinkedHashMap<>();

        private AiScoringWeights resolvedDefault = defaults();
        private Map<Sector, AiScoringWeights> resolvedOverrides = Map.of();

        void validateAndResolve() {
            AiScoringWeights configuredDefault = new AiScoringWeights(
                    essentialFitWeight,
                    experienceFitWeight,
                    desirableFitWeight,
                    domainFitWeight,
                    credentialsFitWeight
            );
            if (!isValid(configuredDefault)) {
                log.warn("Invalid AI scoring weights sum {}. Expected 1.0, falling back to defaults.",
                        sum(configuredDefault));
                resolvedDefault = defaults();
            } else {
                resolvedDefault = configuredDefault;
            }

            Map<Sector, AiScoringWeights> overrides = new LinkedHashMap<>();
            for (Map.Entry<String, AiSectorOverride> entry : sectorOverrides.entrySet()) {
                Sector sector = Sector.fromString(entry.getKey());
                if (sector == Sector.GENERIC && !"generic".equalsIgnoreCase(entry.getKey().trim())
                        && !"GENERIC".equalsIgnoreCase(entry.getKey().trim())) {
                    log.warn("Ignoring unknown AI scoring sector override '{}'.", entry.getKey());
                    continue;
                }

                AiScoringWeights merged = entry.getValue().mergeOnto(resolvedDefault);
                if (!isValid(merged)) {
                    log.warn("Invalid AI scoring sector override for '{}'. Weights sum to {}, falling back to default AI weights.",
                            entry.getKey(), sum(merged));
                    continue;
                }
                overrides.put(sector, merged);
            }
            resolvedOverrides = Map.copyOf(overrides);
        }

        public AiScoringWeights getResolved(Sector sector) {
            Sector resolvedSector = sector != null ? sector : Sector.GENERIC;
            return resolvedOverrides.getOrDefault(resolvedSector, resolvedDefault);
        }

        public void setEssentialFitWeight(double essentialFitWeight) {
            this.essentialFitWeight = essentialFitWeight;
        }

        public void setExperienceFitWeight(double experienceFitWeight) {
            this.experienceFitWeight = experienceFitWeight;
        }

        public void setDesirableFitWeight(double desirableFitWeight) {
            this.desirableFitWeight = desirableFitWeight;
        }

        public void setDomainFitWeight(double domainFitWeight) {
            this.domainFitWeight = domainFitWeight;
        }

        public void setCredentialsFitWeight(double credentialsFitWeight) {
            this.credentialsFitWeight = credentialsFitWeight;
        }

        public void setSectorOverrides(Map<String, AiSectorOverride> sectorOverrides) {
            this.sectorOverrides = sectorOverrides != null ? new LinkedHashMap<>(sectorOverrides) : new LinkedHashMap<>();
        }

        private boolean isValid(AiScoringWeights weights) {
            return Math.abs(sum(weights) - 1.0) <= AI_WEIGHT_TOLERANCE;
        }

        private double sum(AiScoringWeights weights) {
            return weights.essentialFitWeight()
                    + weights.experienceFitWeight()
                    + weights.desirableFitWeight()
                    + weights.domainFitWeight()
                    + weights.credentialsFitWeight();
        }

        private static AiScoringWeights defaults() {
            return new AiScoringWeights(
                    DEFAULT_ESSENTIAL_FIT_WEIGHT,
                    DEFAULT_EXPERIENCE_FIT_WEIGHT,
                    DEFAULT_DESIRABLE_FIT_WEIGHT,
                    DEFAULT_DOMAIN_FIT_WEIGHT,
                    DEFAULT_CREDENTIALS_FIT_WEIGHT
            );
        }
    }

    @Getter
    public static class AiSectorOverride {
        private Double essentialFitWeight;
        private Double experienceFitWeight;
        private Double desirableFitWeight;
        private Double domainFitWeight;
        private Double credentialsFitWeight;

        public void setEssentialFitWeight(Double essentialFitWeight) {
            this.essentialFitWeight = essentialFitWeight;
        }

        public void setExperienceFitWeight(Double experienceFitWeight) {
            this.experienceFitWeight = experienceFitWeight;
        }

        public void setDesirableFitWeight(Double desirableFitWeight) {
            this.desirableFitWeight = desirableFitWeight;
        }

        public void setDomainFitWeight(Double domainFitWeight) {
            this.domainFitWeight = domainFitWeight;
        }

        public void setCredentialsFitWeight(Double credentialsFitWeight) {
            this.credentialsFitWeight = credentialsFitWeight;
        }

        AiScoringWeights mergeOnto(AiScoringWeights base) {
            return new AiScoringWeights(
                    essentialFitWeight != null ? essentialFitWeight : base.essentialFitWeight(),
                    experienceFitWeight != null ? experienceFitWeight : base.experienceFitWeight(),
                    desirableFitWeight != null ? desirableFitWeight : base.desirableFitWeight(),
                    domainFitWeight != null ? domainFitWeight : base.domainFitWeight(),
                    credentialsFitWeight != null ? credentialsFitWeight : base.credentialsFitWeight()
            );
        }
    }

    public record HeuristicScoringWeights(
            int essentialFitMax,
            int broaderSkillFitMax,
            int keywordSupportMax,
            int experienceFitMax,
            int gapPenaltySevere,
            int gapPenaltyModerate
    ) {
        public int broaderSkillFitMaxWithoutEssentials() {
            return broaderSkillFitMax + essentialFitMax;
        }
    }

    public record AiScoringWeights(
            double essentialFitWeight,
            double experienceFitWeight,
            double desirableFitWeight,
            double domainFitWeight,
            double credentialsFitWeight
    ) {
        @Override
        public String toString() {
            return String.format(Locale.US, "essential=%.3f, experience=%.3f, desirable=%.3f, domain=%.3f, credentials=%.3f",
                    essentialFitWeight, experienceFitWeight, desirableFitWeight, domainFitWeight, credentialsFitWeight);
        }
    }

    @Getter
    public static class AiCost {

        private double promptPricePerMillion = 0.15;
        private double completionPricePerMillion = 0.60;

        public void setPromptPricePerMillion(double promptPricePerMillion) {
            this.promptPricePerMillion = promptPricePerMillion;
        }

        public void setCompletionPricePerMillion(double completionPricePerMillion) {
            this.completionPricePerMillion = completionPricePerMillion;
        }
    }
}
