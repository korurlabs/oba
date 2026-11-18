package com.korur.oba.shrine.storage.service.attachment.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

// 4. Internal
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.constant.common.StorageConstant;
import com.korur.oba.shrine.storage.constant.service.attachment.AttachmentServiceLogConstant;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentCreateRequest;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.entity.attachment.Attachment;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.exception.ExceptionDomainStorageError;
import com.korur.oba.shrine.storage.helper.attachment.AttachmentHashHelper;
import com.korur.oba.shrine.storage.mapper.attachment.AttachmentMapper;
import com.korur.oba.shrine.storage.repository.attachment.AttachmentRepository;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;
import com.korur.oba.shrine.storage.validator.attachment.AttachmentValidator;

/**
 * Command-side implementation of the attachment service.
 *
 * The upload path is structured as a two-pass operation precisely because
 * Azure Blob Storage and the local database are independent persistence
 * layers — once a file is in Azure we cannot atomically roll it back if
 * the surrounding transaction fails, so all validation that could reject
 * the batch must complete before any byte leaves for Azure.
 *
 * @see AttachmentQueryService
 */
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AttachmentServiceImpl.class);

    private final AttachmentRepository attachmentRepository;
    private final AttachmentMapper attachmentMapper;
    private final AttachmentValidator attachmentValidator;
    private final AttachmentHashHelper attachmentHashHelper;
    private final AzureBlobStorageService azureBlobStorageService;
    private final LoggingService loggingService;
    private final ResourceAccessQueryService resourceAccessQueryService;

    /**
     * Persists a batch of attachments under a parent entity, after fully
     * validating the entire batch.
     *
     * Implemented as two strictly ordered passes:
     *
     * Pass 1 validates every (metadata, file) pair: empty file, file-name
     * match, content hash, and duplicate (in-batch + DB). Computed hashes
     * are cached so they are not recomputed in Pass 2.
     *
     * Pass 2 runs only if Pass 1 succeeded for ALL files. It uploads each
     * file to Azure Blob Storage and persists the entities in a single
     * saveAll. This ordering is load-bearing: a duplicate or empty file at
     * index N must NOT have caused files 0..N-1 to land in Azure already,
     * because Azure uploads are not part of the JPA transaction and would
     * otherwise leak orphaned blobs on any validation failure.
     *
     * The access check runs before Pass 1, once per distinct parent in the
     * batch, so a refused parent costs neither a hash, a duplicate query nor
     * an Azure upload.
     *
     * @param user the authenticated user
     * @param organizationId the organization of the request, which every parent must belong to
     * @param requests one metadata entry per file; size and order must match files
     * @param files the multipart files in the same order as requests
     * @return one response per persisted attachment, in the same order
     */
    @Override
    @Transactional
    public List<AttachmentResponse> uploadAttachments(User user, UUID organizationId, List<AttachmentCreateRequest> requests,
                                                      List<MultipartFile> files) {
        attachmentValidator.ensureMetadataMatchesFiles(requests, files);

        // A batch may name several parents; each one must be writable, not only the first.
        requests.stream()
                .map(request -> Map.entry(request.entityType(), request.entityId()))
                .distinct()
                .forEach(parent -> verifyWrite(user, organizationId, parent.getKey(), parent.getValue()));

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_UPLOADING_BATCH,
                files.size(), requests.getFirst().entityType(), requests.getFirst().entityId());

        // Pass 1 - validate the entire batch before any side effect. Azure uploads are
        // NOT covered by the surrounding @Transactional (they hit a separate service),
        // so if we mixed validation with uploads we could end up with files 0..N-1 in
        // Azure and a rolled-back DB transaction when file N fails validation. Caching
        // hashes here also avoids paying the SHA-256 cost twice per file.
        List<String> hashes = new ArrayList<>(files.size());
        Set<String> seenHashes = new HashSet<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            AttachmentCreateRequest request = requests.get(i);

            attachmentValidator.ensureFileNotEmpty(file);
            attachmentValidator.ensureFileNameMatches(file, request);

            String hash = attachmentHashHelper.computeHash(file);
            attachmentValidator.ensureFileNotDuplicate(file, request, hash, seenHashes);
            hashes.add(hash);
        }

        // Pass 2 - only reached when Pass 1 succeeded for every file. Upload to Azure
        // and persist in a single saveAll so the database side is one round-trip.
        List<Attachment> attachments = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            attachments.add(buildAttachment(files.get(i), requests.get(i), hashes.get(i)));
        }

        List<Attachment> saved = attachmentRepository.saveAll(attachments);

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_UPLOADED_BATCH, saved.size());

        return saved.stream()
                .map(attachmentMapper::toResponse)
                .toList();
    }

    /**
     * Stores one attachment from bytes the server already holds, under a parent
     * entity, with the same checks as an upload.
     *
     * The order matches uploadAttachments: the WRITE check first, then the empty
     * and duplicate checks, and only then the blob upload, so a refused request
     * writes nothing to Azure.
     *
     * @param user the authenticated user
     * @param organizationId the organization of the request, which the parent must belong to
     * @param entityType polymorphic parent entity type
     * @param entityId polymorphic parent entity ID
     * @param content the file name, media type and bytes to store
     * @return the persisted attachment
     */
    @Override
    @Transactional
    public AttachmentResponse storeAttachmentContent(User user, UUID organizationId, StorageEntityType entityType,
                                                     UUID entityId, AttachmentContent content) {
        verifyWrite(user, organizationId, entityType, entityId);

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_STORING_CONTENT,
                content.fileName(), content.content() == null ? 0 : content.content().length, entityType, entityId);

        attachmentValidator.ensureContentNotEmpty(content);
        AttachmentCreateRequest request = new AttachmentCreateRequest(content.fileName(), entityType, entityId);
        String hash = attachmentHashHelper.computeHash(content.content());
        attachmentValidator.ensureStoredContentNotDuplicate(request, hash);

        Attachment saved = attachmentRepository.save(
                buildAttachment(request, content.contentType(), content.content(), hash));

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_STORED_CONTENT, content.fileName(), saved.getId());

        return attachmentMapper.toResponse(saved);
    }

    /**
     * Deletes one attachment row and, when no other row holds it, its blob.
     *
     * The row is deleted and flushed first, so the shared-blob check sees the
     * database without it. The blob delete then fails loudly: an Azure error
     * rolls the row delete back, and the user can try again.
     */
    @Override
    @Transactional
    public void deleteAttachment(User user, UUID organizationId, UUID attachmentId) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_DELETING, attachmentId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainStorageError.NOT_FILE_FOUND,
                        Map.of(StorageConstant.FIELD_ATTACHMENT_ID, attachmentId.toString())));

        // The parent comes from the stored row, never from the client, so an attachment
        // cannot be deleted by naming a parent the user is allowed to write to.
        verifyDelete(user, organizationId, attachment.getEntityType(), attachment.getEntityId());

        attachmentRepository.delete(attachment);
        attachmentRepository.flush();
        if (blobStillReferenced(attachment)) {
            loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                    AttachmentServiceLogConstant.ATTACHMENT_BLOB_KEPT_SHARED, attachmentId, attachment.getUrl());
        } else {
            azureBlobStorageService.deleteFile(attachment.getUrl());
        }

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_DELETED, attachmentId);
    }

    /**
     * Cascade-deletes attachments belonging to the supplied parent entity.
     *
     * Step 1: load every attachment under (entityType, entityId).
     * Step 2: delete the DB rows so the /exists predicate (existsByEntityTypeAndFileHash)
     *         flips to false once the surrounding transaction commits.
     * Step 3: best-effort delete each Azure blob that no remaining row holds (a
     *         blob may back attachments of several parents); one stuck blob never blocks the
     *         cascade or the parent-domain delete. Each failure is logged at ERROR
     *         level with the full stacktrace via LoggingService (per
     *         .claude/rules/10-no-silent-failures.md) so an orphan blob is
     *         recoverable from operational logs.
     *
     * The DB-first ordering deviates from the per-attachment deleteAttachment(UUID)
     * path -- that path is interactive and prefers to fail loudly on a blob error;
     * this cascade is invoked as a side effect of a higher-level delete (for example
     * invoice delete) where surfacing a 5xx for a transient Azure hiccup would
     * leave the user unable to delete a draft.
     */
    @Override
    @Transactional
    public void deleteAttachmentsByEntity(StorageEntityType entityType, UUID entityId) {
        List<Attachment> attachments = attachmentRepository.findByEntityTypeAndEntityId(entityType, entityId);
        if (attachments.isEmpty()) {
            return;
        }

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_DELETING_BY_ENTITY,
                attachments.size(), entityType, entityId);

        // DB rows first so the exists-check answer is correct as soon as the surrounding
        // transaction commits. deleteAllInBatch issues a single DELETE WHERE id IN (...),
        // matching the saveAll round-trip count on the upload path.
        attachmentRepository.deleteAllInBatch(attachments);

        // Blob deletes are best-effort. A single blob failure must not abort the rest of
        // the cascade or the parent-domain delete -- the row is already gone from the DB
        // so the user-visible fix (exists-check returning false) is already in place; a
        // leftover blob is operational cleanup, not a user-facing error. Logged at ERROR
        // with the full stacktrace so operators can investigate without losing context
        // (per .claude/rules/10-no-silent-failures.md -- never silently swallow).
        // A blob shared with an attachment of another parent stays. deleteAllInBatch has
        // already run its DELETE, so the check below no longer sees these rows.
        Set<String> handledUrls = new HashSet<>();
        for (Attachment attachment : attachments) {
            if (!handledUrls.add(attachment.getUrl())) {
                continue;
            }
            if (blobStillReferenced(attachment)) {
                loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                        AttachmentServiceLogConstant.ATTACHMENT_BLOB_KEPT_SHARED,
                        attachment.getId(), attachment.getUrl());
                continue;
            }
            try {
                azureBlobStorageService.deleteFile(attachment.getUrl());
            } catch (Exception e) {
                loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                        AttachmentServiceLogConstant.ATTACHMENT_BLOB_DELETE_FAILED,
                        e, attachment.getId(), attachment.getUrl());
            }
        }

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_DELETED_BY_ENTITY,
                attachments.size(), entityType, entityId);
    }

    // ---- Private helpers ----

    /** Refuses a write to the parent in the request's organization; a type without access rules is refused. */
    private void verifyWrite(User user, UUID organizationId, StorageEntityType entityType, UUID entityId) {
        resourceAccessQueryService.verifyResourceAccess(user, organizationId,
                attachmentValidator.ensureEntityTypeAccessible(entityType), entityId, ResourceAccessAction.WRITE);
    }

    /** Refuses a delete on the parent in the request's organization; a type without access rules is refused. */
    private void verifyDelete(User user, UUID organizationId, StorageEntityType entityType, UUID entityId) {
        resourceAccessQueryService.verifyResourceAccess(user, organizationId,
                attachmentValidator.ensureEntityTypeAccessible(entityType), entityId, ResourceAccessAction.DELETE);
    }

    /** Whether a row other than the deleted ones still holds the attachment's blob URL. */
    private boolean blobStillReferenced(Attachment attachment) {
        return attachmentRepository.existsByUrl(attachment.getUrl());
    }

    private Attachment buildAttachment(MultipartFile file, AttachmentCreateRequest request, String hash) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    AttachmentServiceLogConstant.ATTACHMENT_UPLOADING_BATCH,
                    e, request.fileName(), request.entityType(), request.entityId());
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_UPLOAD);
        }
        return buildAttachment(request, file.getContentType(), content, hash);
    }

    /**
     * Uploads the content as a new blob and builds the attachment row for it,
     * with its size, hash and, for an image, its dimensions.
     */
    private Attachment buildAttachment(AttachmentCreateRequest request, String contentType, byte[] content,
                                       String hash) {
        try {
            // The blob name is a fresh UUID per upload (with the original extension preserved so
            // Azure returns the correct Content-Type on download). Using the original filename --
            // even after sanitisation -- meant two uploads of "report.pdf" under the same entity
            // would map to the same blob key, so the second upload would overwrite the first or
            // collide. The user-facing original filename is still persisted on the Attachment row
            // (fileName below), so downloads can be named back to it from the DB.
            String blobName = UUID.randomUUID() + getFileExtension(request.fileName());
            String fileUrl = azureBlobStorageService.uploadFile(blobName, content);

            Attachment attachment = Attachment.builder()
                    .url(fileUrl)
                    .fileName(request.fileName())
                    .contentType(contentType)
                    .fileSize((long) content.length)
                    .entityType(request.entityType())
                    .entityId(request.entityId())
                    .fileHash(hash)
                    .build();
            applyImageDimensions(attachment, content);
            return attachment;
        } catch (ApplicationException e) {
            // Pass through any ApplicationException raised by collaborators (NotFoundException,
            // ValidationException, IntegrationErrorException from the Azure service, etc.). The
            // collaborator has already enriched the exception with errorValues -- re-wrapping
            // here would strip that detail and produce an opaque "Failed to upload" with no
            // diagnostic context.
            throw e;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    AttachmentServiceLogConstant.ATTACHMENT_CONTENT_UPLOAD_FAILED,
                    e, request.fileName(), request.entityType(), request.entityId());
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_UPLOAD);
        }
    }

    /**
     * Sets width and height on an image attachment from its header, so clients
     * can reserve the image's space before it loads.
     *
     * Only the header is read (no pixel decode), so a huge or hostile image
     * costs no more memory than its bytes. The dimensions are a layout hint,
     * never a reason to refuse an upload: a missing reader or a corrupt header
     * logs a warning with the cause and leaves both columns null, which the
     * client handles with its fallback box.
     */
    private void applyImageDimensions(Attachment attachment, byte[] content) {
        if (!isDimensionedImage(attachment.getContentType())) {
            return;
        }
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        AttachmentServiceLogConstant.ATTACHMENT_IMAGE_READER_MISSING,
                        attachment.getFileName(), attachment.getContentType());
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // The column check accepts only both null or both positive.
                if (width > 0 && height > 0) {
                    attachment.setWidth(width);
                    attachment.setHeight(height);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            // The trailing exception argument makes the logger print the stack trace.
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    AttachmentServiceLogConstant.ATTACHMENT_IMAGE_DIMENSIONS_UNREADABLE,
                    attachment.getFileName(), attachment.getContentType(), e);
        }
    }

    /** Whether the content type, without parameters and case, is one whose dimensions are read. */
    private boolean isDimensionedImage(String contentType) {
        if (contentType == null) {
            return false;
        }
        int parametersStart = contentType.indexOf(';');
        String mediaType = parametersStart < 0 ? contentType : contentType.substring(0, parametersStart);
        return StorageConstant.DIMENSIONED_IMAGE_CONTENT_TYPES.contains(mediaType.trim().toLowerCase(Locale.ROOT));
    }

     /** Extracts the file extension (e.g. ".pdf") from the original filename. */
    private String getFileExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }
}
