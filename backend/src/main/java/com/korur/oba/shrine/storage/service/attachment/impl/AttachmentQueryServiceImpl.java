package com.korur.oba.shrine.storage.service.attachment.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.service.AzureBlobStorageQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.constant.service.attachment.AttachmentServiceLogConstant;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentExistsCheckRequest;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentExistsResponse;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.entity.attachment.Attachment;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.helper.attachment.AttachmentHashHelper;
import com.korur.oba.shrine.storage.mapper.attachment.AttachmentMapper;
import com.korur.oba.shrine.storage.repository.attachment.AttachmentRepository;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;
import com.korur.oba.shrine.storage.validator.attachment.AttachmentValidator;

/**
 * Read-only implementation of the attachment query service.
 *
 * Class-level @Transactional(readOnly = true) ensures every method runs
 * in a read-only transaction so any accidental mutation in this layer is
 * caught at flush time instead of silently committing.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentQueryServiceImpl implements AttachmentQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AttachmentQueryServiceImpl.class);

    private final AttachmentRepository attachmentRepository;
    private final AttachmentMapper attachmentMapper;
    private final AttachmentValidator attachmentValidator;
    private final AttachmentHashHelper attachmentHashHelper;
    private final LoggingService loggingService;
    private final ResourceAccessQueryService resourceAccessQueryService;
    private final AzureBlobStorageQueryService azureBlobStorageQueryService;

    @Override
    public List<AttachmentResponse> listAttachmentsByEntity(User user, UUID organizationId, StorageEntityType entityType,
                                                            UUID entityId) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_LISTING, entityType, entityId);

        resourceAccessQueryService.verifyResourceAccess(user, organizationId,
                attachmentValidator.ensureEntityTypeAccessible(entityType), entityId, ResourceAccessAction.READ);

        return attachmentRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc(entityType, entityId)
                .stream()
                .map(attachmentMapper::toResponse)
                .toList();
    }

    @Override
    public Map<UUID, List<AttachmentResponse>> listAttachmentsByEntities(User user, UUID organizationId,
                                                                         StorageEntityType entityType,
                                                                         Collection<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> distinctIds = new LinkedHashSet<>(entityIds);
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_LISTING_BY_ENTITIES, distinctIds.size(), entityType);

        // Every parent passes the access check before any file is read, so a refused
        // parent never leaks the files of the others either.
        ResourceAccessType accessType = attachmentValidator.ensureEntityTypeAccessible(entityType);
        distinctIds.forEach(entityId -> resourceAccessQueryService.verifyResourceAccess(user, organizationId,
                accessType, entityId, ResourceAccessAction.READ));

        Map<UUID, List<AttachmentResponse>> attachmentsByEntityId = new LinkedHashMap<>();
        for (UUID entityId : distinctIds) {
            attachmentsByEntityId.put(entityId, new ArrayList<>());
        }
        List<Attachment> attachments = attachmentRepository
                .findByEntityTypeAndEntityIdInOrderByCreatedAtAscIdAsc(entityType, distinctIds);
        for (Attachment attachment : attachments) {
            attachmentsByEntityId.get(attachment.getEntityId()).add(attachmentMapper.toResponse(attachment));
        }
        return attachmentsByEntityId;
    }

    /**
     * Downloads each attachment's blob by the last segment of its URL, which is
     * the blob name in the private container. Attachments of several parents may
     * share one blob, so each row is read on its own.
     */
    @Override
    public List<AttachmentContent> getEntityAttachmentContents(StorageEntityType entityType, UUID entityId) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_READING_CONTENTS, entityType, entityId);

        List<AttachmentContent> contents = attachmentRepository
                .findByEntityTypeAndEntityIdInOrderByCreatedAtAscIdAsc(entityType, List.of(entityId))
                .stream()
                .map(attachment -> new AttachmentContent(attachment.getFileName(), attachment.getContentType(),
                        azureBlobStorageQueryService.downloadPrivateBlob(blobName(attachment.getUrl()))))
                .toList();

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_READ_CONTENTS, contents.size(), entityType, entityId);
        return contents;
    }

    /**
     * Reports whether an attachment with identical content already exists
     * under the given parent entity, without persisting anything.
     *
     * The shape mirrors the upload path's validation prefix (empty file,
     * file-name match, hash) but stops short of the duplicate-rejecting
     * validator: this endpoint's purpose is to report existence rather
     * than reject it, so it queries the repository predicate directly.
     * Sharing the repository method with the upload-time validator
     * guarantees the two answers stay in lockstep. For an entity type whose
     * content is not unique across the type (see
     * {@link StorageEntityType#isUniqueContentAcrossType()}) the answer is always
     * false: the upload validator accepts that content again, or for a Kalkan
     * page refuses it only on the page that already holds it.
     *
     * An entity type without a resource access type is refused before the file is
     * read, so the hash match cannot serve as an existence oracle for the
     * attachments of a type nobody may reach.
     *
     * @param request parent-entity coordinates and the original file name
     * @param file the candidate upload; only its bytes are read for hashing
     * @return a boolean-only response indicating whether a content match exists
     */
    @Override
    public AttachmentExistsResponse checkAttachmentExists(AttachmentExistsCheckRequest request, MultipartFile file) {
        attachmentValidator.ensureEntityTypeAccessible(request.entityType());
        attachmentValidator.ensureFileNotEmpty(file);
        attachmentValidator.ensureFileNameMatches(file, request);

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_EXISTS_CHECKING,
                request.entityType(), request.fileName());

        // Intentionally bypassing AttachmentValidator.ensureFileNotDuplicate: that method
        // throws on a hit (which is correct for the upload path) but here we want to
        // report the hit instead. Hitting the same repository predicate keeps the answer
        // identical to what the upload-time validator would compute for a type whose
        // content is unique across the type. A chat message never counts files stored
        // before; a Kalkan page refuses only a file already on that page, and this
        // request names no page, so for both nothing "exists" here.
        boolean exists = request.entityType().isUniqueContentAcrossType()
                && attachmentRepository.existsByEntityTypeAndFileHash(request.entityType(),
                        attachmentHashHelper.computeHash(file));

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_EXISTS_RESULT,
                request.entityType(), exists);

        return attachmentMapper.toExistsResponse(exists);
    }

    /** The blob name in the container: the part of the unsigned URL after its last slash. */
    private static String blobName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
