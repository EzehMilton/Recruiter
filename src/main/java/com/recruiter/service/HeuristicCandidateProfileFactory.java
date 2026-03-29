package com.recruiter.service;

import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HeuristicCandidateProfileFactory implements CandidateProfileFactory {

    private static final Pattern NAME_LINE_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z'\\-]+(?:\\s+[A-Za-z][A-Za-z'\\-]+){1,3}$");

    private static final Set<String> NAME_LINE_REJECTIONS = Set.of(
            "curriculum vitae", "resume", "profile", "summary", "professional summary", "experience",
            "work experience", "employment", "education", "skills", "technical skills", "projects",
            "certifications", "contact", "email", "phone"
    );

    private final TextProfileHeuristicsService heuristicsService;

    @Override
    public CandidateProfile create(ExtractedDocument extractedDocument) {
        String extractedText = extractedDocument.text();
        return new CandidateProfile(
                inferCandidateName(extractedDocument),
                extractedDocument.originalFilename(),
                extractedText,
                heuristicsService.extractSkills(extractedText),
                heuristicsService.extractYearsOfExperience(extractedText)
        );
    }

    private String inferCandidateName(ExtractedDocument extractedDocument) {
        String nameFromOpeningLines = extractNameFromOpeningLines(extractedDocument.text());
        if (!nameFromOpeningLines.isBlank()) {
            return nameFromOpeningLines;
        }
        return extractNameFromFilename(extractedDocument.originalFilename());
    }

    private String extractNameFromOpeningLines(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        List<String> lines = text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .limit(8)
                .toList();

        for (String line : lines) {
            if (!looksLikeCandidateName(line)) {
                continue;
            }
            return line;
        }

        return "";
    }

    private boolean looksLikeCandidateName(String line) {
        if (line.length() < 3 || line.length() > 50) {
            return false;
        }
        if (line.contains("@") || line.matches(".*\\d.*")) {
            return false;
        }

        String normalized = line.toLowerCase(Locale.ROOT).trim();
        if (NAME_LINE_REJECTIONS.contains(normalized)) {
            return false;
        }
        if (normalized.startsWith("skills ") || normalized.startsWith("technical skills ")) {
            return false;
        }
        if (containsOnlyKnownSkillTerms(line)) {
            return false;
        }

        return NAME_LINE_PATTERN.matcher(line).matches();
    }

    private boolean containsOnlyKnownSkillTerms(String line) {
        List<String> matchedSkills = heuristicsService.extractSkills(line);
        if (matchedSkills.isEmpty()) {
            return false;
        }

        Set<String> normalizedSkillTokens = matchedSkills.stream()
                .map(skill -> skill.toLowerCase(Locale.ROOT))
                .flatMap(skill -> List.of(skill.split("\\s+")).stream())
                .collect(java.util.stream.Collectors.toSet());

        List<String> lineTokens = List.of(line.toLowerCase(Locale.ROOT).split("\\s+"));
        return !lineTokens.isEmpty() && lineTokens.stream().allMatch(normalizedSkillTokens::contains);
    }

    private String extractNameFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Unknown Candidate";
        }

        String cleaned = filename.replaceFirst("\\.[^.]+$", "")
                .replaceAll("(?i)\\b(cv|resume|profile)\\b", " ")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isBlank()) {
            return "Unknown Candidate";
        }

        StringBuilder formatted = new StringBuilder();
        for (String token : cleaned.split("\\s+")) {
            if (formatted.length() > 0) {
                formatted.append(' ');
            }
            formatted.append(token.substring(0, 1).toUpperCase(Locale.ROOT));
            if (token.length() > 1) {
                formatted.append(token.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return formatted.toString();
    }
}
