package com.korur.oba.apps.kazan.organizationsetting.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.organizationsetting.dto.response.OrganizationSettingResponse;
import com.korur.oba.apps.kazan.organizationsetting.entity.OrganizationSetting;
import com.korur.oba.apps.kazan.organizationsetting.mapper.OrganizationSettingMapper;
import com.korur.oba.apps.kazan.organizationsetting.repository.OrganizationSettingRepository;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class OrganizationSettingServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private OrganizationSettingRepository organizationSettingRepository;

    @Mock
    private OrganizationSettingMapper organizationSettingMapper;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private OrganizationSettingServiceImpl organizationSettingServiceImpl;

    @Nested
    class UpdateSecurityBoard {

        @Test
        void adminAndBoardMember_withoutRow_createsRowWithBoard() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            OrganizationSettingResponse response = new OrganizationSettingResponse(organizationId, boardId, null);

            when(organizationSettingRepository.findById(organizationId)).thenReturn(Optional.empty());
            when(organizationSettingRepository.save(any(OrganizationSetting.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(organizationSettingMapper.toResponse(any(OrganizationSetting.class))).thenReturn(response);

            // Act
            OrganizationSettingResponse result =
                    organizationSettingServiceImpl.updateSecurityBoard(organizationId, boardId, userId);

            // Assert
            ArgumentCaptor<OrganizationSetting> saved = ArgumentCaptor.forClass(OrganizationSetting.class);
            verify(organizationSettingRepository).save(saved.capture());
            assertEquals(organizationId, saved.getValue().getOrganizationId());
            assertEquals(boardId, saved.getValue().getSecurityBoardId());
            assertSame(response, result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, userId, BoardMemberRole.VIEWER);
        }

        @Test
        void nullBoard_clearsExistingBoardWithoutBoardCheck() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            OrganizationSetting existing = OrganizationSetting.builder()
                    .organizationId(organizationId)
                    .securityBoardId(UUID.randomUUID())
                    .build();

            when(organizationSettingRepository.findById(organizationId)).thenReturn(Optional.of(existing));
            when(organizationSettingRepository.save(existing)).thenReturn(existing);

            // Act
            organizationSettingServiceImpl.updateSecurityBoard(organizationId, null, userId);

            // Assert
            assertNull(existing.getSecurityBoardId());
            verify(organizationSettingRepository).save(existing);
            verifyNoInteractions(boardAccessService);
        }

        @Test
        void boardOutsideCallersBoards_throwsNotFoundAndSavesNothing() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, userId, BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_BOARD_MEMBER_FOUND,
                            Map.of("boardId", boardId)));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    organizationSettingServiceImpl.updateSecurityBoard(organizationId, boardId, userId));
            verify(organizationSettingRepository, never()).save(any(OrganizationSetting.class));
        }
    }
}
