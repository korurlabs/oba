package com.korur.oba.apps.kaizen.organizationsetting.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.organizationsetting.service.OrganizationSettingQueryService;
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.identity.entity.User;

/**
 * The security board endpoints against the Flyway-migrated database: only an ADMIN or
 * OWNER of the organization may set the board, a board that does not exist or that the caller is
 * not a member of reads as not found, a null board clears it, and the first write creates the
 * settings row that later writes update. The deprecated platform path must behave the same.
 */
class OrganizationSettingControllerIT extends AbstractTenantIT {

    private static final String PATH = "/kaizen/organizations/%s/security-board";
    private static final String LEGACY_PATH = "/organizations/%s/security-board";

    @Autowired
    private OrganizationSettingQueryService organizationSettingQueryService;

    /** A board with its default backlog, which every board loads together with itself. */
    private UUID createBoard(Tenant tenant) {
        String suffix = uniqueSuffix();
        UUID boardId = jdbcTemplate.queryForObject("INSERT INTO kaizen.boards (id, name, prefix, slug, organization_id) "
                + "VALUES (gen_random_uuid(), ?, ?, ?, ?) RETURNING id", UUID.class,
                "Board " + suffix, suffix.substring(0, 8).toUpperCase(), "board-" + suffix,
                tenant.organization().getId());
        jdbcTemplate.update("INSERT INTO kaizen.backlogs (id, board_id) VALUES (gen_random_uuid(), ?)", boardId);
        return boardId;
    }

    private void addBoardMember(UUID boardId, User user) {
        jdbcTemplate.update("INSERT INTO kaizen.board_members (id, role, board_id, user_id) "
                + "VALUES (gen_random_uuid(), 'MEMBER'::kaizen_board_member_role_enum, ?, ?)", boardId, user.getId());
    }

    private UUID createBoardOf(Tenant tenant, User user) {
        UUID boardId = createBoard(tenant);
        addBoardMember(boardId, user);
        return boardId;
    }

    private static String body(UUID boardId) {
        return boardId == null ? "{\"boardId\":null}" : "{\"boardId\":\"" + boardId + "\"}";
    }

    private List<UUID> storedBoardIds(Tenant tenant) {
        return jdbcTemplate.query("SELECT security_board_id FROM kaizen.organization_settings WHERE organization_id = ?",
                (rows, index) -> rows.getObject(1, UUID.class), tenant.organization().getId());
    }

    private MockHttpServletRequestBuilder call(String path, Tenant tenant, User caller, UUID boardId) {
        return in(tenant, put(path.formatted(tenant.id())), jwtOf(caller), body(boardId));
    }

    @Test
    void updateSecurityBoard_adminAndBoardMember_createsSettingRow() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        UUID boardId = createBoardOf(tenant, admin);

