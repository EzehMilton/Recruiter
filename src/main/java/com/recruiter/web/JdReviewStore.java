package com.recruiter.web;

import com.recruiter.ai.JdQualityAssessment;
import com.recruiter.domain.ScreeningDepth;
import com.recruiter.domain.ShortlistQuality;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class JdReviewStore {

    private static final int MAX_ENTRIES = 20;

    private final Map<String, JdReviewEntry> store = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_ENTRIES + 1, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, JdReviewEntry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

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
