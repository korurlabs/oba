package com.korur.oba.dojo.security.token.authentication;

// 1. Framework-level
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

/**
 * The authentication of a request made with an API token. It carries no credential, because the
 * plaintext token must not outlive the token filter, and no authority, because what the token may do is
 * decided per request by the RequiredUserAccess check against its user's membership. Controllers read
 * the principal of the kind through AuthenticationPrincipal.
 */
public class ApiTokenAuthentication extends AbstractAuthenticationToken {

    private final ApiTokenKind kind;
    private final transient ApiTokenPrincipal principal;

    public ApiTokenAuthentication(ApiTokenKind kind, ApiTokenPrincipal principal) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.kind = kind;
        this.principal = principal;
        setAuthenticated(true);
    }

    public ApiTokenKind getKind() {
        return kind;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public ApiTokenPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
