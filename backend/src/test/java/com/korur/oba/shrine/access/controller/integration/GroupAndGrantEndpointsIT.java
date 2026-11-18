package com.korur.oba.shrine.access.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// 3. Java SDK
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.connect.realtime.service.RealtimeTicketStore;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The group and grant endpoints end to end: the role gate, the status codes, the refusal of another
 * organization's ids, the Everyone group that never changes, soft delete and re-creation, the app
 * revoke events of grant, group and member role writes, and the realtime layer acting on a Kazan loss
 * and not on a Kaya loss.
 */
@RecordApplicationEvents
class GroupAndGrantEndpointsIT extends AbstractTenantIT {

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private RealtimeTicketStore realtimeTicketStore;

    private Tenant tenant;
    private Tenant other;
    private User admin;
    private User member;

    @BeforeEach
    void setUp() {
        tenant = newTenant();
        other = newTenant();
        admin = tenant.admin().getUser();
        member = tenant.member().getUser();
    }

    private String groupsPath(Tenant of) {
        return "/organizations/" + of.id() + "/groups";
    }

    private String grantsPath(Tenant of) {
        return "/organizations/" + of.id() + "/app-grants";
    }

    private ResultActions call(Tenant of, User caller, MockHttpServletRequestBuilder builder, String body) throws Exception {
        MockHttpServletRequestBuilder prepared = builder.with(jwtOf(caller)).header("X-Organization-ID", of.id());
        return mockMvc.perform(body == null ? prepared : prepared.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private UUID createGroupThroughApi(Tenant of, User caller, String name) throws Exception {
        String body = call(of, caller, MockMvcRequestBuilders.post(groupsPath(of)),
                "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private UUID everyoneGroupId(Tenant of) {
        return jdbcTemplate.queryForObject("SELECT id FROM shrine.organization_groups "
                + "WHERE organization_id = ? AND type = 'EVERYONE'::shrine_organization_group_type_enum AND deleted = FALSE",
                UUID.class, of.organization().getId());
    }

    private UUID grantIdOf(UUID groupId, String app) {
        return jdbcTemplate.queryForObject("SELECT id FROM shrine.organization_app_grants "
                + "WHERE group_id = ? AND app = ?::shrine_app_grant_app_enum AND deleted = FALSE", UUID.class, groupId, app);
    }

    private void addDirectGrant(Tenant of, User user, String app) {
        jdbcTemplate.update("INSERT INTO shrine.organization_app_grants (organization_id, app, user_id) "
                + "VALUES (?, ?::shrine_app_grant_app_enum, ?)", of.organization().getId(), app, user.getId());
    }

    private Set<UUID> revokedUsers(App app) {
        return applicationEvents.stream(AppAccessRevokedEvent.class)
                .filter((AppAccessRevokedEvent event) -> event.app() == app)
                .map(AppAccessRevokedEvent::userId)
                .collect(Collectors.toSet());
    }

    private Map<App, Set<UUID>> revokedUsersByApp() {
        return applicationEvents.stream(AppAccessRevokedEvent.class)
                .collect(Collectors.groupingBy(AppAccessRevokedEvent::app,
                        Collectors.mapping(AppAccessRevokedEvent::userId, Collectors.toSet())));
    }

    private void issueRealtimeTicket(Tenant of, User user) throws Exception {
        // The ticket lives as long as the access token, so the token of this request carries an expiry.
        mockMvc.perform(MockMvcRequestBuilders.post("/realtime/tickets")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt
                                .subject(user.getKeycloakId())
                                .claim("email", user.getEmail())
                                .claim("email_verified", true)
                                .expiresAt(Instant.now().plusSeconds(3_600))))
                        .header("X-Organization-ID", of.id()))
                .andExpect(status().isCreated());
    }

    @Test
    void groupEndpoints_memberRole_answers403WithInsufficientPermissions() throws Exception {
        call(tenant, member, MockMvcRequestBuilders.get(groupsPath(tenant)), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        call(tenant, member, MockMvcRequestBuilders.post(groupsPath(tenant)), "{\"name\":\"Eng\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        call(tenant, member, MockMvcRequestBuilders.get(grantsPath(tenant)), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        call(tenant, member, MockMvcRequestBuilders.get(grantsPath(tenant) + "/app-access"), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
    }

    @Test
    void createGroup_adminAndOwner_answers201AndListsTheGroup() throws Exception {
        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant)),
                "{\"name\":\"  Engineering  \",\"description\":\"Developers\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Engineering"))
                .andExpect(jsonPath("$.data.type").value("CUSTOM"))
                .andExpect(jsonPath("$.data.memberCount").value(0));
        call(tenant, tenant.owner().getUser(), MockMvcRequestBuilders.post(groupsPath(tenant)),
                "{\"name\":\"Support\"}")
                .andExpect(status().isCreated());

