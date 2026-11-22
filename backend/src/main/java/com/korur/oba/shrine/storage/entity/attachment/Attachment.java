package com.korur.oba.shrine.storage.entity.attachment;

// 1. Framework-level
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Generic file attachment linked to any domain entity via the polymorphic
 * (entityType, entityId) coordinate.
 *
 * The polymorphic shape lets a single table back attachments for tickets,
 * chat messages, Kalkan pages, mails, relations and any future entity without
 * a dedicated join table per parent. The trade-off is that the parent's
 * existence is enforced by application code rather than a foreign key.
 *
 * Every attachment also carries the organization of its parent, so that a
 * read can be limited to the request's organization without joining the
 * parent's table, and a file can never be served across organizations.
 */
@Table(name = "attachments", schema = "shrine")
@Entity
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Attachment extends BaseEntity {

    @Column(name = "url", columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "entity_type", nullable = false)
    private StorageEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * Organization that owns the attachment, set once on create from the
     * request's organization after the parent has been checked to belong to
     * it, and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * SHA-256 content fingerprint, encoded as a 64-character lowercase hex
     * string. The fixed length (length = 64) is enforced at the column level
     * so a malformed digest cannot be persisted.
     *
     * Nullable because rows that pre-date the hash-based duplicate check
     * keep file_hash = NULL — backfilling them would require re-reading
     * every blob from Azure and is intentionally out of scope. Legacy NULLs
     * never satisfy existsBy...AndFileHash, so they cannot produce a
     * false-positive duplicate against an incoming upload.
     */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /**
     * Pixel width of an image attachment, so clients can reserve the image's
     * space before it loads. Set on upload together with height for PNG,
     * JPEG, GIF and WebP files whose header is readable; null for every other
     * file and for rows that pre-date the column. The database check
     * attachments_dimensions_ck requires both to be null or both positive.
     */
    @Column(name = "width")
    private Integer width;

    /** Pixel height of an image attachment; set and cleared together with width. */
    @Column(name = "height")
    private Integer height;
}
