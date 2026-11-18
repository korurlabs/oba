package com.korur.oba.connect.notification.dto;

/**
 * A mailbox of the Microsoft 365 tenant as Microsoft Graph describes it.
 *
 * Returned by {@link com.korur.oba.connect.notification.client.MsGraphClient#findMailbox} (inside
 * {@link GraphMailboxLookup.Found}) and by
 * {@link com.korur.oba.connect.notification.client.MsGraphClient#findSharedMailbox}. The
 * outreach send of the Otak app uses the id of a shared mailbox as the sender of
 * {@link com.korur.oba.connect.notification.client.MsGraphClient#sendEmail}.
 *
 * @param id          the Graph object id of the account; a shared mailbox is addressed by it
 *                    when mail is sent from it, because its address may differ from its user
 *                    principal name
 * @param mailAddress the primary mail address of the mailbox
 */
public record GraphMailbox(
        String id,
        String mailAddress
) {
}
