package com.korur.oba.apps.kaya.machine.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;

/**
 * The caller of a machine token path: a live token of an ACTIVE machine. Ingest attributes every
 * session of the batch to the token's user and machine, whatever machine name the batch reports.
 *
 * @param organizationId the organization of the machine
 * @param userId         the user of the machine and its token
 * @param machineId        the machine
 * @param tokenId        the token that authenticated the request
 * @param machineName    the machine name of the machine
 */
public record MachinePrincipal(
        UUID organizationId,
        UUID userId,
        UUID machineId,
        UUID tokenId,
        String machineName
) implements ApiTokenPrincipal {
}
