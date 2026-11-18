package com.korur.oba.apps.kaya.identity.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.identity.constant.IdentityConstant;
import com.korur.oba.apps.kaya.identity.constant.service.OrganizationAccessServiceLogConstant;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationAccessQueryServiceImpl implements OrganizationAccessQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationAccessQueryServiceImpl.class);

    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    @Override
    public OrganizationMember verifyTenantAccess(User user, UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE,
                OrganizationAccessServiceLogConstant.CHECKING_TENANT_ACCESS, user.getId(), organizationId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationAccessServiceLogConstant.USER_NOT_ACTIVE, user.getId(), user.getStatus());
            throw new ForbiddenException(ExceptionSecurityError.FORBIDDEN,
                    Map.of(IdentityConstant.FIELD_USER_STATUS, user.getStatus().name()));
        }

        OrganizationMember member = organizationMemberQueryService.findMember(organizationId, user.getId())
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                            OrganizationAccessServiceLogConstant.NOT_A_MEMBER, user.getId(), organizationId);
                    return new ForbiddenException(ExceptionSecurityError.FORBIDDEN,
                            Map.of(IdentityConstant.FIELD_ORGANIZATION_ID, organizationId.toString()));
                });

        if (member.getStatus() != OrganizationMemberStatus.ACTIVE) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationAccessServiceLogConstant.MEMBERSHIP_NOT_ACTIVE,
                    user.getId(), organizationId, member.getStatus());
            throw new ForbiddenException(ExceptionDomainKayaError.SEC_MEMBERSHIP_DISABLED,
                    Map.of(IdentityConstant.FIELD_ORGANIZATION_ID, organizationId.toString(),
                            IdentityConstant.FIELD_MEMBER_STATUS, member.getStatus().name()));
        }

        Organization organization = member.getOrganization();
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationAccessServiceLogConstant.ORGANIZATION_NOT_ACTIVE,
                    organizationId, organization.getStatus());
            throw new ForbiddenException(ExceptionDomainKayaError.SEC_MEMBERSHIP_DISABLED,
                    Map.of(IdentityConstant.FIELD_ORGANIZATION_ID, organizationId.toString(),
                            IdentityConstant.FIELD_ORGANIZATION_STATUS, organization.getStatus().name()));
        }

        // A platform GUEST may read what Kazan shares with guests, but Kaya holds the
        // organization's agent sessions and configuration, so a guest gets no access at all.
        if (member.getRole() == OrganizationMemberRole.GUEST) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationAccessServiceLogConstant.GUEST_REFUSED, user.getId(), organizationId);
            throw new ForbiddenException(ExceptionSecurityError.INCORRECT_ROLE,
                    Map.of(IdentityConstant.FIELD_REQUIRED_ROLE, OrganizationMemberRole.MEMBER.name(),
                            IdentityConstant.FIELD_ACTUAL_ROLE, member.getRole().name()));
        }

        return member;
    }
}
