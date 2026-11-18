package com.korur.oba.apps.kaya.version.constant.controller;

/**
 * Log messages of the version and draft controllers.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class VersionControllerLogConstant {

    private VersionControllerLogConstant() {
    }

    // --- Versions ---
    public static final String VERSION_LIST_REQUEST = "Request to list versions of organization {} with {}";
    public static final String VERSION_LIST_COMPLETED = "Listed {} versions of organization {}";
    public static final String VERSION_GET_REQUEST = "Request to read version {} of organization {}";
    public static final String VERSION_GET_COMPLETED = "Read version {} with {} files of organization {}";
    public static final String FILE_GET_REQUEST = "Request to read a file of version {} of organization {}";
    public static final String FILE_GET_COMPLETED = "Read file {} of version {} of organization {}";
    public static final String DIFF_REQUEST = "Request for the diff of version {} against {}, path {}, in organization {}";
    public static final String DIFF_COMPLETED = "Returned {} changed paths of version {} in organization {}";
    public static final String RESTORE_REQUEST = "Request of user {} to restore version {} of organization {}";
    public static final String RESTORE_COMPLETED = "Created rollback draft {} number {} in organization {}";

    // --- Drafts ---
    public static final String DRAFT_CREATE_REQUEST = "Request of user {} to create a draft of pack {} in organization {}";
    public static final String DRAFT_CREATE_COMPLETED = "Created draft {} number {} in organization {}";
    public static final String FILE_SAVE_REQUEST = "Request of user {} to save a file in draft {} of organization {}";
    public static final String FILE_SAVE_COMPLETED = "Saved {} in draft {} of organization {}";
    public static final String FILE_DELETE_REQUEST = "Request of user {} to delete a file of draft {} in organization {}";
    public static final String FILE_DELETE_COMPLETED = "Deleted a file of draft {} in organization {}";
    public static final String DRAFT_DELETE_REQUEST = "Request of user {} to delete draft {} of organization {}";
    public static final String DRAFT_DELETE_COMPLETED = "Deleted draft {} of organization {}";
    public static final String REBASE_REQUEST = "Request of user {} to rebase draft {} of organization {}";
    public static final String REBASE_COMPLETED = "Rebased draft {} on version {} in organization {}";
    public static final String OVERRIDE_SET_REQUEST = "Request of user {} to set an override base in draft {} of organization {}";
    public static final String OVERRIDE_SET_COMPLETED = "Set the override base of {} in draft {} of organization {}";
    public static final String OVERRIDE_CLEAR_REQUEST = "Request of user {} to clear an override in draft {} of organization {}";
    public static final String OVERRIDE_CLEAR_COMPLETED = "Cleared an override in draft {} of organization {}";
}
