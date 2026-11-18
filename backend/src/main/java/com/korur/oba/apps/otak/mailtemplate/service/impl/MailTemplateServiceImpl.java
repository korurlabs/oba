package com.korur.oba.apps.otak.mailtemplate.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.mailtemplate.constant.service.MailTemplateServiceLogConstant.*;

import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateCreateRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateUpdateRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateDetailResponse;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;
import com.korur.oba.apps.otak.mailtemplate.mapper.MailTemplateMapper;
import com.korur.oba.apps.otak.mailtemplate.repository.MailTemplateRepository;
import com.korur.oba.apps.otak.mailtemplate.service.MailTemplateService;
import com.korur.oba.apps.otak.mailtemplate.validator.MailTemplateValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements the writes to an organization's mail templates. The contract and the callers
 * are on {@link MailTemplateService}; this class documents how each write is carried out.
 *
 * The name rule is checked by {@link MailTemplateValidator} before anything is written, so a
 * refused request changes nothing; the unique index repeats it as a last line of defence.
 * The field rules (all present, not blank, within the column sizes) are checked by the
 * request annotations before the controller calls in.
 *
 * @see com.korur.oba.apps.otak.mailtemplate.service.MailTemplateQueryService
 */
@Service
@RequiredArgsConstructor
public class MailTemplateServiceImpl implements MailTemplateService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailTemplateServiceImpl.class);

    private final MailTemplateRepository mailTemplateRepository;
    private final MailTemplateMapper mailTemplateMapper;
    private final MailTemplateValidator mailTemplateValidator;
    private final LoggingService loggingService;

    /**
     * {@inheritDoc}
     *
     * Steps: trim the name and check it is free in the language
     * ({@link MailTemplateValidator#ensureNameFree}, with no mail template excluded), map the
     * request to a new entity ({@link MailTemplateMapper#toEntity}), write the fields through
     * {@link #applyContent}, set the organization, save and map the saved row.
     */
    @Override
    @Transactional
    public MailTemplateDetailResponse create(UUID organizationId, User user, MailTemplateCreateRequest request) {
        // Trimmed before the name check, so " Intro" and "Intro" count as the same name.
        String name = request.name().trim();
        mailTemplateValidator.ensureNameFree(organizationId, name, request.language(), null);

        MailTemplate mailTemplate = mailTemplateMapper.toEntity(request);
        applyContent(mailTemplate, name, request.language(), request.subject(), request.body());
        // The organization comes from the checked header, never from the request body.
        mailTemplate.setOrganizationId(organizationId);
        mailTemplate = mailTemplateRepository.save(mailTemplate);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIL_TEMPLATE_CREATED, mailTemplate.getId(),
                organizationId, user.getId());

        return mailTemplateMapper.toDetailResponse(mailTemplate);
    }

    /**
     * {@inheritDoc}
     *
     * Steps: read the mail template within the organization
     * ({@link MailTemplateValidator#ensureMailTemplateExists}), trim the name and check it is
     * free in the language with this mail template excluded
     * ({@link MailTemplateValidator#ensureNameFree}), write every field through
     * {@link #applyContent}, flush and map the row.
     */
    @Override
    @Transactional
    public MailTemplateDetailResponse update(UUID organizationId, UUID mailTemplateId,
                                             MailTemplateUpdateRequest request) {
        // The organization-scoped lookup: a mail template of another organization is not found.
        MailTemplate mailTemplate = mailTemplateValidator.ensureMailTemplateExists(organizationId, mailTemplateId);

        String name = request.name().trim();
        // The mail template itself is excluded, so it may keep its own name.
        mailTemplateValidator.ensureNameFree(organizationId, name, request.language(), mailTemplateId);

        applyContent(mailTemplate, name, request.language(), request.subject(), request.body());
        // Flushed here so the response carries the new last-change time.
        mailTemplate = mailTemplateRepository.saveAndFlush(mailTemplate);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIL_TEMPLATE_UPDATED, mailTemplateId, organizationId);

        return mailTemplateMapper.toDetailResponse(mailTemplate);
    }

    /**
     * {@inheritDoc}
     *
     * Reads the mail template within the organization
     * ({@link MailTemplateValidator#ensureMailTemplateExists}) and deletes the row. The
     * database clears the link of the mails copied from it; their content stays.
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID mailTemplateId) {
        MailTemplate mailTemplate = mailTemplateValidator.ensureMailTemplateExists(organizationId, mailTemplateId);

        mailTemplateRepository.delete(mailTemplate);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIL_TEMPLATE_DELETED, mailTemplateId, organizationId);
    }

    /**
     * Writes the trimmed name, the language, the subject and the body, shared by
     * {@link #create} and {@link #update} so both store the columns the same way. Every
     * field is written, so an update is a full replace.
     *
     * @param mailTemplate the entity to write to, new or managed
     * @param name         the name, already trimmed
     * @param language     the language of the mail
     * @param subject      the mail subject
     * @param body         the plain-text mail body
     */
    private void applyContent(MailTemplate mailTemplate, String name, DocumentLanguage language, String subject,
                              String body) {
        mailTemplate.setName(name);
        mailTemplate.setLanguage(language);
        mailTemplate.setSubject(subject);
        mailTemplate.setBody(body);
    }
}
