package com.korur.oba.apps.kaya.skill.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the skill controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SkillControllerLogConstant {

    private SkillControllerLogConstant() {
    }

    public static final String SKILL_LIST_REQUEST = "Received request to list the skills of {} versions in organization {}";
    public static final String SKILL_LIST_COMPLETED = "Listed {} skills in organization {}";
    public static final String SKILL_GET_REQUEST = "Received request to read skill {} of organization {}";
    public static final String SKILL_GET_COMPLETED = "Read skill {} of organization {}";
}
