package com.korur.oba.shrine.storage.validator.attachment;

// 1. Framework-level
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.storage.constant.common.StorageConstant;
import com.korur.oba.shrine.storage.constant.service.attachment.AttachmentServiceLogConstant;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentCreateRequest;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentMetadata;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.exception.ExceptionDomainStorageError;
import com.korur.oba.shrine.storage.repository.attachment.AttachmentRepository;

/**
 * Pre-condition checks for attachment upload and exists-check flows.
 *
 * Each public method represents one independent invariant the storage
 * domain wants to enforce before any side effect (Azure upload, DB
 * persist, etc.) takes place. The validator owns no transactional state;
 * callers compose it into their own service-layer transactions.
 *
 * The file checks accept AttachmentMetadata rather than a concrete request
 * type so the same body serves both the upload (AttachmentCreateRequest) and
 * the pre-flight check (AttachmentExistsCheckRequest) without overloads. The
 * duplicate check takes the upload request, which names the parent entity.
 */
@Component
@RequiredArgsConstructor
public class AttachmentValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AttachmentValidator.class);

    private final AttachmentRepository attachmentRepository;
    private final LoggingService loggingService;

    /**
     * Returns the access type that guards the attachments of an entity type, and
     * refuses every entity type that has none.
     *
     * The attachment endpoints name the parent only by entity type and id, so the
     * access type's rules are what tie a file to an organization. A type without
     * one would be open to every member of every organization, so its attachments
     * are refused instead: deny by default.
     *
     * @param entityType the parent entity type of the attachments
     * @return the access type whose rules guard the parent
     * @throws ForbiddenException with
     *         {@link ExceptionDomainStorageError#SEC_ENTITY_TYPE_NOT_ATTACHABLE} if no access type guards it
     */
    public ResourceAccessType ensureEntityTypeAccessible(StorageEntityType entityType) {
        return entityType.findResourceAccessType().orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    AttachmentServiceLogConstant.ATTACHMENT_ENTITY_TYPE_NOT_ATTACHABLE, entityType);
            return new ForbiddenException(ExceptionDomainStorageError.SEC_ENTITY_TYPE_NOT_ATTACHABLE,
                    Map.of(StorageConstant.FIELD_ENTITY_TYPE, entityType.name()));
        });
    }

    public void ensureMetadataMatchesFiles(List<AttachmentCreateRequest> requests, List<MultipartFile> files) {
        if (requests.size() != files.size()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    AttachmentServiceLogConstant.ATTACHMENT_METADATA_MISMATCH, requests.size(), files.size());
            throw new ValidationException(ExceptionDomainStorageError.VAL_METADATA_FILE_COUNT_MISMATCH,
                    Map.of(StorageConstant.FIELD_METADATA_COUNT, String.valueOf(requests.size()),
                            StorageConstant.FIELD_FILE_COUNT, String.valueOf(files.size())));
        }
    }

    public void ensureFileNotEmpty(MultipartFile file) {
        if (file.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    AttachmentServiceLogConstant.ATTACHMENT_FILE_EMPTY, file.getOriginalFilename());
            throw new ValidationException(ExceptionDomainStorageError.VAL_FILE_EMPTY,
                    Map.of(StorageConstant.FIELD_FILE_NAME, String.valueOf(file.getOriginalFilename())));
        }
    }

    public void ensureFileNameMatches(MultipartFile file, AttachmentMetadata request) {
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().equals(request.fileName())) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    AttachmentServiceLogConstant.ATTACHMENT_FILE_NAME_MISMATCH,
                    file.getOriginalFilename(), request.fileName());
            throw new ValidationException(ExceptionDomainStorageError.VAL_FILE_NAME_MISSING,
                    Map.of(StorageConstant.FIELD_FILE_NAME, String.valueOf(request.fileName())));
        }
    }

    /**
     * Rejects a content-level duplicate against either the current batch
     * or the database, throwing ValidationException(VAL_FILE_ALREADY_EXISTS)
     * on a hit.
     *
     * The seenHashesInBatch set covers the gap that the database check
     * cannot: two files in the same multipart upload have not been
     * persisted yet when each is validated, so existsBy... returns false
     * for both even though committing them together would create a
     * duplicate. The caller is expected to seed an empty set per request
     * and reuse it across every file in that request, so each successful
     * call extends the in-flight uniqueness check for the batch.
     *
     * Entity types whose content is unique across the type (see
     * {@link StorageEntityType#isUniqueContentAcrossType()}) are checked
     * against every stored attachment of the type in the organization. Entity types whose content
     * is unique per entity (see {@link StorageEntityType#isUniqueContentPerEntity()})
     * are checked against the parent's own attachments: a Kalkan page, a mail
     * or a relation keeps its own copy of a file, but only one. Chat messages
     * have no database check; files within one upload must still differ.
     * Attachments stored without a hash never match.
     *
     * @param organizationId the organization of the request, whose attachments are compared
     * @param file the file being validated; only used for logging context
     * @param request the parent-entity coordinates and original file name
     * @param hash the SHA-256 hex digest of the file content
     * @param seenHashesInBatch mutable set of hashes already validated in
     *        the current batch; this method reads it and, on success,
     *        adds the hash to it
     * @throws ValidationException if the hash collides with another file
     *         in the same batch, with an attachment of the same entity type in
     *         the organization for a type whose content is unique across the type, or with
     *         an attachment of the same parent for a type whose content is
     *         unique per entity
     */
    public void ensureFileNotDuplicate(UUID organizationId, MultipartFile file, AttachmentCreateRequest request,
                                       String hash, Set<String> seenHashesInBatch) {
        // In-batch check first: caller seeds an empty set and reuses it for the whole
        // request, so a hit here means two files in the same multipart upload share
        // identical content. The DB check below cannot detect this — neither row has
        // been inserted yet, so existsBy... would return false for both and both rows
        // would land in the same transaction.
        if (seenHashesInBatch.contains(hash)) {
            throw duplicate(request, hash);
        }

        // DB check second: catches duplicates against attachments that were committed
        // by previous requests under the same parent entity type, or under the same
        // parent for a type whose content is unique per entity.
        ensureStoredContentNotDuplicate(organizationId, request, hash);

        // Only record the hash after both checks pass; recording earlier would let an
        // exception interleave half-state into a caller that catches and retries.
        seenHashesInBatch.add(hash);
    }

    /**
     * Rejects content that the database already holds in the organization under
     * the parent's entity type or under the parent itself, by the rules of
     * {@link #ensureFileNotDuplicate}. Used for a single attachment made on the
     * server from bytes in memory, where no other file of the same request exists.
     *
     * @param organizationId the organization of the request, whose attachments are compared
     * @param request the parent-entity coordinates and the file name
     * @param hash the SHA-256 hex digest of the content
     * @throws ValidationException with {@link ExceptionDomainStorageError#VAL_FILE_ALREADY_EXISTS}
     *         if the content is already stored where the entity type forbids a second copy
     */
    public void ensureStoredContentNotDuplicate(UUID organizationId, AttachmentCreateRequest request, String hash) {
        if (request.entityType().isUniqueContentAcrossType()
                && attachmentRepository.existsByEntityTypeAndFileHashAndOrganizationId(request.entityType(), hash,
                        organizationId)) {
            throw duplicate(request, hash);
        }
        if (request.entityType().isUniqueContentPerEntity()
                && attachedToEntity(organizationId, request, hash)) {
            throw duplicate(request, hash);
        }
    }

    /**
     * Rejects content of a server-made attachment that has no bytes, as the upload
     * path rejects an empty file.
     *
     * @param content the attachment content
     * @throws ValidationException with {@link ExceptionDomainStorageError#VAL_FILE_EMPTY} if it has no bytes
     */
    public void ensureContentNotEmpty(AttachmentContent content) {
        if (content.content() == null || content.content().length == 0) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    AttachmentServiceLogConstant.ATTACHMENT_FILE_EMPTY, content.fileName());
            throw new ValidationException(ExceptionDomainStorageError.VAL_FILE_EMPTY,
                    Map.of(StorageConstant.FIELD_FILE_NAME, String.valueOf(content.fileName())));
        }
    }

    /** Compares against the parent's own attachments, which for a page, a mail or a relation are few. */
    private boolean attachedToEntity(UUID organizationId, AttachmentCreateRequest request, String hash) {
        return attachmentRepository.findByEntityTypeAndEntityIdAndOrganizationId(request.entityType(),
                        request.entityId(), organizationId)
                .stream()
                .anyMatch(attachment -> hash.equals(attachment.getFileHash()));
    }

    private ValidationException duplicate(AttachmentMetadata request, String hash) {
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                AttachmentServiceLogConstant.ATTACHMENT_DUPLICATE_DETECTED,
                request.entityType(), request.fileName(), hash);
        return new ValidationException(ExceptionDomainStorageError.VAL_FILE_ALREADY_EXISTS,
                Map.of(StorageConstant.FIELD_FILE_NAME, String.valueOf(request.fileName()),
                        StorageConstant.FIELD_FILE_HASH, hash));
    }
}
