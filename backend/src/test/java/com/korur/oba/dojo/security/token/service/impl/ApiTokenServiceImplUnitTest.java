package com.korur.oba.dojo.security.token.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.dto.IssuedApiToken;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.helper.ApiTokenHelper;
import com.korur.oba.dojo.security.token.repository.ApiTokenRepository;
import com.korur.oba.shrine.identity.entity.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTokenServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Mock
    private ApiTokenRepository apiTokenRepository;

    @Mock
    private LoggingService loggingService;

    private final ApiTokenHelper apiTokenHelper = new ApiTokenHelper();
    private ApiTokenServiceImpl service;
    private final UUID subjectId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final User owner = User.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        service = new ApiTokenServiceImpl(apiTokenRepository, apiTokenHelper, Clock.fixed(NOW, ZoneOffset.UTC),
                loggingService);
    }

    private void echoSaves() {
        when(apiTokenRepository.saveAndFlush(any(ApiToken.class))).thenAnswer(invocation -> {
            ApiToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
            }
            return token;
        });
    }

    @Test
    void issue_noLiveToken_storesOnlyTheHashAndPrefixWithTheKindsLifetime() {
        echoSaves();
        when(apiTokenRepository.findByKindAndSubjectIdAndRevokedAtIsNull(ApiTokenKind.AGENT, subjectId))
                .thenReturn(Optional.empty());

        IssuedApiToken issued = service.issue(ApiTokenKind.AGENT, subjectId, owner, organizationId);

        ApiToken token = issued.token();
        assertTrue(issued.plaintext().startsWith("oat_"));
        assertEquals(apiTokenHelper.hash(issued.plaintext()), token.getTokenHash());
        assertEquals(issued.plaintext().substring(0, 12), token.getTokenPrefix());
        assertEquals(NOW.plus(Duration.ofDays(365)), token.getExpiresAt());
        assertEquals(ApiTokenKind.AGENT, token.getKind());
        assertEquals(subjectId, token.getSubjectId());
        assertEquals(owner.getId(), token.getUserId());
        assertEquals(organizationId, token.getOrganizationId());
        assertNull(issued.revokedTokenId());
        assertFalse(issued.toString().contains(issued.plaintext()));
    }

    @Test
    void issue_liveTokenExists_revokesAndFlushesItBeforeTheInsert() {
        echoSaves();
        ApiToken live = ApiToken.builder().id(UUID.randomUUID()).kind(ApiTokenKind.AGENT).subjectId(subjectId).build();
        when(apiTokenRepository.findByKindAndSubjectIdAndRevokedAtIsNull(ApiTokenKind.AGENT, subjectId))
                .thenReturn(Optional.of(live));

        IssuedApiToken issued = service.issue(ApiTokenKind.AGENT, subjectId, owner, organizationId);

        assertEquals(NOW, live.getRevokedAt());
        assertEquals(live.getId(), issued.revokedTokenId());
        ArgumentCaptor<ApiToken> saved = ArgumentCaptor.forClass(ApiToken.class);
        InOrder order = inOrder(apiTokenRepository);
        order.verify(apiTokenRepository, times(2)).saveAndFlush(saved.capture());
        assertEquals(live, saved.getAllValues().get(0));
        assertEquals(issued.token(), saved.getAllValues().get(1));
    }

    @Test
    void revoke_noLiveToken_returnsEmptyAndSavesNothing() {
        when(apiTokenRepository.findByKindAndSubjectIdAndRevokedAtIsNull(ApiTokenKind.KAYA_MACHINE, subjectId))
                .thenReturn(Optional.empty());

        assertTrue(service.revoke(ApiTokenKind.KAYA_MACHINE, subjectId).isEmpty());
        verify(apiTokenRepository, never()).saveAndFlush(any());
    }

    @Test
    void revoke_liveToken_setsRevokedAt() {
        ApiToken live = ApiToken.builder().id(UUID.randomUUID()).build();
        when(apiTokenRepository.findByKindAndSubjectIdAndRevokedAtIsNull(ApiTokenKind.KAYA_MACHINE, subjectId))
                .thenReturn(Optional.of(live));

        Optional<ApiToken> revoked = service.revoke(ApiTokenKind.KAYA_MACHINE, subjectId);

        assertEquals(live, revoked.orElseThrow());
        assertEquals(NOW, live.getRevokedAt());
        verify(apiTokenRepository).saveAndFlush(live);
    }

    @Test
    void revokeAllOfMember_revokesByOrganizationAndOwner() {
        UUID userId = UUID.randomUUID();
        when(apiTokenRepository.revokeLiveByOrganizationIdAndUserId(organizationId, userId, NOW)).thenReturn(2);

        assertEquals(2, service.revokeAllOfMember(organizationId, userId));
    }

    @Test
    void markUsed_refreshesOnlyAUseOlderThanAMinute() {
        UUID tokenId = UUID.randomUUID();

        service.markUsed(tokenId);

        verify(apiTokenRepository).markUsed(tokenId, NOW, NOW.minusSeconds(60));
    }
}
