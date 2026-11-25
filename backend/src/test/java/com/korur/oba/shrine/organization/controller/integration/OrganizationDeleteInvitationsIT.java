package com.korur.oba.shrine.organization.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.invitation.repository.InvitationRepository;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DELETE /organizations/{organizationId} revokes the organization's pending invitations, because a
 * deleted organization can no longer be joined. An invitation that was already decided keeps its
 * status, the invitations of another organization are untouched, and the invited user's GET /users/me
 * no longer lists the revoked invitation.
 */
class OrganizationDeleteInvitationsIT extends ObaIntegrationTest {

    @Autowired
    private InvitationRepository invitationRepository;

    private User owner;
    private User invitee;
    private Organization organization;
    private Invitation pendingInvitation;
    private Invitation acceptedInvitation;
    private Invitation pendingInvitationOfOtherOrganization;

    @BeforeEach
    void setUp() {
        owner = createUser();
        invitee = createUser();
        organization = createOrganization();
        createMember(organization, owner, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE);
        Organization otherOrganization = createOrganization();

        pendingInvitation = invitation(organization, invitee.getEmail(), InvitationStatus.PENDING);
        acceptedInvitation = invitation(organization, createUser().getEmail(), InvitationStatus.ACCEPTED);
        pendingInvitationOfOtherOrganization = invitation(otherOrganization, invitee.getEmail(), InvitationStatus.PENDING);
    }

    private Invitation invitation(Organization of, String email, InvitationStatus invitationStatus) {
        return invitationRepository.saveAndFlush(Invitation.builder()
                .organization(of)
                .email(email)
                .role(OrganizationMemberRole.MEMBER)
                .status(invitationStatus)
                .invitedBy(owner)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());
    }

    private String storedStatus(Invitation invitation) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM shrine.user_invitations WHERE id = ?",
                String.class, invitation.getId());
    }

    private void deleteOrganization() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + organization.getId()).with(jwtOf(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteOrganization_pendingInvitation_isRevoked() throws Exception {
        deleteOrganization();

        assertEquals("REVOKED", storedStatus(pendingInvitation));
    }

    @Test
    void deleteOrganization_decidedInvitationAndOtherOrganization_keepTheirStatus() throws Exception {
        deleteOrganization();

        assertEquals("ACCEPTED", storedStatus(acceptedInvitation));
        assertEquals("PENDING", storedStatus(pendingInvitationOfOtherOrganization));
    }

    @Test
    void deleteOrganization_invitee_getMeListsOnlyTheInvitationOfTheOtherOrganization() throws Exception {
        deleteOrganization();

        String body = mockMvc.perform(MockMvcRequestBuilders.get("/users/me").with(jwtOf(invitee)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> listedIds = JsonPath.read(body, "$.data.pendingInvitations[*].id");
        assertEquals(1, listedIds.size());
        assertTrue(listedIds.contains(pendingInvitationOfOtherOrganization.getId().toString()));
    }
}
