package com.recruiter.service;

import com.recruiter.config.SkillDictionaryProperties;
import org.springframework.core.io.DefaultResourceLoader;

final class TextProfileHeuristicsServiceTestSupport {

    private TextProfileHeuristicsServiceTestSupport() {
    }

    static TextProfileHeuristicsService createService() {
        return new TextProfileHeuristicsService(createProperties("classpath:skills.yml"));
    }

    static TextProfileHeuristicsService createService(String location) {
        return new TextProfileHeuristicsService(createProperties(location));
    }

    static SkillDictionaryProperties createProperties(String location) {
        SkillDictionaryProperties properties = new SkillDictionaryProperties(new DefaultResourceLoader());
        properties.setLocation(location);
        properties.afterPropertiesSet();
        return properties;
    }
}
