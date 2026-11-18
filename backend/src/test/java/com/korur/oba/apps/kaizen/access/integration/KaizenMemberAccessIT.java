package com.korur.oba.apps.kaizen.access.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

/**
 * Kaizen tells which members of an organization can open it and refuses to attach one who cannot. The
 * organization has no Everyone group: the OWNER holds Kaizen through the role, the ADMIN through a direct
 * grant, and the MEMBER holds nothing.
 */
class KaizenMemberAccessIT extends AbstractTenantIT {

    private static final String MEMBER_WITHOUT_APP_ACCESS = "APP_VAL_002";

    private Tenant tenant;
    private Tenant other;
    private RequestPostProcessor owner;
    private String ownerId;
    private String adminId;
    private String memberId;
    private String boardId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenantWithoutGrants();
        other = newTenant();
        owner = jwtOf(tenant.owner().getUser());
        ownerId = tenant.owner().getUser().getId().toString();
        adminId = tenant.admin().getUser().getId().toString();
        memberId = tenant.member().getUser().getId().toString();
        jdbcTemplate.update("INSERT INTO shrine.organization_app_grants (organization_id, app, user_id) "
                + "VALUES (?, 'KAIZEN'::shrine_app_grant_app_enum, ?)", tenant.organization().getId(),
                tenant.admin().getUser().getId());
        boardId = createBoard(null);
    }

    private String createBoard(String initialMemberId) throws Exception {
        String prefix = "K" + uniqueSuffix().substring(0, 8).toUpperCase();
        String members = initialMemberId == null ? ""
                : ",\"members\":[{\"userId\":\"" + initialMemberId + "\",\"role\":\"MEMBER\"}]";
        return read(mockMvc.perform(in(tenant, post("/kaizen/boards"), owner,
                        "{\"name\":\"Board " + prefix + "\",\"prefix\":\"" + prefix + "\"" + members + "}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
    }

    private String addMemberBody(String userId) {
        return "{\"userId\":\"" + userId + "\",\"role\":\"MEMBER\"}";
    }

    private String sprintId() throws Exception {
        return read(mockMvc.perform(in(tenant, post("/kaizen/boards/" + boardId + "/sprints"), owner,
                "{\"name\":\"Sprint\"}")).andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
    }

    private String ticketBody(String sprintId, String field, String userId) {
        return "{\"title\":\"Ticket\",\"type\":\"FEATURE\",\"priority\":\"MEDIUM\",\"status\":\"TODO\","
                + "\"missionRank\":\"M\",\"sprintId\":\"" + sprintId + "\",\"" + field + "\":[\"" + userId + "\"]}";
    }

    private long boardMemberRows(String userId) {
        return count("SELECT count(*) FROM kaizen.board_members WHERE board_id = ?::uuid AND user_id = ?::uuid",
                boardId, userId);
    }

    @Test
    void boardMembers_listEachMemberWithKaizenAccess() throws Exception {
        // Arrange
        mockMvc.perform(in(tenant, post("/kaizen/boards/" + boardId + "/members"), owner, addMemberBody(adminId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kaizenAccess").value(true));
        jdbcTemplate.update("UPDATE shrine.organization_app_grants SET deleted = TRUE WHERE user_id = ?",
                tenant.admin().getUser().getId());

        // Act and assert
        mockMvc.perform(in(tenant, get("/kaizen/boards/" + boardId + "/members"), owner, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == '" + ownerId + "')].kaizenAccess").value(true))
                .andExpect(jsonPath("$.data[?(@.userId == '" + adminId + "')].kaizenAccess").value(false));
    }

    @Test
    void memberCandidates_listOnlyTheBoardOrganizationWithKaizenAccess() throws Exception {
        // Arrange
        String outsiderId = other.member().getUser().getId().toString();

        // Act and assert
        mockMvc.perform(in(tenant, get("/kaizen/boards/" + boardId + "/members/candidates"), owner, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[*].userId", not(hasItem(outsiderId))))
                .andExpect(jsonPath("$.data[?(@.userId == '" + ownerId + "')].kaizenAccess").value(true))
                .andExpect(jsonPath("$.data[?(@.userId == '" + adminId + "')].kaizenAccess").value(true))
                .andExpect(jsonPath("$.data[?(@.userId == '" + memberId + "')].kaizenAccess").value(false))
                .andExpect(jsonPath("$.data[?(@.userId == '" + memberId + "')].role").value("MEMBER"));
    }

    @Test
    void memberCandidates_boardMemberBelowAdmin_answers403() throws Exception {
        // Arrange
        mockMvc.perform(in(tenant, post("/kaizen/boards/" + boardId + "/members"), owner, addMemberBody(adminId)))
                .andExpect(status().isCreated());

        // Act and assert
        mockMvc.perform(in(tenant, get("/kaizen/boards/" + boardId + "/members/candidates"),
                        jwtOf(tenant.admin().getUser()), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void addBoardMember_withoutKaizen_answers422AndAddsNobody() throws Exception {
        mockMvc.perform(in(tenant, post("/kaizen/boards/" + boardId + "/members"), owner, addMemberBody(memberId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(MEMBER_WITHOUT_APP_ACCESS));

        assertEquals(0, boardMemberRows(memberId));
    }

    @Test
    void createBoard_initialMemberWithoutKaizen_answers422() throws Exception {
        String prefix = "K" + uniqueSuffix().substring(0, 8).toUpperCase();

        mockMvc.perform(in(tenant, post("/kaizen/boards"), owner, "{\"name\":\"Board " + prefix + "\",\"prefix\":\""
                        + prefix + "\",\"members\":[{\"userId\":\"" + memberId + "\",\"role\":\"MEMBER\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(MEMBER_WITHOUT_APP_ACCESS));

        assertEquals(0, count("SELECT count(*) FROM kaizen.boards WHERE prefix = ?", prefix));
    }

    @Test
    void createTicket_assigneeOrReviewerWithoutKaizen_answers422() throws Exception {
        // Arrange
        String sprintId = sprintId();

        // Act and assert
        mockMvc.perform(in(tenant, post("/kaizen/boards/" + boardId + "/tickets"), owner,
                        ticketBody(sprintId, "assigneeIds", memberId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(MEMBER_WITHOUT_APP_ACCESS));
        mockMvc.perform(in(tenant, post("/kaizen/boards/" + boardId + "/tickets"), owner,
                        ticketBody(sprintId, "reviewerIds", memberId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(MEMBER_WITHOUT_APP_ACCESS));
        mockMvc.perform(in(tenant, post("/kaizen/boards/" + boardId + "/tickets"), owner,
                        ticketBody(sprintId, "assigneeIds", adminId)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void chatMembers_listEachMemberWithKaizenAccess() throws Exception {
        mockMvc.perform(in(tenant, get("/kaizen/chat/members"), owner, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == '" + adminId + "')].kaizenAccess").value(true))
                .andExpect(jsonPath("$.data[?(@.userId == '" + memberId + "')].kaizenAccess").value(false));
    }

    @Test
    void createPrivateChannel_memberWithoutKaizen_answers422() throws Exception {
        mockMvc.perform(in(tenant, post("/kaizen/chat/channels"), owner,
                        "{\"name\":\"private" + uniqueSuffix() + "\",\"visibility\":\"PRIVATE\",\"purpose\":\"GENERAL\","
                                + "\"memberIds\":[\"" + memberId + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(MEMBER_WITHOUT_APP_ACCESS));
    }

    @Test
    void chatMessage_mentionOfMemberWithoutKaizen_answers422() throws Exception {
        // Arrange
        String channelId = read(mockMvc.perform(in(tenant, post("/kaizen/chat/channels"), owner,
                        "{\"name\":\"general" + uniqueSuffix() + "\",\"visibility\":\"PUBLIC\",\"purpose\":\"GENERAL\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        MockMultipartFile message = new MockMultipartFile("message", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"content\":\"Hello <@" + memberId + ">\",\"channelId\":\"" + channelId
                        + "\",\"clientNonce\":\"" + UUID.randomUUID() + "\"}").getBytes());

        // Act and assert
        mockMvc.perform(multipart("/kaizen/chat/messages").file(message)
                        .with(owner).header("X-Organization-ID", tenant.id()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(MEMBER_WITHOUT_APP_ACCESS));
    }
}
