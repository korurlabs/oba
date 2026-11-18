package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupMemberResponse;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.access.mapper.OrganizationGroupMapper;
import com.korur.oba.shrine.access.mapper.OrganizationGroupMapperImpl;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupMemberRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupRepository;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class OrganizationGroupQueryServiceImplUnitTest {

    @Mock
    private OrganizationGroupRepository organizationGroupRepository;

    @Mock
    private OrganizationGroupMemberRepository organizationGroupMemberRepository;

    @Mock
    private AppGrantRepository appGrantRepository;

    @Spy
    private OrganizationGroupMapper organizationGroupMapper = new OrganizationGroupMapperImpl();

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private OrganizationGroupQueryServiceImpl organizationGroupQueryServiceImpl;

    private static Organization organization() {
        Organization organization = Organization.builder().name("Acme").build();
        organization.setId(UUID.randomUUID());
        return organization;
    }

    private static OrganizationGroup group(Organization organization, String name, OrganizationGroupType type) {
        OrganizationGroup group = OrganizationGroup.builder().organization(organization).name(name).type(type).build();
        group.setId(UUID.randomUUID());
        return group;
    }

    private static OrganizationMemberResponse memberResponse(UUID userId, String firstName, String email,
                                                             OrganizationMemberRole role,
                                                             OrganizationMemberStatus status) {
        return new OrganizationMemberResponse(UUID.randomUUID(), userId, email, firstName, "Doe", role, status);
    }

    private static User actor() {
        User user = User.builder().build();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Nested
    class ListGroups {

        @Test
        void noGroups_returnsEmptyWithoutReadingGrantsOrMembers() {
            UUID organizationId = UUID.randomUUID();
            when(organizationGroupRepository.findByOrganization_Id(organizationId)).thenReturn(List.of());

            assertTrue(organizationGroupQueryServiceImpl.listGroups(actor(), organizationId).isEmpty());

            verifyNoInteractions(appGrantRepository, organizationMemberQueryService);
        }

        @Test
        void groups_listEveryoneFirstThenByNameWithCountsAndApps() {
            Organization organization = organization();
            UUID organizationId = organization.getId();
            User actor = actor();
            OrganizationGroup everyone = group(organization, "Everyone", OrganizationGroupType.EVERYONE);
            OrganizationGroup zeta = group(organization, "zeta", OrganizationGroupType.CUSTOM);
            OrganizationGroup alpha = group(organization, "Alpha", OrganizationGroupType.CUSTOM);
            when(organizationGroupRepository.findByOrganization_Id(organizationId))
                    .thenReturn(List.of(zeta, everyone, alpha));
            when(appGrantRepository.findByOrganization_Id(organizationId)).thenReturn(List.of(
                    AppGrant.builder().group(alpha).app(App.KAYA).build(),
                    AppGrant.builder().group(alpha).app(App.KAIZEN).build(),
                    AppGrant.builder().user(actor).app(App.KAIZEN).build()));
            when(organizationMemberQueryService.listOrganizationMembers(actor, organizationId)).thenReturn(List.of(
                    memberResponse(UUID.randomUUID(), "A", "a@x.io", OrganizationMemberRole.OWNER,
                            OrganizationMemberStatus.ACTIVE),
                    memberResponse(UUID.randomUUID(), "B", "b@x.io", OrganizationMemberRole.MEMBER,
                            OrganizationMemberStatus.ACTIVE),
                    memberResponse(UUID.randomUUID(), "C", "c@x.io", OrganizationMemberRole.MEMBER,
                            OrganizationMemberStatus.DISABLED)));
            when(organizationGroupMemberRepository.countByGroup_Id(alpha.getId())).thenReturn(3L);
            when(organizationGroupMemberRepository.countByGroup_Id(zeta.getId())).thenReturn(0L);

            List<OrganizationGroupResponse> result = organizationGroupQueryServiceImpl.listGroups(actor, organizationId);

            assertEquals(List.of("Everyone", "Alpha", "zeta"), result.stream().map(OrganizationGroupResponse::name).toList());
            assertEquals(2L, result.get(0).memberCount());
            assertEquals(3L, result.get(1).memberCount());
            assertEquals(List.of(App.KAIZEN, App.KAYA), result.get(1).apps());
            assertTrue(result.get(2).apps().isEmpty());
        }
    }

    @Nested
    class ListGroupMembers {

        @Test
        void everyoneGroup_listsActiveMembersSortedByName() {
            Organization organization = organization();
            UUID organizationId = organization.getId();
            OrganizationGroup everyone = group(organization, "Everyone", OrganizationGroupType.EVERYONE);
            User actor = actor();
            when(organizationGroupRepository.findByIdAndOrganization_Id(everyone.getId(), organizationId))
                    .thenReturn(Optional.of(everyone));
            when(organizationMemberQueryService.listOrganizationMembers(actor, organizationId)).thenReturn(List.of(
                    memberResponse(UUID.randomUUID(), "Zed", "z@x.io", OrganizationMemberRole.MEMBER,
                            OrganizationMemberStatus.ACTIVE),
                    memberResponse(UUID.randomUUID(), "Off", "o@x.io", OrganizationMemberRole.MEMBER,
                            OrganizationMemberStatus.DISABLED),
                    memberResponse(UUID.randomUUID(), "amy", "a@x.io", OrganizationMemberRole.OWNER,
                            OrganizationMemberStatus.ACTIVE)));

            List<OrganizationGroupMemberResponse> result =
                    organizationGroupQueryServiceImpl.listGroupMembers(actor, organizationId, everyone.getId());

            assertEquals(List.of("amy Doe", "Zed Doe"),
                    result.stream().map(OrganizationGroupMemberResponse::fullName).toList());
            verifyNoInteractions(organizationGroupMemberRepository);
        }

        @Test
        void customGroup_listsMemberRowsWithRoleEvenForADisabledMembership() {
            Organization organization = organization();
            UUID organizationId = organization.getId();
            OrganizationGroup custom = group(organization, "Dev", OrganizationGroupType.CUSTOM);
            User actor = actor();
            User disabled = User.builder().email("d@x.io").build();
            disabled.setId(UUID.randomUUID());
            disabled.getProfile().setFirstName("Dan");
            when(organizationGroupRepository.findByIdAndOrganization_Id(custom.getId(), organizationId))
                    .thenReturn(Optional.of(custom));
            when(organizationMemberQueryService.listOrganizationMembers(actor, organizationId)).thenReturn(List.of(
                    memberResponse(disabled.getId(), "Dan", "d@x.io", OrganizationMemberRole.GUEST,
                            OrganizationMemberStatus.DISABLED)));
            when(organizationGroupMemberRepository.findByGroup_Id(custom.getId())).thenReturn(List.of(
                    OrganizationGroupMember.builder().group(custom).user(disabled).build()));

            List<OrganizationGroupMemberResponse> result =
                    organizationGroupQueryServiceImpl.listGroupMembers(actor, organizationId, custom.getId());

            assertEquals(1, result.size());
            assertEquals(OrganizationMemberRole.GUEST, result.get(0).role());
            assertEquals("Dan", result.get(0).fullName());
        }

        @Test
        void customGroupRowOfAUserWhoLeft_hasNullRole() {
            Organization organization = organization();
            UUID organizationId = organization.getId();
            OrganizationGroup custom = group(organization, "Dev", OrganizationGroupType.CUSTOM);
            User actor = actor();
            User gone = User.builder().email("g@x.io").build();
            gone.setId(UUID.randomUUID());
            when(organizationGroupRepository.findByIdAndOrganization_Id(custom.getId(), organizationId))
                    .thenReturn(Optional.of(custom));
            when(organizationMemberQueryService.listOrganizationMembers(actor, organizationId)).thenReturn(List.of());
            when(organizationGroupMemberRepository.findByGroup_Id(custom.getId())).thenReturn(List.of(
                    OrganizationGroupMember.builder().group(custom).user(gone).build()));

            List<OrganizationGroupMemberResponse> result =
                    organizationGroupQueryServiceImpl.listGroupMembers(actor, organizationId, custom.getId());

            assertNull(result.get(0).role());
            assertEquals("g@x.io", result.get(0).fullName());
        }
    }

    @Nested
    class GetGroupEntity {

        @Test
        void groupFound_returnsIt() {
            Organization organization = organization();
            OrganizationGroup custom = group(organization, "Dev", OrganizationGroupType.CUSTOM);
            when(organizationGroupRepository.findByIdAndOrganization_Id(custom.getId(), organization.getId()))
                    .thenReturn(Optional.of(custom));

            assertSame(custom, organizationGroupQueryServiceImpl.getGroupEntity(organization.getId(), custom.getId()));
        }

        @Test
        void groupOfAnotherOrganizationOrDeleted_throwsNotFound() {
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            when(organizationGroupRepository.findByIdAndOrganization_Id(groupId, organizationId))
                    .thenReturn(Optional.empty());

            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> organizationGroupQueryServiceImpl.getGroupEntity(organizationId, groupId));

            assertEquals("APP_NOT_001", exception.getErrorCode());
        }
    }

    @Nested
    class FindGroupUserIds {

        @Test
        void everyoneGroup_returnsActiveMembersBelowOwner() {
            // Arrange
            Organization organization = organization();
            Set<UUID> ids = Set.of(UUID.randomUUID());
            ArgumentCaptor<Set<OrganizationMemberRole>> roles = ArgumentCaptor.forClass(Set.class);
            when(organizationMemberQueryService.findActiveUserIdsByRoles(any(), anySet())).thenReturn(ids);

            // Act
            Set<UUID> result = organizationGroupQueryServiceImpl.findGroupUserIds(
                    group(organization, "Everyone", OrganizationGroupType.EVERYONE));

            // Assert
            verify(organizationMemberQueryService).findActiveUserIdsByRoles(any(), roles.capture());
            assertEquals(ids, result);
            assertEquals(Set.of(OrganizationMemberRole.ADMIN, OrganizationMemberRole.MEMBER,
                    OrganizationMemberRole.GUEST), roles.getValue());
            verifyNoInteractions(organizationGroupMemberRepository);
        }

        @Test
        void customGroup_returnsTheUsersOfItsMemberRows() {
            // Arrange
            OrganizationGroup group = group(organization(), "Dev", OrganizationGroupType.CUSTOM);
            User first = actor();
            User second = actor();
            when(organizationGroupMemberRepository.findByGroup_Id(group.getId())).thenReturn(List.of(
                    OrganizationGroupMember.builder().user(first).build(),
                    OrganizationGroupMember.builder().user(second).build()));

            // Act
            Set<UUID> result = organizationGroupQueryServiceImpl.findGroupUserIds(group);

            // Assert
            assertEquals(Set.of(first.getId(), second.getId()), result);
            verifyNoInteractions(organizationMemberQueryService);
        }
    }
}
