package com.korur.oba.apps.otak.mailsender.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mailsender.constant.MailSenderConstant;
import com.korur.oba.apps.otak.mailsender.constant.MailSenderExceptionLogConstant;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;
import com.korur.oba.apps.otak.mailsender.repository.MailSenderRepository;
import com.korur.oba.connect.notification.client.MsGraphClient;
import com.korur.oba.connect.notification.dto.GraphMailbox;
import com.korur.oba.connect.notification.dto.GraphMailboxLookup;
import com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for mail sender lookups, the per-organization address rule and the check that
 * an address is a personal or shared mailbox of the Microsoft 365 tenant.
 */
@Component
@RequiredArgsConstructor
public class MailSenderValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailSenderValidator.class);

    private final MailSenderRepository mailSenderRepository;
    private final MsGraphClient msGraphClient;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the mail sender and returns it. A sender of another
     * organization answers the same not found as a missing one, so its id reveals nothing
     * to another tenant.
     *
     * @param organizationId the organization the caller acts in
     * @param mailSenderId   the mail sender identifier
     * @return the mail sender
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_MAIL_SENDER_FOUND} if the
     *         organization has no sender with the id
     */
    public MailSender ensureMailSenderExists(UUID organizationId, UUID mailSenderId) {
        return mailSenderRepository.findByIdAndOrganizationId(mailSenderId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            MailSenderExceptionLogConstant.MAIL_SENDER_NOT_FOUND,
                            mailSenderId,
                            organizationId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_MAIL_SENDER_FOUND,
                            Map.of(MailSenderConstant.FIELD_MAIL_SENDER_ID, mailSenderId));
                });
    }

    /**
     * Ensures no sender of the organization has the mail address, case ignored, because
     * mail addresses do not differ by case.
     *
     * @param organizationId the organization that owns the senders
     * @param mailAddress    the trimmed mail address to check
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_MAIL_SENDER_TAKEN} when a
     *         sender of the organization has the mail address
     */
    public void ensureMailAddressFree(UUID organizationId, String mailAddress) {
        if (mailSenderRepository.existsByOrganizationIdAndMailAddressIgnoreCase(organizationId, mailAddress)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    MailSenderExceptionLogConstant.MAIL_SENDER_TAKEN, organizationId);
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_MAIL_SENDER_TAKEN,
                    Map.of(MailSenderConstant.FIELD_MAIL_ADDRESS, mailAddress));
        }
    }

    /**
     * Ensures the Microsoft 365 tenant has a mailbox with the mail address and returns it, so
     * the organization can only list addresses the application is able to send from. An
     * address that names no account and an account without a mailbox answer different codes,
     * because the admin fixes them differently.
     *
     * @param organizationId the organization the sender is added to
     * @param mailAddress    the trimmed mail address to look up
     * @return the mailbox Microsoft Graph found
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_MAILBOX_NOT_IN_TENANT}
     *         when no account of the tenant has the address
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_ACCOUNT_WITHOUT_MAILBOX}
     *         when the account with the address has no mailbox
     * @throws BadGatewayException {@link ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION}
     *         when Microsoft Graph cannot answer
     */
    public GraphMailbox ensureMailboxInTenant(UUID organizationId, String mailAddress) {
        GraphMailboxLookup lookup = msGraphClient.findMailbox(mailAddress);
        return switch (lookup) {
            case GraphMailboxLookup.Found found -> found.mailbox();
            case GraphMailboxLookup.AccountNotFound accountNotFound -> {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        MailSenderExceptionLogConstant.MAILBOX_NOT_IN_TENANT, organizationId);
                throw new ValidationException(
                        ExceptionDomainOtakError.VAL_MAILBOX_NOT_IN_TENANT,
                        Map.of(MailSenderConstant.FIELD_MAIL_ADDRESS, mailAddress));
            }
            case GraphMailboxLookup.AccountWithoutMailbox accountWithoutMailbox -> {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        MailSenderExceptionLogConstant.ACCOUNT_WITHOUT_MAILBOX, organizationId);
                throw new ValidationException(
                        ExceptionDomainOtakError.VAL_ACCOUNT_WITHOUT_MAILBOX,
                        Map.of(MailSenderConstant.FIELD_MAIL_ADDRESS, mailAddress));
            }
        };
    }

    /**
     * Ensures a member account of the Microsoft 365 tenant has the mail address and returns
     * its mailbox. A shared mailbox is found by its mail property, because its address may
     * differ from its user principal name. The admin's choice of a shared mailbox is trusted
     * for any member account; a guest account has no mailbox in the tenant and is not a match.
     *
     * @param organizationId the organization the sender is added to
     * @param mailAddress    the trimmed mail address to look up
     * @return the mailbox Microsoft Graph found
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_SHARED_MAILBOX_NOT_FOUND}
     *         when no member account of the tenant has the address
     * @throws BadGatewayException {@link ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION}
     *         when Microsoft Graph cannot answer
     */
    public GraphMailbox ensureSharedMailboxInTenant(UUID organizationId, String mailAddress) {
        return msGraphClient.findSharedMailbox(mailAddress)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            MailSenderExceptionLogConstant.SHARED_MAILBOX_NOT_IN_TENANT, organizationId);
                    return new ValidationException(
                            ExceptionDomainOtakError.VAL_SHARED_MAILBOX_NOT_FOUND,
                            Map.of(MailSenderConstant.FIELD_MAIL_ADDRESS, mailAddress));
                });
    }
}
