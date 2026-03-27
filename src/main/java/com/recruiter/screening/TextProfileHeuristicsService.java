package com.recruiter.screening;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TextProfileHeuristicsService {

    private static final Map<String, String> KNOWN_SKILLS = new LinkedHashMap<>();
    private static final Pattern YEARS_OF_EXPERIENCE_PATTERN =
            Pattern.compile("\\b(\\d{1,2})\\+?\\s+years?\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> REQUIREMENT_HINTS = List.of(
            "required",
            "requirements",
            "must",
            "need",
            "needs",
            "looking for",
            "experience with",
            "skills",
            "qualifications"
    );

    static {
        KNOWN_SKILLS.put("java", "Java");
        KNOWN_SKILLS.put("spring", "Spring");
        KNOWN_SKILLS.put("spring boot", "Spring Boot");
        KNOWN_SKILLS.put("python", "Python");
        KNOWN_SKILLS.put("javascript", "JavaScript");
        KNOWN_SKILLS.put("typescript", "TypeScript");
        KNOWN_SKILLS.put("react", "React");
        KNOWN_SKILLS.put("sql", "SQL");
        KNOWN_SKILLS.put("postgresql", "PostgreSQL");
        KNOWN_SKILLS.put("mysql", "MySQL");
        KNOWN_SKILLS.put("aws", "AWS");
        KNOWN_SKILLS.put("docker", "Docker");
        KNOWN_SKILLS.put("kubernetes", "Kubernetes");
        KNOWN_SKILLS.put("microservices", "Microservices");
        KNOWN_SKILLS.put("rest api", "REST APIs");
        KNOWN_SKILLS.put("maven", "Maven");
        KNOWN_SKILLS.put("git", "Git");
        KNOWN_SKILLS.put("html", "HTML");
        KNOWN_SKILLS.put("css", "CSS");
    }

    public List<String> extractSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalizedText = normalizeForMatching(text);
        Set<String> matches = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : KNOWN_SKILLS.entrySet()) {
            if (containsPhrase(normalizedText, entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        return List.copyOf(matches);
    }

    public Integer extractYearsOfExperience(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = YEARS_OF_EXPERIENCE_PATTERN.matcher(text);
        Integer highestValue = null;
        while (matcher.find()) {
            int currentValue = Integer.parseInt(matcher.group(1));
            if (highestValue == null || currentValue > highestValue) {
                highestValue = currentValue;
            }
        }
        return highestValue;
    }

    public List<String> extractRequiredKeywords(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String focusedText = focusOnRequirementSections(text);
        Set<String> keywords = new LinkedHashSet<>(extractKeywords(focusedText));
        if (keywords.isEmpty()) {
            keywords.addAll(extractKeywords(text));
        }

        return keywords.stream()
                .limit(8)
                .toList();
    }

    public Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }

        String normalized = normalizeForMatching(text);
        String[] tokens = normalized.split("\\s+");
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.length() < 4 || STOP_WORDS.contains(token)) {
                continue;
            }
            keywords.add(token);
        }
        return keywords;
    }

    private String focusOnRequirementSections(String text) {
        List<String> matchingLines = text.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(this::looksLikeRequirementLine)
                .toList();

        if (matchingLines.isEmpty()) {
            return text;
        }
        return matchingLines.stream().collect(Collectors.joining(" "));
    }

    private boolean looksLikeRequirementLine(String line) {
        String normalizedLine = normalizeForMatching(line);
        for (String hint : REQUIREMENT_HINTS) {
            if (normalizedLine.contains(normalizeForMatching(hint))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPhrase(String normalizedText, String phrase) {
        String normalizedPhrase = normalizeForMatching(phrase);
        return normalizedText.contains(normalizedPhrase);
    }

    private String normalizeForMatching(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "able", "about", "across", "after", "also", "been", "build", "building", "candidate",
            "candidates", "company", "current", "deliver", "delivering", "engineer", "engineering",
            "experience", "experienced", "familiar", "focused", "have", "help", "into", "join",
            "knowledge", "looking", "must", "need", "nice", "role", "strong", "team", "their",
            "them", "this", "using", "with", "years"
    );
}
