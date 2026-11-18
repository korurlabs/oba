package com.korur.oba.shrine.identity.validator.unit;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

// 2. Third-party

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.validator.UserValidator;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class UserValidatorUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private UserValidator userValidator;

    @Test
    void ensureKeycloakIdNotDeleted_deletedRow_throwsForbiddenException() {
        // Arrange
        String keycloakId = "kc-deleted";
        when(userRepository.existsDeletedByKeycloakId(keycloakId)).thenReturn(true);

        // Act
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> userValidator.ensureKeycloakIdNotDeleted(keycloakId));

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(ExceptionDomainIdentityError.SEC_USER_DELETED.getCode(), exception.getErrorCode());
        assertEquals("IDN_SEC_001", exception.getErrorCode());
        assertEquals(keycloakId, exception.getErrorValues().get(UserConstant.FIELD_USER_KEYCLOAK_ID));
    }

    @Test
    void ensureKeycloakIdNotDeleted_noDeletedRow_passes() {
        // Arrange
        String keycloakId = "kc-live-or-unknown";
        when(userRepository.existsDeletedByKeycloakId(keycloakId)).thenReturn(false);

        // Act & Assert
        assertDoesNotThrow(() -> userValidator.ensureKeycloakIdNotDeleted(keycloakId));
        verify(userRepository).existsDeletedByKeycloakId(keycloakId);
        verifyNoInteractions(loggingService);
    }
}
