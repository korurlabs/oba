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
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageRecentProjection;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageStateProjection;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTrashProjection;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTreeProjection;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository of {@link KalkanPage}. Derived queries see live pages only, through the soft-delete
 * filter; the native queries carry their own deleted conditions, because the filter does not reach
 * them.
 */
@Repository
public interface KalkanPageRepository extends SoftDeleteRepository<KalkanPage, UUID> {

    /**
     * Finds a live page of the organization.
     *
     * @param id             the page id
     * @param organizationId the organization of the request
     * @return the page; empty when it is missing, deleted or belongs to another organization
     */
    Optional<KalkanPage> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Lists the live root pages of an organization.
     *
     * @param organizationId the organization
     * @return the live roots
     */
    List<KalkanPage> findByOrganizationIdAndParentIsNull(UUID organizationId);

    /**
     * Finds the live root page with a slug.
     *
     * @param organizationId the organization
     * @param slug           the root slug
     * @return the root; empty when no live root has the slug
     */
    Optional<KalkanPage> findByOrganizationIdAndParentIsNullAndSlug(UUID organizationId, String slug);

    /**
     * Finds the live child of a page with a slug.
     *
     * @param parentId the parent page
     * @param slug     the child slug
     * @return the child; empty when no live child has the slug
     */
    Optional<KalkanPage> findByParent_IdAndSlug(UUID parentId, String slug);

    /**
     * Whether a live root page of the organization other than the given one has the slug.
     *
     * @param organizationId the organization
     * @param slug           the slug
     * @param excludedId     a page to leave out, the one being renamed; null to leave out none
     * @return true when the slug is taken among the roots
     */
    @Query("""
            SELECT COUNT(p) > 0 FROM KalkanPage p
            WHERE p.organizationId = :organizationId AND p.rootId IS NULL AND p.slug = :slug
              AND (:excludedId IS NULL OR p.id <> :excludedId)
            """)
    boolean existsRootSlug(@Param("organizationId") UUID organizationId, @Param("slug") String slug,
                           @Param("excludedId") UUID excludedId);

