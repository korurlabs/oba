package com.korur.oba.apps.kazan.access.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.helper.ChatRealtimeDestinationAuthorizer;
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.connect.realtime.dto.RealtimeFrameDecision;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.helper.RealtimeDestinationAuthorizerRegistry;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Kazan entry points that the HTTP interceptor does not cover (daily updates, ticket and chat
 * attachments, chat subscriptions and typing) refuse a member whose organization withholds Kazan, and keep
 * serving the same member while the grant exists.
 */
class KazanAppAccessOutsideInterceptorIT extends AbstractTenantIT {

    private static final String APP_ACCESS_DENIED = "APP_SEC_001";

    @Autowired
    private ChatRealtimeDestinationAuthorizer chatAuthorizer;
    @Autowired
    private RealtimeDestinationAuthorizerRegistry realtimeAuthorizerRegistry;

    private Tenant tenant;
    private Person person;
    private String ticketId;
    private String channelId;
    private String messageId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        person = signIn(tenant, "kazan");
        String prefix = "K" + uniqueSuffix().substring(0, 8).toUpperCase();
        MvcResult board = mockMvc.perform(in(tenant, post("/kazan/boards"), person.jwt(),
                "{\"name\":\"Board " + prefix + "\",\"prefix\":\"" + prefix + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        String boardId = read(board, "$.data.id");
        String sprintId = read(mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/sprints"), person.jwt(),
                "{\"name\":\"Sprint\"}")).andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        ticketId = read(mockMvc.perform(in(tenant, post("/kazan/boards/" + boardId + "/tickets"), person.jwt(),
                "{\"title\":\"Ticket\",\"type\":\"FEATURE\",\"priority\":\"MEDIUM\",\"status\":\"TODO\",\"missionRank\":\"M\",\"sprintId\":\"" + sprintId + "\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        channelId = read(mockMvc.perform(in(tenant, post("/kazan/chat/channels"), jwtOf(tenant.admin().getUser()),
                "{\"name\":\"general" + prefix + "\",\"visibility\":\"PUBLIC\",\"purpose\":\"GENERAL\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn(), "$.data.id");
        MockMultipartFile message = new MockMultipartFile("message", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"content\":\"Hello\",\"channelId\":\"" + channelId
                + "\",\"clientNonce\":\"" + uniqueSuffix() + "\"}").getBytes());
        messageId = read(mockMvc.perform(multipart("/kazan/chat/messages").file(message)
                .with(person.jwt()).header("X-Organization-ID", tenant.id()))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private void revokeKazanFromEveryone() {
        jdbcTemplate.update("UPDATE shrine.organization_app_grants SET deleted = TRUE "
                + "WHERE organization_id = ? AND app = 'KAZAN'::shrine_app_grant_app_enum", tenant.organization().getId());
    }

    private String previewBody() {
        return "{\"tickets\":[{\"ticketId\":\"" + ticketId + "\",\"percentage\":10}]}";
    }

    private RealtimePrincipal principal() {
        return new RealtimePrincipal(person.user().getId(), tenant.organization().getId(),
                java.time.Instant.now().plusSeconds(600));
    }

    @Test
    void dailyUpdates_memberWithKazan_isServed() throws Exception {
        mockMvc.perform(in(tenant, get("/kazan/updates/suggested-tickets"), person.jwt(), null))
                .andExpect(status().isOk());
        mockMvc.perform(in(tenant, post("/kazan/updates/preview"), person.jwt(), previewBody()))
                .andExpect(status().isOk());
        mockMvc.perform(in(tenant, get("/kazan/updates/missing").param("organizationId", tenant.id()), jwtOf(tenant.admin().getUser()), null))
                .andExpect(status().isOk());
    }

    @Test
    void dailyUpdates_withoutKazan_everySiteAnswers403AppAccessDenied() throws Exception {
        revokeKazanFromEveryone();

        mockMvc.perform(in(tenant, get("/kazan/updates/suggested-tickets"), person.jwt(), null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(APP_ACCESS_DENIED));
        mockMvc.perform(in(tenant, post("/kazan/updates/preview"), person.jwt(), previewBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(APP_ACCESS_DENIED));
        mockMvc.perform(in(tenant, get("/kazan/updates/missing").param("organizationId", tenant.id()), jwtOf(tenant.admin().getUser()), null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(APP_ACCESS_DENIED));
    }

    @Test
    void attachments_memberWithKazan_listsTicketAndChatMessageAttachments() throws Exception {
        mockMvc.perform(get("/storage/attachments").param("entityType", "TICKET").param("entityId", ticketId)
                .with(person.jwt()).header("X-Organization-ID", tenant.id()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/storage/attachments").param("entityType", "CHAT_MESSAGE").param("entityId", messageId)
                .with(person.jwt()).header("X-Organization-ID", tenant.id()))
                .andExpect(status().isOk());
    }

    @Test
    void attachments_withoutKazan_ticketAndChatMessageAnswer403AppAccessDenied() throws Exception {
        revokeKazanFromEveryone();

        mockMvc.perform(get("/storage/attachments").param("entityType", "TICKET").param("entityId", ticketId)
                .with(person.jwt()).header("X-Organization-ID", tenant.id()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(APP_ACCESS_DENIED));
        mockMvc.perform(get("/storage/attachments").param("entityType", "CHAT_MESSAGE").param("entityId", messageId)
                .with(person.jwt()).header("X-Organization-ID", tenant.id()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(APP_ACCESS_DENIED));
    }

    @Test
    void chatSubscribeAndTyping_memberWithKazan_isAuthorized() {
        UUID organizationId = tenant.organization().getId();
        String organizationTopic = RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX);
        String channelTopic = RealtimeDestinationUtil.organizationTopic(organizationId,
                ChatConstant.CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX + channelId);

        assertTrue(chatAuthorizer.authorizeSubscribe(principal(), organizationTopic));
        assertTrue(chatAuthorizer.authorizeSubscribe(principal(), ChatConstant.CHAT_USER_QUEUE_SUBSCRIPTION));
        assertTrue(chatAuthorizer.authorizeSubscribe(principal(), channelTopic));
        assertTrue(chatAuthorizer.authorizeSend(principal(), ChatConstant.CHAT_TYPING_DESTINATION));
    }

    @Test
    void chatSubscribeAndTyping_withoutKazan_everyDestinationIsRefused() {
        revokeKazanFromEveryone();
        UUID organizationId = tenant.organization().getId();
        String organizationTopic = RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX);
        String channelTopic = RealtimeDestinationUtil.organizationTopic(organizationId,
                ChatConstant.CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX + channelId);

        assertFalse(chatAuthorizer.authorizeSubscribe(principal(), organizationTopic));
        assertFalse(chatAuthorizer.authorizeSubscribe(principal(), ChatConstant.CHAT_USER_QUEUE_SUBSCRIPTION));
        assertFalse(chatAuthorizer.authorizeSubscribe(principal(), channelTopic));
        assertFalse(chatAuthorizer.authorizeSend(principal(), ChatConstant.CHAT_TYPING_DESTINATION));
    }

    @Test
    void chatTypingFrame_memberWithKazan_passesTheSendGate() {
        RealtimeFrameDecision decision = realtimeAuthorizerRegistry.authorizeSend(
                principal(), ChatConstant.CHAT_TYPING_DESTINATION);

        assertEquals(RealtimeFrameDecision.Verdict.PASS, decision.verdict());
    }

    @Test
    void chatTypingFrame_memberWithoutKazan_isRefusedAtTheSendGate() {
        revokeKazanFromEveryone();

        RealtimeFrameDecision decision = realtimeAuthorizerRegistry.authorizeSend(
                principal(), ChatConstant.CHAT_TYPING_DESTINATION);

        assertFalse(decision.passes());
    }
}
