package com.recruiter.ai;

public record EvidenceItem(
        String item,
        EvidenceStrength evidenceStrength,
        String supportingEvidence
) {
}
