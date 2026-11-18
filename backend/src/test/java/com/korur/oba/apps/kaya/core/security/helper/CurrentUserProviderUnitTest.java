package com.korur.oba.apps.kaya.core.security.helper;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.identity.mock_data.IdentityMocks;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserProviderUnitTest {

    @Mock
    private KayaUserQueryService kayaUserQueryService;
    @Mock
    private LoggingService loggingService;

    private CurrentUserProvider provider;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        provider = new CurrentUserProvider(kayaUserQueryService, loggingService);
        request = new MockHttpServletRequest();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithSubject(String subject) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(IdentityMocks.jwt(subject, "ada@example.com", null, null)));
    }

    @Test
    void findCurrentUser_noJwtAuthentication_returnsEmpty() {
        Optional<User> result = provider.findCurrentUser(request);

        assertTrue(result.isEmpty());
        verifyNoInteractions(kayaUserQueryService);
    }

    @Test
    void findCurrentUser_validJwt_loadsUserOnceAndCachesIt() {
        User user = IdentityMocks.user("ada@example.com");
        authenticateWithSubject("kc-1");
        when(kayaUserQueryService.getEntityByKeycloakId("kc-1")).thenReturn(user);

        Optional<User> first = provider.findCurrentUser(request);
        Optional<User> second = provider.findCurrentUser(request);

        assertSame(user, first.orElseThrow());
        assertSame(user, second.orElseThrow());
        assertSame(user, request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER));
        verify(kayaUserQueryService, times(1)).getEntityByKeycloakId("kc-1");
    }

    @Test
    void findCurrentUser_cachedUser_skipsLookup() {
        User user = IdentityMocks.user("ada@example.com");
        provider.cacheCurrentUser(request, user);

        assertSame(user, provider.findCurrentUser(request).orElseThrow());
        verifyNoInteractions(kayaUserQueryService);
    }

    @Test
    void findCurrentUser_blankSubject_throwsUnauthorized() {
        authenticateWithSubject(" ");

        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> provider.findCurrentUser(request));

        assertEquals("GEN_AUT_001", exception.getErrorCode());
    }
}
