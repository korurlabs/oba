package com.korur.oba.apps.otak.mailsender.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.mailsender.constant.service.MailSenderServiceLogConstant.*;
import com.korur.oba.apps.otak.mailsender.dto.request.MailSenderCreateRequest;
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;
import com.korur.oba.apps.otak.mailsender.mapper.MailSenderMapper;
import com.korur.oba.apps.otak.mailsender.repository.MailSenderRepository;
import com.korur.oba.apps.otak.mailsender.service.MailSenderService;
import com.korur.oba.apps.otak.mailsender.validator.MailSenderValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements writes to an organization's mail senders.
 *
 * The organization role is checked once per request by the access interceptor. Every
 * sender is read and written within the request's organization, so a sender of another
 * organization is a missing one.
 *
 * The checks live in {@link MailSenderValidator}: the duplicate check against
 * {@link MailSenderRepository}, and the mailbox lookups, which call Microsoft Graph through
 * {@link com.korur.oba.connect.notification.client.MsGraphClient}. A sender is only added
 * when Graph confirms the mailbox, so every listed sender is one an outreach can actually be
 * sent from at the time it was added; a shared mailbox is looked up again at every send.
 *
 * @see MailSenderService
 * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send
 */
@Service
@RequiredArgsConstructor
public class MailSenderServiceImpl implements MailSenderService {

    /**
     * Log prefix of this service, derived from the class name.
     */
    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailSenderServiceImpl.class);

    /**
     * Saves and deletes sender rows.
     */
    private final MailSenderRepository mailSenderRepository;

    /**
     * Maps a saved sender to the response of the create endpoint.
     */
    private final MailSenderMapper mailSenderMapper;

    /**
     * Checks the address against the organization's senders and the mailbox in the tenant,
     * and finds a sender within the organization.
     */
    private final MailSenderValidator mailSenderValidator;

    /**
     * Writes the audit log line of every write.
     */
    private final LoggingService loggingService;

    /**
     * Adds a mail sender of the organization in one transaction.
     *
     * Steps:
     * - the address is trimmed, so the stored address is the one the checks saw;
     * - {@link MailSenderValidator#ensureMailAddressFree} refuses an address another sender
     *   of the organization has, case ignored, before any Graph call;
     * - the admin's choice of {@link com.korur.oba.apps.otak.mailsender.enums.EmailType} picks
     *   the lookup: {@link MailSenderValidator#ensureMailboxInTenant} for PERSONAL, by user
     *   principal name, or {@link MailSenderValidator#ensureSharedMailboxInTenant} for SHARED,
     *   by mail address, because a shared mailbox's address may differ from its user
     *   principal name;
     * - the entity is built here rather than by the mapper, because every field comes from
     *   the checked context (organization, user, trimmed address) and not from the body as is.
     *
     * The case-insensitive unique index on organization and address backs the duplicate
     * check against two concurrent adds of the same address.
     *
     * Called by {@link com.korur.oba.apps.otak.mailsender.controller.MailSenderController#createMailSender}
     * through {@link MailSenderService#create}.
     *
     * @param organizationId the organization that owns the sender, from the checked request header
     * @param user           the authenticated user, stored as addedBy
     * @param request        the mail address and the kind of mailbox
     * @return the saved sender, mapped by {@link MailSenderMapper#toListResponse}
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_MAIL_SENDER_TAKEN} if the address is taken in the organization
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_MAILBOX_NOT_IN_TENANT}, {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ACCOUNT_WITHOUT_MAILBOX}
     *         or {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_SHARED_MAILBOX_NOT_FOUND} if the tenant has no such mailbox
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException
     *         {@link com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION} if Microsoft Graph cannot answer the lookup
     * @see MailSenderService#create
     */
    @Override
    @Transactional
    public MailSenderListResponse create(UUID organizationId, User user, MailSenderCreateRequest request) {
        String mailAddress = request.mailAddress().trim();
        // The duplicate check comes first, so a known address never costs a call to Microsoft Graph.
        mailSenderValidator.ensureMailAddressFree(organizationId, mailAddress);
        // The admin's choice of type decides the lookup: a shared mailbox's address may not be its user principal name.
        switch (request.type()) {
            case PERSONAL -> mailSenderValidator.ensureMailboxInTenant(organizationId, mailAddress);
            case SHARED -> mailSenderValidator.ensureSharedMailboxInTenant(organizationId, mailAddress);
        }

        // Stored as entered and trimmed, so the stored address is the one the address rule checked.
        MailSender mailSender = MailSender.builder()
                .mailAddress(mailAddress)
                .type(request.type())
                .addedBy(user.getId())
                .organizationId(organizationId)
                .build();
        mailSender = mailSenderRepository.save(mailSender);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIL_SENDER_CREATED, mailSender.getId(), organizationId,
                user.getId());

        return mailSenderMapper.toListResponse(mailSender);
    }

    /**
     * Deletes a mail sender of the organization in one transaction.
     *
     * The sender is first found within the organization by
     * {@link MailSenderValidator#ensureMailSenderExists}, so another organization's sender
     * answers not found and is never deleted. Outreaches sent from it keep their record; the
     * database clears their sender reference.
     *
     * Called by {@link com.korur.oba.apps.otak.mailsender.controller.MailSenderController#deleteMailSender}
     * through {@link MailSenderService#delete}.
     *
     * @param organizationId the organization that owns the sender, from the checked request header
     * @param mailSenderId   the mail sender identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_SENDER_FOUND} if the organization has no such sender
     * @see MailSenderService#delete
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID mailSenderId) {
        // Scoped lookup: a sender of another organization fails here as not found.
        MailSender mailSender = mailSenderValidator.ensureMailSenderExists(organizationId, mailSenderId);

        mailSenderRepository.delete(mailSender);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIL_SENDER_DELETED, mailSenderId, organizationId);
    }
}
