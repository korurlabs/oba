package com.korur.oba.shrine.storage.service.attachment;

// 1. Framework-level
import org.springframework.web.multipart.MultipartFile;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentCreateRequest;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Command-side service for the storage attachment domain.
 *
 * Per the project's CQRS-lite split, mutating operations (upload, delete)
 * live here, while read-only operations live in AttachmentQueryService.
 * Implementations are expected to enforce all storage-domain invariants
 * (file-name match, content-hash uniqueness, etc.) before any external
 * side effect — see AttachmentServiceImpl.uploadAttachments for the
 * two-pass design that guarantees this.
 *
 * @see AttachmentQueryService
 */
public interface AttachmentService {

    /**
     * Uploads a batch of attachments, after each distinct parent in the batch
     * has been checked for a WRITE by the user in the request's organization.
     * An entity type without a resource access type is refused.
     *
     * @param user the authenticated user
     * @param organizationId the organization of the request, which every parent must belong to
     * @param requests one metadata entry per file; size and order must match files
     * @param files the multipart files in the same order as requests
     * @return one response per persisted attachment, in the same order
     */
    List<AttachmentResponse> uploadAttachments(User user, UUID organizationId, List<AttachmentCreateRequest> requests,
                                               List<MultipartFile> files);

    /**
     * Stores one attachment from bytes the server already holds, such as a copy
     * of a document template's PDF, after the parent has been checked for a
     * WRITE by the user in the request's organization. The empty-content and
     * duplicate rules of an upload apply. An entity type without a resource
     * access type is refused.
     *
     * Beside {@link #uploadAttachments}, which takes multipart files from a client.
     *
     * @param user the authenticated user
     * @param organizationId the organization of the request, which the parent must belong to
     * @param entityType polymorphic parent entity type
     * @param entityId polymorphic parent entity ID
     * @param content the file name, media type and bytes to store
     * @return the persisted attachment
     */
    AttachmentResponse storeAttachmentContent(User user, UUID organizationId, StorageEntityType entityType,
                                              UUID entityId, AttachmentContent content);

    /**
     * Deletes one attachment and its blob, after the stored parent has been
     * checked for a DELETE by the user in the request's organization. An
     * entity type without a resource access type is refused. The blob stays
     * when another attachment row still holds its URL.
     *
     * @param user the authenticated user
     * @param organizationId the organization of the request, which the parent must belong to
     * @param attachmentId the attachment to delete
     */
    void deleteAttachment(User user, UUID organizationId, UUID attachmentId);

    /**
     * Cascade-deletes every attachment under the given polymorphic parent.
     *
     * Called by parent-domain services (for example InvoiceServiceImpl) when
     * the parent entity is being deleted, so the attachment table and the
     * underlying Azure blobs are cleaned up in lockstep. Without this the
     * /storage/attachments/exists endpoint would keep reporting true for
     * the parent's content hashes after the parent is gone, blocking the
     * user from re-uploading the same file under a new parent.
     *
     * Order: DB rows are deleted first so the /exists predicate flips to
     * false as soon as the surrounding transaction commits. Blob deletes
     * are best-effort and run after the DB delete, only for blobs no remaining
     * row holds; a failure on any single blob is logged via LoggingService at
     * ERROR level and the remaining
     * blobs continue to be attempted. A stuck blob therefore never blocks
     * the cascade or the parent-domain delete.
     *
     * No-op when no attachments are registered under the supplied parent.
     *
     * @param entityType polymorphic parent entity type
     * @param entityId polymorphic parent entity ID
     */
    void deleteAttachmentsByEntity(StorageEntityType entityType, UUID entityId);
}
