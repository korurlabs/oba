package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.dto.projection.OrganizationAppGrantProjection;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.dto.response.MemberAppAccessResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.AppGrantTargetType;
import com.korur.oba.shrine.access.mapper.AppAccessMapper;
import com.korur.oba.shrine.access.mapper.AppAccessMapperImpl;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.service.OrganizationGroupQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

@ExtendWith(MockitoExtension.class)
class AppAccessQueryServiceImplUnitTest {

    // The role names the set read passes for an app open to guests, and for an app with a MEMBER minimum.
    private static final List<String> ALL_ROLES = List.of("OWNER", "ADMIN", "MEMBER", "GUEST");
    private static final List<String> MEMBER_AND_ABOVE = List.of("OWNER", "ADMIN", "MEMBER");

    @Mock
    private AppGrantRepository appGrantRepository;

    @Spy
    private AppAccessMapper appAccessMapper = buildMapper();

    @Mock
    private OrganizationGroupQueryService organizationGroupQueryService;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private OrganizationQueryService organizationQueryService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private AppAccessQueryServiceImpl appAccessQueryServiceImpl;

    private static AppAccessMapper buildMapper() {
        AppAccessMapperImpl mapper = new AppAccessMapperImpl();
        return mapper;
    }

    private static Organization organization(UUID organizationId, OrganizationStatus status) {
        Organization organization = Organization.builder().name("Acme").build();
        organization.setId(organizationId);
        organization.setStatus(status);
        return organization;
    }

    private static User user(String firstName, String lastName) {
        User user = User.builder().email(firstName.toLowerCase() + "@example.com").build();
        user.setId(UUID.randomUUID());
        user.getProfile().setFirstName(firstName);
        user.getProfile().setLastName(lastName);
        return user;
    }

    private static OrganizationMember member(Organization organization, User user, OrganizationMemberRole role,
                                             OrganizationMemberStatus status) {
        OrganizationMember member = OrganizationMember.builder()
                .organization(organization)
                .user(user)
                .role(role)
                .status(status)
                .build();
        member.setId(UUID.randomUUID());
        return member;
    }

    private static OrganizationMember activeMember(OrganizationMemberRole role) {
        return member(organization(UUID.randomUUID(), OrganizationStatus.ACTIVE), user("Ada", "Lovelace"), role,
                OrganizationMemberStatus.ACTIVE);
    }

    @Nested
    class HasAppAccessForMember {

        @Test
        void ownerWithoutGrant_hasAccessWithoutReadingGrants() {
            OrganizationMember member = activeMember(OrganizationMemberRole.OWNER);

            assertTrue(appAccessQueryServiceImpl.hasAppAccess(member, App.KAZAN));
            assertTrue(appAccessQueryServiceImpl.hasAppAccess(member, App.KAYA));

            verifyNoInteractions(appGrantRepository);
        }

        @Test
        void adminMemberAndGuestWithoutGrant_haveNoAccessToKazan() {
            for (OrganizationMemberRole role : List.of(OrganizationMemberRole.ADMIN, OrganizationMemberRole.MEMBER,
                    OrganizationMemberRole.GUEST)) {
                OrganizationMember member = activeMember(role);
                when(appGrantRepository.existsGrantForUser(member.getOrganization().getId(),
                        member.getUser().getId(), App.KAZAN)).thenReturn(false);

                assertFalse(appAccessQueryServiceImpl.hasAppAccess(member, App.KAZAN), role.name());
            }
        }

        @Test
        void memberWithGrant_hasAccess() {
            OrganizationMember member = activeMember(OrganizationMemberRole.MEMBER);
            when(appGrantRepository.existsGrantForUser(member.getOrganization().getId(),
                    member.getUser().getId(), App.KAZAN)).thenReturn(true);

            assertTrue(appAccessQueryServiceImpl.hasAppAccess(member, App.KAZAN));
        }

        @Test
        void guestWithKayaGrant_hasNoAccessBelowMinimumRole() {
            OrganizationMember member = activeMember(OrganizationMemberRole.GUEST);

            assertFalse(appAccessQueryServiceImpl.hasAppAccess(member, App.KAYA));

            verifyNoInteractions(appGrantRepository);
        }

