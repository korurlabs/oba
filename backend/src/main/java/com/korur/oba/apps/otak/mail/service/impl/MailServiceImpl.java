package com.korur.oba.apps.otak.mail.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.mail.constant.service.MailServiceLogConstant.*;

import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.mapper.MailMapper;
import com.korur.oba.apps.otak.mail.repository.MailRepository;
import com.korur.oba.apps.otak.mail.service.MailService;
import com.korur.oba.apps.otak.mail.validator.MailValidator;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;
import com.korur.oba.apps.otak.mailtemplate.service.MailTemplateQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

/**
 * Implementation of the mail writes declared by {@link MailService}.
 *
 * Mail templates are read through {@link MailTemplateQueryService#getEntity} within the
 * organization, so a template of another organization answers its own not found. The files a
 * mail carries are shrine attachments with the mail as parent; {@link #delete} removes them
 * through {@link AttachmentService#deleteAttachmentsByEntity}. Each method joins the transaction of the
 * outreach write that calls it, so a failure at any step rolls back the outreach as well.
 *
 * @see MailQueryServiceImpl
 */
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailServiceImpl.class);

    private final MailRepository mailRepository;
    private final MailMapper mailMapper;
    private final MailValidator mailValidator;
    private final MailTemplateQueryService mailTemplateQueryService;
    private final AttachmentService attachmentService;
    private final LoggingService loggingService;

    /**
     * {@inheritDoc}
     *
     * Copies the template through {@link MailMapper#fromTemplate}, which also records the
     * template id, then sets the organization and the relation.
     */
    @Override
    @Transactional
    public Mail createFromTemplate(UUID organizationId, UUID relationId, UUID mailTemplateId) {
        MailTemplate mailTemplate = mailTemplateQueryService.getEntity(organizationId, mailTemplateId);

        Mail mail = mailMapper.fromTemplate(mailTemplate);
        mail.setOrganizationId(organizationId);
        mail.setRelationId(relationId);
        mail = mailRepository.save(mail);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIL_COPIED_FROM_TEMPLATE, mail.getId(), mailTemplateId,
                organizationId, relationId);
        return mail;
    }

    /**
     * {@inheritDoc}
     *
     * Maps the request through {@link MailMapper#toEntity}, then sets the organization and
     * the relation.
     */
    @Override
    @Transactional
    public Mail create(UUID organizationId, UUID relationId, MailRequest request) {
        Mail mail = mailMapper.toEntity(request);
        mail.setOrganizationId(organizationId);
        mail.setRelationId(relationId);
        mail = mailRepository.save(mail);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIL_CREATED, mail.getId(), organizationId, relationId);
        return mail;
    }

    /**
     * {@inheritDoc}
     *
     * Writes the request through {@link MailMapper#updateEntity} and flushes, so the
     * outreach response read right after carries the stored content.
     */
    @Override
    @Transactional
    public void update(UUID organizationId, UUID mailId, MailRequest request) {
        Mail mail = mailValidator.ensureMailExists(organizationId, mailId);
        mailMapper.updateEntity(mail, request);
        mailRepository.saveAndFlush(mail);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIL_UPDATED, mailId, organizationId);
    }

    /**
     * {@inheritDoc}
     *
     * Reads the mail within the organization first, so a foreign id deletes nothing. The mail's
     * attachments go after the row, in the same transaction, through
     * {@link AttachmentService#deleteAttachmentsByEntity}: their rows are removed with the mail and
     * each blob that no other attachment holds is deleted on a best-effort basis.
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID mailId) {
        Mail mail = mailValidator.ensureMailExists(organizationId, mailId);
        mailRepository.delete(mail);
        attachmentService.deleteAttachmentsByEntity(StorageEntityType.MAIL, mailId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIL_DELETED, mailId, organizationId);
    }
}
