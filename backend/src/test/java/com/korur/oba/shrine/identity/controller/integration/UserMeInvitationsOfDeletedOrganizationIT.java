package com.korur.oba.shrine.identity.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.invitation.repository.InvitationRepository;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A pending invitation of a soft-deleted organization no longer exists for the invited user: GET
 * /users/me answers 200 without it, and accepting or declining it answers 404 INV_NOT_001, while the
 * invitation of a live organization is listed and can be declined as before.
 *
 * The deleted organization is marked deleted in the database after its invitation was sent: the state
 * of an organization that was deleted while its invitations stayed pending.
 */
class UserMeInvitationsOfDeletedOrganizationIT extends ObaIntegrationTest {

    @Autowired
    private InvitationRepository invitationRepository;

    private User invitee;
    private Organization deletedOrganization;
    private Invitation invitationOfLiveOrganization;
    private Invitation invitationOfDeletedOrganization;

    @BeforeEach
    void setUp() {
        invitee = createUser();
        User inviter = createUser();
        Organization liveOrganization = createOrganization();
        deletedOrganization = createOrganization();

        invitationOfLiveOrganization = pendingInvitation(liveOrganization, inviter);
        invitationOfDeletedOrganization = pendingInvitation(deletedOrganization, inviter);
        jdbcTemplate.update("UPDATE shrine.organizations SET deleted = true WHERE id = ?", deletedOrganization.getId());
    }

    private Invitation pendingInvitation(Organization organization, User inviter) {
        return invitationRepository.saveAndFlush(Invitation.builder()
                .organization(organization)
                .email(invitee.getEmail())
                .role(OrganizationMemberRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .invitedBy(inviter)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());
    }

    private ResultActions decide(Invitation invitation, String decision) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/users/me/invitations/decision").with(jwtOf(invitee))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationId\":\"" + invitation.getId() + "\",\"decision\":\"" + decision + "\"}"));
    }

    private String storedStatus(Invitation invitation) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM shrine.user_invitations WHERE id = ?",
                String.class, invitation.getId());
    }

    @Test
    void getMe_pendingInvitationOfDeletedOrganization_returns200AndListsOnlyTheLiveOne() throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/users/me").with(jwtOf(invitee)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> listedIds = JsonPath.read(body, "$.data.pendingInvitations[*].id");
        assertTrue(listedIds.contains(invitationOfLiveOrganization.getId().toString()));
        assertFalse(listedIds.contains(invitationOfDeletedOrganization.getId().toString()));
    }

    @Test
    void decide_declineInvitationOfDeletedOrganization_returns404AndChangesNothing() throws Exception {
        decide(invitationOfDeletedOrganization, "DECLINE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INV_NOT_001"));

        assertEquals("PENDING", storedStatus(invitationOfDeletedOrganization));
    }

    @Test
    void decide_acceptInvitationOfDeletedOrganization_returns404AndAddsNoMembership() throws Exception {
        decide(invitationOfDeletedOrganization, "ACCEPT")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INV_NOT_001"));

        assertEquals(0L, count("SELECT count(*) FROM shrine.organization_members "
                + "WHERE organization_id = ? AND user_id = ?", deletedOrganization.getId(), invitee.getId()));
    }

    @Test
    void decide_declineInvitationOfLiveOrganization_returns200AndDeclinesIt() throws Exception {
        decide(invitationOfLiveOrganization, "DECLINE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));

        assertEquals("DECLINED", storedStatus(invitationOfLiveOrganization));
    }
}
