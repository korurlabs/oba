package com.korur.oba.apps.kazan.organizationsetting.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.kazan.organizationsetting.constant.service.OrganizationSettingServiceLogConstant.*;

import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.organizationsetting.dto.response.OrganizationSettingResponse;
import com.korur.oba.apps.kazan.organizationsetting.entity.OrganizationSetting;
import com.korur.oba.apps.kazan.organizationsetting.mapper.OrganizationSettingMapper;
import com.korur.oba.apps.kazan.organizationsetting.repository.OrganizationSettingRepository;
import com.korur.oba.apps.kazan.organizationsetting.service.OrganizationSettingService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implements writes to the Kazan settings of an organization.
 *
 * Boards carry no organization, so a board counts as part of the organization when
 * the admin who designates it is a member of it: an admin cannot point security
 * tickets at a board they cannot see, and a board they cannot see reads as not found.
 */
@Service
@RequiredArgsConstructor
public class OrganizationSettingServiceImpl implements OrganizationSettingService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationSettingServiceImpl.class);

    private final OrganizationSettingRepository organizationSettingRepository;
    private final OrganizationSettingMapper organizationSettingMapper;
    private final BoardAccessService boardAccessService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public OrganizationSettingResponse updateSecurityBoard(UUID organizationId, UUID boardId, UUID userId) {
        if (boardId != null) {
            boardAccessService.verifyBoardAccess(organizationId, boardId, userId, BoardMemberRole.VIEWER);
        }

        // An organization without a row has no settings yet; the first write creates it.
        OrganizationSetting organizationSetting = organizationSettingRepository.findById(organizationId)
                .orElseGet(() -> OrganizationSetting.builder().organizationId(organizationId).build());
        organizationSetting.setSecurityBoardId(boardId);
        organizationSetting = organizationSettingRepository.save(organizationSetting);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SECURITY_BOARD_UPDATED, organizationId, boardId);

        return organizationSettingMapper.toResponse(organizationSetting);
    }
}
