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
            "required", "requirements", "must", "need", "needs",
            "looking for", "experience with", "skills", "qualifications"
    );

    private static final List<String> PREFERRED_HINTS = List.of(
            "nice to have", "preferred", "bonus", "desirable", "ideally",
            "advantageous", "plus", "a plus", "not required"
    );

    private static final Set<String> KNOWN_QUALIFICATIONS = Set.of(
            "bachelor", "bachelors", "bachelor's",
            "master", "masters", "master's",
            "phd", "ph.d", "doctorate",
            "mba",
            "bsc", "b.sc", "msc", "m.sc",
            "b.s.", "m.s.",
            "computer science", "software engineering",
            "information technology", "mathematics",
            "certified", "certification",
            "scrum master", "pmp", "aws certified",
            "cissp", "cka", "ckad"
    );

    private static final Map<String, String> KNOWN_SOFT_SKILLS = new LinkedHashMap<>();

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

        KNOWN_SOFT_SKILLS.put("communication", "Communication");
        KNOWN_SOFT_SKILLS.put("leadership", "Leadership");
        KNOWN_SOFT_SKILLS.put("teamwork", "Teamwork");
        KNOWN_SOFT_SKILLS.put("team player", "Teamwork");
        KNOWN_SOFT_SKILLS.put("problem solving", "Problem Solving");
        KNOWN_SOFT_SKILLS.put("problem-solving", "Problem Solving");
        KNOWN_SOFT_SKILLS.put("analytical", "Analytical Thinking");
        KNOWN_SOFT_SKILLS.put("mentoring", "Mentoring");
        KNOWN_SOFT_SKILLS.put("mentor", "Mentoring");
        KNOWN_SOFT_SKILLS.put("collaboration", "Collaboration");
        KNOWN_SOFT_SKILLS.put("collaborative", "Collaboration");
        KNOWN_SOFT_SKILLS.put("self-motivated", "Self-Motivated");
        KNOWN_SOFT_SKILLS.put("adaptable", "Adaptability");
        KNOWN_SOFT_SKILLS.put("adaptability", "Adaptability");
        KNOWN_SOFT_SKILLS.put("attention to detail", "Attention to Detail");
        KNOWN_SOFT_SKILLS.put("time management", "Time Management");
        KNOWN_SOFT_SKILLS.put("critical thinking", "Critical Thinking");
        KNOWN_SOFT_SKILLS.put("stakeholder management", "Stakeholder Management");
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

    public List<String> extractSkillsFromSection(String text, List<String> sectionHints) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String focused = focusOnSection(text, sectionHints);
        return extractSkills(focused);
    }

    public List<String> extractPreferredSkills(String text) {
        return extractSkillsFromSection(text, PREFERRED_HINTS);
    }

    public List<String> extractSoftSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalizedText = normalizeForMatching(text);
        Set<String> matches = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : KNOWN_SOFT_SKILLS.entrySet()) {
            if (containsPhrase(normalizedText, entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        return List.copyOf(matches);
    }

    public List<String> extractQualifications(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalizedText = normalizeForMatching(text);
        Set<String> matches = new LinkedHashSet<>();
        for (String qualification : KNOWN_QUALIFICATIONS) {
            if (containsPhrase(normalizedText, qualification)) {
                matches.add(toTitleCase(qualification));
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

    public List<String> extractDomainKeywords(String text) {
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

    private String focusOnSection(String text, List<String> hints) {
        List<String> matchingLines = text.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> {
                    String normalized = normalizeForMatching(line);
                    return hints.stream().anyMatch(hint -> normalized.contains(normalizeForMatching(hint)));
                })
                .toList();

        if (matchingLines.isEmpty()) {
            return "";
        }
        return String.join(" ", matchingLines);
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

    boolean containsPhrase(String normalizedText, String phrase) {
        String normalizedPhrase = normalizeForMatching(phrase);
        return normalizedText.contains(normalizedPhrase);
    }

    String normalizeForMatching(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String toTitleCase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String[] words = value.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "able", "about", "across", "after", "also", "been", "build", "building", "candidate",
            "candidates", "company", "current", "deliver", "delivering", "engineer", "engineering",
            "experience", "experienced", "familiar", "focused", "have", "help", "into", "join",
            "knowledge", "looking", "must", "need", "nice", "role", "strong", "team", "their",
            "them", "this", "using", "with", "years"
    );
}
