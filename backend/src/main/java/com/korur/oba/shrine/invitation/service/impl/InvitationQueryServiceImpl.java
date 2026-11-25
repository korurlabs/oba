package com.korur.oba.shrine.invitation.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.invitation.mapper.InvitationMapper;
import com.korur.oba.shrine.invitation.repository.InvitationRepository;
import com.korur.oba.shrine.invitation.service.InvitationQueryService;

/**
 * Read operations for invitations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationQueryServiceImpl implements InvitationQueryService {

    private final InvitationRepository invitationRepository;
    private final InvitationMapper invitationMapper;

    @Override
    public List<InvitationResponse> listPendingInvitations(User user, UUID organizationId) {
        List<Invitation> invitations = invitationRepository
                .findByOrganizationIdAndStatus(organizationId, InvitationStatus.PENDING);

        return invitations.stream()
                .map(invitationMapper::toResponse)
                .toList();
    }

    @Override
    public List<InvitationResponse> listPendingInvitationsByEmail(String email) {
        List<Invitation> pendingInvitations = invitationRepository
                .findByEmailAndStatusInLiveOrganization(email, InvitationStatus.PENDING);

        Instant now = Instant.now();

        return pendingInvitations.stream()
                .filter(invitation -> invitation.getExpiresAt() == null || now.isBefore(invitation.getExpiresAt()))
                .map(invitationMapper::toResponse)
                .toList();
    }

    @Override
    public List<Invitation> findPendingByEmail(String email) {
        return invitationRepository.findByEmailAndStatusInLiveOrganization(email, InvitationStatus.PENDING);
    }
}