        call(tenant, admin, MockMvcRequestBuilders.get(groupsPath(tenant)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void createGroup_nameDiffersOnlyByCaseAndSpaces_answers409NameTaken() throws Exception {
        createGroupThroughApi(tenant, admin, "Engineering");

        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant)), "{\"name\":\"  engineering \"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APP_STA_002"));
        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant)), "{\"name\":\"everyone\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APP_STA_002"));
        // The same name is free in another organization.
        call(other, other.admin().getUser(), MockMvcRequestBuilders.post(groupsPath(other)), "{\"name\":\"Engineering\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void createGroup_blankName_answers422() throws Exception {
        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant)), "{\"name\":\"   \"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateGroup_renameToTakenNameAfterTrim_answers409AndOwnNameStaysAllowed() throws Exception {
        createGroupThroughApi(tenant, admin, "Engineering");
        UUID support = createGroupThroughApi(tenant, admin, "Support");

        call(tenant, admin, MockMvcRequestBuilders.put(groupsPath(tenant) + "/" + support), "{\"name\":\" ENGINEERING \"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APP_STA_002"));
        call(tenant, admin, MockMvcRequestBuilders.put(groupsPath(tenant) + "/" + support), "{\"name\":\" support \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("support"));
    }

    @Test
    void everyoneGroup_updateDeleteAddAndRemoveMember_answer409SystemGroupImmutable() throws Exception {
        UUID everyone = everyoneGroupId(tenant);
        String base = groupsPath(tenant) + "/" + everyone;

        call(tenant, admin, MockMvcRequestBuilders.put(base), "{\"name\":\"All\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APP_STA_001"));
        call(tenant, admin, MockMvcRequestBuilders.delete(base), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APP_STA_001"));
        call(tenant, admin, MockMvcRequestBuilders.post(base + "/members"), "{\"userIds\":[\"" + member.getId() + "\"]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APP_STA_001"));
        call(tenant, admin, MockMvcRequestBuilders.delete(base + "/members/" + member.getId()), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APP_STA_001"));
    }

    @Test
    void groupOfAnotherOrganization_everyHandlerAnswers404GroupNotFound() throws Exception {
        UUID foreignGroup = createGroupThroughApi(other, other.admin().getUser(), "Foreign");
        String base = groupsPath(tenant) + "/" + foreignGroup;
        String addBody = "{\"userIds\":[\"" + member.getId() + "\"]}";

        call(tenant, admin, MockMvcRequestBuilders.put(base), "{\"name\":\"Mine\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("APP_NOT_001"));
        call(tenant, admin, MockMvcRequestBuilders.delete(base), null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("APP_NOT_001"));
        call(tenant, admin, MockMvcRequestBuilders.get(base + "/members"), null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("APP_NOT_001"));
        call(tenant, admin, MockMvcRequestBuilders.post(base + "/members"), addBody)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("APP_NOT_001"));
        call(tenant, admin, MockMvcRequestBuilders.delete(base + "/members/" + member.getId()), null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("APP_NOT_001"));
        assertEquals(1, count("SELECT count(*) FROM shrine.organization_groups WHERE id = ?::uuid AND deleted = FALSE",
                foreignGroup.toString()));
    }

    @Test
    void addMembers_userOfAnotherOrganization_answers404AndWritesNothing() throws Exception {
        UUID group = createGroupThroughApi(tenant, admin, "Engineering");
        User stranger = other.member().getUser();

        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant) + "/" + group + "/members"),
                "{\"userIds\":[\"" + member.getId() + "\",\"" + stranger.getId() + "\"]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORG_NOT_002"));
        assertEquals(0, count("SELECT count(*) FROM shrine.organization_group_members WHERE group_id = ?::uuid",
                group.toString()));
    }

    @Test
    void groupMembers_removeAndAddAgain_softDeletesThenRestoresAMemberRow() throws Exception {
        UUID group = createGroupThroughApi(tenant, admin, "Engineering");
        String members = groupsPath(tenant) + "/" + group + "/members";
        String addBody = "{\"userIds\":[\"" + member.getId() + "\"]}";

        call(tenant, admin, MockMvcRequestBuilders.post(members), addBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(member.getId().toString()));
        call(tenant, admin, MockMvcRequestBuilders.delete(members + "/" + member.getId()), null)
                .andExpect(status().isOk());
        assertEquals(1, count("SELECT count(*) FROM shrine.organization_group_members "
                + "WHERE group_id = ?::uuid AND deleted = TRUE", group.toString()));
        call(tenant, admin, MockMvcRequestBuilders.delete(members + "/" + member.getId()), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("APP_NOT_003"));

        call(tenant, admin, MockMvcRequestBuilders.post(members), addBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        assertEquals(1, count("SELECT count(*) FROM shrine.organization_group_members "
                + "WHERE group_id = ?::uuid AND deleted = FALSE", group.toString()));
    }

    @Test
    void deleteGroup_thenCreateSameName_softDeletesMembersAndGrantsAndAllowsTheName() throws Exception {
        UUID group = createGroupThroughApi(tenant, admin, "Engineering");
        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant) + "/" + group + "/members"),
                "{\"userIds\":[\"" + member.getId() + "\"]}").andExpect(status().isOk());
        call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)),
                "{\"app\":\"KAZAN\",\"groupId\":\"" + group + "\"}").andExpect(status().isCreated());

        call(tenant, admin, MockMvcRequestBuilders.delete(groupsPath(tenant) + "/" + group), null)
                .andExpect(status().isOk());

        assertEquals(1, count("SELECT count(*) FROM shrine.organization_groups WHERE id = ?::uuid AND deleted = TRUE",
                group.toString()));
        assertEquals(0, count("SELECT count(*) FROM shrine.organization_group_members WHERE group_id = ?::uuid "
                + "AND deleted = FALSE", group.toString()));
        assertEquals(0, count("SELECT count(*) FROM shrine.organization_app_grants WHERE group_id = ?::uuid "
                + "AND deleted = FALSE", group.toString()));
        call(tenant, admin, MockMvcRequestBuilders.get(groupsPath(tenant) + "/" + group + "/members"), null)
                .andExpect(status().isNotFound());

        UUID again = createGroupThroughApi(tenant, admin, "Engineering");
        assertNotEquals(group, again);
    }

    @Test
    void grantApp_toUserAndGroup_answers201AndRepeatingReturnsTheSameGrant() throws Exception {
        UUID group = createGroupThroughApi(tenant, admin, "Engineering");
        String userBody = "{\"app\":\"KAZAN\",\"userId\":\"" + member.getId() + "\"}";

        String first = call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)), userBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.app").value("KAZAN"))
                .andExpect(jsonPath("$.data.userId").value(member.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        String second = call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)), userBody)
                .andReturn().getResponse().getContentAsString();
        assertEquals((String) JsonPath.read(first, "$.data.id"),
                JsonPath.read(second, "$.data.id"));
        assertEquals(1, count("SELECT count(*) FROM shrine.organization_app_grants WHERE user_id = ?::uuid "
                + "AND app = 'KAZAN'::shrine_app_grant_app_enum AND deleted = FALSE", member.getId().toString()));

        call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)),
                "{\"app\":\"KAYA\",\"groupId\":\"" + group + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.groupId").value(group.toString()))
                .andExpect(jsonPath("$.data.groupName").value("Engineering"));
    }

    @Test
    void grantApp_noTargetOrBothTargets_answers422TargetInvalid() throws Exception {
        UUID group = createGroupThroughApi(tenant, admin, "Engineering");

        call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)), "{\"app\":\"KAZAN\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("APP_VAL_001"));
        call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)),
                "{\"app\":\"KAZAN\",\"userId\":\"" + member.getId() + "\",\"groupId\":\"" + group + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("APP_VAL_001"));
    }

    @Test
    void grantApp_unknownApp_answers400() throws Exception {
        call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)),
                "{\"app\":\"UNKNOWN\",\"userId\":\"" + member.getId() + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void grantApp_groupOrUserOfAnotherOrganization_answers404() throws Exception {
        UUID foreignGroup = createGroupThroughApi(other, other.admin().getUser(), "Foreign");

        call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)),
                "{\"app\":\"KAZAN\",\"groupId\":\"" + foreignGroup + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("APP_NOT_001"));
        call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)),
                "{\"app\":\"KAZAN\",\"userId\":\"" + other.member().getUser().getId() + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORG_NOT_002"));
    }

    @Test
    void revokeGrant_grantOfAnotherOrganization_answers404GrantNotFound() throws Exception {
        UUID foreignGrant = grantIdOf(everyoneGroupId(other), "KAZAN");

        call(tenant, admin, MockMvcRequestBuilders.delete(grantsPath(tenant) + "/" + foreignGrant), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("APP_NOT_002"));
        assertEquals(1, count("SELECT count(*) FROM shrine.organization_app_grants WHERE id = ?::uuid AND deleted = FALSE",
                foreignGrant.toString()));
    }

    @Test
    void revokeGrant_thenGrantAgain_softDeletesTheOldRowAndCreatesANewOne() throws Exception {
        String body = "{\"app\":\"KAYA\",\"userId\":\"" + member.getId() + "\"}";
        String created = call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String grantId = JsonPath.read(created, "$.data.id");

        call(tenant, admin, MockMvcRequestBuilders.delete(grantsPath(tenant) + "/" + grantId), null)
                .andExpect(status().isOk());
        assertEquals(1, count("SELECT count(*) FROM shrine.organization_app_grants WHERE id = ?::uuid AND deleted = TRUE",
                grantId));

        String again = call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertNotEquals(grantId, (String) JsonPath.read(again, "$.data.id"));
    }

    @Test
    void listMemberAccess_reflectsEveryoneGrantsAndTheOwnerRole() throws Exception {
        call(tenant, admin, MockMvcRequestBuilders.get(grantsPath(tenant) + "/app-access"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == '" + member.getId() + "')].apps.length()")
                        .value(App.values().length));
    }

    @Test
    void revokeEveryoneKazan_publishesTheKazanEventOnlyForMembersWhoLostKazan() throws Exception {
        User keeper = signInMember("keeper");
        addDirectGrant(tenant, keeper, "KAZAN");
        UUID everyoneKazan = grantIdOf(everyoneGroupId(tenant), "KAZAN");

        call(tenant, admin, MockMvcRequestBuilders.delete(grantsPath(tenant) + "/" + everyoneKazan), null)
                .andExpect(status().isOk());

        Set<UUID> revoked = revokedUsers(App.KAZAN);
        assertTrue(revoked.contains(member.getId()), "the MEMBER lost Kazan");
        assertTrue(revoked.contains(admin.getId()), "the ADMIN lost Kazan");
        assertTrue(!revoked.contains(keeper.getId()), "a direct grant keeps Kazan, so no event");
        assertTrue(!revoked.contains(tenant.owner().getUser().getId()), "the OWNER holds Kazan through the role");
        assertEquals(Set.of(App.KAZAN), revokedUsersByApp().keySet(), "only the revoked app is announced");
        assertEquals(0, applicationEvents.stream(AccessRevokedEvent.class).count(),
                "the members keep the organization, so no organization access event");
    }

    @Test
    void revokeEveryoneKaya_publishesOnlyTheKayaEvent() throws Exception {
        UUID everyoneKaya = grantIdOf(everyoneGroupId(tenant), "KAYA");

        call(tenant, admin, MockMvcRequestBuilders.delete(grantsPath(tenant) + "/" + everyoneKaya), null)
                .andExpect(status().isOk());

        assertEquals(Set.of(App.KAYA), revokedUsersByApp().keySet());
        assertTrue(revokedUsers(App.KAYA).contains(member.getId()), "the MEMBER lost Kaya");
        assertEquals(0, applicationEvents.stream(AccessRevokedEvent.class).count());
    }

    @Test
    void revokeKayaThenKazan_onlyTheKazanLossDropsTheMembersRealtimeTicket() throws Exception {
        issueRealtimeTicket(tenant, member);
        assertEquals(1, realtimeTicketStore.countLive(member.getId(), Instant.now()));

        call(tenant, admin, MockMvcRequestBuilders.delete(grantsPath(tenant) + "/"
                + grantIdOf(everyoneGroupId(tenant), "KAYA")), null).andExpect(status().isOk());

        assertEquals(1, realtimeTicketStore.countLive(member.getId(), Instant.now()),
                "Kaya sends no realtime signals, so its loss keeps the ticket");

        call(tenant, admin, MockMvcRequestBuilders.delete(grantsPath(tenant) + "/"
                + grantIdOf(everyoneGroupId(tenant), "KAZAN")), null).andExpect(status().isOk());

        assertEquals(0, realtimeTicketStore.countLive(member.getId(), Instant.now()),
                "the Kazan loss drops the ticket after the commit");
    }

    @Test
    void deleteKazanGroup_publishesTheKazanEventOnlyForUsersWhoLostKazan() throws Exception {
        // Start without Everyone's Kazan grant so that the group is the only source.
        call(tenant, admin, MockMvcRequestBuilders.delete(grantsPath(tenant) + "/"
                + grantIdOf(everyoneGroupId(tenant), "KAZAN")), null).andExpect(status().isOk());
        User holder = signInMember("holder");
        User keeper = signInMember("keeper");
        addDirectGrant(tenant, keeper, "KAZAN");
        UUID group = createGroupThroughApi(tenant, admin, "Engineering");
        call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)),
                "{\"app\":\"KAZAN\",\"groupId\":\"" + group + "\"}").andExpect(status().isCreated());
        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant) + "/" + group + "/members"),
                "{\"userIds\":[\"" + holder.getId() + "\",\"" + keeper.getId() + "\"]}").andExpect(status().isOk());
        applicationEvents.clear();

        call(tenant, admin, MockMvcRequestBuilders.delete(groupsPath(tenant) + "/" + group), null)
                .andExpect(status().isOk());

        assertEquals(Map.of(App.KAZAN, Set.of(holder.getId())), revokedUsersByApp());
    }

    @Test
    void removeUserFromKazanGroup_publishesTheKazanEventForThatUser() throws Exception {
        // Start without Everyone's Kazan grant so that the group is the only source.
        call(tenant, admin, MockMvcRequestBuilders.delete(grantsPath(tenant) + "/"
                + grantIdOf(everyoneGroupId(tenant), "KAZAN")), null).andExpect(status().isOk());
        User holder = signInMember("holder");
        UUID group = createGroupThroughApi(tenant, admin, "Engineering");
        call(tenant, admin, MockMvcRequestBuilders.post(grantsPath(tenant)),
                "{\"app\":\"KAZAN\",\"groupId\":\"" + group + "\"}").andExpect(status().isCreated());
        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant) + "/" + group + "/members"),
                "{\"userIds\":[\"" + holder.getId() + "\"]}").andExpect(status().isOk());
        applicationEvents.clear();

        call(tenant, admin, MockMvcRequestBuilders.delete(groupsPath(tenant) + "/" + group + "/members/"
                + holder.getId()), null).andExpect(status().isOk());

        assertEquals(Map.of(App.KAZAN, Set.of(holder.getId())), revokedUsersByApp());
    }

    @Test
    void removeMember_dropsGroupRowsAndDirectGrants() throws Exception {
        UUID group = createGroupThroughApi(tenant, admin, "Engineering");
        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant) + "/" + group + "/members"),
                "{\"userIds\":[\"" + member.getId() + "\"]}").andExpect(status().isOk());
        addDirectGrant(tenant, member, "KAYA");

        call(tenant, admin, MockMvcRequestBuilders.delete("/organizations/" + tenant.id() + "/members/" + member.getId()), null)
                .andExpect(status().isOk());

        assertEquals(0, count("SELECT count(*) FROM shrine.organization_group_members WHERE group_id = ?::uuid "
                + "AND user_id = ?::uuid AND deleted = FALSE", group.toString(), member.getId().toString()));
        assertEquals(0, count("SELECT count(*) FROM shrine.organization_app_grants WHERE organization_id = ?::uuid "
                + "AND user_id = ?::uuid AND deleted = FALSE", tenant.id(), member.getId().toString()));
    }

    @Test
    void leaveOrganization_dropsGroupRowsAndDirectGrantsOfTheLeavingUser() throws Exception {
        UUID group = createGroupThroughApi(tenant, admin, "Engineering");
        call(tenant, admin, MockMvcRequestBuilders.post(groupsPath(tenant) + "/" + group + "/members"),
                "{\"userIds\":[\"" + member.getId() + "\"]}").andExpect(status().isOk());
        addDirectGrant(tenant, member, "KAYA");

        call(tenant, member, MockMvcRequestBuilders.delete("/organizations/" + tenant.id() + "/members/me"), null)
                .andExpect(status().isOk());

        assertEquals(0, count("SELECT count(*) FROM shrine.organization_group_members WHERE group_id = ?::uuid "
                + "AND user_id = ?::uuid AND deleted = FALSE", group.toString(), member.getId().toString()));
        assertEquals(0, count("SELECT count(*) FROM shrine.organization_app_grants WHERE organization_id = ?::uuid "
                + "AND user_id = ?::uuid AND deleted = FALSE", tenant.id(), member.getId().toString()));
    }

    private ResultActions changeRole(Tenant of, User actor, User target, OrganizationMemberRole role) throws Exception {
        return call(of, actor, MockMvcRequestBuilders.patch("/organizations/" + of.id() + "/members/"
                + target.getId() + "/role"), "{\"role\":\"" + role.name() + "\"}");
    }

    @Test
    void changeRole_ownerToAdminWithoutGrants_publishesEveryAppAndDropsTheRealtimeTicket() throws Exception {
        Tenant ungranted = newTenantWithoutGrants();
        String suffix = uniqueSuffix();
        User secondOwner = createUser("kc-" + suffix, "owner" + suffix + "@" + ungranted.domain());
        createMember(ungranted.organization(), secondOwner, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE);
        issueRealtimeTicket(ungranted, secondOwner);
        assertEquals(1, realtimeTicketStore.countLive(secondOwner.getId(), Instant.now()));
        applicationEvents.clear();

        changeRole(ungranted, ungranted.owner().getUser(), secondOwner, OrganizationMemberRole.ADMIN)
                .andExpect(status().isOk());

        Map<App, Set<UUID>> expected = Arrays.stream(App.values())
                .collect(Collectors.toMap(Function.identity(), (App app) -> Set.of(secondOwner.getId())));
        assertEquals(expected, revokedUsersByApp(), "an ADMIN without a grant holds no app");
        assertEquals(0, realtimeTicketStore.countLive(secondOwner.getId(), Instant.now()),
                "the Kazan loss drops the ticket after the commit");
        assertEquals(0, applicationEvents.stream(AccessRevokedEvent.class).count(),
                "the member keeps the organization, so no organization access event");
    }

    @Test
    void changeRole_memberToGuest_publishesOnlyKayaAndOtakAndKeepsTheRealtimeTicket() throws Exception {
        issueRealtimeTicket(tenant, member);
        applicationEvents.clear();

        changeRole(tenant, admin, member, OrganizationMemberRole.GUEST).andExpect(status().isOk());

        assertEquals(Map.of(App.KAYA, Set.of(member.getId()), App.OTAK, Set.of(member.getId())),
                revokedUsersByApp(), "a GUEST keeps Kazan and Kalkan through the Everyone grants");
        assertEquals(1, realtimeTicketStore.countLive(member.getId(), Instant.now()),
                "the member keeps Kazan, so the ticket stays");
    }

    @Test
    void changeRole_memberToAdmin_publishesNoAppEvent() throws Exception {
        changeRole(tenant, admin, member, OrganizationMemberRole.ADMIN).andExpect(status().isOk());

        assertEquals(0, applicationEvents.stream(AppAccessRevokedEvent.class).count());
    }

    private User signInMember(String localPart) {
        String suffix = uniqueSuffix();
        User user = createUser("kc-" + suffix, localPart + suffix + "@" + tenant.domain());
        createMember(tenant.organization(), user, OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE);
        return user;
    }
}
