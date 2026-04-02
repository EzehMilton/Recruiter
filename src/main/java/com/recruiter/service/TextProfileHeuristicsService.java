package com.recruiter.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        // Technical / Software
        KNOWN_SKILLS.put("java", "Java");
        KNOWN_SKILLS.put("spring", "Spring");
        KNOWN_SKILLS.put("spring boot", "Spring Boot");
        KNOWN_SKILLS.put("python", "Python");
        KNOWN_SKILLS.put("javascript", "JavaScript");
        KNOWN_SKILLS.put("typescript", "TypeScript");
        KNOWN_SKILLS.put("react", "React");
        KNOWN_SKILLS.put("angular", "Angular");
        KNOWN_SKILLS.put("node js", "Node.js");
        KNOWN_SKILLS.put("sql", "SQL");
        KNOWN_SKILLS.put("postgresql", "PostgreSQL");
        KNOWN_SKILLS.put("mysql", "MySQL");
        KNOWN_SKILLS.put("mongodb", "MongoDB");
        KNOWN_SKILLS.put("aws", "AWS");
        KNOWN_SKILLS.put("azure", "Azure");
        KNOWN_SKILLS.put("gcp", "GCP");
        KNOWN_SKILLS.put("docker", "Docker");
        KNOWN_SKILLS.put("kubernetes", "Kubernetes");
        KNOWN_SKILLS.put("terraform", "Terraform");
        KNOWN_SKILLS.put("microservices", "Microservices");
        KNOWN_SKILLS.put("rest api", "REST APIs");
        KNOWN_SKILLS.put("ci cd", "CI/CD");
        KNOWN_SKILLS.put("maven", "Maven");
        KNOWN_SKILLS.put("git", "Git");
        KNOWN_SKILLS.put("html", "HTML");
        KNOWN_SKILLS.put("css", "CSS");
        KNOWN_SKILLS.put("linux", "Linux");
        KNOWN_SKILLS.put("agile", "Agile");
        KNOWN_SKILLS.put("scrum", "Scrum");
        KNOWN_SKILLS.put("devops", "DevOps");
        KNOWN_SKILLS.put("machine learning", "Machine Learning");
        KNOWN_SKILLS.put("data analysis", "Data Analysis");

        // Healthcare
        KNOWN_SKILLS.put("patient care", "Patient Care");
        KNOWN_SKILLS.put("clinical", "Clinical");
        KNOWN_SKILLS.put("nursing", "Nursing");
        KNOWN_SKILLS.put("phlebotomy", "Phlebotomy");
        KNOWN_SKILLS.put("medication administration", "Medication Administration");
        KNOWN_SKILLS.put("care planning", "Care Planning");
        KNOWN_SKILLS.put("safeguarding", "Safeguarding");
        KNOWN_SKILLS.put("first aid", "First Aid");
        KNOWN_SKILLS.put("manual handling", "Manual Handling");
        KNOWN_SKILLS.put("infection control", "Infection Control");
        KNOWN_SKILLS.put("nhs", "NHS");

        // Science / Research
        KNOWN_SKILLS.put("laboratory", "Laboratory");
        KNOWN_SKILLS.put("research", "Research");
        KNOWN_SKILLS.put("peer review", "Peer Review");
        KNOWN_SKILLS.put("gmp", "GMP");
        KNOWN_SKILLS.put("gcp", "GCP");
        KNOWN_SKILLS.put("statistical analysis", "Statistical Analysis");
        KNOWN_SKILLS.put("r programming", "R Programming");

        // Education
        KNOWN_SKILLS.put("teaching", "Teaching");
        KNOWN_SKILLS.put("lesson planning", "Lesson Planning");
        KNOWN_SKILLS.put("curriculum", "Curriculum");
        KNOWN_SKILLS.put("classroom management", "Classroom Management");
        KNOWN_SKILLS.put("sen", "SEN");
        KNOWN_SKILLS.put("tutoring", "Tutoring");
        KNOWN_SKILLS.put("assessment", "Assessment");

        // Trades / Manual
        KNOWN_SKILLS.put("carpentry", "Carpentry");
        KNOWN_SKILLS.put("plumbing", "Plumbing");
        KNOWN_SKILLS.put("electrical", "Electrical");
        KNOWN_SKILLS.put("welding", "Welding");
        KNOWN_SKILLS.put("bricklaying", "Bricklaying");
        KNOWN_SKILLS.put("painting and decorating", "Painting and Decorating");
        KNOWN_SKILLS.put("forklift", "Forklift");
        KNOWN_SKILLS.put("cscs", "CSCS");
        KNOWN_SKILLS.put("health and safety", "Health and Safety");
        KNOWN_SKILLS.put("risk assessment", "Risk Assessment");
        KNOWN_SKILLS.put("cnc", "CNC");

        // Hospitality / Food
        KNOWN_SKILLS.put("food hygiene", "Food Hygiene");
        KNOWN_SKILLS.put("food safety", "Food Safety");
        KNOWN_SKILLS.put("barista", "Barista");
        KNOWN_SKILLS.put("front of house", "Front of House");
        KNOWN_SKILLS.put("housekeeping", "Housekeeping");
        KNOWN_SKILLS.put("customer service", "Customer Service");
        KNOWN_SKILLS.put("cash handling", "Cash Handling");

        // Finance / Business
        KNOWN_SKILLS.put("accounting", "Accounting");
        KNOWN_SKILLS.put("bookkeeping", "Bookkeeping");
        KNOWN_SKILLS.put("financial reporting", "Financial Reporting");
        KNOWN_SKILLS.put("budgeting", "Budgeting");
        KNOWN_SKILLS.put("forecasting", "Forecasting");
        KNOWN_SKILLS.put("excel", "Excel");
        KNOWN_SKILLS.put("sap", "SAP");
        KNOWN_SKILLS.put("salesforce", "Salesforce");
        KNOWN_SKILLS.put("crm", "CRM");
        KNOWN_SKILLS.put("erp", "ERP");
        KNOWN_SKILLS.put("payroll", "Payroll");
        KNOWN_SKILLS.put("audit", "Audit");
        KNOWN_SKILLS.put("compliance", "Compliance");

        // Legal
        KNOWN_SKILLS.put("conveyancing", "Conveyancing");
        KNOWN_SKILLS.put("litigation", "Litigation");
        KNOWN_SKILLS.put("contract law", "Contract Law");
        KNOWN_SKILLS.put("gdpr", "GDPR");
        KNOWN_SKILLS.put("legal research", "Legal Research");
        KNOWN_SKILLS.put("case management", "Case Management");

        // Logistics / Supply Chain
        KNOWN_SKILLS.put("warehouse", "Warehouse");
        KNOWN_SKILLS.put("supply chain", "Supply Chain");
        KNOWN_SKILLS.put("inventory management", "Inventory Management");
        KNOWN_SKILLS.put("logistics", "Logistics");
        KNOWN_SKILLS.put("procurement", "Procurement");
        KNOWN_SKILLS.put("dispatch", "Dispatch");
        KNOWN_SKILLS.put("goods in", "Goods In");
        KNOWN_SKILLS.put("picking and packing", "Picking and Packing");

        // Creative
        KNOWN_SKILLS.put("graphic design", "Graphic Design");
        KNOWN_SKILLS.put("adobe", "Adobe");
        KNOWN_SKILLS.put("photoshop", "Photoshop");
        KNOWN_SKILLS.put("illustrator", "Illustrator");
        KNOWN_SKILLS.put("indesign", "InDesign");
        KNOWN_SKILLS.put("figma", "Figma");
        KNOWN_SKILLS.put("ux design", "UX Design");
        KNOWN_SKILLS.put("ui design", "UI Design");
        KNOWN_SKILLS.put("copywriting", "Copywriting");
        KNOWN_SKILLS.put("content creation", "Content Creation");
        KNOWN_SKILLS.put("video editing", "Video Editing");
        KNOWN_SKILLS.put("photography", "Photography");
        KNOWN_SKILLS.put("seo", "SEO");

        // General transferable
        KNOWN_SKILLS.put("project management", "Project Management");
        KNOWN_SKILLS.put("stakeholder management", "Stakeholder Management");
        KNOWN_SKILLS.put("communication", "Communication");
        KNOWN_SKILLS.put("leadership", "Leadership");
        KNOWN_SKILLS.put("teamwork", "Teamwork");
        KNOWN_SKILLS.put("problem solving", "Problem Solving");
        KNOWN_SKILLS.put("time management", "Time Management");
        KNOWN_SKILLS.put("negotiation", "Negotiation");
        KNOWN_SKILLS.put("presentation", "Presentation");
        KNOWN_SKILLS.put("report writing", "Report Writing");
        KNOWN_SKILLS.put("data entry", "Data Entry");
        KNOWN_SKILLS.put("microsoft office", "Microsoft Office");
        KNOWN_SKILLS.put("powerpoint", "PowerPoint");
        KNOWN_SKILLS.put("word", "Word");
        KNOWN_SKILLS.put("driving licence", "Driving Licence");
        KNOWN_SKILLS.put("dbs", "DBS");
    }

    private static final Map<String, String> SKILL_ALIASES = new LinkedHashMap<>();

    static {
        // Technology
        SKILL_ALIASES.put("reactjs", "react");
        SKILL_ALIASES.put("react js", "react");
        SKILL_ALIASES.put("nodejs", "node js");
        SKILL_ALIASES.put("postgres", "postgresql");
        SKILL_ALIASES.put("psql", "postgresql");
        SKILL_ALIASES.put("amazon web services", "aws");
        SKILL_ALIASES.put("k8s", "kubernetes");
        SKILL_ALIASES.put("kube", "kubernetes");
        SKILL_ALIASES.put("mongo", "mongodb");
        SKILL_ALIASES.put("google cloud", "gcp");
        SKILL_ALIASES.put("google cloud platform", "gcp");
        SKILL_ALIASES.put("restful", "rest api");
        SKILL_ALIASES.put("rest apis", "rest api");
        SKILL_ALIASES.put("restful api", "rest api");
        SKILL_ALIASES.put("restful apis", "rest api");
        SKILL_ALIASES.put("springboot", "spring boot");

        // Healthcare
        SKILL_ALIASES.put("nhs experience", "nhs");
        SKILL_ALIASES.put("patient handling", "patient care");

        // Business / Office
        SKILL_ALIASES.put("ms office", "microsoft office");
        SKILL_ALIASES.put("office 365", "microsoft office");
        SKILL_ALIASES.put("microsoft 365", "microsoft office");
        SKILL_ALIASES.put("ms excel", "excel");
        SKILL_ALIASES.put("ms word", "word");
        SKILL_ALIASES.put("ms powerpoint", "powerpoint");
        SKILL_ALIASES.put("ms ppt", "powerpoint");
        SKILL_ALIASES.put("stakeholder engagement", "stakeholder management");

        // Creative
        SKILL_ALIASES.put("ux", "ux design");
        SKILL_ALIASES.put("user experience", "ux design");
        SKILL_ALIASES.put("user experience design", "ux design");
        SKILL_ALIASES.put("ui", "ui design");
        SKILL_ALIASES.put("user interface", "ui design");
        SKILL_ALIASES.put("user interface design", "ui design");
        SKILL_ALIASES.put("adobe photoshop", "photoshop");
        SKILL_ALIASES.put("adobe illustrator", "illustrator");
        SKILL_ALIASES.put("adobe indesign", "indesign");

        // General
        SKILL_ALIASES.put("programme management", "project management");
        SKILL_ALIASES.put("full driving licence", "driving licence");
        SKILL_ALIASES.put("clean driving licence", "driving licence");
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
        for (Map.Entry<String, String> entry : KNOWN_SKILLS.entrySet()) {
            if (containsPhrase(normalizedText, entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        for (Map.Entry<String, String> aliasEntry : SKILL_ALIASES.entrySet()) {
            String alias = aliasEntry.getKey();
            boolean matched = alias.length() <= 3
                    ? containsToken(normalizedText, alias)
                    : normalizedText.contains(alias);
            if (matched) {
                String displayLabel = KNOWN_SKILLS.get(aliasEntry.getValue());
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

    private static final List<String> ESSENTIAL_HINTS = List.of(
            "must", "required", "essential", "mandatory", "minimum",
            "need", "critical", "necessary", "key requirement"
    );

    private static final List<String> DESIRABLE_HINTS = List.of(
            "desirable", "nice to have", "preferred", "bonus",
            "advantageous", "ideally", "would be beneficial", "plus"
    );

    public RequirementClassification classifyRequirements(String jobDescriptionText,
                                                          List<String> extractedSkills) {
        if (jobDescriptionText == null || jobDescriptionText.isBlank() || extractedSkills.isEmpty()) {
            return new RequirementClassification(List.of(), List.of(), List.copyOf(extractedSkills));
        }

        List<String> essentialLines = new ArrayList<>();
        List<String> desirableLines = new ArrayList<>();

        for (String line : jobDescriptionText.lines().toList()) {
            String normalizedLine = normalizeForMatching(line);
            if (normalizedLine.isBlank()) {
                continue;
            }
            boolean isEssential = ESSENTIAL_HINTS.stream()
                    .anyMatch(hint -> normalizedLine.contains(normalizeForMatching(hint)));
            boolean isDesirable = DESIRABLE_HINTS.stream()
                    .anyMatch(hint -> normalizedLine.contains(normalizeForMatching(hint)));
            if (isEssential) {
                essentialLines.add(normalizedLine);
            }
            if (isDesirable) {
                desirableLines.add(normalizedLine);
            }
        }

        if (essentialLines.isEmpty() && desirableLines.isEmpty()) {
            return new RequirementClassification(List.of(), List.of(), List.copyOf(extractedSkills));
        }

        List<String> essential = new ArrayList<>();
        List<String> desirable = new ArrayList<>();
        List<String> unclassified = new ArrayList<>();

        for (String skill : extractedSkills) {
            String normalizedSkill = normalizeForMatching(skill);
            boolean onEssential = essentialLines.stream()
                    .anyMatch(line -> line.contains(normalizedSkill));
            boolean onDesirable = desirableLines.stream()
                    .anyMatch(line -> line.contains(normalizedSkill));

            if (onEssential && onDesirable) {
                unclassified.add(skill);
            } else if (onEssential) {
                essential.add(skill);
            } else if (onDesirable) {
                desirable.add(skill);
            } else {
                unclassified.add(skill);
            }
        }

        return new RequirementClassification(essential, desirable, unclassified);
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

    private boolean containsToken(String normalizedText, String token) {
        String padded = " " + normalizedText + " ";
        return padded.contains(" " + token + " ");
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
