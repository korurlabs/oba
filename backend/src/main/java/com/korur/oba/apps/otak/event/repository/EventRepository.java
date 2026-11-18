package com.korur.oba.apps.otak.event.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.entity.Event;

/**
 * Repository interface for {@link Event} entity database operations.
 */
@Repository
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    /**
     * Reads one event within an organization, so an event of another organization is not found.
     *
     * @param id             the event identifier
     * @param organizationId the organization that owns the event
     * @return the event, or empty when the organization has no event with the id
     */
    Optional<Event> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Tells whether the organization already has an event with the name, case ignored.
     * Used on create, where no event is excluded.
     *
     * @param organizationId the organization that owns the events
     * @param name           the name to look for
     * @return true when an event of the organization has the name
     */
    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    /**
     * Tells whether another event of the organization has the name, case ignored. Used on
     * update, where the event being changed may keep its own name.
     *
     * @param organizationId the organization that owns the events
     * @param name           the name to look for
     * @param id             the event left out of the check
     * @return true when an event of the organization other than the given one has the name
     */
    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(UUID organizationId, String name, UUID id);
}
