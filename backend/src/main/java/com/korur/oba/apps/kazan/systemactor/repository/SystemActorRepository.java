package com.korur.oba.apps.kazan.systemactor.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.systemactor.entity.SystemActor;
import com.korur.oba.apps.kazan.systemactor.enums.SystemActorType;

/**
 * Repository interface for SystemActor entity database operations.
 */
@Repository
public interface SystemActorRepository extends JpaRepository<SystemActor, UUID> {

    /**
     * Finds the system actor of a type; the type is unique.
     *
     * @param type type of the system actor
     * @return the system actor, or empty when none of that type exists
     */
    Optional<SystemActor> findByType(SystemActorType type);
}
