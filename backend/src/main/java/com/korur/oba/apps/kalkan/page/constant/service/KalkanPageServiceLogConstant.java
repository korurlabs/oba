package com.korur.oba.apps.kalkan.page.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the page services and the page attachment policy, including the refusal lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class KalkanPageServiceLogConstant {

    private KalkanPageServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_TREE = "Listing the page tree of organization {} for user {}";
    public static final String READING_PAGE = "Reading page {} of organization {}";
    public static final String LISTING_REVISIONS = "Listing the revisions of page {} of organization {}";
    public static final String READING_REVISION = "Reading revision {} of page {} of organization {}";
    public static final String REVISION_NOT_FOUND = "Revision {} not found for page {} in organization {}";
    public static final String LISTING_TRASH = "Listing the deleted pages of organization {} for user {}";
    public static final String LISTING_RECENT = "Listing the {} most recently changed pages of organization {} for user {}";
    public static final String PAGE_NOT_FOUND = "Page {} not found in organization {}";
    public static final String PAGE_NOT_FOUND_BY_ID = "Page {} not found";
    public static final String DELETED_PAGE_NOT_FOUND = "Page {} not found in organization {}, deleted pages included";

    // --- Writes ---
    public static final String PAGE_CREATED = "Created page {} under parent {} in organization {} by user {}";
    public static final String PAGE_UPDATED = "Updated page {} in organization {} by user {} to version {}: {} translations changed";
    public static final String PAGE_MOVED = "Moved page {} under parent {} at position {} in organization {} by user {}; {} descendants moved to root {}";
    public static final String PAGE_DELETED = "Deleted page {} in organization {} by user {}: {} pages, {} translations";
    public static final String PAGE_RESTORED = "Restored page {} in organization {} by user {}: {} pages, {} translations";
    public static final String PAGE_UPSERTED = "Upserted path {} in organization {} by user {}: {}, dry run {}";

    // --- Refusals ---
    public static final String VERSION_OUTDATED = "Refused a change of page {} in organization {}: version {} sent, {} stored";
    public static final String SLUG_TAKEN = "Refused slug {} under parent {} in organization {}: taken";
    public static final String ROOT_SLUG_IN_TRASH = "Refused root slug {} in organization {}: held by deleted root {}";
    public static final String RESERVED_ROOT_SLUG = "Refused reserved root slug {} in organization {}";
    public static final String ROOT_SLUG_FIXED = "Refused a slug change of root page {} in organization {} from {} to {}";
    public static final String DUPLICATE_LOCALE = "Refused a write in organization {}: locale {} given twice";
    public static final String MOVE_INVALID = "Refused a move of page {} under parent {} in organization {}: {}";
    public static final String DEPTH_EXCEEDED = "Refused page {} under parent {} in organization {}: deeper than {} levels";
    public static final String PAGE_NOT_DELETED = "Refused the restore of page {} in organization {}: it is not deleted";
    public static final String PARENT_DELETED = "Refused the restore of page {} in organization {}: its parent is deleted";
    public static final String RESTORE_TOO_DEEP = "Refused the restore of page {} in organization {}: its pages would reach depth {}, deeper than {} levels";
    public static final String PARENT_PATH_MISSING = "Refused the upsert of path {} in organization {}: segment {} is missing";
    public static final String CHANGED_SINCE_PUSH = "Refused the upsert of path {} in organization {}: version {}, pushed version {}";
    public static final String ATTACHMENT_DELETE_REFUSED = "Refused the delete of an attachment of page {} by user {}: Kalkan attachments are kept";
}
