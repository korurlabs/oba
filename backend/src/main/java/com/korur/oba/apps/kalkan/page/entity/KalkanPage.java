package com.korur.oba.apps.kalkan.page.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * A page of an organization's Kalkan docs tree.
 *
 * A page without a parent is a root: access levels are granted on roots only and hold for the
 * whole subtree, so every other page carries the id of its root and access is one lookup. Pages
 * are never erased; a delete soft-deletes the page and its subtree, and createdById and
 * updatedById keep who created the page and who changed, deleted or restored it last.
 *
 * The slug is unique among the live children of one parent, a null parent counting as one value,
 * so the path of slugs names one live page and a deleted page below a root frees its slug. A
 * deleted root keeps its slug against new roots, so its section can always be restored whole.
 */
@Entity
@Table(name = "pages", schema = "kalkan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class KalkanPage extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * Root of the subtree this page belongs to; null on a root page. The database checks that it
     * is set exactly when the parent is.
     */
    @Column(name = "root_id")
    private UUID rootId;

    @Column(name = "slug", length = 100, nullable = false)
    private String slug;

    @Column(name = "position", nullable = false)
    private int position;

    @Builder.Default
    @Column(name = "compliance", nullable = false)
    private boolean compliance = false;

    /**
     * Optimistic locking counter, bumped by every write to the page or its translations, so a
     * client that edits an older version is refused instead of overwriting a newer one.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * The version after the last write by a push; null when no push wrote the page. A push that
     * finds a different version knows the page was changed in Kalkan since.
     */
    @Column(name = "pushed_version")
    private Long pushedVersion;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private UUID createdById;

    @Column(name = "updated_by_id", nullable = false)
    private UUID updatedById;

    // -------------Relations-----------//

    /**
     * Parent page; null for a root page.
     *
     * No cascade: a delete or a restore of a subtree runs as native updates in the service.
     *
     * Fetch: EAGER, required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private KalkanPage parent;

    /**
     * The id of the root whose permissions decide access to this page: the root id, or the page's
     * own id when it is a root.
     *
     * @return the id of the page's root
     */
    public UUID getAccessRootId() {
        return rootId != null ? rootId : getId();
    }
}
