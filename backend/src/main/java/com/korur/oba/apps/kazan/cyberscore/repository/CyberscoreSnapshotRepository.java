package com.korur.oba.apps.kazan.cyberscore.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreSnapshot;

/**
 * Repository interface for {@link CyberscoreSnapshot} entity database operations.
 *
 * Both queries serve the trend chart and use the index on organization_id and
 * taken_at.
 */
@Repository
public interface CyberscoreSnapshotRepository extends JpaRepository<CyberscoreSnapshot, UUID>,
        JpaSpecificationExecutor<CyberscoreSnapshot> {

    /**
     * Finds the snapshots of the organization taken at or after the given
     * moment, oldest first, so a later snapshot of the same day replaces an
     * earlier one.
     *
     * @param organizationId ID of the organization
     * @param from           start of the trend window, inclusive
     * @return the snapshots in the window
     */
    List<CyberscoreSnapshot> findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(
            UUID organizationId, Instant from);

    /**
     * Finds the last snapshot of the organization taken before the given
     * moment, the score the trend window starts with.
     *
     * @param organizationId ID of the organization
     * @param before         start of the trend window, exclusive
     * @return the latest earlier snapshot, or empty when there is none
     */
    Optional<CyberscoreSnapshot> findFirstByOrganizationIdAndTakenAtBeforeOrderByTakenAtDesc(
            UUID organizationId, Instant before);
}
