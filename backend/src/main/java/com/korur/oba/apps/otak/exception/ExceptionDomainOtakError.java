package com.korur.oba.apps.otak.exception;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Otak domain (OTK).
 *
 * Covers the CRM of an organization: relations, their contacts and the organization's
 * ICPs, events, outreaches, their mails, mail senders, mail templates and document
 * templates. A record of another organization answers the same not-found code as a
 * missing one, so an id reveals nothing to another tenant.
 *
 * A retired code is never given to another error, so a client that still knows it
 * never misreads a new one. Retired: OTK_NOT_003, OTK_NOT_008, OTK_STA_004, OTK_STA_007,
 * OTK_STA_010, OTK_STA_012 and OTK_VAL_006.
 */
public enum ExceptionDomainOtakError implements ExceptionDomainError {

    // ── Not found (OTK_NOT) ──────────────────────────────────────────────────
    NOT_RELATION_FOUND("OTK_NOT_001", "Relation not found.", ExceptionCategory.NOT),
    NOT_CONTACT_FOUND("OTK_NOT_002", "Contact not found.", ExceptionCategory.NOT),
    NOT_ICP_FOUND("OTK_NOT_004", "ICP not found.", ExceptionCategory.NOT),
    NOT_OUTREACH_FOUND("OTK_NOT_005", "Outreach not found.", ExceptionCategory.NOT),
    NOT_MAIL_SENDER_FOUND("OTK_NOT_006", "Mail sender not found.", ExceptionCategory.NOT),
    NOT_DOCUMENT_TEMPLATE_FOUND("OTK_NOT_007", "Document template not found.", ExceptionCategory.NOT),
    NOT_EVENT_FOUND("OTK_NOT_009", "Event not found.", ExceptionCategory.NOT),
    NOT_MAIL_TEMPLATE_FOUND("OTK_NOT_010", "Mail template not found.", ExceptionCategory.NOT),
    NOT_MAIL_FOUND("OTK_NOT_011", "Mail not found.", ExceptionCategory.NOT),

    // ── State (OTK_STA) ──────────────────────────────────────────────────────
    STA_OWNER_NOT_ORGANIZATION_MEMBER("OTK_STA_001", "The owner must be an active member of the organization.", ExceptionCategory.STA),
    STA_RELATION_NOT_LEAD("OTK_STA_002", "Only a lead's stage can be set.", ExceptionCategory.STA),
    STA_RELATION_TYPE_UNCHANGED("OTK_STA_003", "The relation already has this type.", ExceptionCategory.STA),
    STA_ICP_NAME_TAKEN("OTK_STA_005", "The organization already has an ICP with this name.", ExceptionCategory.STA),
    STA_STAGE_NOT_FOR_TYPE("OTK_STA_006", "This stage does not belong to the relation's type.", ExceptionCategory.STA),
    STA_MAIL_SENDER_TAKEN("OTK_STA_008", "The organization already has a sender with this mail address.", ExceptionCategory.STA),
    STA_DOCUMENT_TEMPLATE_NAME_TAKEN("OTK_STA_009", "The organization already has a template with this name in this language.", ExceptionCategory.STA),
    STA_OUTREACH_NOT_EDITABLE("OTK_STA_011", "The outreach was sent or is being sent and can no longer change.", ExceptionCategory.STA),
    STA_EVENT_NAME_TAKEN("OTK_STA_013", "The organization already has an event with this name.", ExceptionCategory.STA),
    STA_MAIL_TEMPLATE_NAME_TAKEN("OTK_STA_014", "The organization already has a mail template with this name in this language.", ExceptionCategory.STA),

    // ── Validation (OTK_VAL) ─────────────────────────────────────────────────
    VAL_ICP_SIZE_RANGE("OTK_VAL_001", "The minimum company size cannot be above the maximum.", ExceptionCategory.VAL),
    VAL_MAILBOX_NOT_IN_TENANT("OTK_VAL_002", "No mailbox with this address in the Microsoft 365 tenant.", ExceptionCategory.VAL),
    VAL_ACCOUNT_WITHOUT_MAILBOX("OTK_VAL_003", "The Microsoft 365 account with this address has no mailbox.", ExceptionCategory.VAL),
    VAL_SHARED_MAILBOX_NOT_FOUND("OTK_VAL_004", "No shared mailbox with this address in the Microsoft 365 tenant.", ExceptionCategory.VAL),
    VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL("OTK_VAL_005", "An email type needs an email address.", ExceptionCategory.VAL),
    VAL_CONTACT_WITHOUT_ADDRESS("OTK_VAL_007", "The contact has no mail address.", ExceptionCategory.VAL),
    VAL_ATTACHMENTS_TOO_LARGE("OTK_VAL_008", "The attachments together are larger than 3 MB.", ExceptionCategory.VAL),
    VAL_FILE_NOT_HTML_OR_PDF("OTK_VAL_009", "The file is not the HTML or PDF file it should be.", ExceptionCategory.VAL),
    VAL_EVENT_DATE_RANGE("OTK_VAL_010", "The end date of an event cannot be before its start date.", ExceptionCategory.VAL),
    VAL_EVENT_PERIOD_WITHOUT_TODAY("OTK_VAL_011", "A period filter needs the viewer's date in today.", ExceptionCategory.VAL),
    VAL_DOCUMENT_NOT_USABLE("OTK_VAL_012", "The document template can not be used here.", ExceptionCategory.VAL),
    VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND("OTK_VAL_013", "A contact can only be main of its own kind.", ExceptionCategory.VAL),
    VAL_ACTION_NOT_FOR_TYPE("OTK_VAL_014", "This action does not fit the outreach's channel.", ExceptionCategory.VAL),
    VAL_CONTACT_WITHOUT_PHONE("OTK_VAL_015", "The contact has no phone number.", ExceptionCategory.VAL);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainOtakError(String code, String message, ExceptionCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ExceptionCategory getCategory() {
        return category;
    }
}
