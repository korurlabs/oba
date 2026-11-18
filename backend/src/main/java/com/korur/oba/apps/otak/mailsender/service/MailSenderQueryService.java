package com.korur.oba.apps.otak.mailsender.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;

/**
 * Read side of the mail senders of an organization: the mailboxes of the Microsoft 365
 * tenant that the organization may send its outreach mail from.
 *
 * Organization scoping: the caller's role in the organization is checked once per request
 * by the access interceptor, from the X-Organization-ID header, before these methods run.
 * Every method reads only senders of that organization, so another tenant's senders are
 * never listed.
 *
 * Called by {@link com.korur.oba.apps.otak.mailsender.controller.MailSenderController#listMailSenders}
 * and by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl}, which
 * shows the address of the sender an outreach was sent from. Lookups of one sender go through
 * {@link com.korur.oba.apps.otak.mailsender.validator.MailSenderValidator#ensureMailSenderExists}
 * instead.
 *
 * @see com.korur.oba.apps.otak.mailsender.service.impl.MailSenderQueryServiceImpl
 * @see MailSenderService
 */
public interface MailSenderQueryService {

    /**
     * Lists every mail sender of the organization by mail address. An organization has a
     * handful of senders, so the list is not paged.
     *
     * The order is by mail address, then id, so two calls list the senders the same way.
     * Called by {@link com.korur.oba.apps.otak.mailsender.controller.MailSenderController#listMailSenders}
     * for the sender picker, and by the sender-address helper of
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#list}, which
     * reads all senders once per page instead of one per row.
     *
     * @param organizationId the organization that owns the senders, from the checked request header
     * @return the senders, empty when the organization has none
     * @see com.korur.oba.apps.otak.mailsender.service.impl.MailSenderQueryServiceImpl#list
     */
    List<MailSenderListResponse> list(UUID organizationId);
}
