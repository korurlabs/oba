package com.korur.oba.connect.notification.dto;

/**
 * The outcome of a mailbox lookup in the Microsoft 365 tenant. An address that names no
 * account and an account that has no mailbox are kept apart, because the person who entered
 * the address fixes them differently: the first is a wrong address, the second needs a
 * licence or a shared mailbox in the tenant.
 */
public sealed interface GraphMailboxLookup {

    /**
     * The tenant has a mailbox with the address.
     *
     * @param mailbox the mailbox Microsoft Graph found
     */
    record Found(GraphMailbox mailbox) implements GraphMailboxLookup {
    }

    /**
     * No account of the tenant has the address: Microsoft Graph answered not found.
     */
    record AccountNotFound() implements GraphMailboxLookup {
    }

    /**
     * An account of the tenant has the address, but it has no mailbox, so Microsoft Graph
     * returns no mail address for it.
     */
    record AccountWithoutMailbox() implements GraphMailboxLookup {
    }
}
