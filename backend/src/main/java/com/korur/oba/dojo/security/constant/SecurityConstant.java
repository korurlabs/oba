package com.korur.oba.dojo.security.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal

public class SecurityConstant {

    /**
     * The realtime STOMP endpoint. Public at the HTTP layer because a browser cannot send an
     * Authorization header on the WebSocket handshake; the handshake is authenticated instead by a
     * one-time ticket in the query string, which the client gets from an authenticated REST call
     * and which the transport redeems before it accepts the socket.
     */
    public static final String REALTIME_ENDPOINT_PATH = "/ws";

    // Publicly accessible paths
    public static final String[] PUBLIC_PATHS = {
        "/actuator/health/**",
        "/v3/api-docs/**",
        "/swagger-ui**",
        "/swagger-ui/**",
        "/favicon.ico",
        "/invitations/preview",
        REALTIME_ENDPOINT_PATH
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