    /**
     * Finds the most recently deleted root page of the organization with a slug. A deleted root
     * keeps its slug for its restore, so a new root may not take it.
     *
     * @param organizationId the organization
     * @param slug           the root slug
     * @return the deleted root's id; empty when no deleted root has the slug
     */
    @Query(value = """
            SELECT p.id FROM kalkan.pages p
            WHERE p.organization_id = :organizationId AND p.parent_id IS NULL AND p.slug = :slug
              AND p.deleted = TRUE
            ORDER BY p.updated_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findDeletedRootIdBySlug(@Param("organizationId") UUID organizationId,
                                           @Param("slug") String slug);

    /**
     * Whether a live child of the parent other than the given page has the slug.
     *
     * @param parentId   the parent page
     * @param slug       the slug
     * @param excludedId a page to leave out, the one being renamed or moved; null to leave out none
     * @return true when the slug is taken under the parent
     */
    @Query("""
            SELECT COUNT(p) > 0 FROM KalkanPage p
            WHERE p.parent.id = :parentId AND p.slug = :slug
              AND (:excludedId IS NULL OR p.id <> :excludedId)
            """)
    boolean existsChildSlug(@Param("parentId") UUID parentId, @Param("slug") String slug,
                            @Param("excludedId") UUID excludedId);

    /**
     * The highest position among the live children of a parent, for appending a new page.
     *
     * @param parentId the parent page
     * @return the highest position; empty when the parent has no live child
     */
    @Query("SELECT MAX(p.position) FROM KalkanPage p WHERE p.parent.id = :parentId")
    Optional<Integer> findMaxChildPosition(@Param("parentId") UUID parentId);

    /**
     * The highest position among the live roots of an organization, for appending a new root.
     *
     * @param organizationId the organization
     * @return the highest position; empty when the organization has no live root
     */
    @Query("SELECT MAX(p.position) FROM KalkanPage p WHERE p.organizationId = :organizationId AND p.rootId IS NULL")
    Optional<Integer> findMaxRootPosition(@Param("organizationId") UUID organizationId);

    /**
     * Reads the live pages below the given roots, the roots included, with each page's path of
     * slugs, in one recursive query.
     *
     * @param organizationId the organization
     * @param rootIds        the roots, all of that organization; must not be empty
     * @return one row per live page, parents before children, siblings by position and slug
     */
    @Query(value = """
            WITH RECURSIVE tree AS (
                SELECT p.id, p.parent_id, p.root_id, p.slug, p.position, p.compliance,
                       CAST(p.slug AS TEXT) AS path, 1 AS depth
                FROM kalkan.pages p
                WHERE p.id IN (:rootIds) AND p.organization_id = :organizationId
                  AND p.parent_id IS NULL AND p.deleted = FALSE
                UNION ALL
                SELECT c.id, c.parent_id, c.root_id, c.slug, c.position, c.compliance,
                       t.path || '/' || c.slug, t.depth + 1
                FROM kalkan.pages c
                JOIN tree t ON c.parent_id = t.id
                WHERE c.deleted = FALSE
            )
            SELECT id AS id, parent_id AS parentId, root_id AS rootId, slug AS slug, path AS path,
                   position AS position, compliance AS compliance
            FROM tree
            ORDER BY depth, parent_id NULLS FIRST, position, slug
            """, nativeQuery = true)
    List<KalkanPageTreeProjection> findTree(@Param("organizationId") UUID organizationId,
                                            @Param("rootIds") Collection<UUID> rootIds);

    /**
     * The number of levels of a page's live subtree, the page itself counting as one.
     *
     * @param pageId the page
     * @return the height of the subtree
     */
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT p.id, 1 AS depth FROM kalkan.pages p WHERE p.id = :pageId AND p.deleted = FALSE
                UNION ALL
                SELECT c.id, s.depth + 1
                FROM kalkan.pages c
                JOIN subtree s ON c.parent_id = s.id
                WHERE c.deleted = FALSE
            )
            SELECT COALESCE(MAX(depth), 0) FROM subtree
            """, nativeQuery = true)
    int findSubtreeHeight(@Param("pageId") UUID pageId);

    /**
     * The depth the deepest page a restore of this deleted page would bring back: the page's live
     * ancestors plus the height of the subtree {@link #restoreSubtree(UUID, UUID, Instant)} matches,
     * the page itself counting as one. A move after the delete counts only the live subtree, so the
     * restored pages may end up deeper than a move would have allowed.
     *
     * @param pageId the deleted page
     * @return the resulting depth; 0 when the page is not deleted
     */
    @Query(value = """
            WITH RECURSIVE ancestors AS (
                SELECT p.parent_id AS id FROM kalkan.pages p WHERE p.id = :pageId
                UNION ALL
                SELECT a.parent_id
                FROM kalkan.pages a
                JOIN ancestors s ON a.id = s.id
            ),
            deleted_at AS (
                SELECT p.updated_at FROM kalkan.pages p WHERE p.id = :pageId AND p.deleted = TRUE
            ),
            subtree AS (
                SELECT p.id, 1 AS depth FROM kalkan.pages p
                WHERE p.id = :pageId AND p.deleted = TRUE
                UNION ALL
                SELECT c.id, s.depth + 1
                FROM kalkan.pages c
                JOIN subtree s ON c.parent_id = s.id
                WHERE c.deleted = TRUE AND c.updated_at = (SELECT updated_at FROM deleted_at)
            )
            SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM subtree)
                             THEN (SELECT COUNT(id) FROM ancestors) + (SELECT MAX(depth) FROM subtree)
                             ELSE 0 END AS INTEGER)
            """, nativeQuery = true)
    int findRestoredDepth(@Param("pageId") UUID pageId);

