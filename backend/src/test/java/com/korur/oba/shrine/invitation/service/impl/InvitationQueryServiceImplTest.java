package com.korur.oba.shrine.invitation.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.invitation.mapper.InvitationMapper;
import com.korur.oba.shrine.invitation.repository.InvitationRepository;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class InvitationQueryServiceImplTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private InvitationMapper invitationMapper;

    @InjectMocks
    private InvitationQueryServiceImpl invitationQueryServiceImpl;

    // ── Shared helpers ────────────────────────────────────────────────────────

    private Organization stubOrganization() {
        Organization org = Organization.builder().name("Acme Corp").build();
        org.setId(UUID.randomUUID());
        return org;
    }

    private Invitation stubPendingInvitation(String email, Instant expiresAt) {
        Invitation invitation = Invitation.builder()
                .email(email)
                .role(OrganizationMemberRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .organization(stubOrganization())
                .expiresAt(expiresAt)
                .build();
        invitation.setId(UUID.randomUUID());
        return invitation;
    }

    private InvitationResponse stubResponse(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getOrganization().getId(),
                invitation.getOrganization().getName(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getStatus(),
                "admin@example.com",
                null,
                invitation.getExpiresAt(),
                null,
                Instant.now());
    }

    // ── ListPendingInvitationsByEmail ─────────────────────────────────────────

    @Nested
    class ListPendingInvitationsByEmail {

        @Test
        void nonExpiredPendingInvitations_returnsMappedResponses() {
            // Arrange
            String email = "invitee@example.com";
            Invitation invitation = stubPendingInvitation(email, Instant.now().plusSeconds(3600));
            InvitationResponse response = stubResponse(invitation);

            when(invitationRepository.findByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING))
                    .thenReturn(List.of(invitation));
            when(invitationMapper.toResponse(invitation)).thenReturn(response);

            // Act
            List<InvitationResponse> result = invitationQueryServiceImpl.listPendingInvitationsByEmail(email);

            // Assert
            assertEquals(1, result.size());
            assertEquals(invitation.getId(), result.get(0).id());
        }

        @Test
        void expiredInvitation_isFilteredOut() {
            // Arrange
            String email = "invitee@example.com";
            Invitation expiredInvitation = stubPendingInvitation(email, Instant.now().minusSeconds(1));

            when(invitationRepository.findByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING))
                    .thenReturn(List.of(expiredInvitation));

            // Act
            List<InvitationResponse> result = invitationQueryServiceImpl.listPendingInvitationsByEmail(email);

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        void nullExpiresAt_treatedAsNonExpiredAndIncluded() {
            // Arrange
            String email = "invitee@example.com";
            Invitation legacyInvitation = stubPendingInvitation(email, null);
            InvitationResponse response = stubResponse(legacyInvitation);

            when(invitationRepository.findByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING))
                    .thenReturn(List.of(legacyInvitation));
            when(invitationMapper.toResponse(legacyInvitation)).thenReturn(response);

            // Act
            List<InvitationResponse> result = invitationQueryServiceImpl.listPendingInvitationsByEmail(email);

            // Assert
            assertEquals(1, result.size());
            assertEquals(legacyInvitation.getId(), result.get(0).id());
        }

        @Test
        void mixedInvitations_returnsOnlyNonExpired() {
            // Arrange
            String email = "invitee@example.com";
            Invitation validInvitation = stubPendingInvitation(email, Instant.now().plusSeconds(3600));
            Invitation expiredInvitation = stubPendingInvitation(email, Instant.now().minusSeconds(1));
            Invitation legacyInvitation = stubPendingInvitation(email, null);

            InvitationResponse validResponse = stubResponse(validInvitation);
            InvitationResponse legacyResponse = stubResponse(legacyInvitation);

            when(invitationRepository.findByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING))
                    .thenReturn(List.of(validInvitation, expiredInvitation, legacyInvitation));
            when(invitationMapper.toResponse(validInvitation)).thenReturn(validResponse);
            when(invitationMapper.toResponse(legacyInvitation)).thenReturn(legacyResponse);

            // Act
            List<InvitationResponse> result = invitationQueryServiceImpl.listPendingInvitationsByEmail(email);

            // Assert
            assertEquals(2, result.size());
        }

        @Test
        void noPendingInvitations_returnsEmptyList() {
            // Arrange
            String email = "invitee@example.com";

            when(invitationRepository.findByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING))
                    .thenReturn(Collections.emptyList());

            // Act
            List<InvitationResponse> result = invitationQueryServiceImpl.listPendingInvitationsByEmail(email);

            // Assert
            assertTrue(result.isEmpty());
        }
    }
}
