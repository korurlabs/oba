package com.korur.oba.apps.kaizen.systemactor.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaizen.systemactor.entity.SystemActor;
import com.korur.oba.apps.kaizen.systemactor.enums.SystemActorType;

/**
 * Read operations for system actors.
 */
public interface SystemActorQueryService {

    /**
     * Finds the system actor of a type.
     *
     * @param type type of the system actor
     * @return the system actor, or empty when none of that type exists
     */
    Optional<SystemActor> findByType(SystemActorType type);
}
