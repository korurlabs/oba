package com.korur.oba.apps.otak.relation.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.response.RelationAttachmentResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Reads the files of a relation together with those of its mails, for the attachments card of
 * the relation page. A service of its own, not a method of {@link RelationQueryService}: the
 * access service depends on RelationQueryService, and this read depends on the access service.
 */
public interface RelationAttachmentQueryService {

    /**
     * Lists the relation's own attachments and those of its mails, newest first, after READ on
     * the relation.
     *
     * @param user           the caller
     * @param organizationId the organization from the checked request header
     * @param relationId     the relation
     * @return the files, newest first
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_001 when the
     *         organization has no relation with the id
     */
    List<RelationAttachmentResponse> list(User user, UUID organizationId, UUID relationId);
}
