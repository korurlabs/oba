package com.korur.oba.apps.kaizen.organizationsetting.controller.unit;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.organizationsetting.controller.OrganizationSettingController;
import com.korur.oba.apps.kaizen.organizationsetting.dto.request.OrganizationSecurityBoardUpdateRequest;
import com.korur.oba.apps.kaizen.organizationsetting.dto.response.OrganizationSettingResponse;
import com.korur.oba.apps.kaizen.organizationsetting.service.OrganizationSettingService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class OrganizationSettingControllerUnitTest {

    @Mock
    private OrganizationSettingService organizationSettingService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private OrganizationSettingController organizationSettingController;

    private User userWithId(UUID userId) {
        User user = User.builder().build();
        user.setId(userId);
        return user;
    }

    @Test
    void updateSecurityBoard_validRequest_returnsSettingInBody() {
        // Arrange
        UUID organizationId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrganizationSettingResponse response = new OrganizationSettingResponse(organizationId, boardId, Instant.now());
        when(organizationSettingService.updateSecurityBoard(organizationId, boardId, userId)).thenReturn(response);

        // Act
        ResponseEntity<CustomResponse<OrganizationSettingResponse>> result = organizationSettingController
                .updateSecurityBoard(userWithId(userId), organizationId, new OrganizationSecurityBoardUpdateRequest(boardId));

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertSame(response, result.getBody().data());
    }

    @Test
    void updateSecurityBoardOnPlatformPath_nullBoard_delegatesAndAnswersWithoutData() {
        // Arrange
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> result = organizationSettingController
                .updateSecurityBoardOnPlatformPath(userWithId(userId), organizationId,
                        new OrganizationSecurityBoardUpdateRequest(null));

        // Assert
        verify(organizationSettingService).updateSecurityBoard(organizationId, null, userId);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertNull(result.getBody().data());
    }
}
