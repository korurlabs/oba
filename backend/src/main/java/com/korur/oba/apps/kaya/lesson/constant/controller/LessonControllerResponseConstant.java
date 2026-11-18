package com.korur.oba.apps.kaya.lesson.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Response messages of the lesson controllers.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LessonControllerResponseConstant {

    private LessonControllerResponseConstant() {
    }

    public static final String RESPONSE_LESSONS_RETRIEVED = "Lessons retrieved successfully";
    public static final String RESPONSE_FILTERS_RETRIEVED = "Lesson filters retrieved successfully";
    public static final String RESPONSE_LESSON_RETRIEVED = "Lesson retrieved successfully";
    public static final String RESPONSE_LESSON_CREATED = "Lesson created successfully";
    public static final String RESPONSE_LESSON_UPDATED = "Lesson updated successfully";
    public static final String RESPONSE_LESSON_DELETED = "Lesson deleted successfully";
}
