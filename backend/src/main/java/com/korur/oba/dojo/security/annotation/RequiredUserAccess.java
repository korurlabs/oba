package com.korur.oba.dojo.security.annotation;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Declares the minimum organization role a controller method needs.
 *
 * RequiredUserAccessInterceptor enforces it before the controller method runs. The request must
 * name its organization in the organizationId path variable or the X-Organization-ID header, the
 * caller must be a provisioned ACTIVE user holding an ACTIVE membership of an organization that is
 * not disabled, and the membership role must be at least the given one.
 *
 * Every controller method on the user chain carries this annotation unless its path is listed in
 * {@link com.korur.oba.dojo.security.constant.SecurityConstant#PUBLIC_PATHS} or
 * {@link com.korur.oba.dojo.security.constant.SecurityConstant#ORGANIZATION_EXEMPT_PATHS}; any
 * other method without it is refused with 403.
 *
 * Usage:
 *
 *   @GetMapping
 *   @RequiredUserAccess(OrganizationMemberRole.MEMBER)
 *   public ResponseEntity list(
 *           @Parameter(hidden = true) @GetUser User user,
 *           @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) { ... }
 *
 * @see com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiredUserAccess {

    OrganizationMemberRole value();
}
