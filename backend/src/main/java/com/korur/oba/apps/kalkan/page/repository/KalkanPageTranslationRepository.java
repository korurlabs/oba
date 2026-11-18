package com.korur.oba.apps.kalkan.page.repository;

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
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTitleProjection;
import com.korur.oba.apps.kalkan.page.entity.KalkanPageTranslation;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository of {@link KalkanPageTranslation}. JPQL and derived queries see live translations
 * only; the native updates carry their own deleted conditions.
 */
@Repository
public interface KalkanPageTranslationRepository extends SoftDeleteRepository<KalkanPageTranslation, UUID> {

    /**
     * Lists the live translations of a page.
     *
     * @param pageId the page
     * @return the live translations, by locale
     */
    List<KalkanPageTranslation> findByPage_IdOrderByLocaleAsc(UUID pageId);

    /**
     * Reads the titles of the live translations of several pages without their bodies.
     *
     * @param pageIds the pages
     * @return one row per live translation
     */
    @Query("""
            SELECT t.page.id AS pageId, t.locale AS locale, t.title AS title
            FROM KalkanPageTranslation t
            WHERE t.page.id IN :pageIds
            """)
    List<KalkanPageTitleProjection> findTitles(@Param("pageIds") Collection<UUID> pageIds);

    /**
     * Soft-deletes the live translations of a page's live subtree. Runs before the pages
     * themselves are deleted, with the same instant, so a restore can match both.
     *
     * @param pageId the page whose subtree is deleted
     * @param userId the deleting user
     * @param now    the delete time
     * @return the number of translations deleted
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT p.id FROM kalkan.pages p WHERE p.id = :pageId AND p.deleted = FALSE
                UNION ALL
                SELECT c.id
                FROM kalkan.pages c
                JOIN subtree s ON c.parent_id = s.id
                WHERE c.deleted = FALSE
            )
            UPDATE kalkan.page_translations
            SET deleted = TRUE, updated_at = :now, updated_by_id = :userId
            WHERE deleted = FALSE AND page_id IN (SELECT id FROM subtree)
            """, nativeQuery = true)
    int softDeleteBySubtree(@Param("pageId") UUID pageId, @Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Restores the translations that the delete of a page took, for the page and every descendant
     * the same delete took. Runs before the pages are restored, while they still carry the delete
     * instant it matches.
     *
     * @param pageId the deleted page
     * @param userId the restoring user
     * @param now    the restore time
     * @return the number of translations restored
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            WITH RECURSIVE deleted_at AS (
                SELECT p.updated_at FROM kalkan.pages p WHERE p.id = :pageId AND p.deleted = TRUE
            ),
            subtree AS (
                SELECT p.id FROM kalkan.pages p
                WHERE p.id = :pageId AND p.deleted = TRUE
                UNION ALL
                SELECT c.id
                FROM kalkan.pages c
                JOIN subtree s ON c.parent_id = s.id
                WHERE c.deleted = TRUE AND c.updated_at = (SELECT updated_at FROM deleted_at)
            )
            UPDATE kalkan.page_translations
            SET deleted = FALSE, updated_at = :now, updated_by_id = :userId
            WHERE deleted = TRUE AND page_id IN (SELECT id FROM subtree)
              AND updated_at = (SELECT updated_at FROM deleted_at)
            """, nativeQuery = true)
    int restoreBySubtree(@Param("pageId") UUID pageId, @Param("userId") UUID userId, @Param("now") Instant now);
}
