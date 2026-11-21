package com.korur.oba.dojo.security.token.dto;

// 1. Framework-level
import org.springframework.http.HttpMethod;

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * One handler a token kind may call although it declares no RequiredUserAccess. Method and path are
 * matched together, because one path can carry several handlers with different rules, such as GET and
 * DELETE on /users/me.
 *
 * @param method      the HTTP method
 * @param pathPattern a Spring path pattern, matched against the context-relative request path
 */
public record ApiTokenEndpoint(
        HttpMethod method,
        String pathPattern
) {
}
