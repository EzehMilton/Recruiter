package com.recruiter.web;

import com.recruiter.domain.ScreeningPackage;
import com.recruiter.config.RecruitmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds recent screening snapshots so the recruiter can rerun a job without
 * re-pasting the description or re-selecting files. Entries are evicted once
 * the store exceeds MAX_ENTRIES (oldest-first).
 */
@Component
class RerunStore {

    private static final Logger log = LoggerFactory.getLogger(RerunStore.class);

    private final int maxEntries;
    private final Map<String, RerunSnapshot> store;

    @Autowired
    RerunStore(RecruitmentProperties properties) {
        this(properties.getTransientStores().getRerunMaxEntries());
    }

    RerunStore() {
        this(new RecruitmentProperties().getTransientStores().getRerunMaxEntries());
    }

    private RerunStore(int maxEntries) {
        this.maxEntries = maxEntries;
        this.store = Collections.synchronizedMap(
            new LinkedHashMap<>(maxEntries + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, RerunSnapshot> eldest) {
                    boolean shouldRemove = size() > RerunStore.this.maxEntries;
                    if (shouldRemove) {
                        log.debug("Evicting rerun snapshot {}", eldest.getKey());
                    }
                    return shouldRemove;
                }
            });
    }

    /**
     * Saves a snapshot and returns its ID. File bytes are read immediately so
     * the caller is free to release the multipart files afterwards.
     */
    String save(String jobDescription, List<MultipartFile> files) {
        return save(jobDescription, files, ScreeningPackage.QUICK_SCREEN);
    }

    String save(String jobDescription, List<MultipartFile> files, ScreeningPackage screeningPackage) {
        String id = UUID.randomUUID().toString();
        List<RerunFile> rerunFiles = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                try {
                    rerunFiles.add(new RerunFile(
                            file.getOriginalFilename() != null ? file.getOriginalFilename() : "cv.pdf",
                            file.getContentType() != null ? file.getContentType() : "application/pdf",
                            file.getBytes()
                    ));
                } catch (Exception ex) {
                    log.warn("Could not read bytes from '{}' for rerun store: {}",
                            file.getOriginalFilename(), ex.getMessage());
                }
            }
        }
        store.put(id, new RerunSnapshot(jobDescription != null ? jobDescription : "",
                List.copyOf(rerunFiles),
                screeningPackage != null ? screeningPackage : ScreeningPackage.QUICK_SCREEN));
        return id;
    }

    Optional<RerunSnapshot> get(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }

    record RerunSnapshot(String jobDescription, List<RerunFile> files, ScreeningPackage screeningPackage) {}

    record RerunFile(String filename, String contentType, byte[] bytes) {}
}
