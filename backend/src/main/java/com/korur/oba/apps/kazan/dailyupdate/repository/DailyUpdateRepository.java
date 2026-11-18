package com.korur.oba.apps.kazan.dailyupdate.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.dto.projection.DailyUpdateFirstPostProjection;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for DailyUpdate entity database operations.
 *
 * Every read of an update goes through DailyUpdateSpecification, so that
 * visibility to the caller's colleagues is applied to single reads and to the
 * feed alike. Queries exclude soft-deleted updates through the Hibernate
 * soft-delete filter. Ticket lines are saved and removed through
 * DailyUpdate.items, so they have no repository of their own. The posting-time
 * report reads only creation times, never update content.
 */
@Repository
public interface DailyUpdateRepository extends SoftDeleteRepository<DailyUpdate, UUID>,
        JpaSpecificationExecutor<DailyUpdate> {

    /**
     * Finds, per author, the creation time of their first live update within
     * [from, to). Authors without such an update have no row. An edit does not
     * change creation time, so it never moves a post.
     *
     * @param authorIds IDs of the authors, not empty
     * @param from      window start, inclusive
     * @param to        window end, exclusive
     * @return one row per author with an update in the window
     */
    @Query(value = """
            SELECT d.author_id AS authorId,
                   MIN(d.created_at) AS firstPostedAt
            FROM kazan.daily_updates d
            WHERE d.deleted = false
              AND d.author_id IN (:authorIds)
              AND d.created_at >= :from
              AND d.created_at < :to
            GROUP BY d.author_id
            """, nativeQuery = true)
    List<DailyUpdateFirstPostProjection> findFirstPostsByAuthors(@Param("authorIds") Collection<UUID> authorIds,
                                                                 @Param("from") Instant from,
                                                                 @Param("to") Instant to);
}
