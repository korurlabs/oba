package com.korur.oba.apps.kaizen.organizationsetting.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.organizationsetting.entity.OrganizationSetting;
import com.korur.oba.apps.kaizen.organizationsetting.repository.OrganizationSettingRepository;
import com.korur.oba.apps.kaizen.organizationsetting.service.OrganizationSettingQueryService;

/**
 * Implements reads of the Kaizen settings of an organization.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationSettingQueryServiceImpl implements OrganizationSettingQueryService {

    private final OrganizationSettingRepository organizationSettingRepository;

    @Override
    public Optional<UUID> getSecurityBoardId(UUID organizationId) {
        return organizationSettingRepository.findById(organizationId)
                .map(OrganizationSetting::getSecurityBoardId);
    }
}
