package com.korur.oba.dojo.security.token.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.helper.ApiTokenHelper;
import com.korur.oba.dojo.security.token.repository.ApiTokenRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTokenQueryServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Mock
    private ApiTokenRepository apiTokenRepository;

    @Mock
    private LoggingService loggingService;

    private final ApiTokenHelper apiTokenHelper = new ApiTokenHelper();
    private ApiTokenQueryServiceImpl service;
    private String plaintext;

    @BeforeEach
    void setUp() {
        service = new ApiTokenQueryServiceImpl(apiTokenRepository, apiTokenHelper,
                Clock.fixed(NOW, ZoneOffset.UTC), loggingService);
        plaintext = apiTokenHelper.generate(ApiTokenKind.AGENT);
    }

    private ApiToken stored(ApiTokenKind kind, Instant expiresAt, Instant revokedAt) {
        return ApiToken.builder()
                .id(UUID.randomUUID())
                .kind(kind)
                .subjectId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .organizationId(UUID.randomUUID())
                .tokenHash(apiTokenHelper.hash(plaintext))
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
    }

    @Test
    void authenticate_liveToken_isFoundByItsHash() {
        ApiToken token = stored(ApiTokenKind.AGENT, NOW.plusSeconds(1), null);
        when(apiTokenRepository.findByTokenHash(apiTokenHelper.hash(plaintext))).thenReturn(Optional.of(token));

        assertSame(token, service.authenticate(plaintext));
    }

    @Test
    void authenticate_unknownPrefix_isRefusedWithoutALookup() {
        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> service.authenticate("eyJhbGciOiJSUzI1NiJ9.e30.sig"));

        assertEquals("GEN_AUT_001", exception.getErrorCode());
        verifyNoInteractions(apiTokenRepository);
    }

    @Test
    void authenticate_malformedToken_isRefusedWithoutALookup() {
        assertThrows(UnauthorizedException.class, () -> service.authenticate("oat_short"));

        verify(apiTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void authenticate_unknownHash_isRefused() {
        when(apiTokenRepository.findByTokenHash(apiTokenHelper.hash(plaintext))).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.authenticate(plaintext));
    }

    @Test
    void authenticate_storedKindDiffersFromThePrefix_isRefused() {
        when(apiTokenRepository.findByTokenHash(apiTokenHelper.hash(plaintext)))
                .thenReturn(Optional.of(stored(ApiTokenKind.KAYA_MACHINE, NOW.plusSeconds(60), null)));

        assertThrows(UnauthorizedException.class, () -> service.authenticate(plaintext));
    }

    @Test
    void authenticate_revokedToken_isRefused() {
        when(apiTokenRepository.findByTokenHash(apiTokenHelper.hash(plaintext)))
                .thenReturn(Optional.of(stored(ApiTokenKind.AGENT, NOW.plusSeconds(60), NOW.minusSeconds(1))));

        assertThrows(UnauthorizedException.class, () -> service.authenticate(plaintext));
    }

    @Test
    void authenticate_tokenExpiringNow_isRefused() {
        when(apiTokenRepository.findByTokenHash(apiTokenHelper.hash(plaintext)))
                .thenReturn(Optional.of(stored(ApiTokenKind.AGENT, NOW, null)));

        assertThrows(UnauthorizedException.class, () -> service.authenticate(plaintext));
    }

    @Test
    void findLive_noSubjects_returnsEmptyWithoutAQuery() {
        assertTrue(service.findLive(ApiTokenKind.AGENT, List.of()).isEmpty());
        verifyNoInteractions(apiTokenRepository);
    }
}
