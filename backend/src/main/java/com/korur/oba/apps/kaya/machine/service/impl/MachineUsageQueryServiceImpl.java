package com.korur.oba.apps.kaya.machine.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.machine.constant.service.MachineServiceLogConstant;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageSharedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageWindowResponse;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.entity.MachineUsageSnapshot;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;
import com.korur.oba.apps.kaya.machine.enums.MachineUsageState;
import com.korur.oba.apps.kaya.machine.mapper.MachineMapper;
import com.korur.oba.apps.kaya.machine.repository.MachineRepository;
import com.korur.oba.apps.kaya.machine.repository.MachineUsageSnapshotRepository;
import com.korur.oba.apps.kaya.machine.service.MachineUsageQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineUsageQueryServiceImpl implements MachineUsageQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MachineUsageQueryServiceImpl.class);

    private final MachineRepository machineRepository;
    private final MachineUsageSnapshotRepository machineUsageSnapshotRepository;
    private final MachineMapper machineMapper;
    private final LoggingService loggingService;

    @Override
    public MachineUsageResponse usageFor(Machine machine, Instant now) {
        return usageFor(machine.getOrganizationId(), List.of(machine), now).get(machine.getId());
    }

    @Override
    public Map<UUID, MachineUsageResponse> usageFor(UUID organizationId, List<Machine> machines, Instant now) {
        if (machines.isEmpty()) {
            return Map.of();
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.READING_USAGE, machines.size(), organizationId);

        Set<UUID> machineIds = new LinkedHashSet<>();
        Set<UUID> accountIds = new LinkedHashSet<>();
        for (Machine machine : machines) {
            machineIds.add(machine.getId());
            UUID accountId = accountIdOf(machine);
            if (accountId != null) {
                accountIds.add(accountId);
            }
        }

        Map<UUID, MachineUsageSnapshot> ownByMachine = new HashMap<>();
        Map<UUID, MachineUsageSnapshot> newestByAccount = new HashMap<>();
        List<MachineUsageSnapshot> snapshots = accountIds.isEmpty()
                ? machineUsageSnapshotRepository.findByOrganizationIdAndMachine_IdIn(organizationId, machineIds)
                : machineUsageSnapshotRepository.findForMachinesOrAccounts(organizationId, machineIds, accountIds);
        for (MachineUsageSnapshot snapshot : snapshots) {
            ownByMachine.put(snapshot.getMachine().getId(), snapshot);
            keepNewest(newestByAccount, snapshot);
        }

        Map<UUID, List<Machine>> activeByAccount = new HashMap<>();
        if (!accountIds.isEmpty()) {
            for (Machine active : machineRepository.findByOrganizationIdAndStatusAndAccount_IdIn(
                    organizationId, MachineStatus.ACTIVE, accountIds)) {
                activeByAccount.computeIfAbsent(accountIdOf(active), key -> new ArrayList<>()).add(active);
            }
        }

        Map<UUID, MachineUsageResponse> result = new HashMap<>();
        for (Machine machine : machines) {
            UUID accountId = accountIdOf(machine);
            List<MachineUsageSharedResponse> sharedWith = new ArrayList<>();
            if (accountId != null) {
                for (Machine other : activeByAccount.getOrDefault(accountId, List.of())) {
                    if (!other.getId().equals(machine.getId())) {
                        sharedWith.add(machineMapper.toUsageSharedResponse(other));
                    }
                }
            }
            MachineUsageSnapshot snapshot = accountId != null ? newestByAccount.get(accountId) : ownByMachine.get(machine.getId());
            result.put(machine.getId(), toResponse(accountId, snapshot, sharedWith, now));
        }
        return result;
    }

    @Override
    public Map<UUID, MachineUsageResponse> usageForAccounts(UUID organizationId, Collection<UUID> accountIds, Instant now) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.READING_ACCOUNT_USAGE,
                accountIds.size(), organizationId);

        Map<UUID, MachineUsageSnapshot> newestByAccount = new HashMap<>();
        for (MachineUsageSnapshot snapshot : machineUsageSnapshotRepository.findByOrganizationIdAndAccount_IdIn(
                organizationId, accountIds)) {
            keepNewest(newestByAccount, snapshot);
        }

        Map<UUID, MachineUsageResponse> result = new HashMap<>();
        for (UUID accountId : accountIds) {
            result.put(accountId, toResponse(accountId, newestByAccount.get(accountId), List.of(), now));
        }
        return result;
    }

    /**
     * Keeps the newest snapshot of each account, since machines sharing an account report the same
     * limits and the latest capture is the closest to the truth.
     */
    private static void keepNewest(Map<UUID, MachineUsageSnapshot> newestByAccount, MachineUsageSnapshot snapshot) {
        UUID accountId = snapshot.getAccount() == null ? null : snapshot.getAccount().getId();
        if (accountId == null) {
            return;
        }
        MachineUsageSnapshot newest = newestByAccount.get(accountId);
        if (newest == null || snapshot.getCapturedAt().isAfter(newest.getCapturedAt())) {
            newestByAccount.put(accountId, snapshot);
        }
    }

    private MachineUsageResponse toResponse(UUID accountId, MachineUsageSnapshot snapshot,
            List<MachineUsageSharedResponse> sharedWith, Instant now) {
        if (snapshot == null) {
            MachineUsageState state = accountId == null ? MachineUsageState.NO_ACCOUNT : MachineUsageState.NO_SNAPSHOT;
            return machineMapper.toUsageResponse(state, null, sharedWith, null, null);
        }
        return machineMapper.toUsageResponse(MachineUsageState.KNOWN, snapshot, sharedWith,
                window(snapshot.getFiveHourUsedPercentage(), snapshot.getFiveHourResetsAt(), now),
                window(snapshot.getSevenDayUsedPercentage(), snapshot.getSevenDayResetsAt(), now));
    }

    /**
     * A window whose reset time has passed no longer holds its stored percentage, so it is shown
     * reset without a value rather than with an old one.
     */
    private MachineUsageWindowResponse window(BigDecimal usedPercentage, Instant resetsAt, Instant now) {
        if (resetsAt == null) {
            return null;
        }
        boolean reset = !resetsAt.isAfter(now);
        return machineMapper.toUsageWindowResponse(reset ? null : usedPercentage, resetsAt, reset);
    }

    private static UUID accountIdOf(Machine machine) {
        return machine.getAccount() == null ? null : machine.getAccount().getId();
    }
}
