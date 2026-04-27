package com.recruiter.web;

import com.recruiter.ai.JdQualityAssessment;
import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.ScreeningDepth;
import com.recruiter.domain.ShortlistQuality;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class JdReviewStore {

    private final int maxEntries;
    private final Map<String, JdReviewEntry> store;

    @Autowired
    JdReviewStore(RecruitmentProperties properties) {
        this(properties.getTransientStores().getJdReviewMaxEntries());
    }

    JdReviewStore() {
        this(new RecruitmentProperties().getTransientStores().getJdReviewMaxEntries());
    }

    private JdReviewStore(int maxEntries) {
        this.maxEntries = maxEntries;
        this.store = Collections.synchronizedMap(
            new LinkedHashMap<>(maxEntries + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, JdReviewEntry> eldest) {
                    return size() > JdReviewStore.this.maxEntries;
                }
            });
    }

    String save(JdQualityAssessment assessment, String rerunId,
                int shortlistCount, ShortlistQuality shortlistQuality,
                ScreeningDepth screeningDepth, String scoringMode, String sector) {
        String id = UUID.randomUUID().toString();
        store.put(id, new JdReviewEntry(assessment, rerunId, shortlistCount,
                shortlistQuality, screeningDepth, scoringMode, sector));
        return id;
    }

    Optional<JdReviewEntry> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    record JdReviewEntry(
            JdQualityAssessment assessment,
            String rerunId,
            int shortlistCount,
            ShortlistQuality shortlistQuality,
            ScreeningDepth screeningDepth,
            String scoringMode,
            String sector
    ) {}
}
