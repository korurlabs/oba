package com.korur.oba.apps.kaya.role.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.role.constant.service.RoleServiceLogConstant;
import com.korur.oba.apps.kaya.role.dto.embedded.RoleIndex;
import com.korur.oba.apps.kaya.role.entity.Role;
import com.korur.oba.apps.kaya.role.repository.RoleRepository;
import com.korur.oba.apps.kaya.role.service.RoleService;

/**
 * The {@link RoleService} implementation.
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RoleServiceImpl.class);

    private final RoleRepository roleRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Role> replaceVersion(UUID organizationId, UUID versionId, List<RoleIndex> roles) {
        // The old rows are deleted and flushed first, so a role saved again at the same path does
        // not collide with the old row on the version and path unique index.
        int removed = roleRepository.deleteByVersion(organizationId, versionId);
        List<Role> entities = roles.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Role> saved = roleRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RoleServiceLogConstant.ROLES_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Role saveFile(UUID organizationId, UUID versionId, RoleIndex index) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and path unique key.
        int removed = roleRepository.deleteByPath(organizationId, versionId, index.relativePath());
        Role saved = roleRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RoleServiceLogConstant.ROLE_SAVED,
                index.relativePath(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteFile(UUID organizationId, UUID versionId, String relativePath) {
        int removed = roleRepository.deleteByPath(organizationId, versionId, relativePath);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RoleServiceLogConstant.ROLE_DELETED,
                relativePath, versionId, organizationId, removed);
        return removed > 0;
    }

    private Role toEntity(UUID organizationId, UUID versionId, RoleIndex index) {
        return Role.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .relativePath(index.relativePath())
                .title(index.title())
                .build();
    }
}
