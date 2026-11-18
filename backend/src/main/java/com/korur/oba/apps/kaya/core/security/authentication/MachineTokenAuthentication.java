package com.korur.oba.apps.kaya.core.security.authentication;

// 1. Framework-level
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * The authentication of a request on the machine token paths. It carries no credential, because the
 * plaintext token must not outlive the request filter, and no authority, because a machine token
 * grants exactly the machine token paths and nothing else. Controllers read the MachinePrincipal
 * through AuthenticationPrincipal.
 */
public class MachineTokenAuthentication extends AbstractAuthenticationToken {

    private final transient MachinePrincipal principal;

    public MachineTokenAuthentication(MachinePrincipal principal) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public MachinePrincipal getPrincipal() {
        return principal;
    }
}
