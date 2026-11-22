package com.korur.oba.apps.kalkan.page.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Set;

// 4. Internal

/**
 * Field names, error value keys and tree limits of the page domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class KalkanPageConstant {

    private KalkanPageConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_PAGE_ID = "pageId";
    public static final String FIELD_REVISION_ID = "revisionId";
    public static final String FIELD_DELETED_PAGE_ID = "deletedPageId";
    public static final String FIELD_PARENT_ID = "parentId";
    public static final String FIELD_SLUG = "slug";
    public static final String FIELD_PATH = "path";
    public static final String FIELD_LOCALE = "locale";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_REASON = "reason";
    public static final String FIELD_MAX_DEPTH = "maxDepth";
    public static final String FIELD_UPDATED_BY_ID = "updatedById";
    public static final String FIELD_UPDATED_BY_NAME = "updatedByName";
    public static final String FIELD_UPDATED_AT = "updatedAt";

    // --- Move refusal reasons ---
    public static final String REASON_OWN_SUBTREE = "OWN_SUBTREE";
    public static final String REASON_TOO_DEEP = "TOO_DEEP";
    public static final String REASON_ROOT_UNDER_PAGE = "ROOT_UNDER_PAGE";
    public static final String REASON_PAGE_TO_TOP = "PAGE_TO_TOP";

    // --- Slugs and paths ---
    /**
     * A slug starts with a letter or digit and may hold dots, so control ids such as A.5.1 and
     * file-like names such as 05_KALKAN_DOCS stay as they are. The database checks the same pattern.
     */
    public static final String SLUG_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$";

    /**
     * A path is one to ten slugs joined by a slash, without a leading or trailing slash, so the
     * depth limit holds for every path a push names.
     */
    public static final String PATH_PATTERN =
            "^[A-Za-z0-9][A-Za-z0-9._-]{0,99}(/[A-Za-z0-9][A-Za-z0-9._-]{0,99}){0,9}$";
    public static final String PATH_SEPARATOR = "/";

    /**
     * Root slugs the web app's own routes use, so a root page named like one could never be opened.
     * Compared without regard to case.
     */
    public static final Set<String> RESERVED_ROOT_SLUGS = Set.of("api", "onboarding", "invitations");

    /**
     * The most levels a tree may have, a root counting as level one.
     */
    public static final int MAX_DEPTH = 10;

    // --- Recent pages ---
    /**
     * The fewest and the most recently changed pages one request may ask for, so the list stays a
     * quick read and its query a bounded one.
     */
    public static final int RECENT_MIN_LIMIT = 1;
    public static final int RECENT_MAX_LIMIT = 100;
    public static final String RECENT_DEFAULT_LIMIT = "20";

    // --- Display ---
    public static final String NAME_SEPARATOR = " ";
}
