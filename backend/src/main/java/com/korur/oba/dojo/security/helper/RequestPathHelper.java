package com.korur.oba.dojo.security.helper;

// 1. Framework-level
import org.springframework.web.util.UrlPathHelper;

// 2. Third-party
import jakarta.servlet.http.HttpServletRequest;

// 3. Java SDK

// 4. Internal

/**
 * The one request path every security decision matches its path patterns against: decoded and without
 * the context path, the same path the security chains' matchers and the MVC routing take.
 *
 * The raw request URI keeps percent-encoded letters, so a check that matched it would see
 * /kaya/%69ngest/batches where the routing serves /kaya/ingest/batches, and a pattern list would either
 * refuse a legitimate call or let an encoded path skip a rule. Every chain, filter and interceptor
 * therefore takes its path from here.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RequestPathHelper {

    private RequestPathHelper() {
    }

    /**
     * Returns the decoded, context-relative path of a request.
     *
     * @param request the request
     * @return the path within the application, decoded
     */
    public static String pathWithinApplication(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
}
