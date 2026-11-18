package com.korur.oba.apps.otak.mailsender.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailsender.dto.request.MailSenderCreateRequest;
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for an organization's mail senders. The caller's role in the
 * organization is checked before these methods run; every method works within that
 * organization, so a sender of another organization is a missing one. A sender is never
 * changed: a wrong address is deleted and added again.
 *
 * A mail sender is a mailbox of the Microsoft 365 tenant that an outreach of the
 * organization may be sent from, so that mail never goes out from the user who clicks send.
 * Adding one checks with Microsoft Graph that the application can actually send from it.
 * The role check by the access interceptor runs from the X-Organization-ID header; only
 * organization admins and owners reach these methods.
 *
 * Called only by {@link com.korur.oba.apps.otak.mailsender.controller.MailSenderController}.
 *
 * @see com.korur.oba.apps.otak.mailsender.service.impl.MailSenderServiceImpl
 * @see MailSenderQueryService
 * @see com.korur.oba.apps.otak.outreach.service.OutreachService#send
 */
public interface MailSenderService {

    /**
     * Adds a mail sender after checking that the organization does not have the address
     * yet and that the Microsoft 365 tenant has a mailbox of the requested type with it:
     * a personal mailbox by user principal name, a shared one by mail address.
     *
     * The address is trimmed and compared case-insensitively with the organization's other
     * senders, because mail addresses are case-insensitive and two senders for one mailbox
     * would only confuse the picker. The duplicate check runs before the Graph lookup, so a
     * known address costs no call to Microsoft Graph.
     *
     * Called by {@link com.korur.oba.apps.otak.mailsender.controller.MailSenderController#createMailSender}.
     *
     * @param organizationId the organization that owns the sender, from the checked request header
     * @param user           the authenticated user, recorded as the one who added it
     * @param request        the mail address and the kind of mailbox
     * @return the added sender
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_MAIL_SENDER_TAKEN} if another sender of the organization has the
     *         address, case ignored
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_MAILBOX_NOT_IN_TENANT} or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ACCOUNT_WITHOUT_MAILBOX} for a PERSONAL address the tenant has no
     *         account or no mailbox for, or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_SHARED_MAILBOX_NOT_FOUND} for a SHARED address no member account
     *         of the tenant has
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException
     *         {@link com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION} if Microsoft Graph cannot answer the lookup
     * @see com.korur.oba.apps.otak.mailsender.service.impl.MailSenderServiceImpl#create
     */
    MailSenderListResponse create(UUID organizationId, User user, MailSenderCreateRequest request);

    /**
     * Deletes a mail sender.
     *
     * Outreaches already sent from it keep their send record: the database clears their
     * sender reference (ON DELETE SET NULL), so they show no sender address but stay SENT
     * with the address they went to. Drafts are not tied to a sender, because the sender is
     * chosen per send.
     *
     * Called by {@link com.korur.oba.apps.otak.mailsender.controller.MailSenderController#deleteMailSender}.
     *
     * @param organizationId the organization that owns the sender, from the checked request header
     * @param mailSenderId   the mail sender identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_SENDER_FOUND} if the organization has no such sender
     * @see com.korur.oba.apps.otak.mailsender.service.impl.MailSenderServiceImpl#delete
     */
    void delete(UUID organizationId, UUID mailSenderId);
}
