package com.korur.oba.apps.kaizen.board.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that a board belongs to
 * one organization: a user who sits in two organizations reaches a board only with the header of the
 * organization that owns it, and every other header answers as if the board did not exist.
 */
class BoardOrganizationIsolationIT extends AbstractTenantIT {

    private static final String NOT_FOUND_CODE_PREFIX = "KAI_NOT_00";
    private static final String NOT_ORGANIZATION_MEMBER_CODE = "KAI_STA_019";

    private Tenant tenantA;
    private Tenant tenantB;
    private Person user;
    private String boardId;
    private String boardSlug;
    private String sprintId;
    private String ticketId;
    private String ticketKey;
    private String labelId;
    private String milestoneId;
    private String commentId;

    @BeforeEach
    void setUpTenantsAndBoardOfA() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        user = signIn(tenantA, "isolation");
        createMember(tenantB.organization(), user.user(), OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE);

        String prefix = "I" + uniqueSuffix().substring(0, 8).toUpperCase();
        MvcResult board = mockMvc.perform(in(tenantA, post("/kaizen/boards"), user.jwt(),
                "{\"name\":\"Isolation " + prefix + "\",\"prefix\":\"" + prefix + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        boardId = read(board, "$.data.id");
        boardSlug = read(board, "$.data.slug");
        sprintId = read(mockMvc.perform(in(tenantA, post(boardPath("/sprints")), user.jwt(), "{\"name\":\"Sprint A\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        MvcResult ticket = mockMvc.perform(in(tenantA, post(boardPath("/tickets")), user.jwt(),
                "{\"title\":\"Ticket of A\",\"type\":\"FEATURE\",\"priority\":\"MEDIUM\",\"status\":\"TODO\",\"missionRank\":\"M\","
                        + "\"sprintId\":\"" + sprintId + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        ticketId = read(ticket, "$.data.id");
        ticketKey = read(ticket, "$.data.key");
        labelId = read(mockMvc.perform(in(tenantA, post(boardPath("/labels")), user.jwt(),
                "{\"name\":\"Label A\",\"color\":\"#3B82F6\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        milestoneId = read(mockMvc.perform(in(tenantA, post(boardPath("/milestones")), user.jwt(),
                "{\"name\":\"Milestone A\",\"targetDate\":\"2030-01-01\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        commentId = read(mockMvc.perform(in(tenantA, post(boardPath("/tickets/" + ticketId + "/comments")), user.jwt(),
                "{\"content\":\"Comment of A\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
    }

    private String boardPath(String suffix) {
        return "/kaizen/boards/" + boardId + suffix;
    }

    private List<String> readPathsOfBoardOfA() {
        return List.of(
                "/kaizen/boards/" + boardId,
                "/kaizen/boards/" + boardId + "/related",
                "/kaizen/boards/by-slug/" + boardSlug,
                "/kaizen/boards/" + boardId + "/tickets?sprintId=" + sprintId,
                "/kaizen/boards/" + boardId + "/tickets/" + ticketId,
                "/kaizen/tickets/by-key/" + ticketKey,
                "/kaizen/boards/" + boardId + "/tickets/" + ticketId + "/comments",
                "/kaizen/boards/" + boardId + "/labels",
                "/kaizen/boards/" + boardId + "/sprints",
                "/kaizen/boards/" + boardId + "/sprints/" + sprintId,
                "/kaizen/boards/" + boardId + "/milestones",
                "/kaizen/boards/" + boardId + "/milestones/" + milestoneId,
                "/kaizen/boards/" + boardId + "/roadmap",
                "/kaizen/boards/" + boardId + "/members");
    }

    private void assertAnswersBoardNotFound(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains(NOT_FOUND_CODE_PREFIX);
    }

    @Test
    void readsWithHeaderOfAnotherOrganization_boardAndEverythingOnIt_answer404() throws Exception {
        for (String path : readPathsOfBoardOfA()) {
            assertAnswersBoardNotFound(in(tenantB, get(path), user.jwt(), null));
        }
    }

    @Test
    void readsWithHeaderOfTheOwningOrganization_sameUser_answer200() throws Exception {
        for (String path : readPathsOfBoardOfA()) {
            mockMvc.perform(in(tenantA, get(path), user.jwt(), null)).andExpect(status().isOk());
        }
    }

    @Test
    void listsAndHomeWithHeaderOfAnotherOrganization_showNothingOfTheBoardOfA() throws Exception {
        for (String path : List.of("/kaizen/boards", "/kaizen/home/summary", "/kaizen/home/stale-backlog",
                "/kaizen/home/sprint", "/kaizen/home/deadlines")) {
            MvcResult result = mockMvc.perform(in(tenantB, get(path), user.jwt(), null))
                    .andExpect(status().isOk()).andReturn();
            String body = result.getResponse().getContentAsString();
            assertThat(body).as(path).doesNotContain(boardId).doesNotContain(ticketKey).doesNotContain(boardSlug);
        }
        MvcResult own = mockMvc.perform(in(tenantA, get("/kaizen/boards"), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        assertThat(own.getResponse().getContentAsString()).contains(boardId);
    }

    @Test
    void writesWithHeaderOfAnotherOrganization_answer404AndChangeNothing() throws Exception {
        long ticketsBefore = count("SELECT count(*) FROM kaizen.tickets WHERE board_id = ?::uuid", boardId);
        long labelsBefore = count("SELECT count(*) FROM kaizen.labels WHERE board_id = ?::uuid", boardId);
        long commentsBefore = count("SELECT count(*) FROM kaizen.comments WHERE ticket_id = ?::uuid", ticketId);
        long sprintLinksBefore = count("SELECT count(*) FROM kaizen.board_sprints WHERE board_id = ?::uuid", boardId);

        assertAnswersBoardNotFound(in(tenantB, put("/kaizen/boards/" + boardId), user.jwt(),
                "{\"name\":\"Hijacked\"}"));
        assertAnswersBoardNotFound(in(tenantB, post(boardPath("/tickets")), user.jwt(),
                "{\"title\":\"Intruder\",\"type\":\"FEATURE\",\"priority\":\"MEDIUM\",\"status\":\"TODO\",\"missionRank\":\"M\","
                        + "\"sprintId\":\"" + sprintId + "\"}"));
        assertAnswersBoardNotFound(in(tenantB, put(boardPath("/tickets/" + ticketId)), user.jwt(),
                "{\"title\":\"Hijacked\"}"));
        assertAnswersBoardNotFound(in(tenantB, delete(boardPath("/tickets/" + ticketId)), user.jwt(), null));
        assertAnswersBoardNotFound(in(tenantB, post(boardPath("/tickets/" + ticketId + "/comments")), user.jwt(),
                "{\"content\":\"Intruder\"}"));
        assertAnswersBoardNotFound(in(tenantB, delete(boardPath("/tickets/" + ticketId + "/comments/" + commentId)),
                user.jwt(), null));
        assertAnswersBoardNotFound(in(tenantB, post(boardPath("/labels")), user.jwt(),
                "{\"name\":\"Intruder\",\"color\":\"#3B82F6\"}"));
        assertAnswersBoardNotFound(in(tenantB, delete(boardPath("/labels/" + labelId)), user.jwt(), null));
        assertAnswersBoardNotFound(in(tenantB, delete(boardPath("/sprints/" + sprintId)), user.jwt(),
                "{\"ticketAction\":\"DELETE\"}"));
        assertAnswersBoardNotFound(in(tenantB, delete(boardPath("/milestones/" + milestoneId)), user.jwt(), null));
        assertAnswersBoardNotFound(in(tenantB, delete("/kaizen/boards/" + boardId), user.jwt(), null));

        assertThat(count("SELECT count(*) FROM kaizen.tickets WHERE board_id = ?::uuid", boardId)).isEqualTo(ticketsBefore);
        assertThat(count("SELECT count(*) FROM kaizen.labels WHERE board_id = ?::uuid", boardId)).isEqualTo(labelsBefore);
        assertThat(count("SELECT count(*) FROM kaizen.comments WHERE ticket_id = ?::uuid", ticketId)).isEqualTo(commentsBefore);
        assertThat(count("SELECT count(*) FROM kaizen.board_sprints WHERE board_id = ?::uuid", boardId)).isEqualTo(sprintLinksBefore);
        assertThat(count("SELECT count(*) FROM kaizen.boards WHERE id = ?::uuid AND deleted = false AND name <> 'Hijacked'",
                boardId)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM kaizen.tickets WHERE id = ?::uuid AND deleted = false AND title = 'Ticket of A'",
                ticketId)).isEqualTo(1L);
    }

    @Test
    void ticketOfBoardInOrganizationA_withSprintOfAnotherOrganization_answers404() throws Exception {
        // A board of B that tries to take the sprint of A by its id.
        String boardOfB = read(mockMvc.perform(in(tenantB, post("/kaizen/boards"), user.jwt(),
                "{\"name\":\"Board B\",\"prefix\":\"B" + uniqueSuffix().substring(0, 8).toUpperCase() + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");

        MvcResult result = mockMvc.perform(in(tenantB, post("/kaizen/boards/" + boardOfB + "/tickets"), user.jwt(),
                "{\"title\":\"Takes foreign sprint\",\"type\":\"FEATURE\",\"priority\":\"MEDIUM\",\"status\":\"TODO\",\"missionRank\":\"M\","
                        + "\"sprintId\":\"" + sprintId + "\"}"))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("KAI_NOT_");
        assertThat(count("SELECT count(*) FROM kaizen.board_sprints WHERE sprint_id = ?::uuid", sprintId)).isEqualTo(1L);
    }

    @Test
    void userRemovedFromOrganizationA_headerOfAAnswers403AndBoardsOfBStillWork() throws Exception {
        jdbcTemplate.update("UPDATE shrine.organization_members SET status = 'DISABLED'::shrine_organization_member_status_enum "
                + "WHERE organization_id = ? AND user_id = ?", tenantA.organization().getId(), user.user().getId());

        mockMvc.perform(in(tenantA, get("/kaizen/boards/" + boardId), user.jwt(), null))
                .andExpect(status().isForbidden());
        mockMvc.perform(in(tenantB, get("/kaizen/boards"), user.jwt(), null)).andExpect(status().isOk());
        mockMvc.perform(in(tenantB, post("/kaizen/boards"), user.jwt(),
                "{\"name\":\"Board B\",\"prefix\":\"B" + uniqueSuffix().substring(0, 8).toUpperCase() + "\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void organizationADisabled_headerOfAAnswers403AndBoardsOfBStillWork() throws Exception {
        jdbcTemplate.update("UPDATE shrine.organizations SET status = 'DISABLED'::shrine_organization_status_enum WHERE id = ?",
                tenantA.organization().getId());

        mockMvc.perform(in(tenantA, get("/kaizen/boards/" + boardId), user.jwt(), null))
                .andExpect(status().isForbidden());
        mockMvc.perform(in(tenantB, get("/kaizen/boards"), user.jwt(), null)).andExpect(status().isOk());
    }

    @Test
    void addingUserOnlyInOrganizationBToBoardOfA_answers409() throws Exception {
        Person onlyInB = signIn(tenantB, "onlyb");

        MvcResult result = mockMvc.perform(in(tenantA, post(boardPath("/members")), user.jwt(),
                "{\"userId\":\"" + onlyInB.userId() + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(NOT_ORGANIZATION_MEMBER_CODE);
        assertThat(count("SELECT count(*) FROM kaizen.board_members WHERE board_id = ?::uuid AND user_id = ?::uuid",
                boardId, onlyInB.userId())).isZero();
    }

    @Test
    void schema_childBoardOfAnotherOrganization_isRefusedByTheCompositeForeignKey() {
        UUID childId = UUID.randomUUID();
        String childPrefix = "C" + uniqueSuffix().substring(0, 8).toUpperCase();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO kaizen.boards (id, name, prefix, slug, organization_id, parent_board_id) "
                        + "VALUES (?, 'child', ?, ?, ?, ?::uuid)",
                childId, childPrefix, "child-" + childId, tenantB.organization().getId(), boardId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schema_boardWithoutOrganization_isRefusedByNotNull() {
        UUID id = UUID.randomUUID();
        String prefix = "N" + uniqueSuffix().substring(0, 8).toUpperCase();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO kaizen.boards (id, name, prefix, slug) VALUES (?, 'orphan', ?, ?)",
                id, prefix, "orphan-" + id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schema_childBoardOfTheSameOrganization_isAccepted() {
        UUID childId = UUID.randomUUID();
        String childPrefix = "S" + uniqueSuffix().substring(0, 8).toUpperCase();

        int inserted = jdbcTemplate.update(
                "INSERT INTO kaizen.boards (id, name, prefix, slug, organization_id, parent_board_id) "
                        + "VALUES (?, 'child', ?, ?, ?, ?::uuid)",
                childId, childPrefix, "child-" + childId, tenantA.organization().getId(), boardId);

        assertThat(inserted).isEqualTo(1);
    }
}