        mockMvc.perform(call(PATH, tenant, admin, boardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationId").value(tenant.id()))
                .andExpect(jsonPath("$.data.securityBoardId").value(boardId.toString()));

        assertEquals(List.of(boardId), storedBoardIds(tenant));
    }

    @Test
    void updateSecurityBoard_ownerAndBoardMember_succeeds() throws Exception {
        Tenant tenant = newTenant();
        User owner = tenant.owner().getUser();
        UUID boardId = createBoardOf(tenant, owner);

        mockMvc.perform(call(PATH, tenant, owner, boardId))
                .andExpect(status().isOk());

        assertEquals(List.of(boardId), storedBoardIds(tenant));
    }

    @Test
    void updateSecurityBoard_rowExists_updatesTheSameRow() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        UUID first = createBoardOf(tenant, admin);
        UUID second = createBoardOf(tenant, admin);

        mockMvc.perform(call(PATH, tenant, admin, first)).andExpect(status().isOk());
        mockMvc.perform(call(PATH, tenant, admin, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.securityBoardId").value(second.toString()));

        assertEquals(List.of(second), storedBoardIds(tenant));
        assertEquals(1L, count("SELECT count(*) FROM kaizen.organization_settings WHERE organization_id = ?",
                tenant.organization().getId()));
    }

    @Test
    void updateSecurityBoard_nullBoard_clearsTheBoard() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        UUID boardId = createBoardOf(tenant, admin);
        mockMvc.perform(call(PATH, tenant, admin, boardId)).andExpect(status().isOk());

        mockMvc.perform(call(PATH, tenant, admin, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.securityBoardId").doesNotExist());

        List<UUID> stored = storedBoardIds(tenant);
        assertEquals(1, stored.size());
        assertNull(stored.get(0));
    }

    @Test
    void updateSecurityBoard_nullBoardWithoutRow_createsRowWithoutBoard() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(call(PATH, tenant, tenant.admin().getUser(), null))
                .andExpect(status().isOk());

        List<UUID> stored = storedBoardIds(tenant);
        assertEquals(1, stored.size());
        assertNull(stored.get(0));
    }

    @Test
    void updateSecurityBoard_plainMember_returns403() throws Exception {
        Tenant tenant = newTenant();
        User member = tenant.member().getUser();
        UUID boardId = createBoardOf(tenant, member);

        mockMvc.perform(call(PATH, tenant, member, boardId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));

        assertTrue(storedBoardIds(tenant).isEmpty());
    }

    @Test
    void updateSecurityBoard_boardDoesNotExist_returns404() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(call(PATH, tenant, tenant.admin().getUser(), UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAI_NOT_001"));

        assertTrue(storedBoardIds(tenant).isEmpty());
    }

    @Test
    void updateSecurityBoard_boardDeleted_returns404() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        UUID boardId = createBoardOf(tenant, admin);
        jdbcTemplate.update("UPDATE kaizen.boards SET deleted = TRUE WHERE id = ?", boardId);

        mockMvc.perform(call(PATH, tenant, admin, boardId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAI_NOT_001"));
    }

    @Test
    void updateSecurityBoard_callerNotBoardMember_returns404() throws Exception {
        Tenant tenant = newTenant();
        UUID boardId = createBoardOf(tenant, tenant.owner().getUser());

        mockMvc.perform(call(PATH, tenant, tenant.admin().getUser(), boardId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAI_NOT_002"));

        assertTrue(storedBoardIds(tenant).isEmpty());
    }

    @Test
    void updateSecurityBoard_callerOfAnotherOrganization_returns403() throws Exception {
        Tenant tenant = newTenant();
        Tenant foreign = newTenant();

        mockMvc.perform(in(tenant, put(PATH.formatted(tenant.id())), jwtOf(foreign.admin().getUser()), body(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_001"));

        assertTrue(storedBoardIds(tenant).isEmpty());
    }

    @Test
    void updateSecurityBoard_withoutToken_returns401() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(put(PATH.formatted(tenant.id()))
                        .header("X-Organization-ID", tenant.id())
                        .contentType("application/json")
                        .content(body(null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateSecurityBoardOnPlatformPath_adminAndBoardMember_setsBoardWithoutData() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        UUID boardId = createBoardOf(tenant, admin);

        mockMvc.perform(call(LEGACY_PATH, tenant, admin, boardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals(List.of(boardId), storedBoardIds(tenant));
    }

    @Test
    void updateSecurityBoardOnPlatformPath_nullBoard_clearsTheBoard() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        mockMvc.perform(call(LEGACY_PATH, tenant, admin, createBoardOf(tenant, admin))).andExpect(status().isOk());

        mockMvc.perform(call(LEGACY_PATH, tenant, admin, null)).andExpect(status().isOk());

        assertNull(storedBoardIds(tenant).get(0));
    }

    @Test
    void updateSecurityBoardOnPlatformPath_plainMember_returns403() throws Exception {
        Tenant tenant = newTenant();
        User member = tenant.member().getUser();

        mockMvc.perform(call(LEGACY_PATH, tenant, member, createBoardOf(tenant, member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
    }

    @Test
    void updateSecurityBoardOnPlatformPath_callerNotBoardMember_returns404() throws Exception {
        Tenant tenant = newTenant();
        UUID boardId = createBoardOf(tenant, tenant.owner().getUser());

        mockMvc.perform(call(LEGACY_PATH, tenant, tenant.admin().getUser(), boardId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAI_NOT_002"));
    }

    @Test
    void getSecurityBoardId_afterSetAndClear_followsTheRow() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        UUID boardId = createBoardOf(tenant, admin);
        assertEquals(Optional.empty(), organizationSettingQueryService.getSecurityBoardId(tenant.organization().getId()));

        mockMvc.perform(call(PATH, tenant, admin, boardId)).andExpect(status().isOk());
        assertEquals(Optional.of(boardId), organizationSettingQueryService.getSecurityBoardId(tenant.organization().getId()));

        mockMvc.perform(call(PATH, tenant, admin, null)).andExpect(status().isOk());
        assertEquals(Optional.empty(), organizationSettingQueryService.getSecurityBoardId(tenant.organization().getId()));
    }
}
