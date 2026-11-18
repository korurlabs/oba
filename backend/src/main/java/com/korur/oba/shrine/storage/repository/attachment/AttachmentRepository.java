package com.korur.oba.shrine.storage.repository.attachment;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.storage.entity.attachment.Attachment;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Spring Data repository for Attachment, scoped by the polymorphic parent
 * coordinate (entityType, entityId).
 *
 * Reads in this repository are intentionally narrow: the storage domain only
 * needs lookup-by-parent and content-uniqueness predicates. Anything broader
 * belongs in a dedicated query path with its own filtering surface.
 */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Finds the attachments of one parent in no particular order, for callers that
     * only test or delete the set, such as the duplicate guard and the cascade delete.
     *
     * @param entityType polymorphic parent entity type
     * @param entityId   polymorphic parent entity ID
     * @return the parent's attachments, unordered
     */
    List<Attachment> findByEntityTypeAndEntityId(StorageEntityType entityType, UUID entityId);

    /**
     * Finds the attachments of one parent in upload order: oldest first, then by ID,
     * so rows created in the same instant still list in a stable order.
     *
     * @param entityType polymorphic parent entity type
     * @param entityId   polymorphic parent entity ID
     * @return the parent's attachments in upload order
     */
    List<Attachment> findByEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc(StorageEntityType entityType,
                                                                        UUID entityId);

    /**
     * Finds the attachments of several parents of one entity type in a single
     * query, oldest first and then by ID, so each parent's files keep their
     * upload order.
     *
     * @param entityType polymorphic parent entity type
     * @param entityIds  polymorphic parent entity IDs, not empty
     * @return the attachments of every given parent
     */
    List<Attachment> findByEntityTypeAndEntityIdInOrderByCreatedAtAscIdAsc(StorageEntityType entityType,
                                                                          Collection<UUID> entityIds);

    /**
     * Returns true when an attachment with the given content hash already
     * exists under the given parent entity type.
     *
     * Backs both the duplicate guard run during multipart upload (in
     * AttachmentValidator) and the standalone /storage/attachments/exists
     * pre-flight endpoint. Sharing one predicate keeps the two paths in
     * lockstep — anything one accepts the other accepts, and vice versa.
     *
     * The composite (entity_type, file_hash) index added by V1_2_54 makes
     * this an index seek rather than a table scan.
     *
     * @param entityType polymorphic parent entity type
     * @param fileHash 64-character lowercase hex SHA-256 digest
     * @return true if a matching attachment exists, false otherwise
     */
    boolean existsByEntityTypeAndFileHash(StorageEntityType entityType, String fileHash);

    /**
     * Returns true when any attachment row holds the given blob URL.
     *
     * Several rows may share one blob (a document moved onto a relation and onto
     * the mails that carried it), so a delete asks this after its own rows are
     * flushed and removes the blob only when no row is left on it.
     *
     * @param url the unsigned blob URL
     * @return true if an attachment row still references the blob
     */
    boolean existsByUrl(String url);
}
