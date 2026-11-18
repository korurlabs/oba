package com.korur.oba.shrine.access.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.repository.OrganizationGroupMemberRepository;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;

/**
 * Validates the business rules of organization groups and their member rows.
 */
@Component
@RequiredArgsConstructor
public class OrganizationGroupValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationGroupValidator.class);

    private final OrganizationGroupMemberRepository organizationGroupMemberRepository;
    private final LoggingService loggingService;

    /**
     * Refuses a change to the Everyone group, whose members are all members of the organization and whose
     * name every organization shares.
     *
     * @param organizationId the organization identifier
     * @param group          the group the request changes
     */
    public void ensureCustomGroup(UUID organizationId, OrganizationGroup group) {
        if (group.getType() == OrganizationGroupType.CUSTOM) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, AccessExceptionLogConstant.SYSTEM_GROUP_IMMUTABLE,
                organizationId, group.getId());
        throw new ConflictException(
                ExceptionDomainAccessError.STA_SYSTEM_GROUP_IMMUTABLE,
                Map.of(
                        OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                        AccessConstant.FIELD_GROUP_ID, group.getId()));
    }

    /**
     * Ensures the user has a member row in the group and returns it.
     *
     * @param organizationId the organization identifier, for the error payload
     * @param groupId        the group identifier
     * @param userId         the user identifier
     * @return the member row
     */
    public OrganizationGroupMember ensureGroupMemberExists(UUID organizationId, UUID groupId, UUID userId) {
        return organizationGroupMemberRepository.findByGroup_IdAndUser_Id(groupId, userId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            AccessExceptionLogConstant.GROUP_MEMBER_NOT_FOUND, organizationId, groupId, userId);
                    return new NotFoundException(
                            ExceptionDomainAccessError.NOT_GROUP_MEMBER_FOUND,
                            Map.of(
                                    OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                                    AccessConstant.FIELD_GROUP_ID, groupId,
                                    AccessConstant.FIELD_USER_ID, userId));
                });
    }
}
