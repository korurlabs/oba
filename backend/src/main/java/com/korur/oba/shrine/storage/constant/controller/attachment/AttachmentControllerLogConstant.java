package com.korur.oba.shrine.storage.constant.controller.attachment;

/**
 * Log-message templates emitted from AttachmentController.
 *
 * Each entry is an SLF4J-style format string with positional {} placeholders
 * matching the arguments LoggingService.observe is called with at the
 * relevant call site. Kept in a constant class so message wording can be
 * changed in one place without editing controller code.
 */
public final class AttachmentControllerLogConstant {

    private AttachmentControllerLogConstant() {
    }

    public static final String ATTACHMENT_UPLOAD_REQUEST = "Attachment upload request received";
    public static final String ATTACHMENT_UPLOAD_REQUEST_COMPLETED = "Attachment upload request completed";
    public static final String ATTACHMENT_LIST_REQUEST = "Attachment list request received";
    public static final String ATTACHMENT_LIST_REQUEST_COMPLETED = "Attachment list request completed";
    public static final String ATTACHMENT_DELETE_REQUEST = "Attachment delete request received for {}";
    public static final String ATTACHMENT_DELETE_REQUEST_COMPLETED = "Attachment delete request completed for {}";
    public static final String ATTACHMENT_EXISTS_CHECK_REQUEST = "Attachment exists-check request received for {}";
    public static final String ATTACHMENT_EXISTS_CHECK_REQUEST_COMPLETED = "Attachment exists-check request completed; exists={}";
}
