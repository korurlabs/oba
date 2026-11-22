package com.korur.oba.apps.kaya.repository.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits, patterns and validation messages of the repository domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RepositoryConstant {

    private RepositoryConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_REPOSITORY_ID = "repositoryId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_REMOTE_URL = "remoteUrl";

    // --- Repository ---
    public static final int NAME_MAX_LENGTH = 100;
    public static final int REMOTE_URL_MAX_LENGTH = 500;
    public static final int TICKET_URL_TEMPLATE_MAX_LENGTH = 500;
    public static final String TICKET_KEY_PLACEHOLDER = "{key}";
    // An https URL without whitespace that holds {key}, or an empty string, which removes the
    // template. Only https is accepted, because the session view renders the result as a link and
    // a javascript: or data: URL there would run in the viewer's browser.
    public static final String TICKET_URL_TEMPLATE_PATTERN = "^$|^https://\\S*\\{key\\}\\S*$";
    public static final String NAME_BLANK_MESSAGE = "Name must not be blank";
    public static final String NAME_SIZE_MESSAGE = "Name must not exceed 100 characters";
    public static final String TICKET_URL_TEMPLATE_SIZE_MESSAGE = "Ticket URL template must not exceed 500 characters";
    public static final String TICKET_URL_TEMPLATE_FORMAT_MESSAGE =
            "Ticket URL template must be an https URL containing {key}, or empty to remove it";

    // --- Remote URL normalisation ---
    // A URL remote (https, http, ssh or git scheme), optionally with credentials and a port,
    // which are dropped: group 1 is the host, group 2 the path. A query or fragment is accepted only
    // after a path. The host may itself contain ? and #, so a query allowed straight after the host
    // would let the matcher try every split between the two, and a long crafted remote would then
    // take quadratic time to reject. A remote without a path is rejected anyway, so no remote that
    // was accepted before is lost.
    public static final String REMOTE_URL_FORM_PATTERN =
            "^(?i)(?:https?|ssh|git)://(?:[^@/\\s]*@)?([^/:@\\s]+)(?::\\d+)?(?:(/[^?#\\s]*)(?:[?#]\\S*)?)?$";
    // An scp-like SSH remote such as git@github.com:owner/name.git: group 1 is the host, group 2 the path.
    public static final String REMOTE_SCP_FORM_PATTERN = "^(?:[^@/\\s]+@)?([^:/@\\s]+):(?!//)(\\S+)$";
    public static final String SCHEME_SEPARATOR = "://";
    public static final String REMOTE_URL_INVALID_MESSAGE =
            "Remote URL must be a git remote such as https://github.com/owner/name or git@github.com:owner/name.git";
    public static final String GIT_SUFFIX = ".git";
    public static final String PATH_SEPARATOR = "/";
    public static final char PATH_SEPARATOR_CHAR = '/';
    public static final String PARENT_SEGMENT = "..";
    public static final String CURRENT_SEGMENT = ".";
    public static final int REMOTE_URL_MIN_PATH_SEGMENTS = 2;
}
