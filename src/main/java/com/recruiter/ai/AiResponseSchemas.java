package com.recruiter.ai;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class AiResponseSchemas {

    private AiResponseSchemas() {
    }

    public static ObjectNode jobDescriptionProfile() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.set("requiredSkills", stringArrayProperty("Required technical skills explicitly listed in the job description"));
        properties.set("preferredSkills", stringArrayProperty("Preferred or nice-to-have technical skills"));
        properties.set("qualifications", stringArrayProperty("Required degrees, certifications, or academic qualifications"));
        properties.set("softSkills", stringArrayProperty("Required soft skills such as communication, leadership, teamwork"));
        properties.set("domainKeywords", stringArrayProperty("Important domain keywords from requirements sections"));
        properties.set("yearsOfExperience", nullableIntegerProperty("Required years of experience if clearly stated"));
        schema.set("properties", properties);
        schema.set("required", required("requiredSkills", "preferredSkills", "qualifications", "softSkills", "domainKeywords", "yearsOfExperience"));
        return schema;
    }

    public static ObjectNode candidateProfile() {
        ObjectNode schema = baseObjectSchema();
        ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.set("candidateName", nullableStringProperty("Candidate name if visible in the CV text"));
        properties.set("skills", stringArrayProperty("Normalized technical skills from the CV"));
        properties.set("qualifications", stringArrayProperty("Degrees, certifications, or academic qualifications from the CV"));
        properties.set("softSkills", stringArrayProperty("Soft skills evidenced in the CV"));
        properties.set("yearsOfExperience", nullableIntegerProperty("Years of experience if explicitly stated or reasonably inferable"));
        schema.set("properties", properties);
        schema.set("required", required("candidateName", "skills", "qualifications", "softSkills", "yearsOfExperience"));
        return schema;
    }

    public static ObjectNode candidateSummary() {
        ObjectNode schema = baseObjectSchema();
        schema.set("properties", JsonNodeFactory.instance.objectNode()
                .set("summary", stringProperty("Short recruiter-facing summary grounded in the provided structured data")));
        schema.set("required", required("summary"));
        return schema;
    }

    private static ObjectNode baseObjectSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ObjectNode stringArrayProperty(String description) {
        ObjectNode property = JsonNodeFactory.instance.objectNode();
        property.put("type", "array");
        property.put("description", description);
        property.set("items", JsonNodeFactory.instance.objectNode().put("type", "string"));
        return property;
    }

    private static ObjectNode nullableIntegerProperty(String description) {
        ObjectNode property = JsonNodeFactory.instance.objectNode();
        property.put("description", description);
        ArrayNode anyOf = JsonNodeFactory.instance.arrayNode();
        anyOf.add(JsonNodeFactory.instance.objectNode().put("type", "integer"));
        anyOf.add(JsonNodeFactory.instance.objectNode().put("type", "null"));
        property.set("anyOf", anyOf);
        return property;
    }

    private static ObjectNode nullableStringProperty(String description) {
        ObjectNode property = JsonNodeFactory.instance.objectNode();
        property.put("description", description);
        ArrayNode anyOf = JsonNodeFactory.instance.arrayNode();
        anyOf.add(JsonNodeFactory.instance.objectNode().put("type", "string"));
        anyOf.add(JsonNodeFactory.instance.objectNode().put("type", "null"));
        property.set("anyOf", anyOf);
        return property;
    }

    private static ObjectNode stringProperty(String description) {
        ObjectNode property = JsonNodeFactory.instance.objectNode();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private static ArrayNode required(String... fieldNames) {
        ArrayNode required = JsonNodeFactory.instance.arrayNode();
        for (String fieldName : fieldNames) {
            required.add(fieldName);
        }
        return required;
    }
}
