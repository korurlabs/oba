package com.korur.oba.dojo.security.constant;

// 1. Framework-level
import org.springframework.http.HttpMethod;

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.security.token.dto.ApiTokenEndpoint;

public class SecurityConstant {

    /**
     * The realtime STOMP endpoint. Public at the HTTP layer because a browser cannot send an
     * Authorization header on the WebSocket handshake; the handshake is authenticated instead by a
     * one-time ticket in the query string, which the client gets from an authenticated REST call
     * and which the transport redeems before it accepts the socket.
     */
    public static final String REALTIME_ENDPOINT_PATH = "/ws";

    /**
     * The OpenID Connect discovery document and key set of the issuer that signs cloud connection
     * assertions. Public because a cloud provider fetches them without credentials; they hold only
     * the issuer URL and public keys. The issuer URL is the public origin of the API root, so both
     * sit at its /.well-known.
     */
    public static final String OIDC_DISCOVERY_PATH = "/.well-known/openid-configuration";
    public static final String OIDC_JWKS_PATH = "/.well-known/jwks.json";

    // Publicly accessible paths
    public static final String[] PUBLIC_PATHS = {
        "/actuator/health/**",
        "/v3/api-docs/**",
        "/swagger-ui**",
        "/swagger-ui/**",
        "/favicon.ico",
        "/invitations/preview",
        REALTIME_ENDPOINT_PATH,
        OIDC_DISCOVERY_PATH,
        OIDC_JWKS_PATH
    };

    // Paths that need a login and an active account but no organization, so their methods carry no
    // RequiredUserAccess; the service checks any rule about the resource, such as board membership.
    // Matched as Spring path patterns against the context-relative request path.
    public static final String[] ORGANIZATION_EXEMPT_PATHS = {
        // Colleagues across every organization of the caller
        "/users",
        "/users/me/**",
        "/onboarding/**",
        // Creating an organization and listing the caller's own organizations
        "/organizations",
        // An OWNER enables a disabled organization, which the organization check would refuse;
        // the service checks the ownership
        "/organizations/*/status",
        // A phone receives the pushes of every organization of its user; the service scopes every
        // device and preference to the caller
        "/push/**",
        // An author's daily updates span organizations; the service checks any organization it is given
        "/kazan/updates/**"
    };

    // --- Alertmanager webhook chain ---
    // The webhook runs on its own chain, authenticated by a static bearer token of one organization, not a
    // JWT. The path lives under /kazan, the app whose boards the alerts become incident tickets on.
    public static final String ALERTMANAGER_WEBHOOK_PATH_PATTERN = "/kazan/integrations/alertmanager/**";
    // Granted only to a caller with the configured token, and required by the webhook chain
    public static final String AUTHORITY_INTEGRATION_ALERTMANAGER = "INTEGRATION_ALERTMANAGER";

    // Paths whose own security chain authenticates the caller with a credential that is neither a JWT
    // nor an API token, and checks what it may do. Only there does the RequiredUserAccess check let such
    // an authentication through; anywhere else it is refused.
    public static final String[] INTEGRATION_CHAIN_PATHS = {
        ALERTMANAGER_WEBHOOK_PATH_PATTERN
    };

    // --- API token chain ---
    public static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";

    // --- Kaya machine tokens ---
    // Paths the recorder calls with a machine token to record sessions; the only machine paths open to a
    // token whose user has no Kaya app
    public static final String KAYA_MACHINE_TOKEN_INGEST_PATH = "/kaya/ingest/**";
    // Paths the CLI calls with a machine token to read and change agent config; each needs the Kaya app
    public static final String KAYA_MACHINE_TOKEN_DELIVERY_PATH = "/kaya/delivery/**";
    // Paths only a machine token may call; any other credential sent there is refused
    public static final List<String> KAYA_MACHINE_TOKEN_PATHS = List.of(
            KAYA_MACHINE_TOKEN_INGEST_PATH,
            KAYA_MACHINE_TOKEN_DELIVERY_PATH);
    // Ingest records sessions and serves no config, so it stays open to every live token of an ACTIVE
    // machine without the membership and app checks
    public static final List<ApiTokenEndpoint> KAYA_MACHINE_TOKEN_EXEMPT_ENDPOINTS = List.of(
            new ApiTokenEndpoint(HttpMethod.POST, KAYA_MACHINE_TOKEN_INGEST_PATH));
    // A batch holds at most 500 events and 50 handoffs, and a delivery proposal may carry one file of
    // 5 MiB, which is about 7 MB as base64; 8 MiB bounds what one request may make the server buffer
    // before the token's user is even known to be within the rate limit.
    public static final int KAYA_MACHINE_TOKEN_MAX_BODY_BYTES = 8 * 1024 * 1024;

    // --- Agent tokens ---
    // An agent may call every path its membership role and app grants allow, so it reserves none. The
    // only handler without RequiredUserAccess it may call is reading its own user; changing or deleting
    // that user stays a person's action.
    public static final String AGENT_TOKEN_CURRENT_USER_PATH = "/users/me";
    public static final List<ApiTokenEndpoint> AGENT_TOKEN_EXEMPT_ENDPOINTS = List.of(
            new ApiTokenEndpoint(HttpMethod.GET, AGENT_TOKEN_CURRENT_USER_PATH));

    // Handlers of this package are guarded by the RequiredUserAccess check; framework handlers such
    // as SpringDoc and the error page are not
    public static final String OBA_PACKAGE_PREFIX = "com.korur.oba.";

    // The path variable that names the organization of a request
    public static final String PATH_VARIABLE_ORGANIZATION_ID = "organizationId";

    // --- Request attributes set once the RequiredUserAccess check passes, so one request loads its
    // user and membership once ---
    public static final String REQUEST_ATTRIBUTE_CURRENT_USER = "oba.currentUser";
    public static final String REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER = "oba.organizationMember";

    // --- Sensey Security Logging Tags ---
    public static final String LOG_PREFIX = "[SenseySecurity]";
    public static final String LOG_TYPE_REQUEST = LOG_PREFIX + "[Request]";
    public static final String LOG_TYPE_CHAIN_MATCH = LOG_PREFIX + "[ChainMatch]";
    public static final String LOG_TYPE_ACCESS_DECISION = LOG_PREFIX + "[AccessDecision]";
    public static final String LOG_TYPE_JWT_VALIDATION = LOG_PREFIX + "[JwtValidation]";

    // --- Profiles ---
    public static final String PROFILE_PROD = "prod";

    // --- CORS origins of the deployed frontends, allowed in every environment ---
    // Also the origins the realtime handshake accepts, so HTTP and WebSocket clients are trusted alike.
    public static final List<String> CORS_ORIGIN_PATTERNS = List.of("https://*.sensey.io", "https://*.korur.com");

    // --- CORS request headers beyond the defaults ---
    // A client that reconnects to a server-sent event stream, such as the Kaya session stream, may
    // send the id of the last event it received; the preflight must allow it or the reconnect fails.
    public static final String HEADER_LAST_EVENT_ID = "Last-Event-ID";

    // --- CORS origins of a developer's machine, off unless switched on ---
    public static final String CORS_ALLOW_LOCAL_ORIGINS_PROPERTY = "sensey.cors.allow-local-origins";
    public static final List<String> CORS_LOCAL_ORIGIN_PATTERNS = List.of(
            "http://localhost:*",
            "http://*.localhost:*");
    public static final String CORS_LOCAL_ORIGINS_IN_PROD =
            CORS_ALLOW_LOCAL_ORIGINS_PROPERTY + " is true while the " + PROFILE_PROD
                    + " profile is active; production must never allow localhost origins";
}
