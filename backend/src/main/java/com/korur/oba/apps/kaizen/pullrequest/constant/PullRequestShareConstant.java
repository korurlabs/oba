package com.korur.oba.apps.kaizen.pullrequest.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Map;
import java.util.Set;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.enums.PullRequestShareStatus;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Field names, limits and summary text constants for the pull request share domain.
 */
public final class PullRequestShareConstant {

    private PullRequestShareConstant() {
        // Private constructor to prevent instantiation
    }

    // ── Field names ─────────────────────────────────────────────────────────

    /**
     * Field name constant for the share ID.
     */
    public static final String FIELD_SHARE_ID = "shareId";

    /**
     * Field name constant for the organization ID.
     */
    public static final String FIELD_ORGANIZATION_ID = "organizationId";

    /**
     * Field name constant for the user ID.
     */
    public static final String FIELD_USER_ID = "userId";

    /**
     * Field name constant for GitHub's repository ID.
     */
    public static final String FIELD_GITHUB_REPOSITORY_ID = "githubRepositoryId";

    /**
     * Field name constant for the pull request number.
     */
    public static final String FIELD_NUMBER = "number";

    /**
     * Field name constant for the note.
     */
    public static final String FIELD_NOTE = "note";

    /**
     * Field name constant for the reviewer IDs of a request.
     */
    public static final String FIELD_REVIEWER_IDS = "reviewerIds";

    /**
     * Field name constant for the related ticket IDs of a request.
     */
    public static final String FIELD_RELATED_TICKET_IDS = "relatedTicketIds";

    /**
     * Field name constant for the ticket ID of a related ticket.
     */
    public static final String FIELD_TICKET_ID = "ticketId";

    /**
     * Field name constant for the picker search text.
     */
    public static final String FIELD_SEARCH = "search";

    /**
     * Field name constant for the picker's author logins.
     */
    public static final String FIELD_AUTHORS = "authors";

    /**
     * Field name constant for the picker page.
     */
    public static final String FIELD_PAGE = "page";

    /**
     * Field name constant for the creation time, the sort key of the share list.
     */
    public static final String FIELD_CREATED_AT = "createdAt";

    // ── Limits ──────────────────────────────────────────────────────────────

    /**
     * Longest note of a share, in characters; the same as a daily update note.
     */
    public static final int MAX_NOTE_LENGTH = 500;

    /**
     * Most reviewers one share may name.
     */
    public static final int MAX_REVIEWERS = 10;

    /**
     * Most related tickets one share may name.
     */
    public static final int MAX_RELATED_TICKETS = 10;

    /**
     * Longest picker search text, in characters.
     */
    public static final int MAX_SEARCH_LENGTH = 100;

    /**
     * Most authors one picker query may name; with the states it stays within
     * the five operators GitHub's search allows.
     */
    public static final int MAX_AUTHOR_FILTERS = 5;

    /**
     * First picker page; pages count from 1, as on GitHub.
     */
    public static final int MIN_PAGE = 1;

    /**
     * Longest stored title, in characters; a longer GitHub title is cut.
     */
    public static final int MAX_TITLE_LENGTH = 500;

    /**
     * Longest stored GitHub login, in characters.
     */
    public static final int MAX_LOGIN_LENGTH = 100;

    /**
     * Longest stored branch name, in characters; a longer one is cut.
     */
    public static final int MAX_REF_LENGTH = 255;

    /**
     * Organization roles a reviewer may hold. GUEST is left out because guests
     * take no part in the team's reviews.
     */
    public static final Set<OrganizationMemberRole> REVIEWER_ROLES = Set.of(
            OrganizationMemberRole.OWNER,
            OrganizationMemberRole.ADMIN,
            OrganizationMemberRole.MEMBER);

    // ── Summary text ────────────────────────────────────────────────────────

    /**
     * Separator between the ticket key and the title on the first line.
     */
    public static final String SUMMARY_KEY_SEPARATOR = ": ";

    /**
     * Prefix of the status line.
     */
    public static final String SUMMARY_STATUS_PREFIX = "Status: ";

    /**
     * Separator between the status label and the note on the status line.
     */
    public static final String SUMMARY_NOTE_SEPARATOR = " ";

    /**
     * Line break between the lines of the summary; a plain newline, so the text
     * pastes the same on every platform.
     */
    public static final String SUMMARY_LINE_BREAK = "\n";

    /**
     * Label of each status, as the team writes it in the status line.
     */
    public static final Map<PullRequestShareStatus, String> STATUS_LABELS = Map.of(
            PullRequestShareStatus.DRAFT, "Draft",
            PullRequestShareStatus.OPEN, "Open",
            PullRequestShareStatus.IN_REVIEW, "In Review",
            PullRequestShareStatus.MERGED, "Merged",
            PullRequestShareStatus.DISCUSS, "Discuss");
}
