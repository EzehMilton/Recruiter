package com.recruiter.service;

import com.recruiter.config.SkillDictionaryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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

    private static final Logger log = LoggerFactory.getLogger(TextProfileHeuristicsService.class);
    private static final Map<String, String> DEFAULT_KNOWN_SKILLS = createDefaultKnownSkills();
    private static final Map<String, String> DEFAULT_SKILL_ALIASES = createDefaultSkillAliases();
    private static final Pattern YEARS_OF_EXPERIENCE_PATTERN =
            Pattern.compile("\\b(\\d{1,2})\\+?\\s+years?\\b", Pattern.CASE_INSENSITIVE);
    private static final int MAX_YEARS_OF_EXPERIENCE = 40;

    private static final List<String> PERSONAL_INDICATORS = List.of(
            "i have", "my experience", "worked for", "working for", "experience of",
            "years of experience", "years in", "years experience", "bringing"
    );

    private static final List<String> NON_PERSONAL_INDICATORS = List.of(
            "founded in", "established", "company has", "organisation with",
            "team of", "history of", "running for", "company with"
    );
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

    private final Map<String, String> knownSkills;
    private final Map<String, String> skillAliases;

    public TextProfileHeuristicsService(SkillDictionaryProperties skillDictionaryProperties) {
        if (skillDictionaryProperties.isLoaded()) {
            this.knownSkills = configuredKnownSkills(skillDictionaryProperties);
            this.skillAliases = Map.copyOf(skillDictionaryProperties.getAliases());
            return;
        }

        this.knownSkills = DEFAULT_KNOWN_SKILLS;
        this.skillAliases = DEFAULT_SKILL_ALIASES;
        log.warn("Skill dictionary at {} was missing or empty. Falling back to hardcoded defaults.",
                skillDictionaryProperties.getLocation());
    }

    private static Map<String, String> createDefaultKnownSkills() {
        Map<String, String> knownSkills = new LinkedHashMap<>();

        // Technical / Software
        knownSkills.put("java", "Java");
        knownSkills.put("spring", "Spring");
        knownSkills.put("spring boot", "Spring Boot");
        knownSkills.put("python", "Python");
        knownSkills.put("javascript", "JavaScript");
        knownSkills.put("typescript", "TypeScript");
        knownSkills.put("react", "React");
        knownSkills.put("angular", "Angular");
        knownSkills.put("node js", "Node.js");
        knownSkills.put("sql", "SQL");
        knownSkills.put("postgresql", "PostgreSQL");
        knownSkills.put("mysql", "MySQL");
        knownSkills.put("mongodb", "MongoDB");
        knownSkills.put("aws", "AWS");
        knownSkills.put("azure", "Azure");
        knownSkills.put("gcp", "GCP");
        knownSkills.put("docker", "Docker");
        knownSkills.put("kubernetes", "Kubernetes");
        knownSkills.put("terraform", "Terraform");
        knownSkills.put("microservices", "Microservices");
        knownSkills.put("rest api", "REST APIs");
        knownSkills.put("ci cd", "CI/CD");
        knownSkills.put("maven", "Maven");
        knownSkills.put("git", "Git");
        knownSkills.put("html", "HTML");
        knownSkills.put("css", "CSS");
        knownSkills.put("linux", "Linux");
        knownSkills.put("agile", "Agile");
        knownSkills.put("scrum", "Scrum");
        knownSkills.put("devops", "DevOps");
        knownSkills.put("machine learning", "Machine Learning");
        knownSkills.put("data analysis", "Data Analysis");

        // Healthcare
        knownSkills.put("patient care", "Patient Care");
        knownSkills.put("clinical", "Clinical");
        knownSkills.put("nursing", "Nursing");
        knownSkills.put("phlebotomy", "Phlebotomy");
        knownSkills.put("medication administration", "Medication Administration");
        knownSkills.put("care planning", "Care Planning");
        knownSkills.put("safeguarding", "Safeguarding");
        knownSkills.put("first aid", "First Aid");
        knownSkills.put("manual handling", "Manual Handling");
        knownSkills.put("infection control", "Infection Control");
        knownSkills.put("nhs", "NHS");

        // Science / Research
        knownSkills.put("laboratory", "Laboratory");
        knownSkills.put("research", "Research");
        knownSkills.put("peer review", "Peer Review");
        knownSkills.put("gmp", "GMP");
        knownSkills.put("gcp", "GCP");
        knownSkills.put("statistical analysis", "Statistical Analysis");
        knownSkills.put("r programming", "R Programming");

        // Education
        knownSkills.put("teaching", "Teaching");
        knownSkills.put("lesson planning", "Lesson Planning");
        knownSkills.put("curriculum", "Curriculum");
        knownSkills.put("classroom management", "Classroom Management");
        knownSkills.put("sen", "SEN");
        knownSkills.put("tutoring", "Tutoring");
        knownSkills.put("assessment", "Assessment");

        // Trades / Manual
        knownSkills.put("carpentry", "Carpentry");
        knownSkills.put("plumbing", "Plumbing");
        knownSkills.put("electrical", "Electrical");
        knownSkills.put("welding", "Welding");
        knownSkills.put("bricklaying", "Bricklaying");
        knownSkills.put("painting and decorating", "Painting and Decorating");
        knownSkills.put("forklift", "Forklift");
        knownSkills.put("cscs", "CSCS");
        knownSkills.put("health and safety", "Health and Safety");
        knownSkills.put("risk assessment", "Risk Assessment");
        knownSkills.put("cnc", "CNC");

        // Hospitality / Food
        knownSkills.put("food hygiene", "Food Hygiene");
        knownSkills.put("food safety", "Food Safety");
        knownSkills.put("barista", "Barista");
        knownSkills.put("front of house", "Front of House");
        knownSkills.put("housekeeping", "Housekeeping");
        knownSkills.put("customer service", "Customer Service");
        knownSkills.put("cash handling", "Cash Handling");

        // Finance / Business
        knownSkills.put("accounting", "Accounting");
        knownSkills.put("bookkeeping", "Bookkeeping");
        knownSkills.put("financial reporting", "Financial Reporting");
        knownSkills.put("budgeting", "Budgeting");
        knownSkills.put("forecasting", "Forecasting");
        knownSkills.put("excel", "Excel");
        knownSkills.put("sap", "SAP");
        knownSkills.put("salesforce", "Salesforce");
        knownSkills.put("crm", "CRM");
        knownSkills.put("erp", "ERP");
        knownSkills.put("payroll", "Payroll");
        knownSkills.put("audit", "Audit");
        knownSkills.put("compliance", "Compliance");

        // Legal
        knownSkills.put("conveyancing", "Conveyancing");
        knownSkills.put("litigation", "Litigation");
        knownSkills.put("contract law", "Contract Law");
        knownSkills.put("gdpr", "GDPR");
        knownSkills.put("legal research", "Legal Research");
        knownSkills.put("case management", "Case Management");

        // Logistics / Supply Chain
        knownSkills.put("warehouse", "Warehouse");
        knownSkills.put("supply chain", "Supply Chain");
        knownSkills.put("inventory management", "Inventory Management");
        knownSkills.put("logistics", "Logistics");
        knownSkills.put("procurement", "Procurement");
        knownSkills.put("dispatch", "Dispatch");
        knownSkills.put("goods in", "Goods In");
        knownSkills.put("picking and packing", "Picking and Packing");

        // Creative
        knownSkills.put("graphic design", "Graphic Design");
        knownSkills.put("adobe", "Adobe");
        knownSkills.put("photoshop", "Photoshop");
        knownSkills.put("illustrator", "Illustrator");
        knownSkills.put("indesign", "InDesign");
        knownSkills.put("figma", "Figma");
        knownSkills.put("ux design", "UX Design");
        knownSkills.put("ui design", "UI Design");
        knownSkills.put("copywriting", "Copywriting");
        knownSkills.put("content creation", "Content Creation");
        knownSkills.put("video editing", "Video Editing");
        knownSkills.put("photography", "Photography");
        knownSkills.put("seo", "SEO");

        // General transferable
        knownSkills.put("project management", "Project Management");
        knownSkills.put("stakeholder management", "Stakeholder Management");
        knownSkills.put("communication", "Communication");
        knownSkills.put("leadership", "Leadership");
        knownSkills.put("teamwork", "Teamwork");
        knownSkills.put("problem solving", "Problem Solving");
        knownSkills.put("time management", "Time Management");
        knownSkills.put("negotiation", "Negotiation");
        knownSkills.put("presentation", "Presentation");
        knownSkills.put("report writing", "Report Writing");
        knownSkills.put("data entry", "Data Entry");
        knownSkills.put("microsoft office", "Microsoft Office");
        knownSkills.put("powerpoint", "PowerPoint");
        knownSkills.put("word", "Word");
        knownSkills.put("driving licence", "Driving Licence");
        knownSkills.put("dbs", "DBS");

        return Collections.unmodifiableMap(knownSkills);
    }

    private static Map<String, String> createDefaultSkillAliases() {
        Map<String, String> skillAliases = new LinkedHashMap<>();

        // Technology
        skillAliases.put("reactjs", "react");
        skillAliases.put("react js", "react");
        skillAliases.put("nodejs", "node js");
        skillAliases.put("postgres", "postgresql");
        skillAliases.put("psql", "postgresql");
        skillAliases.put("amazon web services", "aws");
        skillAliases.put("k8s", "kubernetes");
        skillAliases.put("kube", "kubernetes");
        skillAliases.put("mongo", "mongodb");
        skillAliases.put("google cloud", "gcp");
        skillAliases.put("google cloud platform", "gcp");
        skillAliases.put("restful", "rest api");
        skillAliases.put("rest apis", "rest api");
        skillAliases.put("restful api", "rest api");
        skillAliases.put("restful apis", "rest api");
        skillAliases.put("springboot", "spring boot");

        // Healthcare
        skillAliases.put("nhs experience", "nhs");
        skillAliases.put("patient handling", "patient care");

        // Business / Office
        skillAliases.put("ms office", "microsoft office");
        skillAliases.put("office 365", "microsoft office");
        skillAliases.put("microsoft 365", "microsoft office");
        skillAliases.put("ms excel", "excel");
        skillAliases.put("ms word", "word");
        skillAliases.put("ms powerpoint", "powerpoint");
        skillAliases.put("ms ppt", "powerpoint");
        skillAliases.put("stakeholder engagement", "stakeholder management");

        // Creative
        skillAliases.put("ux", "ux design");
        skillAliases.put("user experience", "ux design");
        skillAliases.put("user experience design", "ux design");
        skillAliases.put("ui", "ui design");
        skillAliases.put("user interface", "ui design");
        skillAliases.put("user interface design", "ui design");
        skillAliases.put("adobe photoshop", "photoshop");
        skillAliases.put("adobe illustrator", "illustrator");
        skillAliases.put("adobe indesign", "indesign");

        // General
        skillAliases.put("programme management", "project management");
        skillAliases.put("full driving licence", "driving licence");
        skillAliases.put("clean driving licence", "driving licence");

        return Collections.unmodifiableMap(skillAliases);
    }

    private static Map<String, String> configuredKnownSkills(SkillDictionaryProperties skillDictionaryProperties) {
        Map<String, String> configuredSkills = new LinkedHashMap<>();
        for (String skill : skillDictionaryProperties.getSkills()) {
            String displayName = skillDictionaryProperties.getDisplayName(skill);
            configuredSkills.put(skill, displayName != null ? displayName : skill);
        }
        return Collections.unmodifiableMap(configuredSkills);
    }

    public List<String> extractSkills(String text) {
        return extractSkills(text, List.of());
    }

    public List<String> extractSkills(String text, List<String> additionalSkills) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalizedText = normalizeForMatching(text);
        Set<String> matches = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : knownSkills.entrySet()) {
            if (matchesSkill(normalizedText, entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        for (Map.Entry<String, String> aliasEntry : skillAliases.entrySet()) {
            String alias = aliasEntry.getKey();
            boolean matched = alias.length() <= 3
                    ? containsToken(normalizedText, alias)
                    : normalizedText.contains(alias);
            if (matched) {
                String displayLabel = knownSkills.get(aliasEntry.getValue());
                if (displayLabel != null) {
                    matches.add(displayLabel);
                }
            }
        }
        for (String additionalSkill : additionalSkills) {
            String normalizedSkill = normalizeForMatching(additionalSkill);
            if (normalizedSkill.isBlank() || !containsPhrase(normalizedText, normalizedSkill)) {
                continue;
            }
            matches.add(additionalSkill.trim());
        }
        return List.copyOf(matches);
    }

    private boolean matchesSkill(String normalizedText, String skill) {
        String normalizedSkill = normalizeForMatching(skill);
        if (normalizedSkill.isBlank()) {
            return false;
        }

        // Short canonical skills such as "SEN", "AWS", and "SQL" should only
        // match on token boundaries. Otherwise "SEN" matches inside "essential"
        // or "present", which can materially distort scoring.
        if (normalizedSkill.length() <= 3) {
            return containsToken(normalizedText, normalizedSkill);
        }
        return containsPhrase(normalizedText, normalizedSkill);
    }

    private static final List<String> ESSENTIAL_HINTS = List.of(
            "must", "required", "essential", "mandatory", "minimum",
            "need", "critical", "necessary", "key requirement"
    );

    private static final List<String> DESIRABLE_HINTS = List.of(
            "desirable", "nice to have", "preferred", "bonus",
            "advantageous", "ideally", "would be beneficial", "plus"
    );
    private static final Pattern CLAUSE_DELIMITER_PATTERN = Pattern.compile(
            "(?i)\\s*;\\s*|\\s+-\\s+|\\b(?:but|although|ideally|preferably)\\b|\\b(?:and|or)\\s+(?=(?:ideally|preferably|desirable|preferred|bonus|advantageous|essential|mandatory|required|necessary|critical|minimum|must|need)\\b)"
    );

    public RequirementClassification classifyRequirements(String jobDescriptionText,
                                                          List<String> extractedSkills) {
        if (jobDescriptionText == null || jobDescriptionText.isBlank() || extractedSkills.isEmpty()) {
            return new RequirementClassification(List.of(), List.of(), List.copyOf(extractedSkills));
        }

        List<RequirementClause> classifiedClauses = new ArrayList<>();

        for (String line : jobDescriptionText.lines().toList()) {
            for (String clause : splitIntoClauses(line)) {
                String normalizedClause = normalizeForMatching(clause);
                if (normalizedClause.isBlank()) {
                    continue;
                }
                boolean isEssential = ESSENTIAL_HINTS.stream()
                        .anyMatch(hint -> normalizedClause.contains(normalizeForMatching(hint)));
                boolean isDesirable = DESIRABLE_HINTS.stream()
                        .anyMatch(hint -> normalizedClause.contains(normalizeForMatching(hint)));
                classifiedClauses.add(new RequirementClause(normalizedClause, isEssential, isDesirable));
            }
        }

        boolean hasIndicators = classifiedClauses.stream()
                .anyMatch(clause -> clause.essential() || clause.desirable());
        if (!hasIndicators) {
            return new RequirementClassification(List.of(), List.of(), List.copyOf(extractedSkills));
        }

        List<String> essential = new ArrayList<>();
        List<String> desirable = new ArrayList<>();
        List<String> unclassified = new ArrayList<>();

        for (String skill : extractedSkills) {
            String normalizedSkill = normalizeForMatching(skill);
            boolean onEssential = classifiedClauses.stream()
                    .anyMatch(clause -> clause.essential() && clause.text().contains(normalizedSkill));
            boolean onDesirable = classifiedClauses.stream()
                    .anyMatch(clause -> clause.desirable() && clause.text().contains(normalizedSkill));

            if (onEssential) {
                essential.add(skill);
            } else if (onDesirable) {
                desirable.add(skill);
            } else {
                unclassified.add(skill);
            }
        }

        return new RequirementClassification(essential, desirable, unclassified);
    }

    private List<String> splitIntoClauses(String line) {
        String trimmedLine = line == null ? "" : line.trim();
        if (trimmedLine.isEmpty()) {
            return List.of();
        }

        List<String> clauses = new ArrayList<>();
        int start = 0;
        Matcher matcher = CLAUSE_DELIMITER_PATTERN.matcher(trimmedLine);
        while (matcher.find()) {
            String clause = trimmedLine.substring(start, matcher.start()).trim();
            if (!clause.isEmpty()) {
                clauses.add(clause);
            }
            String delimiter = matcher.group().trim().toLowerCase(Locale.ROOT);
            if (delimiter.equals("but")
                    || delimiter.equals("although")
                    || delimiter.equals("ideally")
                    || delimiter.equals("preferably")) {
                start = matcher.start();
            } else {
                start = matcher.end();
            }
        }

        String trailingClause = trimmedLine.substring(start).trim();
        if (!trailingClause.isEmpty()) {
            clauses.add(trailingClause);
        }
        return clauses;
    }

    private record RequirementClause(String text, boolean essential, boolean desirable) {
    }

    public Integer extractYearsOfExperience(String text, boolean contextAware) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = YEARS_OF_EXPERIENCE_PATTERN.matcher(text);

        if (!contextAware) {
            Integer highest = null;
            while (matcher.find()) {
                int val = Integer.parseInt(matcher.group(1));
                if (highest == null || val > highest) {
                    highest = val;
                }
            }
            return highest;
        }

        // CV mode: classify each match as personal or non-personal using surrounding context
        Integer highestPersonal = null;
        Integer highestOverall = null;

        while (matcher.find()) {
            int val = Integer.parseInt(matcher.group(1));
            if (val > MAX_YEARS_OF_EXPERIENCE) {
                continue;
            }

            String context = extractContextWindow(text, matcher.start(), matcher.end());
            String contextLower = context.toLowerCase(Locale.ROOT);

            boolean isPersonal = PERSONAL_INDICATORS.stream().anyMatch(contextLower::contains);
            boolean isNonPersonal = !isPersonal &&
                    NON_PERSONAL_INDICATORS.stream().anyMatch(contextLower::contains);

            if (isPersonal) {
                if (highestPersonal == null || val > highestPersonal) {
                    highestPersonal = val;
                }
            }
            if (highestOverall == null || val > highestOverall) {
                highestOverall = val;
            }
        }

        return highestPersonal != null ? highestPersonal : highestOverall;
    }

    private String extractContextWindow(String text, int matchStart, int matchEnd) {
        // Use only the line containing the match so that personal/non-personal indicators
        // from adjacent sentences do not bleed into each other's classification.
        int lineStart = text.lastIndexOf('\n', matchStart - 1);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        int lineEnd = text.indexOf('\n', matchEnd);
        lineEnd = lineEnd < 0 ? text.length() : lineEnd;
        return text.substring(lineStart, lineEnd);
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

    private boolean containsToken(String normalizedText, String token) {
        String padded = " " + normalizedText + " ";
        return padded.contains(" " + token + " ");
    }

    private boolean containsPhrase(String normalizedText, String phrase) {
        return normalizedText.contains(phrase);
    }

    private String normalizeForMatching(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "able", "about", "across", "after", "also", "apply", "area", "based", "been",
            "best", "both", "build", "building", "candidate", "candidates", "click", "come",
            "company", "could", "current", "currently", "daily", "deliver", "delivering",
            "department", "desired", "each", "employer", "enjoy", "ensure", "essential",
            "even", "every", "experience", "experienced", "familiar", "find", "focused",
            "following", "from", "full", "good", "great", "have", "help", "here", "high",
            "hold", "hour", "hours", "ideal", "ideally", "include", "including", "individual",
            "into", "join", "keep", "know", "knowledge", "like", "looking", "make", "many",
            "more", "most", "much", "must", "need", "new", "nice", "offer", "only", "open",
            "opportunity", "other", "over", "part", "people", "person", "please", "position",
            "previous", "previously", "provide", "range", "related", "relevant", "required",
            "right", "role", "salary", "should", "some", "strong", "such", "suitable",
            "support", "take", "team", "that", "their", "them", "then", "there", "these",
            "they", "this", "those", "time", "under", "upon", "used", "using", "various",
            "very", "want", "week", "were", "what", "when", "where", "which", "while",
            "will", "with", "within", "work", "working", "would", "year", "years", "your"
    );
}
