package com.korur.oba.dojo.security.token.handler;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.constant.SecurityLogConstant;
import com.korur.oba.dojo.security.token.dto.ApiTokenBodyLimit;
import com.korur.oba.dojo.security.token.dto.ApiTokenEndpoint;
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;
import com.korur.oba.dojo.security.token.dto.ApiTokenRefusedPaths;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

/**
 * The Kaya machine token: the bearer credential of the recorder and the CLI on one machine, acting as
 * the machine's user in the machine's organization.
 *
 * Its paths, {@link SecurityConstant#KAYA_MACHINE_TOKEN_PATHS}, take no other credential, and the token
 * opens nothing else. Delivery handlers declare their role, so the request access check applies the
 * user's membership and Kaya grant to every call. Ingest stays open to every live token of an ACTIVE
 * machine ({@link SecurityConstant#KAYA_MACHINE_TOKEN_EXEMPT_ENDPOINTS}): it records sessions and serves
 * no config, and refuses a disabled or deleted organization itself, under the machine lock. The body
 * cap bounds what a caller may make the server buffer before any controller runs.
 *
 * The principal is the {@link com.korur.oba.apps.kaya.machine.dto.MachinePrincipal} the Kaya
 * controllers read, built by {@link MachineQueryService#findActivePrincipal}, so a token of a machine
 * that was retired after the token was issued is refused.
 */
@Component
@RequiredArgsConstructor
public class KayaMachineTokenKindHandler implements ApiTokenKindHandler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(KayaMachineTokenKindHandler.class);

    private static final ApiTokenBodyLimit BODY_LIMIT = new ApiTokenBodyLimit(
            SecurityConstant.KAYA_MACHINE_TOKEN_MAX_BODY_BYTES, ExceptionDomainKayaError.VAL_BODY_TOO_LARGE);

    private final MachineQueryService machineQueryService;
    private final LoggingService loggingService;

    @Override
    public ApiTokenKind kind() {
        return ApiTokenKind.KAYA_MACHINE;
    }

    @Override
    public List<String> reservedPaths() {
        return SecurityConstant.KAYA_MACHINE_TOKEN_PATHS;
    }

    @Override
    public Optional<ApiTokenRefusedPaths> refusedPaths() {
        return Optional.empty();
    }

    @Override
    public List<ApiTokenEndpoint> exemptEndpoints() {
        return SecurityConstant.KAYA_MACHINE_TOKEN_EXEMPT_ENDPOINTS;
    }

    @Override
    public Optional<ApiTokenBodyLimit> bodyLimit() {
        return Optional.of(BODY_LIMIT);
    }

    /**
     * @throws UnauthorizedException KAY_SEC_003 when the token's organization has no such machine, the
     *                               machine is not ACTIVE, or its user is an agent that, or whose
     *                               membership, is not ACTIVE
     */
    @Override
    public ApiTokenPrincipal resolvePrincipal(ApiToken token) {
        return machineQueryService.findActivePrincipal(token.getOrganizationId(), token.getSubjectId(), token.getId())
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, SecurityLogConstant.MACHINE_NOT_ACTIVE,
                            token.getId(), token.getSubjectId(), token.getOrganizationId());
                    return new UnauthorizedException(ExceptionDomainKayaError.SEC_MACHINE_TOKEN_INVALID);
                });
    }

    @Override
    public ExceptionDomainError invalidTokenError() {
        return ExceptionDomainKayaError.SEC_MACHINE_TOKEN_INVALID;
    }
}
