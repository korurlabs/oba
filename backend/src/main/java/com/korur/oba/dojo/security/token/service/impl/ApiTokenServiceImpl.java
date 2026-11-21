package com.korur.oba.dojo.security.token.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.constant.ApiTokenConstant;
import com.korur.oba.dojo.security.token.constant.ApiTokenServiceLogConstant;
import com.korur.oba.dojo.security.token.dto.IssuedApiToken;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.helper.ApiTokenHelper;
import com.korur.oba.dojo.security.token.repository.ApiTokenRepository;
import com.korur.oba.dojo.security.token.service.ApiTokenService;
import com.korur.oba.shrine.identity.entity.User;

@Service
@RequiredArgsConstructor
public class ApiTokenServiceImpl implements ApiTokenService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ApiTokenServiceImpl.class);

    private final ApiTokenRepository apiTokenRepository;
    private final ApiTokenHelper apiTokenHelper;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public IssuedApiToken issue(ApiTokenKind kind, UUID subjectId, User owner, UUID organizationId) {
        Instant now = Instant.now(clock);
        // An expired token that was never revoked still holds the subject's place in the partial unique
        // index, so it is revoked here like a live one.
        Optional<ApiToken> revoked = revokeLive(kind, subjectId, now);

        String plaintext = apiTokenHelper.generate(kind);
        ApiToken token = apiTokenRepository.saveAndFlush(ApiToken.builder()
                .kind(kind)
                .subjectId(subjectId)
                .userId(owner.getId())
                .organizationId(organizationId)
                .tokenPrefix(apiTokenHelper.displayPrefix(plaintext))
                .tokenHash(apiTokenHelper.hash(plaintext))
                .expiresAt(now.plus(kind.getValidity()))
                .build());

        UUID revokedTokenId = revoked.map(ApiToken::getId).orElse(null);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_ISSUED,
                kind, token.getId(), subjectId, owner.getId(), organizationId, revokedTokenId);
        return new IssuedApiToken(token, plaintext, revokedTokenId);
    }

    @Override
    @Transactional
    public Optional<ApiToken> revoke(ApiTokenKind kind, UUID subjectId) {
        Optional<ApiToken> revoked = revokeLive(kind, subjectId, Instant.now(clock));
        if (revoked.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_NONE_TO_REVOKE,
                    kind, subjectId);
            return revoked;
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_REVOKED,
                kind, revoked.get().getId(), subjectId);
        return revoked;
    }

    @Override
    @Transactional
    public int revokeAllOfMember(UUID organizationId, UUID userId) {
        int revoked = apiTokenRepository.revokeLiveByOrganizationIdAndUserId(organizationId, userId,
                Instant.now(clock));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.MEMBER_TOKENS_REVOKED,
                revoked, userId, organizationId);
        return revoked;
    }

    @Override
    @Transactional
    public void markUsed(UUID tokenId) {
        Instant now = Instant.now(clock);
        int updated = apiTokenRepository.markUsed(tokenId, now,
                now.minusSeconds(ApiTokenConstant.MARK_USED_INTERVAL_SECONDS));
        if (updated > 0) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ApiTokenServiceLogConstant.TOKEN_USED, tokenId);
        }
    }

    private Optional<ApiToken> revokeLive(ApiTokenKind kind, UUID subjectId, Instant now) {
        Optional<ApiToken> live = apiTokenRepository.findByKindAndSubjectIdAndRevokedAtIsNull(kind, subjectId);
        live.ifPresent(token -> {
            token.setRevokedAt(now);
            // Flushed now, so the partial unique index sees no live token when the next one is inserted.
            apiTokenRepository.saveAndFlush(token);
        });
        return live;
    }
}
