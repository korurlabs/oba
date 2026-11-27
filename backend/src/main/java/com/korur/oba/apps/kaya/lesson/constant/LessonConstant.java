package com.korur.oba.apps.kaya.lesson.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Duration;

// 4. Internal

/**
 * Field names and limits of lessons. The limits match the checks of the lesson schema, so a
 * request the API accepts is never refused by the database.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LessonConstant {

    private LessonConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_LESSON_ID = "lessonId";
    public static final String FIELD_REMOTE_URL = "remoteUrl";
    public static final String FIELD_SINCE = "since";
    public static final String FIELD_LIMIT = "limit";
    // Names the limit that refused a request, beside its number. KAY_SEC_004 and its message are the
    // same for every lesson limit, the caps on reads at once of a user and of an organization share
    // the number 2, and a count at once is not a number per minute.
    public static final String FIELD_POLICY = "policy";
    public static final String POLICY_TOKEN_READS_PER_MINUTE = "token-reads-per-minute";
    public static final String POLICY_USER_READS_PER_MINUTE = "user-reads-per-minute";
    public static final String POLICY_ORGANIZATION_READS_PER_MINUTE = "organization-reads-per-minute";
    public static final String POLICY_TOKEN_LESSONS_PER_MINUTE = "token-lessons-per-minute";
    public static final String POLICY_USER_READS_AT_ONCE = "user-reads-at-once";
    public static final String POLICY_ORGANIZATION_READS_AT_ONCE = "organization-reads-at-once";
    public static final String POLICY_USER_LESSONS_PER_MINUTE = "user-lessons-per-minute";

    // --- Entity attributes read by the list filters ---
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_REPOSITORY_ID = "repositoryId";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_CLAUDE_SESSION_ID = "claudeSessionId";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_BODY = "body";

    // --- Sorting ---
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_ID = "id";

    // --- List paging and search ---
    public static final int LIST_DEFAULT_AMOUNT = 25;
    public static final int LIST_MAX_AMOUNT = 100;
    public static final String AMOUNT_MAX_MESSAGE = "amount cannot exceed 100";
    public static final int SEARCH_MAX_LENGTH = 200;
    public static final String SEARCH_SIZE_MESSAGE = "Search must be at most 200 characters";
    public static final String SESSION_ID_FILTER_MESSAGE = "sessionId must be a Claude Code session id";
    public static final String LIKE_WILDCARD = "%";
    public static final char LIKE_ESCAPE = '\\';
    public static final String LIKE_SPECIAL_CHARACTERS_PATTERN = "([\\\\%_])";
    public static final String LIKE_ESCAPED_REPLACEMENT = "\\\\$1";

    // --- Column limits ---
    public static final int TITLE_MIN_LENGTH = 1;
    public static final int TITLE_MAX_LENGTH = 300;
    public static final int BODY_MIN_LENGTH = 1;
    public static final int BODY_MAX_LENGTH = 4000;
    public static final int CLAUDE_SESSION_ID_MAX_LENGTH = 100;

    // --- Request validation messages ---
    public static final String TITLE_REQUIRED_MESSAGE = "title is required";
    public static final String TITLE_SIZE_MESSAGE = "title must be 1 to 300 characters";
    public static final String BODY_REQUIRED_MESSAGE = "body is required";
    public static final String BODY_SIZE_MESSAGE = "body must be 1 to 4000 characters";

    // --- Machine delivery ---
    // Caps the results of one search, so a broad search stays cheap however many lessons match.
    public static final int DELIVERY_ITEMS_MAX = 100;
    // One page of titles at session start. The default is the cap, so a CLI that sends no paging
    // still gets the newest 100 it always got.
    public static final int DELIVERY_DEFAULT_AMOUNT = 100;
    public static final int DELIVERY_MAX_AMOUNT = 100;
    // The page a request without page reads.
    public static final int DELIVERY_FIRST_PAGE = 1;
    // Reads of a token per minute, pages of the list (a 304 included) and searches together: each
    // makes PostgreSQL pass over every applicable lesson. One read of the CLI requests at most 100
    // pages and starts again at most twice, so at most 300 requests; the CLI reads at a session start
    // and for oba kaya lessons list, and 600 lets it make two such reads within one minute, or
    // hundreds of reads of a few pages. A token past it is a runaway or hostile client. An import
    // searches each title of its file once on every run, so a file of more than about 600 titles
    // meets this budget part way however often it runs.
    public static final int DELIVERY_READS_PER_MINUTE = 600;
    // Reads of all tokens of one organization per minute, charged beside the budget of each token.
    // A member can mint more machine tokens, and without it each would take 600 more reads a minute
    // from the connection pool every organization shares. A team of ten machines that each read the
    // list from scratch in the same minute, 100 pages and two restarts each, makes 3000 reads, so a
    // team at work never meets it; it is five tokens at their full budget. A read is charged here only
    // once the token's own budget allows it, so one runaway token cannot use up its colleagues' reads.
    public static final int DELIVERY_ORGANIZATION_READS_PER_MINUTE = 3_000;
    // Reads of all tokens of one user per minute, in every organization the user belongs to, charged
    // beside the budgets of the token and the organization. Any signed-in user can create organizations,
    // and each brings an organization budget of its own, so without it one person could multiply their
    // reads by creating organizations. A person works on a few machines: four that each read the list
    // from scratch in the same minute, 100 pages and two restarts each, make 1200, so a person at work
    // never meets it; it is two tokens at their full budget. A read is charged here only once the token
    // allows it, and to the organization only once the user does.
    public static final int DELIVERY_USER_READS_PER_MINUTE = 1_200;
    // Lessons a token adds per minute. A lesson is added by hand with oba kaya lessons add, or by oba
    // kaya lessons import, one after another for each title of a file its scope does not hold yet; real
    // lessons files hold tens. Every added lesson makes each later read of the organization longer, so
    // a runaway or hostile token is held to a tenth of its reads. An import of more than 60 new lessons
    // stops at the refusal; run again a minute later, it skips the titles already added and goes on.
    public static final int DELIVERY_PROPOSALS_PER_MINUTE = 60;
    // Lessons a member adds per minute in the web app, in all their organizations together. A person
    // writes a lesson in a form, a few a day; every added lesson makes each later read of the
    // organization longer, so a script that posts with the member's session is held to the budget of
    // a machine token.
    public static final int USER_LESSONS_PER_MINUTE = 60;
    public static final long DELIVERY_RATE_WINDOW_SECONDS = 60;
    // Windows of tokens or organizations idle for a while are dropped once a table holds this many, at
    // most once per window.
    public static final int DELIVERY_RATE_TABLE_PRUNE_SIZE = 10_000;
    // The time limit of one lesson read, a page of the list or a search of the CLI, or a page of the web
    // list or its filter options, counted from the start of its transaction. A healthy read takes milliseconds, even over
    // thousands of lessons; one that runs for seconds waits on a lock or on a database under strain.
    // Ending it frees its connection of the small pool every request shares, and the CLI at a session
    // start has stopped waiting by then anyway.
    public static final int DELIVERY_READ_TIMEOUT_SECONDS = 5;
    // The same limit for each statement of such a read, as PostgreSQL reads statement_timeout. The JDBC
    // limit Hibernate derives from the transaction timeout is whole seconds, rounded, so a statement that
    // starts with less than half a second left would get no limit at all; PostgreSQL enforces this one
    // itself, on every statement.
    public static final String DELIVERY_STATEMENT_TIMEOUT = DELIVERY_READ_TIMEOUT_SECONDS + "s";
    // Lesson reads that run at the same time on one backend instance, all organizations together: pages
    // of the list and searches of the CLI, and pages of the web list and its filter options. The budgets above bound the reads that start in a minute, not the
    // reads that hold a connection at once. A healthy read holds its connection for milliseconds, so
    // this cap only matters once reads slow down; it then keeps them to 4 of the 10 pooled connections
    // (spring.datasource.hikari.maximum-pool-size), and the web app, ingest and every other request
    // keep the other 6.
    public static final int DELIVERY_CONCURRENT_READS_MAX = 4;
    // Lesson reads of one organization that run at the same time on one instance. The CLI reads the
    // pages of the list one after another, so one checkout holds one read at a time, and two machines
    // of a team rarely read in the same milliseconds. Two lets that happen, and leaves the other half of
    // the instance's reads to the other organizations when the reads of one turn slow.
    public static final int DELIVERY_ORGANIZATION_CONCURRENT_READS_MAX = 2;
    // Lesson reads of one user that run at the same time on one instance, in all their organizations
    // together. A person reads from a few machines, each one page after another, so two at once is
    // already rare. Without it, one person with two organizations of their own would hold all four
    // places of the instance; with it, that takes two accounts.
    public static final int DELIVERY_USER_CONCURRENT_READS_MAX = 2;
    // The Retry-After of a read refused because too many run at once. A healthy read ends within
    // milliseconds, so a second later its place is most likely free again; a refusal by a budget
    // instead names the time until the budget's window ends.
    public static final Duration DELIVERY_BUSY_RETRY_AFTER = Duration.ofSeconds(1);
    public static final String FIELD_ETAG = "etag";
    // A CLI stores the ETag next to the file it wrote and sends it back in If-None-Match, so an ETag
    // must name exactly what that file holds; the form prefix says what one digest stands for. The
    // paged form names every applicable lesson, read page by page. The unpaged form names the answer
    // to a request without page, which holds only the newest 100: an ETag a CLI stored next to those
    // 100 then never answers 304 to a CLI that reads every page, which reads them all once. A new
    // computation of the digest takes new prefixes for both, so an ETag of an earlier computation
    // matches neither.
    public static final String ETAG_PAGED_FORM = "v2-";
    public static final String ETAG_UNPAGED_FORM = "v2u-";
    // A proxy that compresses the answer, as the one in front of the API does, marks the ETag weak by
    // putting W/ before the quotes. The digest still names the same lessons, so the weak form is
    // accepted and compared by its value.
    public static final String ETAG_WEAK_PREFIX = "W/";
    // The ETag of page 1: bare, or quoted as the ETag header carries it, weak or not. Only the paged
    // form names the set that later pages are checked against.
    public static final String ETAG_PATTERN = ETAG_PAGED_FORM + "[0-9a-f]{64}|(" + ETAG_WEAK_PREFIX + ")?\""
            + ETAG_PAGED_FORM + "[0-9a-f]{64}\"";
    public static final String ETAG_QUOTE = "\"";
    public static final String ETAG_INVALID_MESSAGE = "etag must be the ETag header of page 1";
    public static final String ETAG_REQUIRED_MESSAGE = "etag is required from page 2 on";
    public static final int REMOTE_URL_MAX_LENGTH = 500;
    public static final String REMOTE_URL_REQUIRED_MESSAGE = "remoteUrl is required";
    public static final String REMOTE_URL_SIZE_MESSAGE = "Remote URL must not exceed 500 characters";
    public static final String REMOTE_URL_UNKNOWN_MESSAGE = "The organization has no repository with this remote";
    public static final String SEARCH_REQUIRED_MESSAGE = "search is required";
    public static final String CLAUDE_SESSION_ID_INVALID_MESSAGE = "claudeSessionId must be a Claude Code session id";
    public static final String SINCE_INVALID_MESSAGE = "since must be an ISO-8601 instant";
    // The web app's page of a lesson, after the web app's origin.
    public static final String LESSON_PAGE_PATH = "/lessons/";
    public static final String URL_PATH_SEPARATOR = "/";
}
