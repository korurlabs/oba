package com.korur.oba.apps.otak.mailtemplate.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mailtemplate.constant.MailTemplateConstant;
import com.korur.oba.apps.otak.mailtemplate.constant.MailTemplateExceptionLogConstant;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;
import com.korur.oba.apps.otak.mailtemplate.repository.MailTemplateRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Business rules of mail templates that a request annotation can not check, because they
 * need the database:
 * - the lookup of one mail template within the caller's organization
 *   ({@link #ensureMailTemplateExists});
 * - the name rule: unique per organization and language, case ignored ({@link #ensureNameFree}).
 *
 * The database enforces the name rule too, with a unique index on otak.mail_templates. The
 * check here runs first so the client gets a specific error code instead of a generic
 * database error. Failures are logged at warn level, because they are client errors.
 *
 * Callers:
 * {@link com.korur.oba.apps.otak.mailtemplate.service.impl.MailTemplateServiceImpl} and
 * {@link com.korur.oba.apps.otak.mailtemplate.service.impl.MailTemplateQueryServiceImpl}.
 *
 * @see MailTemplateRepository
 */
@Component
@RequiredArgsConstructor
public class MailTemplateValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailTemplateValidator.class);

    private final MailTemplateRepository mailTemplateRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the mail template and returns it. A mail template of
     * another organization answers the same not found as a missing one, so its id reveals
     * nothing to another tenant. Every read and write of a mail template, and every copy into
     * the mail of an outreach, passes through this lookup.
     *
     * @param organizationId the organization the caller acts in
     * @param mailTemplateId the mail template identifier
     * @return the mail template, a managed entity of the caller's transaction
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND} (OTK_NOT_010)
     *         if the organization has no mail template with the id, also when the id belongs to another
     *         organization
     */
    public MailTemplate ensureMailTemplateExists(UUID organizationId, UUID mailTemplateId) {
        return mailTemplateRepository.findByIdAndOrganizationId(mailTemplateId, organizationId)
                .orElseThrow(() -> {
                    // Logged with both ids, so a probe for another tenant's mail template shows in the logs.
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            MailTemplateExceptionLogConstant.MAIL_TEMPLATE_NOT_FOUND,
                            mailTemplateId,
                            organizationId);
                    // The response names only the mail template id, never the organization that owns it.
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_MAIL_TEMPLATE_FOUND,
                            Map.of(MailTemplateConstant.FIELD_MAIL_TEMPLATE_ID, mailTemplateId));
                });
    }

    /**
     * Ensures no other mail template of the organization has the name in the language,
     * case ignored, so a mail template can be picked by its name and language without
     * ambiguity. The same name may exist once per language, which is how a mail is
     * translated. Two requests that race past this check are still stopped by the unique
     * index.
     *
     * @param organizationId       the organization that owns the mail templates
     * @param name                 the trimmed name to check
     * @param language             the language the name must be unique in
     * @param exceptMailTemplateId the mail template that may keep its own name on an update, or null on create
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_MAIL_TEMPLATE_NAME_TAKEN} (OTK_STA_014)
     *         when another mail template of the organization has the name in the language
     */
    public void ensureNameFree(UUID organizationId, String name, DocumentLanguage language,
                               UUID exceptMailTemplateId) {
        // A null exceptMailTemplateId means create: no row may hold the name. Otherwise every
        // row but the one being updated is checked.
        boolean taken = exceptMailTemplateId == null
                ? mailTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguage(
                        organizationId, name, language)
                : mailTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                        organizationId, name, language, exceptMailTemplateId);
        if (taken) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    MailTemplateExceptionLogConstant.MAIL_TEMPLATE_NAME_TAKEN, organizationId, name, language);
            // The details name both fields, so the form can mark the name and language together.
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_MAIL_TEMPLATE_NAME_TAKEN,
                    Map.of(MailTemplateConstant.FIELD_NAME, name,
                            MailTemplateConstant.FIELD_LANGUAGE, language));
        }
    }
}
