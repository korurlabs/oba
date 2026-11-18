package com.korur.oba.apps.kalkan.permission.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.permission.dto.projection.KalkanRootLevelProjection;
import com.korur.oba.apps.kalkan.permission.entity.KalkanPagePermission;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository of {@link KalkanPagePermission}. Derived queries see live grants only, through the
 * soft-delete filter; the native queries carry their own deleted condition.
 */
@Repository
public interface KalkanPagePermissionRepository extends SoftDeleteRepository<KalkanPagePermission, UUID> {

    /**
     * Lists the live grants of a root page, oldest first.
     *
     * @param pageId the root page
     * @return the live grants; empty when the root has none
     */
    List<KalkanPagePermission> findByPage_IdOrderByCreatedAtAsc(UUID pageId);

    /**
     * Reads the level of a member's live grant on a root page. Native, because the soft-delete
     * filter of the page would hide the grants of a deleted root, and a restore must still see them.
     *
     * @param rootId the root page
     * @param userId the member
     * @return the level; empty when the member holds no live grant there
     */
    @Query(value = """
            SELECT CAST(p.level AS TEXT) FROM kalkan.page_permissions p
            WHERE p.page_id = :rootId AND p.user_id = :userId AND p.deleted = FALSE
            """, nativeQuery = true)
    Optional<KalkanLevel> findLevel(@Param("rootId") UUID rootId, @Param("userId") UUID userId);

    /**
     * Reads the levels of a member's live grants on several root pages in one query. Native for
     * the same reason as {@link #findLevel(UUID, UUID)}.
     *
     * @param rootIds the root pages
     * @param userId  the member
     * @return one row per root on which the member holds a live grant
     */
    @Query(value = """
            SELECT p.page_id AS rootId, CAST(p.level AS TEXT) AS level FROM kalkan.page_permissions p
            WHERE p.page_id IN (:rootIds) AND p.user_id = :userId AND p.deleted = FALSE
            """, nativeQuery = true)
    List<KalkanRootLevelProjection> findLevels(@Param("rootIds") Collection<UUID> rootIds,
                                               @Param("userId") UUID userId);

    /**
     * Soft-deletes live grants and records who revoked them and when. The soft delete of
     * {@code delete()} sets only the deleted column, which would lose the revoker.
     *
     * @param ids    the grants
     * @param userId the revoking user
     * @param now    the revocation time
     * @return the number of grants revoked
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE kalkan.page_permissions
            SET deleted = TRUE, updated_at = :now, updated_by_id = :userId
            WHERE id IN (:ids) AND deleted = FALSE
            """, nativeQuery = true)
    int softDeleteByIds(@Param("ids") Collection<UUID> ids, @Param("userId") UUID userId,
                        @Param("now") Instant now);
}
