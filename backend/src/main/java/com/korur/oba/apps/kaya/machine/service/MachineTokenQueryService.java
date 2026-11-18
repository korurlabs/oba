package com.korur.oba.apps.kaya.machine.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * Authenticates the recorder and the CLI on the machine token paths.
 */
public interface MachineTokenQueryService {

    /**
     * Resolves a presented recorder token. The token is hashed and looked up by its hash, so the
     * plaintext is never compared or stored.
     *
     * @param token the plaintext token from the Authorization header, without "Bearer "
     * @return the machine and user the token stands for
     * @throws com.korur.oba.dojo.exception.internal.auth.UnauthorizedException KAY_SEC_003 when the
     *         token is missing, malformed, unknown, revoked or expired, or its machine is RETIRED
     */
    MachinePrincipal authenticate(String token);
}
