package com.korur.oba.shrine.identity.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.identity.dto.UserQueryRequest;
import com.korur.oba.shrine.identity.dto.response.UserListResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.identity.mapper.UserMapper;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.identity.specification.UserSpecification;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryServiceImpl implements UserQueryService {

    // An agent's name is its first name.
    private static final Comparator<User> AGENT_NAME_ORDER = Comparator.comparing(
            (User user) -> user.getProfile().getFirstName(), String.CASE_INSENSITIVE_ORDER);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final EntityManager entityManager;

    @Override
    public Page<UserListResponse> getAllUsers(UUID actorUserId, UserQueryRequest query) {
        Set<UUID> visibleUserIds = organizationMemberQueryService.getColleagueUserIds(actorUserId);
        Specification<User> spec = UserSpecification.filterBy(visibleUserIds, query);
        Page<User> users = userRepository.findAll(spec, query.toPageable());
        return users.map(userMapper::toListResponse);
    }

    @Override
    public User getUserEntityById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainIdentityError.NOT_USER_FOUND));
    }

    @Override
    public List<User> getUserEntitiesByIds(List<UUID> userIds) {
        return userRepository.findAllById(userIds);
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    @Override
    public Optional<User> findUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public User getUserEntityForUpdate(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainIdentityError.NOT_USER_FOUND));
        // A locking query hands back an instance this transaction loaded earlier as it was then;
        // reading it again under the lock gives the state the lock now protects.
        entityManager.refresh(user);
        return user;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockUserForKeyShare(UUID userId) {
        if (userRepository.lockForKeyShare(userId).isEmpty()) {
            throw new NotFoundException(ExceptionDomainIdentityError.NOT_USER_FOUND);
        }
    }

    @Override
    public List<User> getAgentsOfOrganization(UUID organizationId) {
        List<User> agents = new ArrayList<>(
                userRepository.findAllByKindAndAgentDetailOrganizationId(UserKind.AGENT, organizationId));
        agents.sort(AGENT_NAME_ORDER);
        return agents;
    }

    @Override
    public Optional<User> findAgent(UUID organizationId, UUID userId) {
        return userRepository.findByIdAndKindAndAgentDetailOrganizationId(userId, UserKind.AGENT, organizationId);
    }

    @Override
    public boolean existsAgentName(UUID organizationId, String name, UUID excludeUserId) {
        return userRepository.existsAgentName(organizationId, name, excludeUserId);
    }
}
