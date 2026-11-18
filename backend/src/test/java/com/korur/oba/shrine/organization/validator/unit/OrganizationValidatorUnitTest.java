package com.korur.oba.shrine.organization.validator.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.constant.exception.OrganizationExceptionLogConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.repository.OrganizationRepository;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

@ExtendWith(MockitoExtension.class)
class OrganizationValidatorUnitTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private OrganizationValidator organizationValidator;

    @Nested
    class EnsureOrganizationExists {

        @Test
        void organizationExists_returnsOrganizationWithoutLogging() {
            UUID organizationId = UUID.randomUUID();
            Organization organization = Organization.builder().name("Org").build();
            when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

            Organization result = organizationValidator.ensureOrganizationExists(organizationId);

            assertSame(organization, result);
            verify(loggingService, never()).warn(any(), any(), any(String.class), any(Object[].class));
        }

        @Test
        void organizationNotFound_throwsNotFoundAndLogsWarnWithId() {
            UUID organizationId = UUID.randomUUID();
            when(organizationRepository.findById(organizationId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> organizationValidator.ensureOrganizationExists(organizationId));

            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(OrganizationExceptionLogConstant.ORG_NOT_FOUND), eq(organizationId));
            verify(loggingService, never()).error(any(), any(), any(String.class), any(Object[].class));
        }
    }

    @Nested
    class EnsureMemberExists {

        @Test
        void memberExists_returnsMemberWithoutLogging() {
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            OrganizationMember member = OrganizationMember.builder().build();
            when(organizationMemberRepository.findByOrganization_IdAndUser_Id(organizationId, userId))
                    .thenReturn(Optional.of(member));

            OrganizationMember result = organizationValidator.ensureMemberExists(organizationId, userId);

            assertSame(member, result);
            verify(loggingService, never()).warn(any(), any(), any(String.class), any(Object[].class));
        }

        @Test
        void memberNotFound_throwsNotFoundAndLogsWarnWithIds() {
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(organizationMemberRepository.findByOrganization_IdAndUser_Id(organizationId, userId))
                    .thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> organizationValidator.ensureMemberExists(organizationId, userId));

            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(OrganizationExceptionLogConstant.ORG_MEMBER_NOT_FOUND),
                    eq(organizationId), eq(userId));
            verify(loggingService, never()).error(any(), any(), any(String.class), any(Object[].class));
        }
    }
}
