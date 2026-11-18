package com.korur.oba.dojo.security.resolver.unit;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.lang.reflect.Method;
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.resolver.GetUserArgumentResolver;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class GetUserArgumentResolverUnitTest {

    private static final String USER_CACHE_KEY = SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoggingService loggingService;

    @Mock
    private NativeWebRequest webRequest;

    private GetUserArgumentResolver resolver;

    private MethodParameter requireActiveParameter;
    private MethodParameter optionalStatusParameter;

    /**
     * Holder whose parameters carry the two GetUser variants the resolver reads.
     */
    @SuppressWarnings("unused")
    private static class ParameterHolder {

        void requireActive(@GetUser User user) {
        }

        void notRequireActive(@GetUser(requireActive = false) User user) {
        }
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        resolver = new GetUserArgumentResolver(userRepository, loggingService);
        Method requireActive = ParameterHolder.class.getDeclaredMethod("requireActive", User.class);
        Method notRequireActive = ParameterHolder.class.getDeclaredMethod("notRequireActive", User.class);
        requireActiveParameter = new MethodParameter(requireActive, 0);
        optionalStatusParameter = new MethodParameter(notRequireActive, 0);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithSub(String sub) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", sub)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private User userWithStatus(String keycloakId, UserStatus status) {
        return User.builder()
                .keycloakId(keycloakId)
                .email("user@example.com")
                .status(status)
                .build();
    }

    @Test
    void resolveArgument_disabledUser_throwsForbiddenException() {
        // Arrange
        String sub = "kc-disabled";
        authenticateWithSub(sub);
        when(userRepository.findByKeycloakId(sub))
                .thenReturn(Optional.of(userWithStatus(sub, UserStatus.DISABLED)));

        // Act
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> resolver.resolveArgument(requireActiveParameter, null, webRequest, null));

        // Assert
        assertEquals(ExceptionSecurityError.FORBIDDEN.getCode(), exception.getErrorCode());
        verify(webRequest, never()).setAttribute(any(), any(), eq(RequestAttributes.SCOPE_REQUEST));
    }

    @Test
    void resolveArgument_pendingOnboardingUser_throwsForbiddenException() {
        // Arrange
        String sub = "kc-pending";
        authenticateWithSub(sub);
        when(userRepository.findByKeycloakId(sub))
                .thenReturn(Optional.of(userWithStatus(sub, UserStatus.PENDING_ONBOARDING)));

        // Act & Assert
        assertThrows(ForbiddenException.class,
                () -> resolver.resolveArgument(requireActiveParameter, null, webRequest, null));
    }

    @Test
    void resolveArgument_pendingUserRequireActiveFalse_returnsUser() {
        // Arrange
        String sub = "kc-pending";
        authenticateWithSub(sub);
        User pending = userWithStatus(sub, UserStatus.PENDING_ONBOARDING);
        when(userRepository.findByKeycloakId(sub)).thenReturn(Optional.of(pending));

        // Act
        User result = resolver.resolveArgument(optionalStatusParameter, null, webRequest, null);

        // Assert
        assertSame(pending, result);
    }

    @Test
    void resolveArgument_disabledUserRequireActiveFalse_returnsUser() {
        // Arrange
        String sub = "kc-disabled";
        authenticateWithSub(sub);
        User disabled = userWithStatus(sub, UserStatus.DISABLED);
        when(userRepository.findByKeycloakId(sub)).thenReturn(Optional.of(disabled));

        // Act
        User result = resolver.resolveArgument(optionalStatusParameter, null, webRequest, null);

        // Assert
        assertSame(disabled, result);
    }

    @Test
    void resolveArgument_activeUser_returnsUserAndCachesIt() {
        // Arrange
        String sub = "kc-active";
        authenticateWithSub(sub);
        User active = userWithStatus(sub, UserStatus.ACTIVE);
        when(userRepository.findByKeycloakId(sub)).thenReturn(Optional.of(active));

        // Act
        User result = resolver.resolveArgument(requireActiveParameter, null, webRequest, null);

        // Assert
        assertSame(active, result);
        verify(webRequest).setAttribute(USER_CACHE_KEY, active, RequestAttributes.SCOPE_REQUEST);
    }

    @Test
    void resolveArgument_cachedDisabledUser_throwsForbiddenException() {
        // Arrange
        User disabled = userWithStatus("kc-disabled", UserStatus.DISABLED);
        when(webRequest.getAttribute(USER_CACHE_KEY, RequestAttributes.SCOPE_REQUEST)).thenReturn(disabled);

        // Act & Assert
        assertThrows(ForbiddenException.class,
                () -> resolver.resolveArgument(requireActiveParameter, null, webRequest, null));
        verify(userRepository, never()).findByKeycloakId(any());
    }

    @Test
    void resolveArgument_unknownSub_throwsNotFoundException() {
        // Arrange
        String sub = "kc-unknown";
        authenticateWithSub(sub);
        when(userRepository.findByKeycloakId(sub)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> resolver.resolveArgument(requireActiveParameter, null, webRequest, null));
    }
}
