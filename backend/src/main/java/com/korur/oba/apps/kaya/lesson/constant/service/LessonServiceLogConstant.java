package com.korur.oba.apps.kaya.lesson.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the lesson services.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LessonServiceLogConstant {

    private LessonServiceLogConstant() {
    }

    // --- Member reads ---
    public static final String LISTING_LESSONS = "Listing lessons of organization {} with {}";
    public static final String READING_FILTERS = "Reading lesson filter options of organization {}";
    public static final String READING_LESSON = "Reading lesson {} of organization {}";
    public static final String LESSON_NOT_FOUND = "Lesson {} not found in organization {}";

    // --- Changes ---
    public static final String LESSON_CREATED = "User {} created lesson {} in organization {}";
    public static final String LESSON_UPDATED = "User {} updated lesson {} in organization {}";
    public static final String LESSON_DELETED = "User {} deleted lesson {} in organization {}";
    public static final String REPOSITORY_NOT_IN_ORGANIZATION = "Repository {} of a lesson is not in organization {}";
    public static final String REPOSITORY_ARCHIVED = "Refused archived repository {} of organization {} for a lesson";

    // --- Machine delivery ---
    public static final String READING_DELIVERY = "Reading the lessons of repository {} of organization {} for delivery";
    public static final String SEARCHING_DELIVERY = "Searching the lessons of repository {} of organization {} for delivery";
    public static final String DELIVERY_REPOSITORY_UNKNOWN = "No repository of organization {} has the remote of the request";
    public static final String DELIVERY_SINCE_INVALID = "Refused an unreadable since from machine {}";
    public static final String DELIVERY_ETAG_MISSING = "Refused page {} without the ETag of page 1 from machine {}";
    public static final String DELIVERY_LESSONS_CHANGED =
            "The lessons of repository {} of organization {} changed before page {} of machine {}";
    public static final String DELIVERY_RATE_LIMITED =
            "Refused a lesson read of machine {}: token {} made more than {} reads in a minute";
    public static final String DELIVERY_USER_RATE_LIMITED =
            "Refused a lesson read of machine {}: user {} made more than {} reads in a minute";
    public static final String DELIVERY_ORGANIZATION_RATE_LIMITED =
            "Refused a lesson read of machine {}: organization {} made more than {} reads in a minute";
    public static final String DELIVERY_ORGANIZATION_BUSY =
            "Refused a lesson read of machine {}: organization {} already runs {} reads on this instance";
    public static final String DELIVERY_INSTANCE_BUSY =
            "Refused a lesson read of machine {}: this instance already runs {} lesson reads";
    public static final String DELIVERY_PROPOSAL_RATE_LIMITED =
            "Refused a lesson of machine {}: token {} added more than {} lessons in a minute";
}
