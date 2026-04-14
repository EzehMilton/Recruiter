package com.recruiter.service;

import com.recruiter.document.DocumentExtractionOutcome;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CvDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(CvDeduplicationService.class);
    private static final double NEAR_DUPLICATE_THRESHOLD = 0.85;
    private static final Set<String> JACCARD_STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "in", "of", "to", "for", "is", "was", "at", "by",
            "on", "with", "as", "it", "that", "this", "from", "are", "be", "have", "has", "had",
            "not", "but", "we", "my", "i"
    );

    private final CandidateProfileFactory candidateProfileFactory;

    public CvDeduplicationService(CandidateProfileFactory candidateProfileFactory) {
        this.candidateProfileFactory = candidateProfileFactory;
    }

    public DeduplicationResult deduplicate(List<DocumentExtractionOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return new DeduplicationResult(List.of(), 0, 0);
        }

        List<DocumentExtractionOutcome> deduplicated = new ArrayList<>(outcomes.size());
        Map<String, CandidateDocument> seenExactDuplicates = new HashMap<>();
        Map<String, List<Integer>> keptIndexesByCandidateName = new HashMap<>();
        int exactDuplicatesRemoved = 0;
        int nearDuplicatesRemoved = 0;

        for (DocumentExtractionOutcome outcome : outcomes) {
            if (!outcome.succeeded()) {
                deduplicated.add(outcome);
                continue;
            }

            CandidateDocument candidateDocument = CandidateDocument.from(outcome, candidateProfileFactory);
            CandidateDocument exactMatch = seenExactDuplicates.get(candidateDocument.textHash());
            if (exactMatch != null) {
                exactDuplicatesRemoved++;
                log.warn("Exact duplicate CV detected: '{}' matches '{}', skipping",
                        outcome.originalFilename(), exactMatch.outcome().originalFilename());
                continue;
            }

            Integer nearDuplicateIndex = findNearDuplicateIndex(candidateDocument, deduplicated, keptIndexesByCandidateName);
            if (nearDuplicateIndex != null) {
                CandidateDocument keptCandidate = CandidateDocument.from(deduplicated.get(nearDuplicateIndex), candidateProfileFactory);
                nearDuplicatesRemoved++;
                if (candidateDocument.normalizedText().length() > keptCandidate.normalizedText().length()) {
                    deduplicated.set(nearDuplicateIndex, outcome);
                    seenExactDuplicates.remove(keptCandidate.textHash());
                    seenExactDuplicates.put(candidateDocument.textHash(), candidateDocument);
                    log.warn("Near-duplicate CV detected: '{}' replaces '{}' for candidate '{}'",
                            outcome.originalFilename(), keptCandidate.outcome().originalFilename(), candidateDocument.candidateName());
                } else {
                    log.warn("Near-duplicate CV detected: '{}' matches '{}', skipping",
                            outcome.originalFilename(), keptCandidate.outcome().originalFilename(), candidateDocument.candidateName());
                }
                continue;
            }

            int keptIndex = deduplicated.size();
            deduplicated.add(outcome);
            seenExactDuplicates.put(candidateDocument.textHash(), candidateDocument);
            keptIndexesByCandidateName
                    .computeIfAbsent(candidateDocument.candidateName(), ignored -> new ArrayList<>())
                    .add(keptIndex);
        }

        return new DeduplicationResult(List.copyOf(deduplicated), exactDuplicatesRemoved, nearDuplicatesRemoved);
    }

    private Integer findNearDuplicateIndex(CandidateDocument candidateDocument,
                                           List<DocumentExtractionOutcome> deduplicated,
                                           Map<String, List<Integer>> keptIndexesByCandidateName) {
        if (candidateDocument.candidateName().isBlank()) {
            return null;
        }

        List<Integer> candidateIndexes = keptIndexesByCandidateName.get(candidateDocument.candidateName());
        if (candidateIndexes == null || candidateIndexes.isEmpty()) {
            return null;
        }

        for (Integer index : candidateIndexes) {
            CandidateDocument keptCandidate = CandidateDocument.from(deduplicated.get(index), candidateProfileFactory);
            double similarity = jaccardSimilarity(candidateDocument.words(), keptCandidate.words());
            if (similarity > NEAR_DUPLICATE_THRESHOLD) {
                return index;
            }
        }
        return null;
    }

    private double jaccardSimilarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        Set<String> union = new HashSet<>(left);
        union.addAll(right);

        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return (double) intersection.size() / union.size();
    }

    public record DeduplicationResult(
            List<DocumentExtractionOutcome> outcomes,
            int exactDuplicatesRemoved,
            int nearDuplicatesRemoved
    ) {
        public DeduplicationResult {
            outcomes = List.copyOf(outcomes);
        }

        public int duplicatesRemoved() {
            return exactDuplicatesRemoved + nearDuplicatesRemoved;
        }
    }

    private record CandidateDocument(
            DocumentExtractionOutcome outcome,
            String normalizedText,
            String textHash,
            String candidateName,
            Set<String> words
    ) {
        private static CandidateDocument from(DocumentExtractionOutcome outcome,
                                              CandidateProfileFactory candidateProfileFactory) {
            ExtractedDocument extractedDocument = outcome.extractedDocument();
            String normalizedText = extractedDocument == null ? "" : extractedDocument.text().toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", " ")
                    .trim();
            CandidateProfile candidateProfile = candidateProfileFactory.create(extractedDocument);
            String candidateName = candidateProfile == null ? "" : candidateProfile.candidateName();
            return new CandidateDocument(
                    outcome,
                    normalizedText,
                    sha256Static(normalizedText),
                    candidateName == null ? "" : candidateName.trim().toLowerCase(Locale.ROOT),
                    tokeniseStatic(normalizedText)
            );
        }

        private static String sha256Static(String value) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 not available", ex);
            }
        }

        private static Set<String> tokeniseStatic(String text) {
            if (text == null || text.isBlank()) {
                return Set.of();
            }

            Set<String> uniqueWords = new HashSet<>();
            for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (token.isBlank() || JACCARD_STOP_WORDS.contains(token)) {
                    continue;
                }
                uniqueWords.add(token);
            }
            return uniqueWords;
        }
    }
}
