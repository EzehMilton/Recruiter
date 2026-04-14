package com.recruiter.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConfigurationProperties(prefix = "skill-dictionary")
public class SkillDictionaryProperties implements InitializingBean {

    private static final String DEFAULT_LOCATION = "classpath:skills.yml";
    private static final String ALIAS_PREFIX = "aliases.";
    private static final Pattern SKILL_INDEX_PATTERN = Pattern.compile("^skills\\[(\\d+)]$");

    private final ResourceLoader resourceLoader;

    private String location = DEFAULT_LOCATION;
    private Set<String> skills = Set.of();
    private Map<String, String> aliases = Map.of();
    private Map<String, String> displayNames = Map.of();
    private boolean loaded;

    public SkillDictionaryProperties(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void afterPropertiesSet() {
        reload();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = (location == null || location.isBlank()) ? DEFAULT_LOCATION : location;
    }

    public Set<String> getSkills() {
        return skills;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public String getDisplayName(String skill) {
        if (skill == null || skill.isBlank()) {
            return null;
        }
        return displayNames.get(normalize(skill));
    }

    public boolean isLoaded() {
        return loaded;
    }

    void reload() {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            reset();
            return;
        }

        try {
            YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
            yamlFactory.setResources(resource);
            Properties properties = yamlFactory.getObject();
            if (properties == null || properties.isEmpty()) {
                reset();
                return;
            }

            Map<String, String> resolvedDisplayNames = extractDisplayNames(properties);
            if (resolvedDisplayNames.isEmpty()) {
                reset();
                return;
            }

            this.displayNames = Collections.unmodifiableMap(resolvedDisplayNames);
            this.skills = Collections.unmodifiableSet(new LinkedHashSet<>(resolvedDisplayNames.keySet()));
            this.aliases = Collections.unmodifiableMap(extractAliases(properties));
            this.loaded = true;
        } catch (RuntimeException ex) {
            reset();
        }
    }

    private Map<String, String> extractDisplayNames(Properties properties) {
        Map<Integer, String> indexedSkills = new TreeMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            Matcher matcher = SKILL_INDEX_PATTERN.matcher(propertyName);
            if (!matcher.matches()) {
                continue;
            }

            String rawSkill = properties.getProperty(propertyName);
            if (rawSkill == null || rawSkill.isBlank()) {
                continue;
            }
            indexedSkills.put(Integer.parseInt(matcher.group(1)), rawSkill.trim());
        }

        Map<String, String> resolvedDisplayNames = new LinkedHashMap<>();
        for (String rawSkill : indexedSkills.values()) {
            resolvedDisplayNames.putIfAbsent(normalize(rawSkill), rawSkill);
        }
        return resolvedDisplayNames;
    }

    private Map<String, String> extractAliases(Properties properties) {
        Map<String, String> resolvedAliases = new LinkedHashMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith(ALIAS_PREFIX)) {
                continue;
            }

            String alias = propertyName.substring(ALIAS_PREFIX.length()).trim();
            String canonical = properties.getProperty(propertyName);
            if (alias.isBlank() || canonical == null || canonical.isBlank()) {
                continue;
            }
            resolvedAliases.put(normalize(alias), normalize(canonical));
        }
        return resolvedAliases;
    }

    private void reset() {
        this.skills = Set.of();
        this.aliases = Map.of();
        this.displayNames = Map.of();
        this.loaded = false;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
