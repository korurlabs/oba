package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.dto.projection.OrganizationAppGrantProjection;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.dto.response.MemberAppAccessResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.mapper.AppAccessMapper;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.OrganizationGroupQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

/**
 * Implements the read operations for app access.
 *
 * The status and role part of the access rule is checked here, before the grant query, because the
 * native grant query of {@link AppGrantRepository#existsGrantForUser} knows only the grants. Membership
 * and organization data come from the organization services, never from their repositories.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppAccessQueryServiceImpl implements AppAccessQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AppAccessQueryServiceImpl.class);

    // Grants by app, then by the name of the user or group they target, whatever its case.
    private static final Comparator<AppGrantResponse> GRANT_ORDER = Comparator
            .comparing(AppGrantResponse::app)
            .thenComparing(AppAccessQueryServiceImpl::targetNameOf,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    // Members by full name, whatever its case.
    private static final Comparator<MemberAppAccessResponse> MEMBER_ORDER = Comparator
            .comparing(MemberAppAccessResponse::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final AppGrantRepository appGrantRepository;
    private final AppAccessMapper appAccessMapper;
    private final OrganizationGroupQueryService organizationGroupQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final LoggingService loggingService;

    @Override
    public boolean hasAppAccess(OrganizationMember member, App app) {
        if (!isAccessOpen(member)) {
            return false;
        }
        // A grant never raises a role: a member below the app's minimum role stays out on every path,
        // machine tokens included, as listAccessibleApps leaves the app out of the member's list.
        if (!member.getRole().isAtLeast(app.getMinimumRole())) {
            return false;
        }
        if (member.getRole() == OrganizationMemberRole.OWNER) {
            return true;
        }
        return appGrantRepository.existsGrantForUser(member.getOrganization().getId(), member.getUser().getId(), app);
    }

    @Override
    public boolean hasAppAccess(UUID organizationId, UUID userId, App app) {
        return organizationMemberQueryService.findMember(organizationId, userId)
                .map(member -> hasAppAccess(member, app))
                .orElse(false);
    }

    @Override
    public Set<UUID> findUserIdsWithAppAccess(UUID organizationId, App app, Collection<UUID> userIds) {
        // An empty IN list is invalid SQL, and no user means no holder.
        if (userIds.isEmpty()) {
            return Set.of();
        }
        // The roles that reach the app's minimum role; the query adds the OWNER rule and the grants.
        List<String> roleNames = Arrays.stream(OrganizationMemberRole.values())
                .filter((OrganizationMemberRole role) -> role.isAtLeast(app.getMinimumRole()))
                .map(OrganizationMemberRole::name)
                .toList();
        return new HashSet<>(appGrantRepository.findUserIdsWithAppByAppName(
                organizationId, app.name(), roleNames, Set.copyOf(userIds)));
    }

    @Override
    public void verifyAppAccess(UUID organizationId, UUID userId, App app) {
        if (hasAppAccess(organizationId, userId, app)) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AccessExceptionLogConstant.APP_ACCESS_DENIED,
                organizationId, userId, app);
        throw new ForbiddenException(
                ExceptionDomainAccessError.SEC_APP_ACCESS_DENIED,
                Map.of(
                        OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                        AccessConstant.FIELD_APP, app.name()));
    }

    @Override
    public List<App> listAccessibleApps(OrganizationMember member) {
        if (!isAccessOpen(member)) {
            return List.of();
        }
        return resolveApps(member.getOrganization().getId(), member.getUser().getId(), member.getRole());
    }

    @Override
    public Map<UUID, List<App>> listAccessibleAppsByOrganization(User user) {
        List<OrganizationMember> openMemberships = user.getOrganizationMemberships() == null
                ? List.of()
                : user.getOrganizationMemberships().stream()
                        .filter(AppAccessQueryServiceImpl::isAccessOpen)
                        .toList();
        // An empty IN list is invalid SQL, and no open membership means no app.
        if (openMemberships.isEmpty()) {
            return Map.of();
        }
        Set<UUID> organizationIds = new HashSet<>();
        for (OrganizationMember member : openMemberships) {
            organizationIds.add(member.getOrganization().getId());
        }

        Map<UUID, Set<App>> grantedApps = new HashMap<>();
        for (OrganizationAppGrantProjection row : appGrantRepository.findGrantedAppsOfUser(user.getId(),
                organizationIds)) {
            grantedApps.computeIfAbsent(row.getOrganizationId(), (UUID organizationId) -> EnumSet.noneOf(App.class))
                    .add(App.valueOf(row.getApp()));
        }

        Map<UUID, List<App>> appsByOrganization = new HashMap<>();
        for (OrganizationMember member : openMemberships) {
            UUID organizationId = member.getOrganization().getId();
            Set<App> granted = grantedApps.getOrDefault(organizationId, Set.of());
            appsByOrganization.put(organizationId, appsOf(member.getRole(), granted::contains));
        }
        return appsByOrganization;
    }

    @Override
    public List<AppGrantResponse> listGrants(User user, UUID organizationId) {
        return appGrantRepository.findByOrganization_Id(organizationId)
                .stream()
                .map(appAccessMapper::toResponse)
                .sorted(GRANT_ORDER)
                .toList();
    }

    @Override
    public List<MemberAppAccessResponse> listMemberAccess(User user, UUID organizationId) {
        // A disabled organization opens no app to anyone, the owner included.
        Organization organization = organizationQueryService.getOrganizationEntityById(organizationId);
        boolean organizationActive = organization.getStatus() == OrganizationStatus.ACTIVE;

        return organizationMemberQueryService.listOrganizationMembers(user, organizationId)
                .stream()
                .filter(member -> member.status() == OrganizationMemberStatus.ACTIVE)
                .map(member -> appAccessMapper.toMemberAccessResponse(member, organizationActive
                        ? resolveApps(organizationId, member.userId(), member.role())
                        : List.of()))
                .sorted(MEMBER_ORDER)
                .toList();
    }

    @Override
    public Set<UUID> findTargetUserIds(AppGrant grant) {
        if (grant.getUser() != null) {
            return Set.of(grant.getUser().getId());
        }
        return organizationGroupQueryService.findGroupUserIds(grant.getGroup());
    }

    @Override
    public Map<App, Set<UUID>> findAppHolders(UUID organizationId, Collection<App> apps,
                                                 Collection<UUID> userIds) {
        Map<App, Set<UUID>> holders = new EnumMap<>(App.class);
        for (App app : apps) {
            Set<UUID> appHolders = findUserIdsWithAppAccess(organizationId, app, userIds);
            if (!appHolders.isEmpty()) {
                holders.put(app, appHolders);
            }
        }
        return holders;
    }

    /**
     * Tells whether a membership passes the status part of the access rule: an ACTIVE membership of an
     * ACTIVE organization.
     */
    private static boolean isAccessOpen(OrganizationMember member) {
        return member != null
                && member.getStatus() == OrganizationMemberStatus.ACTIVE
                && member.getOrganization().getStatus() == OrganizationStatus.ACTIVE;
    }

    /**
     * Collects the apps an ACTIVE member of an ACTIVE organization can open: every app at or below the
     * member's role that the role (OWNER) or a grant opens. The caller checks the statuses first.
     */
    private List<App> resolveApps(UUID organizationId, UUID userId, OrganizationMemberRole role) {
        return appsOf(role, (App app) -> appGrantRepository.existsGrantForUser(organizationId, userId, app));
    }

    /**
     * Applies the role part of the access rule to every app: an app at or below the role, opened by the
     * OWNER role or by a grant. The grant test runs only for an app the role can reach, so a single read
     * queries no grant it does not need. The caller checks the statuses first.
     */
    private static List<App> appsOf(OrganizationMemberRole role, Predicate<App> granted) {
        List<App> apps = new ArrayList<>();
        for (App app : App.values()) {
            if (!role.isAtLeast(app.getMinimumRole())) {
                continue;
            }
            if (role == OrganizationMemberRole.OWNER || granted.test(app)) {
                apps.add(app);
            }
        }
        return List.copyOf(apps);
    }

    /**
     * Returns the name of the user or group a grant targets.
     */
    private static String targetNameOf(AppGrantResponse grant) {
        return grant.userId() != null ? grant.userFullName() : grant.groupName();
    }
}
