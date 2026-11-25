package com.korur.oba.shrine.agent.routine.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.agent.routine.entity.AgentRoutine;

@Repository
public interface AgentRoutineRepository extends JpaRepository<AgentRoutine, UUID> {

    Optional<AgentRoutine> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<AgentRoutine> findAllByOrganizationIdAndUserIdIn(UUID organizationId, Collection<UUID> userIds);
}
