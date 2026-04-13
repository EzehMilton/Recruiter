package com.recruiter.ai;

public enum Sector {

    GENERIC("Default (Generic)", "generic"),
    IT_AND_TECHNOLOGY("IT & Technology", "it_and_technology"),
    HEALTHCARE("Healthcare", "healthcare"),
    FINANCE("Finance", "finance"),
    EDUCATION("Education", "education"),
    SALES_AND_MARKETING("Sales & Marketing", "sales_and_marketing"),
    MANUAL_LABOUR("Manual / Labour", "manual_labour"),
    RETAIL("Retail", "retail"),
    CONSTRUCTION("Construction", "construction"),
    MANUFACTURING("Manufacturing", "manufacturing"),
    GREEN_ECONOMY("Green Economy", "green_economy");

    private final String label;
    private final String promptKey;

    Sector(String label, String promptKey) {
        this.label = label;
        this.promptKey = promptKey;
    }

    public String getLabel() {
        return label;
    }

    public String getPromptKey() {
        return promptKey;
    }

    public static Sector fromString(String value) {
        if (value == null || value.isBlank()) {
            return GENERIC;
        }
        for (Sector s : values()) {
            if (s.name().equalsIgnoreCase(value.trim()) || s.promptKey.equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return GENERIC;
    }
}
