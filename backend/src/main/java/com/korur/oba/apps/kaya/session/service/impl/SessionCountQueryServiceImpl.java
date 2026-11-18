package com.korur.oba.apps.kaya.session.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.session.dto.response.SessionCountSummaryResponse;
import com.korur.oba.apps.kaya.session.mapper.SessionMapper;
import com.korur.oba.apps.kaya.session.repository.SessionRepository;
import com.korur.oba.apps.kaya.session.service.SessionCountQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionCountQueryServiceImpl implements SessionCountQueryService {

    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;

    @Override
    public Map<UUID, SessionCountSummaryResponse> countByUsers(UUID organizationId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return sessionRepository.countByUserIds(organizationId, userIds).stream()
                .map(sessionMapper::toCountSummaryResponse)
                .collect(Collectors.toMap(SessionCountSummaryResponse::id, Function.identity()));
    }

    @Override
    public Map<UUID, SessionCountSummaryResponse> countByMachines(UUID organizationId, Collection<UUID> machineIds) {
        if (machineIds.isEmpty()) {
            return Map.of();
        }
        return sessionRepository.countByMachineIds(organizationId, machineIds).stream()
                .map(sessionMapper::toCountSummaryResponse)
                .collect(Collectors.toMap(SessionCountSummaryResponse::id, Function.identity()));
    }
}
