package com.korur.oba.connect.github.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Duration;
import java.util.regex.Pattern;

// 4. Internal

/**
 * GitHub REST API addresses, headers, limits and token lifetimes of the GitHub App.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class GithubConstant {

    private GithubConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String PROPERTIES_PREFIX = "oba.integrations.github";

    // ── Addresses ───────────────────────────────────────────────────────────
    public static final String API_BASE_URL = "https://api.github.com";
    // A shared pull request links here; the link is built from the repository name and number
    // so a payload can never point a card somewhere else.
    public static final String WEB_BASE_URL = "https://github.com/";
    public static final String PULL_REQUEST_URL_SEGMENT = "/pull/";
    public static final String FULL_NAME_SEPARATOR = "/";

    public static final String PATH_ACCESS_TOKENS = "/app/installations/{installationId}/access_tokens";
    public static final String PATH_INSTALLATION_REPOSITORIES = "/installation/repositories";
    public static final String PATH_PULL_REQUESTS = "/repos/{owner}/{repo}/pulls";
    public static final String PATH_PULL_REQUEST = "/repos/{owner}/{repo}/pulls/{number}";
    public static final String PATH_SEARCH_ISSUES = "/search/issues";
    public static final String PATH_CONTRIBUTORS = "/repos/{owner}/{repo}/contributors";

    // ── Headers ─────────────────────────────────────────────────────────────
    public static final String MEDIA_TYPE_GITHUB_JSON = "application/vnd.github+json";
    public static final String HEADER_API_VERSION = "X-GitHub-Api-Version";
    public static final String API_VERSION = "2022-11-28";
    public static final String HEADER_RATE_LIMIT_REMAINING = "x-ratelimit-remaining";
    public static final String RATE_LIMIT_EXHAUSTED = "0";
    public static final String HEADER_RETRY_AFTER = "retry-after";
    // GitHub's secondary rate limit does not always carry the headers above; its message says so.
    public static final String RATE_LIMIT_MESSAGE_MARKER = "rate limit";

    // ── Query parameters ────────────────────────────────────────────────────
    public static final String PARAM_PER_PAGE = "per_page";
    public static final String PARAM_PAGE = "page";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_SORT = "sort";
    public static final String PARAM_DIRECTION = "direction";
    public static final String PARAM_ORDER = "order";
    public static final String PARAM_QUERY = "q";
    // Classic search answers a query with OR with zero rows and no error, so a query that holds
    // OR needs this switch; every other query stays a classic one.
    public static final String PARAM_ADVANCED_SEARCH = "advanced_search";
    public static final String ADVANCED_SEARCH_ENABLED = "true";
    // The search query is a template variable, so its value is encoded as a whole.
    public static final String QUERY_TEMPLATE_VARIABLE = "{query}";
    public static final String STATE_ALL = "all";
    public static final String SORT_UPDATED = "updated";
    public static final String ORDER_DESC = "desc";

    // GitHub's own value of an open pull request; drafts are open too.
    public static final String STATE_OPEN = "open";
    // GitHub's own value of a closed pull request; merged ones are closed too.
    public static final String STATE_CLOSED = "closed";

    // ── Paging ──────────────────────────────────────────────────────────────
    public static final int PULL_REQUESTS_PER_PAGE = 30;
    public static final int REPOSITORIES_PER_PAGE = 100;
    // Bounds the calls one repository list costs; an installation of more than 300
    // repositories is not expected for one organization.
    public static final int REPOSITORIES_MAX_PAGES = 3;
    // One call, most commits first: the typed login covers whoever falls outside the list.
    public static final int CONTRIBUTORS_PER_PAGE = 100;
    // GitHub's search serves only the first 1000 hits, whatever total_count says.
    public static final int SEARCH_MAX_RESULTS = 1000;
    public static final int FIRST_PAGE = 1;

    // ── Search ──────────────────────────────────────────────────────────────
    public static final String SEARCH_QUALIFIER_REPOSITORY = "repo:";
    public static final String SEARCH_QUALIFIER_PULL_REQUEST = "is:pr";
    public static final String SEARCH_QUALIFIER_AUTHOR = "author:";
    // Search knows an App's bot account as "app/{name}", not by its "{name}[bot]" login.
    public static final String SEARCH_QUALIFIER_APP_AUTHOR = "author:app/";
    public static final String BOT_LOGIN_SUFFIX = "[bot]";
    public static final String SEARCH_TERM_SEPARATOR = " ";
    public static final String SEARCH_OPERATOR_OR = " OR ";
    public static final String SEARCH_GROUP_START = "(";
    public static final String SEARCH_GROUP_END = ")";
    public static final String SEARCH_STATE_OPEN_ANY = "is:open";
    public static final String SEARCH_STATE_OPEN_READY = "is:open draft:false";
    public static final String SEARCH_STATE_OPEN_DRAFT = "is:open draft:true";
    public static final String SEARCH_STATE_CLOSED_ANY = "is:closed";
    public static final String SEARCH_STATE_MERGED = "is:merged";
    public static final String SEARCH_STATE_CLOSED_UNMERGED = "is:closed is:unmerged";
    // ":" turns a word into a qualifier, so a member's text could otherwise widen the search
    // beyond the repository (for example "repo:other/name").
    public static final Pattern SEARCH_QUALIFIER_CHARACTERS = Pattern.compile(":");
    // Advanced search reads these as logic, so a member's text could otherwise regroup the filters.
    public static final Pattern ADVANCED_SEARCH_GROUPING_CHARACTERS = Pattern.compile("[()\"]");
    public static final Pattern ADVANCED_SEARCH_OPERATOR_WORDS =
            Pattern.compile("(?<!\\S)(?:AND|OR|NOT)(?!\\S)", Pattern.CASE_INSENSITIVE);
    public static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // "#379" or "379" reads that pull request directly instead of searching text.
    public static final Pattern PULL_REQUEST_NUMBER_SEARCH = Pattern.compile("^#?(\\d{1,9})$");
    // A login reaches a search query as a qualifier value, so nothing but GitHub's own login
    // alphabet passes, plus the suffix of a bot account.
    public static final String LOGIN_REGEX = "^[A-Za-z0-9][A-Za-z0-9-]{0,38}(\\[bot\\])?$";
    public static final Pattern LOGIN_PATTERN = Pattern.compile(LOGIN_REGEX);

    // ── Timeouts and caches ─────────────────────────────────────────────────
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration REPOSITORY_CACHE_TTL = Duration.ofMinutes(5);
    // GitHub's own contributor list can already lag by hours, so a shorter time buys no fresher data.
    public static final Duration CONTRIBUTOR_CACHE_TTL = Duration.ofHours(1);

    // ── App JWT and installation token ──────────────────────────────────────
    // Backdated so a GitHub clock slightly behind ours still accepts the JWT.
    public static final Duration APP_JWT_BACKDATE = Duration.ofSeconds(60);
    // GitHub refuses an App JWT that lives longer than 10 minutes.
    public static final Duration APP_JWT_LIFETIME = Duration.ofMinutes(9);
    // An installation token lives one hour; it is replaced this long before it expires so no
    // request starts with a token that runs out on the way.
    public static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(5);

    public static final String BODY_PERMISSIONS = "permissions";
    public static final String PERMISSION_PULL_REQUESTS = "pull_requests";
    public static final String PERMISSION_METADATA = "metadata";
    public static final String PERMISSION_READ = "read";

    public static final String PEM_HEADER_MARKER = "-----BEGIN";
    public static final String PEM_PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    public static final String PEM_PKCS8_FOOTER = "-----END PRIVATE KEY-----";
    public static final String KEY_ALGORITHM_RSA = "RSA";
}
