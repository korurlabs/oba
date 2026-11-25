package com.korur.oba.apps.kaya.machine.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.identity.entity.UserAuditEntry;
import com.korur.oba.apps.kaya.identity.enums.UserAuditAction;
import com.korur.oba.apps.kaya.identity.enums.UserAuditActorKind;
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.UserAuditRecorder;
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;
import com.korur.oba.apps.kaya.machine.constant.service.MachineServiceLogConstant;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.dto.request.MachineCreateRequest;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUpdateRequest;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUsageSnapshotRequest;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUsageWindowRequest;
import com.korur.oba.apps.kaya.machine.dto.response.MachineCreatedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineTokenCreatedResponse;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;
import com.korur.oba.apps.kaya.machine.mapper.MachineMapper;
import com.korur.oba.apps.kaya.machine.repository.MachineRepository;
import com.korur.oba.apps.kaya.machine.repository.MachineUsageSnapshotRepository;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineService;
import com.korur.oba.apps.kaya.machine.service.MachineUsageQueryService;
import com.korur.oba.dojo.security.token.dto.IssuedApiToken;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.service.ApiTokenService;

@Service
@RequiredArgsConstructor
public class MachineServiceImpl implements MachineService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MachineServiceImpl.class);

    private final MachineRepository machineRepository;
    private final MachineQueryService machineQueryService;
    private final MachineUsageQueryService machineUsageQueryService;
    private final MachineUsageSnapshotRepository machineUsageSnapshotRepository;
    private final KayaUserQueryService kayaUserQueryService;
    private final UserAuditRecorder userAuditRecorder;
    private final UserAccessHelper userAccessHelper;
    private final ApiTokenService apiTokenService;
    private final MachineMapper machineMapper;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public MachineCreatedResponse createMachine(User actor, UUID organizationId, MachineCreateRequest request) {
        // The user row lock serialises this with a revoke all of the same user, so a machine created
        // at that moment is either retired by it or created after it.
        User user = lockUser(actor, organizationId, request.userId());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.CREATING_MACHINE,
                actor.getId(), user.getId(), organizationId);
        userAccessHelper.requireAdminOrSelf(actor, organizationId, user.getId());

        Optional<Machine> existing = machineRepository.findByOrganizationIdAndUser_IdAndMachineName(
                organizationId, user.getId(), request.machineName());
        boolean reactivated = existing.isPresent();
        Machine machine;
        if (existing.isPresent()) {
            machine = existing.get();
            if (machine.getStatus() == MachineStatus.ACTIVE) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.MACHINE_NAME_DUPLICATE,
                        user.getId(), organizationId, machine.getId());
                throw new ConflictException(ExceptionDomainKayaError.VAL_MACHINE_NAME_DUPLICATE,
                        Map.of(MachineConstant.FIELD_MACHINE_NAME, request.machineName(),
                                MachineConstant.FIELD_MACHINE_ID, machine.getId().toString()));
            }
            machine.setStatus(MachineStatus.ACTIVE);
            machine = machineRepository.saveAndFlush(machine);
        } else {
            machine = machineRepository.saveAndFlush(Machine.builder()
                    .organizationId(organizationId)
                    .user(user)
                    .machineName(request.machineName())
                    .build());
        }

        IssuedApiToken issued = issueToken(machine);
        ApiToken token = issued.token();

        Map<String, Object> details = tokenDetails(machine, token);
        details.put(MachineConstant.FIELD_MACHINE_NAME, machine.getMachineName());
        details.put(MachineConstant.FIELD_REACTIVATED, reactivated);
        putRevokedTokenId(details, issued.revokedTokenId());
        recordByUser(organizationId, user, actor, UserAuditAction.MACHINE_CREATED, details);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                reactivated ? MachineServiceLogConstant.MACHINE_REACTIVATED : MachineServiceLogConstant.MACHINE_CREATED,
                machine.getId(), user.getId(), organizationId, token.getId());
        // A new machine has recorded no session yet.
        MachineResponse response = machineMapper.toResponse(machine, token, machineUsageQueryService.usageFor(machine, Instant.now(clock)),
                null);
        return machineMapper.toCreatedResponse(response, issued.plaintext());
    }

    @Override
    @Transactional
    public MachineTokenCreatedResponse rotateToken(User actor, UUID organizationId, UUID machineId) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.ROTATING_TOKEN,
                actor.getId(), machineId, organizationId);

        Machine machine = lockMachine(organizationId, machineId);
        User user = machine.getUser();
        requireSelf(actor, user, machine);
        if (machine.getStatus() == MachineStatus.RETIRED) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.MACHINE_RETIRED_REFUSED, machineId, organizationId);
            throw new ConflictException(ExceptionDomainKayaError.VAL_MACHINE_RETIRED,
                    Map.of(MachineConstant.FIELD_MACHINE_ID, machineId.toString()));
        }

        IssuedApiToken issued = issueToken(machine);
        ApiToken token = issued.token();

        Map<String, Object> details = tokenDetails(machine, token);
        putRevokedTokenId(details, issued.revokedTokenId());
        recordByUser(organizationId, user, actor,
                issued.revokedTokenId() != null ? UserAuditAction.TOKEN_ROTATED : UserAuditAction.TOKEN_ISSUED, details);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.TOKEN_ROTATED,
                token.getId(), machineId, organizationId, issued.revokedTokenId());
        return machineMapper.toTokenCreatedResponse(token, issued.plaintext());
    }

    @Override
    @Transactional
    public void revokeToken(User actor, UUID organizationId, UUID machineId) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.REVOKING_TOKEN,
                actor.getId(), machineId, organizationId);

        Machine machine = lockMachine(organizationId, machineId);
        User user = machine.getUser();
        userAccessHelper.requireAdminOrSelf(actor, organizationId, user.getId());

        Optional<ApiToken> revoked = apiTokenService.revoke(ApiTokenKind.KAYA_MACHINE, machine.getId());
        if (revoked.isEmpty()) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.NO_TOKEN_TO_REVOKE, machineId, organizationId);
            return;
        }
        recordByUser(organizationId, user, actor, UserAuditAction.TOKEN_REVOKED, tokenDetails(machine, revoked.get()));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.TOKEN_REVOKED,
                revoked.get().getId(), machineId, organizationId);
    }

    @Override
    @Transactional
    public MachineResponse updateMachine(User actor, UUID organizationId, UUID machineId, MachineUpdateRequest request) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.RETIRING_MACHINE,
                actor.getId(), machineId, organizationId);

        Machine machine = lockMachine(organizationId, machineId);
        User user = machine.getUser();
        userAccessHelper.requireAdminOrSelf(actor, organizationId, user.getId());
        if (machine.getStatus() == MachineStatus.RETIRED) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.MACHINE_ALREADY_RETIRED, machineId, organizationId);
            return machineQueryService.getMachine(organizationId, machineId);
        }

        machine.setStatus(request.status());
        machineRepository.saveAndFlush(machine);
        Optional<ApiToken> revoked = apiTokenService.revoke(ApiTokenKind.KAYA_MACHINE, machine.getId());

        Map<String, Object> details = new HashMap<>();
        details.put(MachineConstant.FIELD_MACHINE_ID, machineId.toString());
        details.put(MachineConstant.FIELD_MACHINE_NAME, machine.getMachineName());
        revoked.ifPresent(old -> details.put(MachineConstant.FIELD_REVOKED_TOKEN_ID, old.getId().toString()));
        recordByUser(organizationId, user, actor, UserAuditAction.MACHINE_RETIRED, details);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.MACHINE_RETIRED,
                machineId, organizationId, revoked.map(ApiToken::getId).orElse(null));
        return machineQueryService.getMachine(organizationId, machineId);
    }

    @Override
    @Transactional
    public Machine recordSeen(MachinePrincipal principal, String claudeCodeVersion, Account account) {
        Machine machine = machineRepository.findByIdAndOrganizationId(principal.machineId(), principal.organizationId())
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.MACHINE_NOT_FOUND,
                            principal.machineId(), principal.organizationId());
                    return new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                            Map.of(MachineConstant.FIELD_MACHINE_ID, principal.machineId().toString()));
                });
        Instant now = Instant.now(clock);
        boolean versionChanged = claudeCodeVersion != null && !claudeCodeVersion.equals(machine.getClaudeCodeVersion());
        boolean accountChanged = account != null
                && (machine.getAccount() == null || !account.getId().equals(machine.getAccount().getId()));
        boolean stale = machine.getLastSeenAt() == null
                || machine.getLastSeenAt().isBefore(now.minusSeconds(MachineConstant.SEEN_REFRESH_SECONDS));
        if (!versionChanged && !accountChanged && !stale) {
            return machine;
        }
        if (versionChanged) {
            machine.setClaudeCodeVersion(claudeCodeVersion);
        }
        if (accountChanged) {
            machine.setAccount(account);
        }
        machine.setLastSeenAt(now);
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.MACHINE_SEEN,
                machine.getId(), claudeCodeVersion, account == null ? null : account.getId());
        return machineRepository.saveAndFlush(machine);
    }

    @Override
    @Transactional
    public void recordUsage(Machine machine, Account account, String sessionId, MachineUsageSnapshotRequest request) {
        Instant receivedAt = Instant.now(clock);
        // A machine clock running ahead would otherwise pin its snapshot above every later one.
        Instant capturedAt = request.capturedAt().isAfter(receivedAt) ? receivedAt : request.capturedAt();
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.RECORDING_USAGE,
                machine.getId(), capturedAt, sessionId);
        BigDecimal fiveHourPercentage = usagePercentage(machine, MachineConstant.WINDOW_FIVE_HOUR, request.fiveHour());
        Instant fiveHourResetsAt = usageResetsAt(machine, MachineConstant.WINDOW_FIVE_HOUR, request.fiveHour(), request.capturedAt());
        BigDecimal sevenDayPercentage = usagePercentage(machine, MachineConstant.WINDOW_SEVEN_DAY, request.sevenDay());
        Instant sevenDayResetsAt = usageResetsAt(machine, MachineConstant.WINDOW_SEVEN_DAY, request.sevenDay(), request.capturedAt());
        if (request.fiveHour() == null && request.sevenDay() == null) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.USAGE_WITHOUT_WINDOW, machine.getId(), capturedAt);
            return;
        }

        int written = machineUsageSnapshotRepository.upsertIfNewer(machine.getOrganizationId(), machine.getId(),
                account == null ? null : account.getId(), sessionId, fiveHourPercentage, fiveHourResetsAt,
                sevenDayPercentage, sevenDayResetsAt, capturedAt, receivedAt);
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE,
                written > 0 ? MachineServiceLogConstant.USAGE_RECORDED : MachineServiceLogConstant.USAGE_OLDER_KEPT,
                machine.getId(), capturedAt);
    }

    private BigDecimal usagePercentage(Machine machine, String windowName, MachineUsageWindowRequest window) {
        if (window == null) {
            return null;
        }
        BigDecimal percentage = window.usedPercentage();
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(MachineConstant.USAGE_PERCENTAGE_MAX)) > 0) {
            throw usageOutOfRange(machine, windowName, MachineConstant.FIELD_USED_PERCENTAGE, percentage.toPlainString());
        }
        return percentage.setScale(MachineConstant.USAGE_PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    private Instant usageResetsAt(Machine machine, String windowName, MachineUsageWindowRequest window, Instant capturedAt) {
        if (window == null) {
            return null;
        }
        Instant resetsAt = Instant.ofEpochSecond(window.resetsAt());
        Duration distance = Duration.between(capturedAt, resetsAt).abs();
        if (distance.compareTo(Duration.ofDays(MachineConstant.USAGE_RESETS_AT_MAX_DAYS)) > 0) {
            throw usageOutOfRange(machine, windowName, MachineConstant.FIELD_RESETS_AT, String.valueOf(window.resetsAt()));
        }
        return resetsAt;
    }

    private BadRequestException usageOutOfRange(Machine machine, String windowName, String field, String value) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.USAGE_OUT_OF_RANGE, windowName, machine.getId(), field);
        return new BadRequestException(ExceptionDomainKayaError.VAL_USAGE_OUT_OF_RANGE,
                Map.of(MachineConstant.FIELD_WINDOW, windowName, field, value));
    }

    private User lockUser(User actor, UUID organizationId, UUID userId) {
        return kayaUserQueryService.getMemberEntityForUpdate(organizationId, userId != null ? userId : actor.getId());
    }

    private Machine lockMachine(UUID organizationId, UUID machineId) {
        // Looked up within the organization, so a machine of another organization answers the
        // same 404 as an unknown id.
        return machineRepository.findByIdAndOrganizationIdForUpdate(machineId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.MACHINE_NOT_FOUND, machineId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                    Map.of(MachineConstant.FIELD_MACHINE_ID, machineId.toString()));
        });
    }

    /**
     * Only the person a token is for may receive it: an ADMIN who rotated someone else's token
     * would hold a working credential of that person.
     */
    private void requireSelf(User actor, User user, Machine machine) {
        if (actor.getId().equals(user.getId())) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.SELF_REQUIRED,
                actor.getId(), user.getId(), machine.getId());
        throw new ForbiddenException(ExceptionSecurityError.INCORRECT_ROLE,
                Map.of(MachineConstant.FIELD_MACHINE_ID, machine.getId().toString(),
                        MachineConstant.FIELD_USER_ID, user.getId().toString()));
    }

    /**
     * Issues the machine's token as its user, valid only in the machine's organization, and revokes the
     * live one in the same transaction.
     */
    private IssuedApiToken issueToken(Machine machine) {
        return apiTokenService.issue(ApiTokenKind.KAYA_MACHINE, machine.getId(), machine.getUser(),
                machine.getOrganizationId());
    }

    private static void putRevokedTokenId(Map<String, Object> details, UUID revokedTokenId) {
        if (revokedTokenId != null) {
            details.put(MachineConstant.FIELD_REVOKED_TOKEN_ID, revokedTokenId.toString());
        }
    }

    private Map<String, Object> tokenDetails(Machine machine, ApiToken token) {
        Map<String, Object> details = new HashMap<>();
        details.put(MachineConstant.FIELD_MACHINE_ID, machine.getId().toString());
        details.put(MachineConstant.FIELD_TOKEN_ID, token.getId().toString());
        details.put(MachineConstant.FIELD_TOKEN_PREFIX, token.getTokenPrefix());
        details.put(MachineConstant.FIELD_EXPIRES_AT, token.getExpiresAt().toString());
        return details;
    }

    private void recordByUser(UUID organizationId, User user, User actor, UserAuditAction action,
            Map<String, Object> details) {
        userAuditRecorder.record(UserAuditEntry.builder()
                .organizationId(organizationId)
                .user(user)
                .actorKind(UserAuditActorKind.USER)
                .actorId(actor.getId())
                .action(action)
                .details(details)
                .build());
    }
}
