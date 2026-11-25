package com.korur.oba.shrine.agent.routine.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.agent.routine.entity.AgentRoutine;
import com.korur.oba.shrine.agent.routine.repository.AgentRoutineRepository;
import com.korur.oba.shrine.agent.routine.service.AgentRoutineQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentRoutineQueryServiceImpl implements AgentRoutineQueryService {

    private final AgentRoutineRepository agentRoutineRepository;

    @Override
    public Optional<AgentRoutine> find(UUID organizationId, UUID agentId) {
        return agentRoutineRepository.findByOrganizationIdAndUserId(organizationId, agentId);
    }

    @Override
    public List<AgentRoutine> findAll(UUID organizationId, Collection<UUID> agentIds) {
        if (agentIds.isEmpty()) {
            return List.of();
        }
        return agentRoutineRepository.findAllByOrganizationIdAndUserIdIn(organizationId, agentIds);
    }
}
