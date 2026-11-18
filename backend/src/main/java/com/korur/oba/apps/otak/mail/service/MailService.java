package com.korur.oba.apps.otak.mail.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.mail.entity.Mail;

/**
 * Writes the mail of a MAIL outreach: create it from a mail template or by hand, change it and
 * delete it with its attachments. The attachments themselves are written through
 * {@link com.korur.oba.shrine.storage.service.attachment.AttachmentService}.
 *
 * No method checks the outreach status. The only caller is
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl}, which calls in only
 * after {@link com.korur.oba.apps.otak.outreach.validator.OutreachValidator#ensureEditable} under
 * the outreach row lock, so that lock stays the one edit gate of a mail.
 *
 * @see MailQueryService
 */
public interface MailService {

    /**
     * Creates a mail of the relation as a copy of a mail template's subject, body and
     * language, recording the template as its source.
     *
     * @param organizationId the organization the caller acts in
     * @param relationId     the relation of the outreach, already checked in the organization
     * @param mailTemplateId the mail template to copy
     * @return the saved mail
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         (OTK_NOT_010) if the organization has no such mail template
     */
    Mail createFromTemplate(UUID organizationId, UUID relationId, UUID mailTemplateId);

    /**
     * Creates a mail of the relation written by hand, without a template link.
     *
     * @param organizationId the organization the caller acts in
     * @param relationId     the relation of the outreach, already checked in the organization
     * @param request        the subject, body and language
     * @return the saved mail
     */
    Mail create(UUID organizationId, UUID relationId, MailRequest request);

    /**
     * Replaces the subject, body and language of a mail.
     *
     * @param organizationId the organization the caller acts in
     * @param mailId         the mail identifier
     * @param request        the new subject, body and language
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionNotFoundError#RESOURCE_FOUND} if the
     *         organization has no such mail
     */
    void update(UUID organizationId, UUID mailId, MailRequest request);

    /**
     * Deletes a mail and the attachments that have the mail as parent. The caller deletes and
     * flushes the outreach row first, because its foreign key to the mail has no cascade.
     *
     * @param organizationId the organization the caller acts in
     * @param mailId         the mail identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionNotFoundError#RESOURCE_FOUND} if the
     *         organization has no such mail
     */
    void delete(UUID organizationId, UUID mailId);
}
