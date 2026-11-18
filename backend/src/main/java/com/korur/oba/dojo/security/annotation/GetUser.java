package com.korur.oba.dojo.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the currently authenticated User into controller method parameters.
 *
 * <p>This annotation extracts the Keycloak user ID from the JWT token in the
 * SecurityContext and fetches the corresponding User entity from the database.</p>
 *
 * <p>The User object is cached at the request scope, so multiple {@code @GetUser}
 * annotations within the same request will only trigger one database query.</p>
 *
 * <p><b>Usage examples:</b></p>
 * <pre>
 * // Required user (throws UnauthorizedException if not authenticated)
 * public ResponseEntity&lt;?&gt; myEndpoint(@GetUser User user) { ... }
 *
 * // Optional user (returns null if not authenticated)
 * public ResponseEntity&lt;?&gt; publicEndpoint(@GetUser(optional = true) User user) { ... }
 *
 * // Require active status (throws exception if user status is not ACTIVE)
 * public ResponseEntity&lt;?&gt; adminEndpoint(@GetUser(requireActive = true) User user) { ... }
 *
 * // Hidden from Swagger documentation
 * public ResponseEntity&lt;?&gt; endpoint(@Parameter(hidden = true) @GetUser User user) { ... }
 * </pre>
 *
 * @see com.korur.oba.dojo.security.resolver.GetUserArgumentResolver
 * @since 1.0
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GetUser {

    /**
     * Whether the user is optional.
     *
     * <p>When {@code true}, the resolver will return {@code null} if no authentication
     * is present, instead of throwing an {@link com.korur.oba.dojo.exception.UnauthorizedException}.</p>
     *
     * <p>This is useful for public endpoints that behave differently for authenticated
     * vs anonymous users.</p>
     *
     * @return {@code true} if the user is optional, {@code false} otherwise
     */
    boolean optional() default false;

    /**
     * Whether to require the user's status to be ACTIVE.
     *
     * <p>When {@code true}, the resolver will throw a
     * {@link com.korur.oba.dojo.exception.ForbiddenException} if the user's
     * status is not {@link com.korur.oba.shrine.identity.constant.UserStatus#ACTIVE}.</p>
     *
     * <p>When {@code false}, users with any status (PENDING_ONBOARDING, ACTIVE, DISABLED)
     * will be allowed.</p>
     *
     * @return {@code true} if the user must be active, {@code false} otherwise
     */
    boolean requireActive() default true;
}
