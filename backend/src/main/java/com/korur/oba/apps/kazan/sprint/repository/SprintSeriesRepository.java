package com.korur.oba.apps.kazan.sprint.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;

/**
 * Repository interface for SprintSeries entity database operations.
 *
 * Every writer locks the series row before it writes any sprint of the series:
 * a user change waits for the lock, the job skips a locked series. A rename
 * and a rollover therefore never interleave.
 */
@Repository
public interface SprintSeriesRepository extends JpaRepository<SprintSeries, UUID>,
        JpaSpecificationExecutor<SprintSeries> {

    /**
     * Locks a series for a user change, waiting while the job holds the row, so
     * the user's save starts from the job's committed counter, schedule and run
     * state instead of overwriting them with values read before.
     *
     * @param id ID of the series
     * @return the locked series, or empty when it does not exist
     */
    @Query(value = """
            SELECT * FROM kazan.sprint_series
            WHERE id = :id
            FOR UPDATE
            """, nativeQuery = true)
    Optional<SprintSeries> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Locks a series for one job run, or returns empty when another transaction
     * holds the lock, so two pods never run the same series.
     *
     * @param id ID of the series
     * @return the locked series, or empty when it is locked elsewhere or gone
     */
    @Query(value = """
            SELECT * FROM kazan.sprint_series
            WHERE id = :id
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<SprintSeries> findByIdForUpdateSkipLocked(@Param("id") UUID id);

    /**
     * Lists the enabled series the job has work for at the given time: a live
     * ACTIVE sprint whose end date has passed, a next creation time that has
     * passed, or no live ACTIVE sprint and a live PLANNED sprint whose start
     * date has passed. The soft-delete filter of Sprint drops deleted sprints.
     *
     * @param now the time of the run
     * @return IDs of the due series
     */
    default List<UUID> findDueIds(Instant now) {
        return findDueIdsByStatus(now, SprintStatus.ACTIVE, SprintStatus.PLANNED);
    }

    /**
     * Runs the due rule of findDueIds with the two sprint statuses bound as
     * parameters. The status column is a PostgreSQL enum: an enum literal in
     * JPQL is cast to a type named after the Java enum, which does not exist
     * in the database, while a bound parameter takes the column's own type.
     *
     * @param now           the time of the run
     * @param activeStatus  the status of a running sprint, always ACTIVE
     * @param plannedStatus the status of a sprint not yet started, always PLANNED
     * @return IDs of the due series
     */
    @Query("""
            SELECT ss.id FROM SprintSeries ss
            WHERE ss.enabled = true
              AND (ss.nextCreationAt <= :now
                OR EXISTS (SELECT 1 FROM Sprint s
                           WHERE s.series = ss
                             AND s.status = :activeStatus
                             AND s.endDate <= :now)
                OR (NOT EXISTS (SELECT 1 FROM Sprint s
                                WHERE s.series = ss
                                  AND s.status = :activeStatus)
                    AND EXISTS (SELECT 1 FROM Sprint s
                                WHERE s.series = ss
                                  AND s.status = :plannedStatus
                                  AND s.startDate <= :now)))
            """)
    List<UUID> findDueIdsByStatus(@Param("now") Instant now,
                                  @Param("activeStatus") SprintStatus activeStatus,
                                  @Param("plannedStatus") SprintStatus plannedStatus);
}
