package com.korur.oba.apps.otak.outreach.constant;

/**
 * Log message constants for outreach domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OutreachExceptionLogConstant {

    private OutreachExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for an outreach that the organization does not have, missing or of another organization.
     */
    public static final String OUTREACH_NOT_FOUND = "Outreach not found: id={}, organizationId={}";

    /**
     * Log message for a change refused because the outreach was sent or is being sent.
     */
    public static final String OUTREACH_NOT_EDITABLE = "Outreach change refused, already sent: id={}, organizationId={}, status={}";

    /**
     * Log message for a create request with no mail source or with both.
     */
    public static final String MAIL_SOURCE_INVALID = "Outreach mail source refused: mailTemplateId={}, mailGiven={}";

    /**
     * Log message for a send refused because the outreach's contact was deleted.
     */
    public static final String OUTREACH_WITHOUT_CONTACT = "Outreach send refused, contact deleted: outreachId={}, organizationId={}";

    /**
     * Log message for a send refused because the contact has no mail address.
     */
    public static final String CONTACT_WITHOUT_ADDRESS = "Outreach send refused, contact has no address: outreachId={}, contactId={}";

    /**
     * Log message for a send refused because the PDFs together exceed the size limit.
     */
    public static final String ATTACHMENTS_TOO_LARGE = "Outreach send refused, attachments too large: outreachId={}, sizeBytes={}, maxSizeBytes={}";

    /**
     * Log message for a create or update request that gives mail fields to an outreach that is not a mail.
     */
    public static final String MAIL_NOT_FOR_TYPE = "Outreach mail fields refused for its channel: type={}, fields={}";

    /**
     * Log message for an action refused because it does not fit the outreach's channel.
     */
    public static final String ACTION_NOT_FOR_TYPE = "Outreach action refused for its channel: id={}, organizationId={}, type={}";

    /**
     * Log message for a completion refused because the contact has no phone number.
     */
    public static final String CONTACT_WITHOUT_PHONE = "Outreach completion refused, contact has no phone: outreachId={}, contactId={}";

    /**
     * Log message for a send or completion that lost the claim: the outreach was sent or recorded as done meanwhile.
     */
    public static final String OUTREACH_CLAIM_LOST = "Outreach claim refused, sent or done meanwhile: id={}, organizationId={}";
}
