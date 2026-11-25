package com.korur.oba.apps.kaya.machine.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserDisplayNameHelper;
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.dto.projection.MachineAccountSummaryProjection;
import com.korur.oba.apps.kaya.machine.dto.projection.MachineUserSummaryProjection;
import com.korur.oba.apps.kaya.machine.dto.response.MachineAccountSummaryResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineCreatedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineTokenCreatedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineUserSummaryResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineAccountEmbeddedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineTokenEmbeddedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageSharedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageWindowResponse;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.entity.MachineUsageSnapshot;
import com.korur.oba.apps.kaya.machine.enums.MachineUsageState;
import com.korur.oba.apps.kaya.session.dto.response.SessionCountSummaryResponse;
import com.korur.oba.dojo.security.token.entity.ApiToken;

/**
 * Maps machines to responses. The live token and the session count are passed in by the caller, which
 * loads them for a whole list at once.
 */
@Mapper(componentModel = "spring", imports = UserDisplayNameHelper.class)
public interface MachineMapper {

    @Mapping(target = "id", source = "machine.id")
    @Mapping(target = "userId", source = "machine.user.id")
    @Mapping(target = "userDisplayName", expression = "java(UserDisplayNameHelper.displayNameOf(machine.getUser()))")
    @Mapping(target = "machineName", source = "machine.machineName")
    @Mapping(target = "claudeCodeVersion", source = "machine.claudeCodeVersion")
    @Mapping(target = "account", source = "machine.account")
    @Mapping(target = "status", source = "machine.status")
    @Mapping(target = "lastSeenAt", source = "machine.lastSeenAt")
    @Mapping(target = "token", source = "token")
    @Mapping(target = "usage", source = "usage")
    @Mapping(target = "sessionCount", source = "sessionSummary.sessions", defaultValue = "0")
    @Mapping(target = "createdAt", source = "machine.createdAt")
    @Mapping(target = "updatedAt", source = "machine.updatedAt")
    MachineResponse toResponse(Machine machine, ApiToken token, MachineUsageResponse usage, SessionCountSummaryResponse sessionSummary);

    @Mapping(target = "usedPercentage", source = "usedPercentage")
    @Mapping(target = "resetsAt", source = "resetsAt")
    @Mapping(target = "reset", source = "reset")
    MachineUsageWindowResponse toUsageWindowResponse(BigDecimal usedPercentage, Instant resetsAt, boolean reset);

    @Mapping(target = "machineId", source = "id")
    MachineUsageSharedResponse toUsageSharedResponse(Machine machine);

    @Mapping(target = "state", source = "state")
    @Mapping(target = "capturedAt", source = "snapshot.capturedAt")
    @Mapping(target = "reportedByMachineId", source = "snapshot.machine.id")
    @Mapping(target = "reportedByMachineName", source = "snapshot.machine.machineName")
    @Mapping(target = "sharedWith", source = "sharedWith")
    @Mapping(target = "fiveHour", source = "fiveHour")
    @Mapping(target = "sevenDay", source = "sevenDay")
    MachineUsageResponse toUsageResponse(MachineUsageState state, MachineUsageSnapshot snapshot,
            List<MachineUsageSharedResponse> sharedWith, MachineUsageWindowResponse fiveHour,
            MachineUsageWindowResponse sevenDay);

    @Mapping(target = "organizationId", source = "machine.organizationId")
    @Mapping(target = "userId", source = "machine.user.id")
    @Mapping(target = "machineId", source = "machine.id")
    @Mapping(target = "tokenId", source = "tokenId")
    @Mapping(target = "machineName", source = "machine.machineName")
    MachinePrincipal toPrincipal(Machine machine, UUID tokenId);

    MachineTokenEmbeddedResponse toTokenEmbeddedResponse(ApiToken token);

    MachineAccountEmbeddedResponse toAccountEmbeddedResponse(Account account);

    @Mapping(target = "machine", source = "machine")
    @Mapping(target = "token", source = "plaintext")
    MachineCreatedResponse toCreatedResponse(MachineResponse machine, String plaintext);

    @Mapping(target = "token", source = "plaintext")
    @Mapping(target = "tokenId", source = "token.id")
    @Mapping(target = "tokenPrefix", source = "token.tokenPrefix")
    @Mapping(target = "expiresAt", source = "token.expiresAt")
    MachineTokenCreatedResponse toTokenCreatedResponse(ApiToken token, String plaintext);

    MachineUserSummaryResponse toUserSummaryResponse(MachineUserSummaryProjection summary);

    MachineAccountSummaryResponse toAccountSummaryResponse(MachineAccountSummaryProjection summary);
}
