package com.korur.oba.dojo.security.token.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The caller of a request authenticated with an API token. Each kind returns its own principal, which
 * may carry more, such as the machine of a Kaya token; the request access check reads only these three.
 */
public interface ApiTokenPrincipal {

    /**
     * @return the token that authenticated the request
     */
    UUID tokenId();

    /**
     * @return the user the request acts as, whose membership and app grants the access check applies
     */
    UUID userId();

    /**
     * @return the only organization the token may act in
     */
    UUID organizationId();
}
