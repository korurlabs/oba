package com.korur.oba.apps.kaya.machine.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.apps.kaya.machine.constant.service.MachineServiceLogConstant;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.entity.MachineToken;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;
import com.korur.oba.apps.kaya.machine.helper.MachineTokenHelper;
import com.korur.oba.apps.kaya.machine.mapper.MachineMapper;
import com.korur.oba.apps.kaya.machine.repository.MachineTokenRepository;
import com.korur.oba.apps.kaya.machine.service.MachineTokenQueryService;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineTokenQueryServiceImpl implements MachineTokenQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MachineTokenQueryServiceImpl.class);

    private final MachineTokenRepository machineTokenRepository;
    private final MachineTokenHelper machineTokenHelper;
    private final MachineMapper machineMapper;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    public MachinePrincipal authenticate(String token) {
        // Every refusal answers the same 401, so a caller cannot tell an unknown token from a
        // revoked one; the log line keeps the reason for an operator.
        if (!machineTokenHelper.hasTokenFormat(token)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.TOKEN_MALFORMED);
            throw invalidToken();
        }
        Optional<MachineToken> found = machineTokenRepository.findByTokenHash(machineTokenHelper.hash(token));
        if (found.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.TOKEN_UNKNOWN);
            throw invalidToken();
        }
        MachineToken machineToken = found.get();
        Machine machine = machineToken.getMachine();
        if (machineToken.getRevokedAt() != null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.TOKEN_REVOKED_REFUSED,
                    machineToken.getId(), machine.getId(), machineToken.getRevokedAt());
            throw invalidToken();
        }
        if (!machineToken.getExpiresAt().isAfter(Instant.now(clock))) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.TOKEN_EXPIRED,
                    machineToken.getId(), machine.getId(), machineToken.getExpiresAt());
            throw invalidToken();
        }
        if (machine.getStatus() != MachineStatus.ACTIVE) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, MachineServiceLogConstant.TOKEN_MACHINE_RETIRED,
                    machineToken.getId(), machine.getId());
            throw invalidToken();
        }
        return machineMapper.toPrincipal(machineToken);
    }

    private UnauthorizedException invalidToken() {
        return new UnauthorizedException(ExceptionDomainKayaError.SEC_MACHINE_TOKEN_INVALID);
    }
}
