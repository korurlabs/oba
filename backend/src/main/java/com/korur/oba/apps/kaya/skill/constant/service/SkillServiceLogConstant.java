package com.korur.oba.apps.kaya.skill.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the skill services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SkillServiceLogConstant {

    private SkillServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_SKILLS = "Listing skills of organization {} with {}";
    public static final String READING_SKILL = "Reading skill {} of organization {}";
    public static final String LOADING_VERSION_SKILLS = "Loading the skills of version {}";
    public static final String LOADING_PART_LINKS = "Loading the part links of skill {} of version {}";
    public static final String LOADING_LINKS_TO_PART = "Loading the skills linking {} part {}";
    public static final String COUNTING_VERSION_SKILLS = "Counting the skills and part links of {} versions of organization {}";
    public static final String SKILL_NOT_FOUND = "Skill {} not found in organization {}";

    // --- Writes ---
    public static final String SKILL_PART_NOT_IN_VERSION = "Skill {} links {} {} at {}, which is not a part of version {}";
    public static final String SKILLS_REPLACED = "Replaced the skills of version {} in organization {}: {} removed, {} written";
    public static final String SKILL_SAVED = "Saved the skill {} of version {} in organization {}: {} old row removed";
    public static final String SKILL_DELETED = "Deleted the skill {} of version {} in organization {}: {} row removed";
}
