package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberQueryServiceImplUnitSoleOwnerTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private OrganizationValidator organizationValidator;

    @InjectMocks
    private OrganizationMemberQueryServiceImpl organizationMemberQueryService;

    private static OrganizationMember membership(UUID userId) {
        User user = User.builder().build();
        user.setId(userId);
        Organization organization = Organization.builder().name("Org").build();
        organization.setId(UUID.randomUUID());
        return OrganizationMember.builder().organization(organization).user(user).build();
    }

    @Test
    void ensureNotSoleOwnerOfAnyOrganization_noMemberships_passes() {
        UUID userId = UUID.randomUUID();
        when(organizationMemberRepository.findByUser_IdAndStatus(userId, OrganizationMemberStatus.ACTIVE))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> organizationMemberQueryService.ensureNotSoleOwnerOfAnyOrganization(userId));

        verify(organizationValidator, never()).ensureNotSoleOwner(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ensureNotSoleOwnerOfAnyOrganization_everyOrganizationHasOtherOwner_checksEachMembership() {
        UUID userId = UUID.randomUUID();
        OrganizationMember first = membership(userId);
        OrganizationMember second = membership(userId);
        when(organizationMemberRepository.findByUser_IdAndStatus(userId, OrganizationMemberStatus.ACTIVE))
                .thenReturn(List.of(first, second));

        assertDoesNotThrow(() -> organizationMemberQueryService.ensureNotSoleOwnerOfAnyOrganization(userId));

        verify(organizationValidator).ensureNotSoleOwner(first.getOrganization().getId(), first);
        verify(organizationValidator).ensureNotSoleOwner(second.getOrganization().getId(), second);
    }

    @Test
    void ensureNotSoleOwnerOfAnyOrganization_soleOwnerOfSecondOrganization_throwsConflict() {
        UUID userId = UUID.randomUUID();
        OrganizationMember first = membership(userId);
        OrganizationMember second = membership(userId);
        when(organizationMemberRepository.findByUser_IdAndStatus(userId, OrganizationMemberStatus.ACTIVE))
                .thenReturn(List.of(first, second));
        doNothing().when(organizationValidator).ensureNotSoleOwner(first.getOrganization().getId(), first);
        doThrow(new ConflictException(ExceptionDomainOrganizationError.STA_SOLE_OWNER_CANNOT_LEAVE))
                .when(organizationValidator).ensureNotSoleOwner(second.getOrganization().getId(), second);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> organizationMemberQueryService.ensureNotSoleOwnerOfAnyOrganization(userId));

        assertEquals("ORG_STA_003", exception.getErrorCode());
    }

    @Test
    void ensureNotSoleOwnerOfAnyOrganization_onlyActiveMembershipsAreRead() {
        UUID userId = UUID.randomUUID();
        when(organizationMemberRepository.findByUser_IdAndStatus(userId, OrganizationMemberStatus.ACTIVE))
                .thenReturn(List.of());

        organizationMemberQueryService.ensureNotSoleOwnerOfAnyOrganization(userId);

        verify(organizationMemberRepository).findByUser_IdAndStatus(userId, OrganizationMemberStatus.ACTIVE);
        verify(organizationMemberRepository, never()).findByUser_IdAndStatus(userId, OrganizationMemberStatus.DISABLED);
    }
}