        @Test
        void disabledMembership_hasNoAccessEvenForOwner() {
            OrganizationMember member = member(organization(UUID.randomUUID(), OrganizationStatus.ACTIVE),
                    user("Ada", "Lovelace"), OrganizationMemberRole.OWNER, OrganizationMemberStatus.DISABLED);

            assertFalse(appAccessQueryServiceImpl.hasAppAccess(member, App.KAZAN));

            verifyNoInteractions(appGrantRepository);
        }

        @Test
        void disabledOrganization_hasNoAccessEvenForOwner() {
            OrganizationMember member = member(organization(UUID.randomUUID(), OrganizationStatus.DISABLED),
                    user("Ada", "Lovelace"), OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE);

            assertFalse(appAccessQueryServiceImpl.hasAppAccess(member, App.KAZAN));

            verifyNoInteractions(appGrantRepository);
        }

        @Test
        void nullMember_hasNoAccess() {
            assertFalse(appAccessQueryServiceImpl.hasAppAccess((OrganizationMember) null, App.KAZAN));
        }
    }

    @Nested
    class HasAppAccessById {

        @Test
        void noMember_returnsFalse() {
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(organizationMemberQueryService.findMember(organizationId, userId)).thenReturn(Optional.empty());

            assertFalse(appAccessQueryServiceImpl.hasAppAccess(organizationId, userId, App.KAZAN));
        }

        @Test
        void memberWithGrant_returnsTrue() {
            OrganizationMember member = activeMember(OrganizationMemberRole.MEMBER);
            UUID organizationId = member.getOrganization().getId();
            UUID userId = member.getUser().getId();
            when(organizationMemberQueryService.findMember(organizationId, userId)).thenReturn(Optional.of(member));
            when(appGrantRepository.existsGrantForUser(organizationId, userId, App.KAZAN)).thenReturn(true);

            assertTrue(appAccessQueryServiceImpl.hasAppAccess(organizationId, userId, App.KAZAN));
        }
    }

    @Nested
    class VerifyAppAccess {

        @Test
        void accessGranted_returnsWithoutWarning() {
            OrganizationMember member = activeMember(OrganizationMemberRole.OWNER);
            UUID organizationId = member.getOrganization().getId();
            UUID userId = member.getUser().getId();
            when(organizationMemberQueryService.findMember(organizationId, userId)).thenReturn(Optional.of(member));

            appAccessQueryServiceImpl.verifyAppAccess(organizationId, userId, App.KAYA);

            verify(loggingService, never()).warn(any(), any(), any(String.class), any(Object[].class));
        }

        @Test
        void accessMissing_throwsAppAccessDeniedNamingOrganizationAndApp() {
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(organizationMemberQueryService.findMember(organizationId, userId)).thenReturn(Optional.empty());

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> appAccessQueryServiceImpl.verifyAppAccess(organizationId, userId, App.KAYA));

