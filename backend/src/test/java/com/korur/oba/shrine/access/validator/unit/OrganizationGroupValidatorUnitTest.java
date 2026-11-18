package com.korur.oba.shrine.access.validator.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.access.repository.OrganizationGroupMemberRepository;
import com.korur.oba.shrine.access.validator.OrganizationGroupValidator;

@ExtendWith(MockitoExtension.class)
class OrganizationGroupValidatorUnitTest {

    @Mock
    private OrganizationGroupMemberRepository organizationGroupMemberRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private OrganizationGroupValidator organizationGroupValidator;

    private static OrganizationGroup group(OrganizationGroupType type) {
        OrganizationGroup group = OrganizationGroup.builder().name("Engineering").type(type).build();
        group.setId(UUID.randomUUID());
        return group;
    }

    @Nested
    class EnsureCustomGroup {

        @Test
        void customGroup_passesWithoutLogging() {
            // Arrange
            OrganizationGroup group = group(OrganizationGroupType.CUSTOM);

            // Act and assert
            assertDoesNotThrow(() -> organizationGroupValidator.ensureCustomGroup(UUID.randomUUID(), group));
            verifyNoInteractions(loggingService);
        }

        @Test
        void everyoneGroup_throwsSystemGroupImmutableAndLogsWarn() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            OrganizationGroup group = group(OrganizationGroupType.EVERYONE);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> organizationGroupValidator.ensureCustomGroup(organizationId, group));

            // Assert
            assertEquals("APP_STA_001", exception.getErrorCode());
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(AccessExceptionLogConstant.SYSTEM_GROUP_IMMUTABLE), eq(organizationId), eq(group.getId()));
        }
    }

    @Nested
    class EnsureGroupMemberExists {

        @Test
        void rowExists_returnsTheRow() {
            // Arrange
            UUID groupId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            OrganizationGroupMember row = OrganizationGroupMember.builder().build();
            when(organizationGroupMemberRepository.findByGroup_IdAndUser_Id(groupId, userId))
                    .thenReturn(Optional.of(row));

            // Act
            OrganizationGroupMember result =
                    organizationGroupValidator.ensureGroupMemberExists(UUID.randomUUID(), groupId, userId);

            // Assert
            assertSame(row, result);
            verifyNoInteractions(loggingService);
        }

        @Test
        void rowMissing_throwsGroupMemberNotFoundAndLogsWarn() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(organizationGroupMemberRepository.findByGroup_IdAndUser_Id(groupId, userId))
                    .thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> organizationGroupValidator.ensureGroupMemberExists(organizationId, groupId, userId));

            // Assert
            assertEquals("APP_NOT_003", exception.getErrorCode());
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(AccessExceptionLogConstant.GROUP_MEMBER_NOT_FOUND),
                    eq(organizationId), eq(groupId), eq(userId));
        }
    }
}
