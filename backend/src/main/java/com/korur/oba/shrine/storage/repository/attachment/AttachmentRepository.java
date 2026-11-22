package com.korur.oba.shrine.storage.repository.attachment;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
 * belongs in a dedicated query path with its own filtering surface. Every read
 * that serves a request is also limited to the request's organization.
 */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Finds the attachments of one parent in no particular order, for the cascade
     * delete a parent domain runs when it deletes the parent.
     *
     * Not filtered by organization: the caller deletes a parent it has already
     * loaded, so the parent's organization is settled. Never use it to serve a
     * read for a request; use the organization-scoped methods below.
     *
     * @param entityType polymorphic parent entity type
     * @param entityId   polymorphic parent entity ID
     * @return the parent's attachments, unordered
     */
    List<Attachment> findByEntityTypeAndEntityId(StorageEntityType entityType, UUID entityId);

    /**
     * Finds one attachment of the given organization.
     *
     * An attachment of another organization is not found, so a request cannot
     * learn that an attachment ID exists outside its organization.
     *
     * @param id             the attachment ID
     * @param organizationId the organization of the request
     * @return the attachment, or empty when it does not exist in the organization
     */
    Optional<Attachment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Finds the attachments of one parent in the given organization in no
     * particular order, for the per-parent duplicate guard.
     *
     * @param entityType     polymorphic parent entity type
     * @param entityId       polymorphic parent entity ID
     * @param organizationId the organization of the request
     * @return the parent's attachments, unordered
     */
    List<Attachment> findByEntityTypeAndEntityIdAndOrganizationId(StorageEntityType entityType, UUID entityId,
                                                                  UUID organizationId);

    /**
     * Finds the attachments of one parent in the given organization in upload
     * order: oldest first, then by ID, so rows created in the same instant still
     * list in a stable order.
     *
     * @param entityType     polymorphic parent entity type
     * @param entityId       polymorphic parent entity ID
     * @param organizationId the organization of the request
     * @return the parent's attachments in upload order
     */
    List<Attachment> findByEntityTypeAndEntityIdAndOrganizationIdOrderByCreatedAtAscIdAsc(
            StorageEntityType entityType, UUID entityId, UUID organizationId);

    /**
     * Finds the attachments of several parents of one entity type in the given
     * organization in a single query, oldest first and then by ID, so each
     * parent's files keep their upload order.
     *
     * @param entityType     polymorphic parent entity type
     * @param entityIds      polymorphic parent entity IDs, not empty
     * @param organizationId the organization of the request
     * @return the attachments of every given parent
     */
    List<Attachment> findByEntityTypeAndEntityIdInAndOrganizationIdOrderByCreatedAtAscIdAsc(
            StorageEntityType entityType, Collection<UUID> entityIds, UUID organizationId);

    /**
     * Returns true when an attachment with the given content hash already
     * exists under the given parent entity type in the given organization.
     *
     * Backs both the duplicate guard run during multipart upload (in
     * AttachmentValidator) and the standalone /storage/attachments/exists
     * pre-flight endpoint. Sharing one predicate keeps the two paths in
     * lockstep: anything one accepts the other accepts, and vice versa.
     * Limiting it to the organization keeps the answer from revealing that
     * another organization holds the same file.
     *
     * The composite (entity_type, file_hash) index added by V1_2_54 makes
     * this an index seek rather than a table scan.
     *
     * @param entityType     polymorphic parent entity type
     * @param fileHash       64-character lowercase hex SHA-256 digest
     * @param organizationId the organization of the request
     * @return true if a matching attachment exists, false otherwise
     */
    boolean existsByEntityTypeAndFileHashAndOrganizationId(StorageEntityType entityType, String fileHash,
                                                           UUID organizationId);

    /**
     * Returns true when any attachment row holds the given blob URL.
     *
     * Several rows may share one blob (a document moved onto a relation and onto
     * the mails that carried it), so a delete asks this after its own rows are
     * flushed and removes the blob only when no row is left on it. Not
     * filtered by organization: a blob that any row holds must stay.
     *
     * @param url the unsigned blob URL
     * @return true if an attachment row still references the blob
     */
    boolean existsByUrl(String url);
}
