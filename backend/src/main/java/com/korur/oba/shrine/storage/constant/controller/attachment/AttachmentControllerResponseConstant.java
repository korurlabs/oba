package com.korur.oba.shrine.storage.constant.controller.attachment;

/**
 * User-facing response messages returned by AttachmentController via
 * ResponseUtil.ok / ResponseUtil.created.
 *
 * Centralised so wording changes — including future i18n — touch one file
 * instead of every controller method. The strings are part of the public
 * API contract; treat any change as a breaking change for consumers that
 * compare on message text.
 */
public final class AttachmentControllerResponseConstant {

    private AttachmentControllerResponseConstant() {
    }

    public static final String RESPONSE_ATTACHMENTS_UPLOADED = "Attachments uploaded successfully";
    public static final String RESPONSE_ATTACHMENTS_RETRIEVED = "Attachments retrieved successfully";
    public static final String RESPONSE_ATTACHMENT_DELETED = "Attachment deleted successfully";
    public static final String RESPONSE_ATTACHMENT_EXISTS_CHECKED = "Attachment existence checked successfully";
}
