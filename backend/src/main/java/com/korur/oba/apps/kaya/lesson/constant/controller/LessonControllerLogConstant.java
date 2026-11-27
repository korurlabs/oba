package com.korur.oba.apps.kaya.lesson.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the lesson controllers.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LessonControllerLogConstant {

    private LessonControllerLogConstant() {
    }

    public static final String LESSON_LIST_REQUEST = "Request for the lessons of organization {}";
    public static final String LESSON_LIST_COMPLETED = "Returned {} lessons of organization {}";
    public static final String FILTERS_REQUEST = "Request for the lesson filter options of organization {}";
    public static final String FILTERS_COMPLETED = "Returned the lesson filter options of organization {}";
    public static final String LESSON_GET_REQUEST = "Request of user {} for lesson {} in organization {}";
    public static final String LESSON_GET_COMPLETED = "Returned lesson {} in organization {}";
    public static final String LESSON_CREATE_REQUEST = "Request of user {} to create a lesson in organization {}";
    public static final String LESSON_CREATE_COMPLETED = "Created lesson {} in organization {}";
    public static final String LESSON_UPDATE_REQUEST = "Request of user {} to update lesson {} in organization {}";
    public static final String LESSON_UPDATE_COMPLETED = "Updated lesson {} in organization {}";
    public static final String LESSON_DELETE_REQUEST = "Request of user {} to delete lesson {} in organization {}";
    public static final String LESSON_DELETE_COMPLETED = "Deleted lesson {} in organization {}";

    // --- Machine delivery ---
    public static final String DELIVERY_LIST_REQUEST = "Request of machine {} for the lessons of a checkout";
    public static final String DELIVERY_LIST_COMPLETED = "Returned {} lessons of page {} of {} to machine {}";
    public static final String DELIVERY_NOT_MODIFIED = "The lessons of machine {} are unchanged";
    public static final String DELIVERY_SEARCH_REQUEST = "Request of machine {} to search the lessons of a checkout";
    public static final String DELIVERY_SEARCH_COMPLETED = "Returned {} lessons of a search to machine {}";
    public static final String DELIVERY_GET_REQUEST = "Request of machine {} for lesson {}";
    public static final String DELIVERY_GET_COMPLETED = "Returned lesson {} to machine {}";
    public static final String DELIVERY_CREATE_REQUEST = "Request of machine {} to add a lesson";
    public static final String DELIVERY_CREATE_COMPLETED = "Added lesson {} for machine {}";
    public static final String DELIVERY_DELETE_REQUEST = "Request of machine {} to delete lesson {}";
    public static final String DELIVERY_DELETE_COMPLETED = "Deleted lesson {} for machine {}";
}
