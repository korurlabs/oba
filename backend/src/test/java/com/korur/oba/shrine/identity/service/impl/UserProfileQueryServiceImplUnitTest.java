package com.korur.oba.shrine.identity.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.dto.response.UserDetailResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.identity.mapper.UserMapper;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.validator.UserValidator;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.service.InvitationQueryService;

@ExtendWith(MockitoExtension.class)
class UserProfileQueryServiceImplUnitTest {

    private static final String KEYCLOAK_ID = "kc-subject";
    private static final String EMAIL = "person@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private LoggingService loggingService;

    @Mock
    private InvitationQueryService invitationQueryService;

    @Mock
    private UserValidator userValidator;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @InjectMocks
    private UserProfileQueryServiceImpl userProfileQueryServiceImpl;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String sub) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", sub)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Nested
    class GetCurrentUser {

        @Test
        void deletedKeycloakId_throwsForbiddenInsteadOfNotFound() {
            // Arrange
            authenticate(KEYCLOAK_ID);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
            doThrow(new ForbiddenException(ExceptionDomainIdentityError.SEC_USER_DELETED))
                    .when(userValidator).ensureKeycloakIdNotDeleted(KEYCLOAK_ID);

            // Act
            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> userProfileQueryServiceImpl.getCurrentUser());

            // Assert
            assertEquals("IDN_SEC_001", exception.getErrorCode());
            verify(invitationQueryService, never()).listPendingInvitationsByEmail(anyString());
        }

        @Test
        void unknownKeycloakId_throwsNotFoundException() {
            // Arrange
            authenticate(KEYCLOAK_ID);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> userProfileQueryServiceImpl.getCurrentUser());

            // Assert
            assertEquals(ExceptionDomainIdentityError.NOT_USER_NOT_SYNCED.getCode(), exception.getErrorCode());
            verify(userValidator).ensureKeycloakIdNotDeleted(KEYCLOAK_ID);
        }

        @Test
        void liveDisabledUser_returnsProfileUnchanged() {
            // Arrange
            authenticate(KEYCLOAK_ID);
            User user = User.builder()
                    .keycloakId(KEYCLOAK_ID)
                    .email(EMAIL)
                    .status(UserStatus.DISABLED)
                    .build();
            List<InvitationResponse> invitations = List.of();
            UserDetailResponse response = mock(UserDetailResponse.class);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            when(invitationQueryService.listPendingInvitationsByEmail(EMAIL)).thenReturn(invitations);
            Map<UUID, List<App>> apps = Map.of(UUID.randomUUID(), List.of(App.KAIZEN, App.KAYA));
            when(appAccessQueryService.listAccessibleAppsByOrganization(user)).thenReturn(apps);
            when(userMapper.toDetailResponse(user, invitations, apps)).thenReturn(response);

            // Act
            UserDetailResponse result = userProfileQueryServiceImpl.getCurrentUser();

            // Assert
            assertSame(response, result);
            verify(userValidator, never()).ensureKeycloakIdNotDeleted(anyString());
        }
    }
}