            assertEquals("APP_SEC_001", exception.getErrorCode());
            assertEquals(organizationId, exception.getErrorValues().get(OrganizationConstant.FIELD_ORGANIZATION_ID));
            assertEquals("KAYA", exception.getErrorValues().get(AccessConstant.FIELD_APP));
        }
    }

    @Nested
    class ListAccessibleApps {

        @Test
        void owner_listsEveryApp() {
            assertEquals(List.of(App.values()),
                    appAccessQueryServiceImpl.listAccessibleApps(activeMember(OrganizationMemberRole.OWNER)));
        }

        @Test
        void guestWithBothGrants_dropsKayaBelowMinimumRole() {
            OrganizationMember member = activeMember(OrganizationMemberRole.GUEST);
            when(appGrantRepository.existsGrantForUser(member.getOrganization().getId(),
                    member.getUser().getId(), App.KAZAN)).thenReturn(true);

            assertEquals(List.of(App.KAZAN), appAccessQueryServiceImpl.listAccessibleApps(member));
        }

        @Test
        void memberWithKayaGrantOnly_listsKaya() {
            OrganizationMember member = activeMember(OrganizationMemberRole.MEMBER);
            UUID organizationId = member.getOrganization().getId();
            UUID userId = member.getUser().getId();
            when(appGrantRepository.existsGrantForUser(organizationId, userId, App.KAZAN)).thenReturn(false);
            when(appGrantRepository.existsGrantForUser(organizationId, userId, App.KAYA)).thenReturn(true);

            assertEquals(List.of(App.KAYA), appAccessQueryServiceImpl.listAccessibleApps(member));
        }

        @Test
        void disabledMembership_listsNothing() {
            OrganizationMember member = member(organization(UUID.randomUUID(), OrganizationStatus.ACTIVE),
                    user("Ada", "Lovelace"), OrganizationMemberRole.OWNER, OrganizationMemberStatus.DISABLED);

            assertTrue(appAccessQueryServiceImpl.listAccessibleApps(member).isEmpty());
        }

        @Test
        void nullMember_listsNothing() {
            assertTrue(appAccessQueryServiceImpl.listAccessibleApps(null).isEmpty());
        }
    }

    @Nested
    class ListAccessibleAppsByOrganization {

        private OrganizationAppGrantProjection grantRow(UUID organizationId, App app) {
            return new OrganizationAppGrantProjection() {
                @Override
                public UUID getOrganizationId() {
                    return organizationId;
                }

                @Override
                public String getApp() {
                    return app.name();
                }
            };
        }

        private OrganizationMember membershipOf(User user, OrganizationStatus organizationStatus,
                                                OrganizationMemberRole role, OrganizationMemberStatus status) {
            OrganizationMember membership = member(organization(UUID.randomUUID(), organizationStatus), user, role,
                    status);
            user.getOrganizationMemberships().add(membership);
            return membership;
        }

        @Test
        void severalOrganizations_readsTheGrantsOnceAndAppliesTheRoleRule() {
            // Arrange
            User user = user("Ada", "Lovelace");
            OrganizationMember admin = membershipOf(user, OrganizationStatus.ACTIVE, OrganizationMemberRole.ADMIN,
                    OrganizationMemberStatus.ACTIVE);
            OrganizationMember guest = membershipOf(user, OrganizationStatus.ACTIVE, OrganizationMemberRole.GUEST,
                    OrganizationMemberStatus.ACTIVE);
            OrganizationMember owner = membershipOf(user, OrganizationStatus.ACTIVE, OrganizationMemberRole.OWNER,
                    OrganizationMemberStatus.ACTIVE);
            membershipOf(user, OrganizationStatus.ACTIVE, OrganizationMemberRole.MEMBER,
                    OrganizationMemberStatus.DISABLED);
            membershipOf(user, OrganizationStatus.DISABLED, OrganizationMemberRole.OWNER,
                    OrganizationMemberStatus.ACTIVE);
            UUID adminOrganizationId = admin.getOrganization().getId();
            UUID guestOrganizationId = guest.getOrganization().getId();
            UUID ownerOrganizationId = owner.getOrganization().getId();
            when(appGrantRepository.findGrantedAppsOfUser(user.getId(),
                    Set.of(adminOrganizationId, guestOrganizationId, ownerOrganizationId)))
                    .thenReturn(List.of(
                            grantRow(adminOrganizationId, App.OTAK),
                            grantRow(adminOrganizationId, App.KAZAN),
                            grantRow(guestOrganizationId, App.KAZAN),
                            grantRow(guestOrganizationId, App.KAYA)));

            // Act
            Map<UUID, List<App>> apps = appAccessQueryServiceImpl.listAccessibleAppsByOrganization(user);

            // Assert
            // A guest keeps no Kaya grant, an owner needs no grant, and a closed membership is left out.
            assertEquals(Map.of(
                    adminOrganizationId, List.of(App.KAZAN, App.OTAK),
                    guestOrganizationId, List.of(App.KAZAN),
                    ownerOrganizationId, List.of(App.values())), apps);
            verify(appGrantRepository, never()).existsGrantForUser(any(), any(), any());
        }

        @Test
        void noOpenMembership_returnsEmptyWithoutQuery() {
            // Arrange
            User user = user("Ada", "Lovelace");
            membershipOf(user, OrganizationStatus.DISABLED, OrganizationMemberRole.OWNER,
                    OrganizationMemberStatus.ACTIVE);

            // Act
            Map<UUID, List<App>> apps = appAccessQueryServiceImpl.listAccessibleAppsByOrganization(user);

            // Assert
            assertTrue(apps.isEmpty());
            verify(appGrantRepository, never()).findGrantedAppsOfUser(any(), anyCollection());
        }
    }

    @Nested
    class ListGrants {

        @Test
        void grants_areSortedByAppThenTargetNameIgnoringCase() {
            UUID organizationId = UUID.randomUUID();
            Organization organization = organization(organizationId, OrganizationStatus.ACTIVE);
            User zoe = user("Zoe", "Zed");
            OrganizationGroup group = OrganizationGroup.builder().organization(organization).name("beta").build();
            group.setId(UUID.randomUUID());
            AppGrant kayaZoe = AppGrant.builder().organization(organization).app(App.KAYA).user(zoe).build();
            AppGrant kazanGroup = AppGrant.builder().organization(organization).app(App.KAZAN).group(group)
                    .build();
            AppGrant kazanZoe = AppGrant.builder().organization(organization).app(App.KAZAN).user(zoe).build();
            AppGrant kazanAda = AppGrant.builder().organization(organization).app(App.KAZAN)
                    .user(user("Ada", "Lovelace")).build();
            when(appGrantRepository.findByOrganization_Id(organizationId))
                    .thenReturn(List.of(kayaZoe, kazanZoe, kazanGroup, kazanAda));

            List<AppGrantResponse> result = appAccessQueryServiceImpl.listGrants(user("Ada", "Lovelace"), organizationId);

            assertEquals(List.of(App.KAZAN, App.KAZAN, App.KAZAN, App.KAYA),
                    result.stream().map(AppGrantResponse::app).toList());
            assertEquals("Ada Lovelace", result.get(0).userFullName());
            assertEquals("beta", result.get(1).groupName());
            assertEquals(AppGrantTargetType.GROUP, result.get(1).targetType());
            assertEquals("Zoe Zed", result.get(2).userFullName());
            assertEquals(AppGrantTargetType.USER, result.get(2).targetType());
        }
    }

    @Nested
    class ListMemberAccess {

        private OrganizationMemberResponse response(UUID userId, String firstName, OrganizationMemberRole role,
                                                    OrganizationMemberStatus status) {
            return new OrganizationMemberResponse(UUID.randomUUID(), userId, firstName + "@example.com", firstName,
                    "Doe", role, status);
        }

        @Test
        void activeOrganization_listsActiveMembersWithTheirAppsSortedByName() {
            UUID organizationId = UUID.randomUUID();
            User actor = user("Actor", "One");
            UUID ownerId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            UUID disabledId = UUID.randomUUID();
            when(organizationQueryService.getOrganizationEntityById(organizationId))
                    .thenReturn(organization(organizationId, OrganizationStatus.ACTIVE));
            when(organizationMemberQueryService.listOrganizationMembers(actor, organizationId)).thenReturn(List.of(
                    response(memberId, "zed", OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE),
                    response(disabledId, "dis", OrganizationMemberRole.MEMBER, OrganizationMemberStatus.DISABLED),
                    response(ownerId, "Amy", OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE)));
            when(appGrantRepository.existsGrantForUser(organizationId, memberId, App.KAZAN)).thenReturn(true);
            when(appGrantRepository.existsGrantForUser(organizationId, memberId, App.KAYA)).thenReturn(false);

            List<MemberAppAccessResponse> result = appAccessQueryServiceImpl.listMemberAccess(actor, organizationId);

            assertEquals(2, result.size());
            assertEquals(ownerId, result.get(0).userId());
            assertEquals(List.of(App.values()), result.get(0).apps());
            assertEquals(memberId, result.get(1).userId());
            assertEquals(List.of(App.KAZAN), result.get(1).apps());
        }

        @Test
        void disabledOrganization_listsMembersWithNoAppsAndReadsNoGrants() {
            UUID organizationId = UUID.randomUUID();
            User actor = user("Actor", "One");
            when(organizationQueryService.getOrganizationEntityById(organizationId))
                    .thenReturn(organization(organizationId, OrganizationStatus.DISABLED));
            when(organizationMemberQueryService.listOrganizationMembers(actor, organizationId)).thenReturn(List.of(
                    response(UUID.randomUUID(), "Amy", OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE)));

            List<MemberAppAccessResponse> result = appAccessQueryServiceImpl.listMemberAccess(actor, organizationId);

            assertEquals(1, result.size());
            assertTrue(result.get(0).apps().isEmpty());
            verifyNoInteractions(appGrantRepository);
        }
    }

    @Nested
    class FindTargetUserIds {

        @Test
        void userGrant_returnsTheTargetUserWithoutAGroupLookup() {
            // Arrange
            User target = user("Ada", "Lovelace");
            AppGrant grant = AppGrant.builder().app(App.KAZAN).user(target).build();

            // Act
            Set<UUID> result = appAccessQueryServiceImpl.findTargetUserIds(grant);

            // Assert
            assertEquals(Set.of(target.getId()), result);
            verifyNoInteractions(organizationGroupQueryService);
        }

        @Test
        void groupGrant_returnsTheUsersTheGroupReaches() {
            // Arrange
            OrganizationGroup group = OrganizationGroup.builder().name("Engineering").build();
            group.setId(UUID.randomUUID());
            AppGrant grant = AppGrant.builder().app(App.KAZAN).group(group).build();
            Set<UUID> groupUsers = Set.of(UUID.randomUUID(), UUID.randomUUID());
            when(organizationGroupQueryService.findGroupUserIds(group)).thenReturn(groupUsers);

            // Act
            Set<UUID> result = appAccessQueryServiceImpl.findTargetUserIds(grant);

            // Assert
            assertEquals(groupUsers, result);
        }
    }

    @Nested
    class FindUserIdsWithAppAccess {

        @Test
        void noUsers_returnsEmptyWithoutAQuery() {
            // Act
            Set<UUID> result = appAccessQueryServiceImpl.findUserIdsWithAppAccess(UUID.randomUUID(), App.KAZAN,
                    List.of());

            // Assert
            assertEquals(Set.of(), result);
            verifyNoInteractions(appGrantRepository, organizationMemberQueryService);
        }

        @Test
        void appOpenToGuests_asksForEveryRoleAndReturnsTheHolders() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID holderId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            when(appGrantRepository.findUserIdsWithAppByAppName(organizationId, "KAZAN", ALL_ROLES,
                    Set.of(holderId, otherId))).thenReturn(List.of(holderId));

            // Act
            Set<UUID> result = appAccessQueryServiceImpl.findUserIdsWithAppAccess(organizationId, App.KAZAN,
                    List.of(holderId, otherId, holderId));

            // Assert
            assertEquals(Set.of(holderId), result);
        }

        @Test
        void appWithMemberMinimum_leavesTheGuestRoleOutOfTheQuery() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(appGrantRepository.findUserIdsWithAppByAppName(organizationId, "KAYA", MEMBER_AND_ABOVE,
                    Set.of(userId))).thenReturn(List.of());

            // Act
            Set<UUID> result = appAccessQueryServiceImpl.findUserIdsWithAppAccess(organizationId, App.KAYA,
                    List.of(userId));

            // Assert
            assertEquals(Set.of(), result);
        }
    }

    @Nested
    class FindAppHolders {

        @Test
        void mixedUsersAndApps_returnsTheHoldersOfEachAppAndLeavesOutAppsNobodyHolds() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID kazanHolderId = UUID.randomUUID();
            UUID bothHolderId = UUID.randomUUID();
            Set<UUID> userIds = Set.of(kazanHolderId, bothHolderId);
            when(appGrantRepository.findUserIdsWithAppByAppName(organizationId, "KAZAN", ALL_ROLES, userIds))
                    .thenReturn(List.of(kazanHolderId, bothHolderId));
            when(appGrantRepository.findUserIdsWithAppByAppName(organizationId, "KAYA", MEMBER_AND_ABOVE, userIds))
                    .thenReturn(List.of(bothHolderId));
            when(appGrantRepository.findUserIdsWithAppByAppName(organizationId, "OTAK", MEMBER_AND_ABOVE, userIds))
                    .thenReturn(List.of());

            // Act
            Map<App, Set<UUID>> result = appAccessQueryServiceImpl.findAppHolders(organizationId,
                    List.of(App.KAZAN, App.KAYA, App.OTAK), List.of(kazanHolderId, bothHolderId));

            // Assert
            assertEquals(Map.of(App.KAZAN, Set.of(kazanHolderId, bothHolderId), App.KAYA, Set.of(bothHolderId)),
                    result);
        }

        @Test
        void noApps_returnsEmptyWithoutAnyAccessCheck() {
            // Act
            Map<App, Set<UUID>> result = appAccessQueryServiceImpl.findAppHolders(UUID.randomUUID(), List.of(),
                    List.of(UUID.randomUUID()));

            // Assert
            assertEquals(Map.of(), result);
            verifyNoInteractions(organizationMemberQueryService, appGrantRepository);
        }
    }
}
