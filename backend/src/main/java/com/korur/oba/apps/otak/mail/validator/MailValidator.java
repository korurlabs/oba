package com.korur.oba.apps.otak.mail.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mail.constant.MailConstant;
import com.korur.oba.apps.otak.mail.constant.MailExceptionLogConstant;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.repository.MailRepository;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for mail lookups and for the lock of a sent mail's attachments.
 *
 * Each check logs a warning with the ids involved and throws an exception the global
 * exception handler turns into the HTTP status and error code the client sees.
 *
 * Used by {@link com.korur.oba.apps.otak.mail.service.impl.MailServiceImpl},
 * {@link com.korur.oba.apps.otak.mail.service.impl.MailQueryServiceImpl} and by
 * {@link com.korur.oba.shrine.access.validator.ResourceAccessValidator#verifyMailRules} for the lock.
 *
 * @see MailRepository
 */
@Component
@RequiredArgsConstructor
public class MailValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailValidator.class);

    private final MailRepository mailRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the mail and returns it. A mail is never addressed by a
     * client: it is reached through its outreach, whose foreign key guarantees it exists. A
     * miss therefore means a broken link, and answers the generic not found rather than an
     * Otak code a client could act on.
     *
     * @param organizationId the organization the caller acts in
     * @param mailId         the mail identifier
     * @return the mail, a managed entity of the caller's transaction
     * @throws NotFoundException {@link ExceptionNotFoundError#RESOURCE_FOUND} if the organization
     *         has no mail with the id
     */
    public Mail ensureMailExists(UUID organizationId, UUID mailId) {
        return mailRepository.findByIdAndOrganizationId(mailId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, MailExceptionLogConstant.MAIL_NOT_FOUND,
                            mailId, organizationId);
                    return new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND,
                            Map.of(MailConstant.FIELD_MAIL_ID, mailId));
                });
    }

    /**
     * Ensures a mail exists in any organization and returns it, for the access check of an attachment that
     * names its mail by id alone. The caller compares the mail's organization with the request's before it
     * shows anything of the mail. A client addresses the mail here, so a miss answers an Otak code.
     *
     * @param mailId the mail identifier
     * @return the mail, a managed entity of the caller's transaction
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_MAIL_FOUND} if no mail has the id
     */
    public Mail ensureMailExists(UUID mailId) {
        return mailRepository.findById(mailId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, MailExceptionLogConstant.MAIL_NOT_FOUND_BY_ID,
                            mailId);
                    return new NotFoundException(ExceptionDomainOtakError.NOT_MAIL_FOUND,
                            Map.of(MailConstant.FIELD_MAIL_ID, mailId));
                });
    }

    /**
     * Ensures the attachments of a mail may still change: the outreach that sends the mail is not SENT. A
     * sent mail's attachments are the record of what the contact received. A mail without an outreach, or
     * whose outreach is DRAFT or FAILED, passes.
     *
     * The status is read with a row lock on the outreach, so an attachment change and a send's claim of the
     * same outreach never interleave: the change either commits before the claim reads the outreach, or
     * waits and then sees SENT. The caller must therefore run inside a read-write transaction.
     *
     * @param mail the mail whose attachments are about to change
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE} if the outreach
     *         is SENT, also while a running send holds the claim
     */
    public void ensureMailUnsent(Mail mail) {
        OutreachStatus status = mailRepository.findOutreachStatusForUpdate(mail.getId()).orElse(null);
        if (status == OutreachStatus.SENT) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, MailExceptionLogConstant.MAIL_SENT,
                    mail.getId(), mail.getOrganizationId());
            throw new ConflictException(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE,
                    Map.of(MailConstant.FIELD_MAIL_ID, mail.getId()));
        }
    }
}
