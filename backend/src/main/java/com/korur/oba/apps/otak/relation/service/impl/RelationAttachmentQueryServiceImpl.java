package com.korur.oba.apps.otak.relation.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.relation.constant.service.RelationServiceLogConstant.LISTING_RELATION_ATTACHMENTS;

import com.korur.oba.apps.otak.relation.dto.response.RelationAttachmentResponse;
import com.korur.oba.apps.otak.relation.enums.RelationAttachmentSource;
import com.korur.oba.apps.otak.relation.service.RelationAttachmentQueryService;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

/**
 * Implements {@link RelationAttachmentQueryService} with four reads: the relation within the
 * organization, the relation's files (which checks READ on it), its mails with their outreach
 * titles, and the files of all those mails at once.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelationAttachmentQueryServiceImpl implements RelationAttachmentQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RelationAttachmentQueryServiceImpl.class);

    // Newest first, with the id as tie-breaker so two files of the same instant keep one order.
    private static final Comparator<RelationAttachmentResponse> NEWEST_FIRST =
            Comparator.comparing(RelationAttachmentResponse::createdAt, Comparator.reverseOrder())
                    .thenComparing(RelationAttachmentResponse::id, Comparator.reverseOrder());

    private final AttachmentQueryService attachmentQueryService;
    private final ResourceAccessQueryService resourceAccessQueryService;
    private final RelationQueryService relationQueryService;
    private final LoggingService loggingService;

    /**
     * {@inheritDoc}
     *
     * The mails are those of the relation the caller was just allowed to read, so their READ
     * check is granted for the one batch read instead of being run per mail.
     */
    @Override
    public List<RelationAttachmentResponse> list(User user, UUID organizationId, UUID relationId) {
        // The lookup within the organization comes first, so another organization's relation is a 404 here,
        // where the access check alone would answer an organization mismatch.
        relationQueryService.getEntity(organizationId, relationId);
        List<AttachmentResponse> ownFiles = attachmentQueryService.listAttachmentsByEntity(user, organizationId,
                StorageEntityType.RELATION, relationId);
        Map<UUID, String> outreachTitles = relationQueryService.getMailOutreachTitles(organizationId, relationId);

        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_RELATION_ATTACHMENTS, relationId, organizationId,
                outreachTitles.size());

        List<RelationAttachmentResponse> files = new ArrayList<>();
        for (AttachmentResponse file : ownFiles) {
            files.add(toResponse(file, RelationAttachmentSource.RELATION, null, null));
        }
        if (!outreachTitles.isEmpty()) {
            Map<UUID, List<AttachmentResponse>> mailFiles = resourceAccessQueryService.withReadGrant(user,
                    ResourceAccessType.MAIL, outreachTitles.keySet(),
                    () -> attachmentQueryService.listAttachmentsByEntities(user, organizationId,
                            StorageEntityType.MAIL, outreachTitles.keySet()));
            for (Map.Entry<UUID, List<AttachmentResponse>> mail : mailFiles.entrySet()) {
                for (AttachmentResponse file : mail.getValue()) {
                    files.add(toResponse(file, RelationAttachmentSource.MAIL, mail.getKey(),
                            outreachTitles.get(mail.getKey())));
                }
            }
        }
        files.sort(NEWEST_FIRST);
        return files;
    }

    private static RelationAttachmentResponse toResponse(AttachmentResponse file, RelationAttachmentSource source,
                                                         UUID mailId, String outreachTitle) {
        return new RelationAttachmentResponse(file.id(), file.url(), file.fileName(), file.contentType(),
                file.fileSize(), file.entityType(), file.entityId(), file.createdAt(), file.width(), file.height(),
                file.documentId(), source, mailId, outreachTitle);
    }
}