    /**
     * Moves every descendant of a page, live or deleted, to a new root. Only root_id changes: the
     * content and the delete record of the descendants stay as they are, so a later restore still
     * matches the pages one delete took.
     *
     * @param pageId the moved page, whose own row the caller updates
     * @param rootId the new root
     * @return the number of descendants updated
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT c.id FROM kalkan.pages c WHERE c.parent_id = :pageId
                UNION ALL
                SELECT c.id
                FROM kalkan.pages c
                JOIN subtree s ON c.parent_id = s.id
            )
            UPDATE kalkan.pages SET root_id = :rootId
            WHERE id IN (SELECT id FROM subtree)
            """, nativeQuery = true)
    int updateDescendantRoot(@Param("pageId") UUID pageId, @Param("rootId") UUID rootId);

    /**
     * Records that a push wrote the page at its current version. A native update, so recording it
     * neither counts as a change of the page nor bumps its version; the persistence context is
     * cleared, so no stale copy of the page is written afterwards.
     *
     * @param pageId the page a push just wrote, already flushed
     * @return the number of pages updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE kalkan.pages SET pushed_version = version
            WHERE id = :pageId AND deleted = FALSE
            """, nativeQuery = true)
    int markPushed(@Param("pageId") UUID pageId);

    /**
     * Soft-deletes a page and its live subtree at one instant and records the deleter. The shared
     * instant is what a restore matches, so it brings back exactly the pages this delete took.
     *
     * @param pageId the page
     * @param userId the deleting user
     * @param now    the delete time
     * @return the number of pages deleted
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT p.id FROM kalkan.pages p WHERE p.id = :pageId AND p.deleted = FALSE
                UNION ALL
                SELECT c.id
                FROM kalkan.pages c
                JOIN subtree s ON c.parent_id = s.id
                WHERE c.deleted = FALSE
            )
            UPDATE kalkan.pages
            SET deleted = TRUE, updated_at = :now, updated_by_id = :userId, version = version + 1
            WHERE id IN (SELECT id FROM subtree)
            """, nativeQuery = true)
    int softDeleteSubtree(@Param("pageId") UUID pageId, @Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Reads the place and delete state of a page of the organization, deleted or not.
     *
     * @param id             the page id
     * @param organizationId the organization of the request
     * @return the state; empty when the organization has no such page at all
     */
    @Query(value = """
            SELECT p.id AS id, p.parent_id AS parentId, p.root_id AS rootId, p.slug AS slug,
                   p.deleted AS deleted, COALESCE(parent.deleted, FALSE) AS parentDeleted
            FROM kalkan.pages p
            LEFT JOIN kalkan.pages parent ON parent.id = p.parent_id
            WHERE p.id = :id AND p.organization_id = :organizationId
            """, nativeQuery = true)
    Optional<KalkanPageStateProjection> findStateIncludingDeleted(@Param("id") UUID id,
                                                                  @Param("organizationId") UUID organizationId);

    /**
     * Restores a deleted page and every deleted descendant that the same delete took, matched by
     * the delete instant the page carries, so pages deleted earlier on their own stay deleted.
     * Records the restoring user.
     *
     * @param pageId the deleted page
     * @param userId the restoring user
     * @param now    the restore time
     * @return the number of pages restored
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
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
            UPDATE kalkan.pages
            SET deleted = FALSE, updated_at = :now, updated_by_id = :userId, version = version + 1
            WHERE id IN (SELECT id FROM subtree)
            """, nativeQuery = true)
    int restoreSubtree(@Param("pageId") UUID pageId, @Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Lists the ids of every root page of an organization, deleted ones included. The grants of a
     * deleted root stay live, so the trash resolves the caller's level on it like on a live one.
     *
     * @param organizationId the organization
     * @return the root ids
     */
    @Query(value = """
            SELECT p.id FROM kalkan.pages p
            WHERE p.organization_id = :organizationId AND p.parent_id IS NULL
            """, nativeQuery = true)
    List<UUID> findRootIdsIncludingDeleted(@Param("organizationId") UUID organizationId);

