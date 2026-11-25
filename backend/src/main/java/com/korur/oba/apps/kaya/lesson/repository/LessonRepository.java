package com.korur.oba.apps.kaya.lesson.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryProjection;
import com.korur.oba.apps.kaya.lesson.entity.Lesson;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID>, JpaSpecificationExecutor<Lesson> {

    Optional<Lesson> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** The repositories that have lessons, for the list's filter options. */
    @Query("SELECT DISTINCT l.repositoryId FROM Lesson l WHERE l.organizationId = :organizationId AND l.repositoryId IS NOT NULL")
    List<UUID> findDistinctRepositoryIds(@Param("organizationId") UUID organizationId);

    /** The authors of lessons, for the list's filter options. */
    @Query("SELECT DISTINCT l.userId FROM Lesson l WHERE l.organizationId = :organizationId")
    List<UUID> findDistinctUserIds(@Param("organizationId") UUID organizationId);

    /**
     * Every lesson a checkout reads, newest first with the id as tie-breaker and without its body,
     * so that the text of each lesson never travels at session start. Not paged here: the ETag
     * covers all of them, and one read keeps the ETag and the delivered page the same state.
     *
     * @param organizationId the organization of the machine token
     * @param repositoryId   the repository of the checkout, or null when the remote is unknown
     * @return the lessons of the repository and the organization-wide ones
     */
    @Query("""
            SELECT l.id AS id, l.title AS title, l.repositoryId AS repositoryId, l.updatedAt AS updatedAt
            FROM Lesson l
            WHERE l.organizationId = :organizationId
              AND (l.repositoryId = :repositoryId OR l.repositoryId IS NULL)
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<LessonDeliveryProjection> findDeliveryLessons(@Param("organizationId") UUID organizationId,
            @Param("repositoryId") UUID repositoryId);
}
