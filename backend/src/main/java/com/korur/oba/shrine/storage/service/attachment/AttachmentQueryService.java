package com.korur.oba.shrine.storage.service.attachment;

// 1. Framework-level
import org.springframework.web.multipart.MultipartFile;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentExistsCheckRequest;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentExistsResponse;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Read-only counterpart to AttachmentService, per the project's CQRS-lite split.
 *
 * Methods here MUST NOT mutate state and implementations are expected to
 * run with @Transactional(readOnly = true). Anything that creates,
 * deletes, or modifies an attachment belongs in AttachmentService.
 *
 * @see AttachmentService
 */
public interface AttachmentQueryService {

    /**
     * Lists the attachments of a parent entity, after the parent has been
     * checked for a READ by the user in the request's organization. An entity
     * type without a resource access type is refused.
     *
     * @param user the authenticated user
     * @param organizationId the organization of the request, which the parent must belong to
     * @param entityType polymorphic parent entity type
     * @param entityId polymorphic parent entity ID
     * @return the parent's attachments
     */
    List<AttachmentResponse> listAttachmentsByEntity(User user, UUID organizationId, StorageEntityType entityType,
                                                     UUID entityId);

    /**
     * Lists the attachments of several parents of one entity type with one
     * repository query, after each parent has been checked for a READ by the
     * user in the request's organization. A refusal for any parent refuses the
     * whole call, so the caller passes only parents the user may already see.
     *
     * Used by other domains that render a page of parents with their files
     * without one query per parent.
     *
     * @param user the authenticated user
     * @param organizationId the organization of the request, which every parent must belong to
     * @param entityType polymorphic parent entity type
     * @param entityIds polymorphic parent entity IDs; null or empty returns an empty map
     * @return every requested parent ID, in request order, mapped to its
     *         attachments in upload order; an empty list for a parent without any
     */
    Map<UUID, List<AttachmentResponse>> listAttachmentsByEntities(User user, UUID organizationId,
                                                                  StorageEntityType entityType,
                                                                  Collection<UUID> entityIds);

    /**
     * Reads the files of a parent entity's attachments in the given
     * organization, in upload order, with their file names and media types.
     *
     * No access check runs here: the caller has already checked that the user
     * may read the parent, or acts for the system (sending a mail). Never call
     * it with a parent taken from a client request without that check.
     *
     * @param organizationId the organization the parent belongs to; attachments of any other are not read
     * @param entityType polymorphic parent entity type
     * @param entityId polymorphic parent entity ID
     * @return one entry per attachment, oldest first; empty when the parent has none
     */
    List<AttachmentContent> getEntityAttachmentContents(UUID organizationId, StorageEntityType entityType,
                                                        UUID entityId);

    /**
     * Reports whether an attachment with content identical to the provided
     * file already exists under the given parent entity type in the request's
     * organization.
     *
     * Pre-flight endpoint: the frontend calls this before kicking off a
     * full multipart upload, so the user can be warned about a duplicate
     * before paying the network cost of sending the file a second time.
     *
     * @param organizationId the organization of the request, whose attachments are compared
     * @param request parent-entity coordinates and the original file name
     * @param file the candidate upload, hashed in-place to derive the predicate
     * @return a boolean-only response; carries no metadata about any
     *         existing attachment by design
     */
    AttachmentExistsResponse checkAttachmentExists(UUID organizationId, AttachmentExistsCheckRequest request,
                                                   MultipartFile file);

    /**
     * Lists the PDF snapshots made from one document, newest first. No access check runs
     * here: the caller has checked READ on the document's owner, and every snapshot sits on a
     * mail of that owner, which the same app and role reach.
     *
     * Called by {@link com.korur.oba.shrine.document.service.document.impl.DocumentQueryServiceImpl#get}.
     *
     * @param organizationId the organization of the request
     * @param documentId     the document
     * @return the snapshots, newest first; empty when the document has none
     */
    List<AttachmentResponse> listAttachmentsByDocument(UUID organizationId, UUID documentId);

    /**
     * Counts the PDF snapshots of each given document in one query. No access check runs
     * here, as in {@link #listAttachmentsByDocument}; only a count leaves this method.
     *
     * Called by {@link com.korur.oba.shrine.document.service.document.impl.DocumentQueryServiceImpl}
     * for a page of documents and for one document.
     *
     * @param organizationId the organization of the request
     * @param documentIds    the documents
     * @return the count per document; a document without snapshots is absent
     */
    Map<UUID, Long> countAttachmentsByDocuments(UUID organizationId, Collection<UUID> documentIds);
}
