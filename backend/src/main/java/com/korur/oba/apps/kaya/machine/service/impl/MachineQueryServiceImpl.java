package com.korur.oba.apps.kaya.machine.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;
import com.korur.oba.apps.kaya.machine.constant.service.MachineServiceLogConstant;
import com.korur.oba.apps.kaya.machine.dto.request.MachineQueryRequest;
import com.korur.oba.apps.kaya.machine.dto.response.MachineAccountSummaryResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineUserSummaryResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageResponse;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.entity.MachineToken;
import com.korur.oba.apps.kaya.machine.mapper.MachineMapper;
import com.korur.oba.apps.kaya.machine.repository.MachineRepository;
import com.korur.oba.apps.kaya.machine.repository.MachineTokenRepository;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineUsageQueryService;
import com.korur.oba.apps.kaya.machine.specification.MachineSpecification;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.session.dto.response.SessionCountSummaryResponse;
import com.korur.oba.apps.kaya.session.service.SessionCountQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineQueryServiceImpl implements MachineQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MachineQueryServiceImpl.class);

    private final MachineRepository machineRepository;
    private final MachineTokenRepository machineTokenRepository;
    private final MachineUsageQueryService machineUsageQueryService;
    private final SessionCountQueryService sessionCountQueryService;
    private final MachineMapper machineMapper;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    public List<MachineResponse> getMachines(UUID organizationId, MachineQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.LISTING_MACHINES, organizationId, request);

        Sort sort = Sort.by(MachineConstant.FIELD_USER_FIRST_NAME)
                .and(Sort.by(MachineConstant.FIELD_USER_LAST_NAME))
                .and(Sort.by(MachineConstant.FIELD_USER_EMAIL))
                .and(Sort.by(MachineConstant.FIELD_MACHINE_NAME))
                .and(Sort.by(CommonConstant.ID));
        List<Machine> machines = machineRepository.findAll(MachineSpecification.filterBy(organizationId, request), sort);

        // The live tokens of the whole list come in one query instead of one per machine.
        List<UUID> machineIds = machines.stream().map(Machine::getId).toList();
        Map<UUID, MachineToken> tokensByMachine = machineIds.isEmpty()
                ? Map.of()
                : machineTokenRepository.findByMachine_IdInAndRevokedAtIsNull(machineIds).stream()
                        .collect(Collectors.toMap(token -> token.getMachine().getId(), Function.identity()));

        Map<UUID, MachineUsageResponse> usageByMachine = machineUsageQueryService.usageFor(organizationId, machines, Instant.now(clock));
        Map<UUID, SessionCountSummaryResponse> sessionsByMachine = sessionCountQueryService.countByMachines(organizationId, machineIds);

        return machines.stream()
                .map(machine -> machineMapper.toResponse(machine, tokensByMachine.get(machine.getId()), usageByMachine.get(machine.getId()),
                        sessionsByMachine.get(machine.getId())))
                .toList();
    }

    @Override
    public MachineResponse getMachine(UUID organizationId, UUID machineId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.READING_MACHINE, machineId, organizationId);
        // Looked up within the organization, so a machine of another organization answers the
        // same 404 as an unknown id.
        Machine machine = machineRepository.findByIdAndOrganizationId(machineId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.MACHINE_NOT_FOUND, machineId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                    Map.of(MachineConstant.FIELD_MACHINE_ID, machineId.toString()));
        });
        return machineMapper.toResponse(machine, machineTokenRepository.findByMachine_IdAndRevokedAtIsNull(machineId).orElse(null),
                machineUsageQueryService.usageFor(machine, Instant.now(clock)),
                sessionCountQueryService.countByMachines(organizationId, List.of(machineId)).get(machineId));
    }

    @Override
    public Map<UUID, MachineUserSummaryResponse> getUserSummaries(UUID organizationId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return machineRepository.summarizeByUserIds(organizationId, userIds).stream()
                .map(machineMapper::toUserSummaryResponse)
                .collect(Collectors.toMap(MachineUserSummaryResponse::userId, Function.identity()));
    }

    @Override
    public Map<UUID, MachineAccountSummaryResponse> getAccountSummaries(UUID organizationId, Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return machineRepository.summarizeByAccountIds(organizationId, accountIds).stream()
                .map(machineMapper::toAccountSummaryResponse)
                .collect(Collectors.toMap(MachineAccountSummaryResponse::accountId, Function.identity()));
    }

    @Override
    public Map<UUID, String> getMachineNames(UUID organizationId, Collection<UUID> machineIds) {
        if (machineIds.isEmpty()) {
            return Map.of();
        }
        return machineRepository.findAllById(machineIds).stream()
                .filter(machine -> machine.getOrganizationId().equals(organizationId))
                .collect(Collectors.toMap(Machine::getId, Machine::getMachineName));
    }
}
