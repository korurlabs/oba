package com.korur.oba.apps.otak.mail.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mail.entity.Mail;

/**
 * Reads the mail of a MAIL outreach and the ids of a relation's mails. There is no
 * list: a mail is read through its outreach, or by id for the access check of its attachments.
 *
 * Used by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#get} for
 * the detail response, by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send}
 * for the content that goes out, and by
 * {@link com.korur.oba.shrine.access.service.impl.ResourceAccessQueryServiceImpl} through
 * {@link #getEntityById}.
 *
 * @see MailService
 */
public interface MailQueryService {

    /**
     * Reads one mail of the organization.
     *
     * @param organizationId the organization the caller acts in
     * @param mailId         the mail identifier
     * @return the mail entity; the caller must not change it
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionNotFoundError#RESOURCE_FOUND} if the
     *         organization has no such mail
     */
    Mail getEntity(UUID organizationId, UUID mailId);

    /**
     * Reads one mail by id alone, in any organization, for the access check of an attachment whose parent is
     * a mail. The caller must compare the mail's organization with the request's before it uses the mail.
     *
     * @param mailId the mail identifier
     * @return the mail entity; the caller must not change it
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_FOUND} if no mail has
     *         the id
     */
    Mail getEntityById(UUID mailId);

    /**
     * Returns the ids of the organization's mails of one relation, so the attachments of those mails can be
     * removed with the relation.
     *
     * @param organizationId the organization that owns the mails
     * @param relationId     the relation identifier
     * @return the mail ids, in no particular order; empty when the relation has no mail
     */
    List<UUID> getIdsByRelation(UUID organizationId, UUID relationId);
}
