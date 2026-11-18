package com.korur.oba.apps.kaya.role.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.role.constant.RoleConstant;
import com.korur.oba.apps.kaya.role.constant.service.RoleServiceLogConstant;
import com.korur.oba.apps.kaya.role.dto.request.RoleQueryRequest;
import com.korur.oba.apps.kaya.role.dto.response.RoleDetailResponse;
import com.korur.oba.apps.kaya.role.dto.response.RoleListResponse;
import com.korur.oba.apps.kaya.role.entity.Role;
import com.korur.oba.apps.kaya.role.mapper.RoleMapper;
import com.korur.oba.apps.kaya.role.repository.RoleRepository;
import com.korur.oba.apps.kaya.role.service.RoleQueryService;
import com.korur.oba.apps.kaya.role.specification.RoleSpecification;

/**
 * The {@link RoleQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleQueryServiceImpl implements RoleQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RoleQueryServiceImpl.class);

    // Version first, so the roles of one version stay together when a page lists several.
    private static final Sort LIST_SORT = Sort.by(RoleConstant.FIELD_VERSION_ID,
            RoleConstant.FIELD_RELATIVE_PATH, RoleConstant.FIELD_ID);

    private final RoleRepository roleRepository;
    private final BlobQueryService blobQueryService;
    private final RoleMapper roleMapper;
    private final LoggingService loggingService;

    @Override
    public List<RoleListResponse> getRoles(UUID organizationId, RoleQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RoleServiceLogConstant.LISTING_ROLES,
                organizationId, request);
        List<Role> roles = roleRepository.findAll(
                RoleSpecification.filterBy(organizationId, request), LIST_SORT);
        if (roles.isEmpty()) {
            return List.of();
        }

        // The sizes are read in one query, without loading any content.
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                roles.stream().map(Role::getContentBlobId).collect(Collectors.toSet()));
        return roles.stream()
                .map(role -> roleMapper.toListResponse(role, sizes.get(role.getContentBlobId())))
                .toList();
    }

    @Override
    public RoleDetailResponse getRole(UUID organizationId, UUID roleId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RoleServiceLogConstant.READING_ROLE,
                roleId, organizationId);
        Role role = getEntityById(organizationId, roleId);
        Blob blob = blobQueryService.getEntityById(organizationId, role.getContentBlobId());
        return roleMapper.toDetailResponse(role, blob.getSize(),
                new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public Role getEntityById(UUID organizationId, UUID roleId) {
        // Looked up within the organization, so a role of another organization answers the same
        // 404 as an unknown id.
        return roleRepository.findByIdAndOrganizationId(roleId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, RoleServiceLogConstant.ROLE_NOT_FOUND,
                    roleId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(RoleConstant.FIELD_ROLE_ID, roleId.toString()));
        });
    }

    @Override
    public List<Role> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RoleServiceLogConstant.LOADING_VERSION_ROLES,
                versionId);
        return roleRepository.findByOrganizationIdAndVersionIdOrderByRelativePathAsc(organizationId, versionId);
    }
}
