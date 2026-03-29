package com.recruiter.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PipelineTimer {

    private final Map<String, Long> durations = new LinkedHashMap<>();
    private final long startTime = System.currentTimeMillis();
    private long phaseStart;
    private String currentPhase;

    public void startPhase(String name) {
        endCurrentPhase();
        this.currentPhase = name;
        this.phaseStart = System.currentTimeMillis();
    }

    public void endCurrentPhase() {
        if (currentPhase != null) {
            durations.put(currentPhase, System.currentTimeMillis() - phaseStart);
            currentPhase = null;
        }
    }

    public long totalElapsed() {
        return System.currentTimeMillis() - startTime;
    }

    public Map<String, Long> getPhaseDurations() {
        endCurrentPhase();
        return Collections.unmodifiableMap(durations);
    }
}
