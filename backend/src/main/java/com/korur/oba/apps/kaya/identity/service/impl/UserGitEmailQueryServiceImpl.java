package com.korur.oba.apps.kaya.identity.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.EmailDomainHelper;
import com.korur.oba.apps.kaya.identity.repository.UserGitEmailRepository;
import com.korur.oba.apps.kaya.identity.service.UserGitEmailQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserGitEmailQueryServiceImpl implements UserGitEmailQueryService {

    private final UserGitEmailRepository userGitEmailRepository;
    private final UserQueryService userQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;

    @Override
    public Optional<UUID> findUserIdByEmail(UUID organizationId, String email) {
        String normalizedEmail = EmailDomainHelper.normalizeEmail(email);
        Optional<UUID> holderId = userGitEmailRepository.findUserIdByOrganizationIdAndEmail(organizationId, normalizedEmail);
        if (holderId.isPresent()) {
            return holderId;
        }
        // A member's sign-in email attributes its sessions without being registered as a git email,
        // because the platform, not Kaya, now owns sign-in.
        return userQueryService.findUserByEmail(normalizedEmail)
                .map(User::getId)
                .filter(userId -> organizationMemberQueryService.findMember(organizationId, userId).isPresent());
    }
}
