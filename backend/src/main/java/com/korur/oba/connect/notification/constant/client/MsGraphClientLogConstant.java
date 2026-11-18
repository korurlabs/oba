package com.korur.oba.connect.notification.constant.client;

/**
 * Log messages and selected user fields of the Microsoft Graph client. No message carries a
 * token, the client secret or a mail address, because an address names a person.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MsGraphClientLogConstant {

    private MsGraphClientLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    /**
     * Graph property of a user's object id, selected with the mail address when a shared
     * mailbox is looked up, because mail is sent from a shared mailbox by its object id.
     */
    public static final String SELECT_ID = "id";
    public static final String SELECT_MAIL = "mail";

    /**
     * Finds a member account by its primary mail address. Guests are left out because they have
     * no mailbox in the tenant. Both conditions are default Graph queries, so the request needs no
     * ConsistencyLevel header. The address goes in with every single quote doubled.
     */
    public static final String FILTER_MEMBER_MAIL_EQUALS = "mail eq '%s' and userType eq 'Member'";
    public static final String ODATA_SINGLE_QUOTE = "'";
    public static final String ODATA_ESCAPED_SINGLE_QUOTE = "''";

    public static final String MAILBOX_FOUND = "Microsoft Graph found the mailbox";
    public static final String MAILBOX_ACCOUNT_NOT_FOUND = "Microsoft Graph has no account with the address";
    public static final String MAILBOX_ACCOUNT_WITHOUT_MAILBOX =
            "Microsoft Graph has an account with the address, but it has no mailbox";
    public static final String SHARED_MAILBOX_FOUND = "Microsoft Graph found a member mailbox with the address";
    public static final String SHARED_MAILBOX_NOT_FOUND = "Microsoft Graph has no member mailbox with the address";
    public static final String MAILBOX_LOOKUP_FAILED = "Microsoft Graph mailbox lookup failed. Status: {}";
    public static final String MAILBOX_LOOKUP_FAILED_WITHOUT_ANSWER =
            "Microsoft Graph mailbox lookup failed without an answer";
}