    /**
     * Lists the top page of every delete under the given roots, newest delete first. A deleted page
     * is the top of its delete when it is a root, its parent is live, or its parent was deleted at
     * another instant, so each delete is listed once, by the page a restore takes. The path walks
     * the ancestors whatever their state, and the descendant count matches the pages
     * {@link #restoreSubtree(UUID, UUID, Instant)} would bring back. Each row carries its access
     * root and whether it is a root, so the caller can apply the restore level.
     *
     * @param organizationId the organization
     * @param rootIds        the roots, live or deleted, all of that organization; must not be empty
     * @return one row per delete
     */
    @Query(value = """
            WITH RECURSIVE tops AS (
                SELECT p.id, p.parent_id, COALESCE(p.root_id, p.id) AS access_root_id, p.updated_at,
                       p.updated_by_id
                FROM kalkan.pages p
                LEFT JOIN kalkan.pages parent ON parent.id = p.parent_id
                WHERE p.organization_id = :organizationId AND p.deleted = TRUE
                  AND COALESCE(p.root_id, p.id) IN (:rootIds)
                  AND (parent.id IS NULL OR parent.deleted = FALSE OR parent.updated_at <> p.updated_at)
            ),
            taken AS (
                SELECT t.id AS top_id, t.id AS page_id, t.updated_at FROM tops t
                UNION ALL
                SELECT k.top_id, c.id, k.updated_at
                FROM kalkan.pages c
                JOIN taken k ON c.parent_id = k.page_id
                WHERE c.deleted = TRUE AND c.updated_at = k.updated_at
            ),
            ancestry AS (
                SELECT t.id AS page_id, t.parent_id, p.slug, 0 AS height
                FROM tops t
                JOIN kalkan.pages p ON p.id = t.id
                UNION ALL
                SELECT a.page_id, p.parent_id, p.slug, a.height + 1
                FROM ancestry a
                JOIN kalkan.pages p ON p.id = a.parent_id
            )
            SELECT t.id AS id, t.access_root_id AS accessRootId, t.parent_id IS NULL AS root,
                   (SELECT STRING_AGG(a.slug, '/' ORDER BY a.height DESC) FROM ancestry a
                    WHERE a.page_id = t.id) AS path,
                   t.updated_at AS deletedAt, t.updated_by_id AS deletedById,
                   (SELECT COUNT(k.page_id) - 1 FROM taken k WHERE k.top_id = t.id) AS descendantCount
            FROM tops t
            ORDER BY t.updated_at DESC, t.id
            """, nativeQuery = true)
    List<KalkanPageTrashProjection> findTrash(@Param("organizationId") UUID organizationId,
                                              @Param("rootIds") Collection<UUID> rootIds);

    /**
     * Lists the most recently changed live pages under the given roots, with each page's path and
     * the slug of its root, in one query.
     *
     * @param organizationId the organization
     * @param rootIds        the live roots, all of that organization; must not be empty
     * @param limit          the most rows to return
     * @return the pages, last change first
     */
    @Query(value = """
            WITH RECURSIVE recent AS (
                SELECT p.id, p.updated_at, p.updated_by_id
                FROM kalkan.pages p
                WHERE p.organization_id = :organizationId AND p.deleted = FALSE
                  AND COALESCE(p.root_id, p.id) IN (:rootIds)
                ORDER BY p.updated_at DESC, p.id
                LIMIT :limit
            ),
            ancestry AS (
                SELECT r.id AS page_id, p.parent_id, p.slug, 0 AS height
                FROM recent r
                JOIN kalkan.pages p ON p.id = r.id
                UNION ALL
                SELECT a.page_id, p.parent_id, p.slug, a.height + 1
                FROM ancestry a
                JOIN kalkan.pages p ON p.id = a.parent_id
            )
            SELECT r.id AS id,
                   (SELECT STRING_AGG(a.slug, '/' ORDER BY a.height DESC) FROM ancestry a
                    WHERE a.page_id = r.id) AS path,
                   (SELECT a.slug FROM ancestry a WHERE a.page_id = r.id AND a.parent_id IS NULL) AS rootSlug,
                   r.updated_at AS updatedAt, r.updated_by_id AS updatedById
            FROM recent r
            ORDER BY r.updated_at DESC, r.id
            """, nativeQuery = true)
    List<KalkanPageRecentProjection> findRecent(@Param("organizationId") UUID organizationId,
                                                @Param("rootIds") Collection<UUID> rootIds,
                                                @Param("limit") int limit);
}
