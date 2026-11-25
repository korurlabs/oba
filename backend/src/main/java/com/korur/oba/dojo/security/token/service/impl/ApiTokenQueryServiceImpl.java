package com.korur.oba.dojo.security.token.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.constant.ApiTokenServiceLogConstant;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.helper.ApiTokenHelper;
import com.korur.oba.dojo.security.token.repository.ApiTokenRepository;
import com.korur.oba.dojo.security.token.service.ApiTokenQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiTokenQueryServiceImpl implements ApiTokenQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ApiTokenQueryServiceImpl.class);

    private final ApiTokenRepository apiTokenRepository;
    private final ApiTokenHelper apiTokenHelper;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    public ApiToken authenticate(String token) {
        // Every refusal throws the same error, so a caller cannot tell an unknown token from a revoked
        // one; the log line keeps the reason for an operator.
        Optional<ApiTokenKind> claimedKind = ApiTokenKind.fromToken(token);
        if (claimedKind.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_UNKNOWN_KIND);
            throw invalidToken();
        }
        ApiTokenKind kind = claimedKind.get();
        if (!apiTokenHelper.hasTokenFormat(token, kind)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_MALFORMED, kind);
            throw invalidToken();
        }
        Optional<ApiToken> found = apiTokenRepository.findByTokenHash(apiTokenHelper.hash(token));
        if (found.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_UNKNOWN, kind);
            throw invalidToken();
        }
        ApiToken apiToken = found.get();
        if (apiToken.getKind() != kind) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_KIND_MISMATCH,
                    apiToken.getId(), apiToken.getKind(), kind);
            throw invalidToken();
        }
        if (apiToken.getRevokedAt() != null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_REVOKED_REFUSED,
                    kind, apiToken.getId(), apiToken.getRevokedAt());
            throw invalidToken();
        }
        if (!apiToken.getExpiresAt().isAfter(Instant.now(clock))) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_EXPIRED,
                    kind, apiToken.getId(), apiToken.getExpiresAt());
            throw invalidToken();
        }
        return apiToken;
    }

    @Override
    public List<ApiToken> findLive(ApiTokenKind kind, Collection<UUID> subjectIds) {
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        return apiTokenRepository.findByKindAndSubjectIdInAndRevokedAtIsNull(kind, subjectIds);
    }

    private UnauthorizedException invalidToken() {
        return new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
    }
}
