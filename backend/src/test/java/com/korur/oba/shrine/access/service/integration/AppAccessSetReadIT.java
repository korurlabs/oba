package com.korur.oba.shrine.access.service.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * The set read of app access answers, for every user and every app, exactly what the single check
 * hasAppAccess answers: one rule, read two ways. The users cover every branch of the rule: the OWNER
 * role, a direct grant, the Everyone group, a custom group, a role below the app's minimum, a disabled
 * membership, a deleted grant, a deleted group, a deleted group member row, a user of another
 * organization and a disabled organization.
 */
class AppAccessSetReadIT extends AbstractTenantIT {

    @Autowired
    private AppAccessQueryService appAccessQueryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Tenant tenant;
    private UUID organizationId;
    private final List<UUID> userIds = new ArrayList<>();

    private UUID ownerId;
    private UUID directKazanGuestId;
    private UUID directKayaGuestId;
    private UUID customGroupMemberId;
    private UUID removedGroupMemberId;
    private UUID deletedGroupMemberId;
    private UUID everyoneMemberId;
    private UUID disabledMemberId;
    private UUID deletedGrantMemberId;
    private UUID outsiderId;

    @BeforeEach
    void setUp() {
        tenant = newTenantWithoutGrants();
        organizationId = tenant.organization().getId();

        ownerId = tenant.owner().getUser().getId();
        directKazanGuestId = member(OrganizationMemberRole.GUEST, OrganizationMemberStatus.ACTIVE);
        directKayaGuestId = member(OrganizationMemberRole.GUEST, OrganizationMemberStatus.ACTIVE);
        customGroupMemberId = member(OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE);
        removedGroupMemberId = member(OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE);
        deletedGroupMemberId = member(OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);
        everyoneMemberId = tenant.member().getUser().getId();
        disabledMemberId = member(OrganizationMemberRole.MEMBER, OrganizationMemberStatus.DISABLED);
        deletedGrantMemberId = member(OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE);
        outsiderId = createUser().getId();
        userIds.addAll(List.of(ownerId, tenant.admin().getUser().getId(), directKazanGuestId, directKayaGuestId,
                customGroupMemberId, removedGroupMemberId, deletedGroupMemberId, everyoneMemberId, disabledMemberId,
                deletedGrantMemberId, outsiderId));

        directGrant(directKazanGuestId, "KAZAN", false);
        // A guest is below Kaya's minimum role, so this grant opens nothing.
        directGrant(directKayaGuestId, "KAYA", false);
        directGrant(disabledMemberId, "KAZAN", false);
        directGrant(deletedGrantMemberId, "KAZAN", true);
        // A grant to a user of another organization opens nothing in this one.
        directGrant(outsiderId, "KAZAN", false);

        UUID customGroup = group("CUSTOM", false);
        groupMember(customGroup, customGroupMemberId, false);
        groupMember(customGroup, removedGroupMemberId, true);
        groupGrant(customGroup, "KAZAN");
        groupGrant(customGroup, "KAYA");

        UUID deletedGroup = group("CUSTOM", true);
        groupMember(deletedGroup, deletedGroupMemberId, false);
        groupGrant(deletedGroup, "KAZAN");

        UUID everyone = group("EVERYONE", false);
        groupGrant(everyone, "OTAK");
    }

    private UUID member(OrganizationMemberRole role, OrganizationMemberStatus status) {
        User user = createUser();
        createMember(tenant.organization(), user, role, status);
        return user.getId();
    }

    private void directGrant(UUID userId, String app, boolean deleted) {
        jdbcTemplate.update("INSERT INTO shrine.organization_app_grants (organization_id, app, user_id, deleted) "
                + "VALUES (?, ?::shrine_app_grant_app_enum, ?, ?)", organizationId, app, userId, deleted);
    }

    private UUID group(String type, boolean deleted) {
        return jdbcTemplate.queryForObject("INSERT INTO shrine.organization_groups (organization_id, name, type, deleted) "
                + "VALUES (?, ?, ?::shrine_organization_group_type_enum, ?) RETURNING id", UUID.class,
                organizationId, "Group " + uniqueSuffix(), type, deleted);
    }

    private void groupMember(UUID groupId, UUID userId, boolean deleted) {
        jdbcTemplate.update("INSERT INTO shrine.organization_group_members (group_id, user_id, deleted) "
                + "VALUES (?, ?, ?)", groupId, userId, deleted);
    }

    private void groupGrant(UUID groupId, String app) {
        jdbcTemplate.update("INSERT INTO shrine.organization_app_grants (organization_id, app, group_id) "
                + "VALUES (?, ?::shrine_app_grant_app_enum, ?)", organizationId, app, groupId);
    }

    private Set<UUID> holdersBySingleCheck(UUID organization, App app, List<UUID> users) {
        Set<UUID> holders = new HashSet<>();
        for (UUID userId : users) {
            if (appAccessQueryService.hasAppAccess(organization, userId, app)) {
                holders.add(userId);
            }
        }
        return holders;
    }

    @Test
    void findUserIdsWithAppAccess_everyApp_matchesHasAppAccessForEveryUser() {
        for (App app : App.values()) {
            // Act
            Set<UUID> setRead = appAccessQueryService.findUserIdsWithAppAccess(organizationId, app, userIds);

            // Assert
            assertEquals(holdersBySingleCheck(organizationId, app, userIds), setRead, "app " + app);
        }
    }

    @Test
    void findUserIdsWithAppAccess_kazan_isTheOwnerTheDirectGuestAndTheCustomGroupMember() {
        // Act
        Set<UUID> holders = appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAZAN, userIds);

        // Assert
        assertEquals(Set.of(ownerId, directKazanGuestId, customGroupMemberId), holders);
    }

    @Test
    void findUserIdsWithAppAccess_kaya_leavesOutTheGuestWithAGrant() {
        // Act
        Set<UUID> holders = appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAYA, userIds);

        // Assert
        assertEquals(Set.of(ownerId, customGroupMemberId), holders);
    }

    @Test
    void findUserIdsWithAppAccess_disabledOrganization_matchesHasAppAccessAndHoldsNobody() {
        // Arrange
        Organization disabled = createOrganization(OrganizationStatus.DISABLED);
        grantEveryAppToEveryone(disabled);
        User owner = createUser();
        createMember(disabled, owner, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE);
        User member = createUser();
        createMember(disabled, member, OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE);
        List<UUID> users = List.of(owner.getId(), member.getId());

        for (App app : App.values()) {
            // Act
            Set<UUID> setRead = appAccessQueryService.findUserIdsWithAppAccess(disabled.getId(), app, users);

            // Assert
            assertEquals(holdersBySingleCheck(disabled.getId(), app, users), setRead, "app " + app);
            assertEquals(Set.of(), setRead, "app " + app);
        }
    }

    @Test
    void listAccessibleAppsByOrganization_everyUser_matchesHasAppAccessForEveryApp() {
        for (UUID userId : userIds) {
            // Arrange
            List<App> expected = new ArrayList<>();
            for (App app : App.values()) {
                if (appAccessQueryService.hasAppAccess(organizationId, userId, app)) {
                    expected.add(app);
                }
            }

            // Act
            // The memberships of the user load lazily, so the read runs in a transaction as in the services.
            Map<UUID, List<App>> apps = new TransactionTemplate(transactionManager).execute(status ->
                    appAccessQueryService.listAccessibleAppsByOrganization(userRepository.findById(userId).orElseThrow()));

            // Assert
            assertEquals(expected, apps.getOrDefault(organizationId, List.of()), "user " + userId);
        }
    }
}
