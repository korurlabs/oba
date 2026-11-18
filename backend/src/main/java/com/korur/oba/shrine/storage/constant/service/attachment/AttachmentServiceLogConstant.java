package com.korur.oba.shrine.storage.constant.service.attachment;

/**
 * Log-message templates emitted from AttachmentService /
 * AttachmentQueryService implementations and AttachmentValidator.
 *
 * Each entry is an SLF4J-style format string with positional {} placeholders
 * matching the arguments LoggingService is called with at the relevant
 * call site. Centralised so message wording can change in one place,
 * which also keeps log greps stable across services.
 */
public final class AttachmentServiceLogConstant {

    private AttachmentServiceLogConstant() {
    }

    public static final String ATTACHMENT_UPLOADING_BATCH = "Uploading batch of {} attachments for {} {}";
    public static final String ATTACHMENT_UPLOADED_BATCH = "Uploaded batch of {} attachments";
    public static final String ATTACHMENT_DELETING = "Deleting attachment {}";
    public static final String ATTACHMENT_DELETED = "Deleted attachment {}";
    public static final String ATTACHMENT_DELETING_BY_ENTITY = "Cascade-deleting {} attachments for {} {}";
    public static final String ATTACHMENT_DELETED_BY_ENTITY = "Cascade-deleted {} attachment rows for {} {}";
    public static final String ATTACHMENT_BLOB_DELETE_FAILED = "Best-effort blob delete failed during cascade; row already removed. attachmentId=[{}], url=[{}]";
    public static final String ATTACHMENT_IMAGE_READER_MISSING = "No image reader for file {} ({}); width and height stay null";
    public static final String ATTACHMENT_IMAGE_DIMENSIONS_UNREADABLE = "Image dimensions unreadable for file {} ({}); width and height stay null";
    public static final String ATTACHMENT_LISTING = "Listing attachments for {} {}";
    public static final String ATTACHMENT_LISTING_BY_ENTITIES = "Listing attachments for {} {} entities";
    public static final String ATTACHMENT_METADATA_MISMATCH = "Metadata count {} does not match file count {}";
    public static final String ATTACHMENT_FILE_EMPTY = "File is empty: {}";
    public static final String ATTACHMENT_FILE_NAME_MISMATCH = "File name {} does not match metadata name {}";
    public static final String ATTACHMENT_HASH_COMPUTING = "Computing SHA-256 hash for file {} ({} bytes)";
    public static final String ATTACHMENT_DUPLICATE_DETECTED = "Duplicate attachment detected for {} (file {}, hash {})";
    public static final String ATTACHMENT_EXISTS_CHECKING = "Checking attachment existence for {} (file {})";
    public static final String ATTACHMENT_EXISTS_RESULT = "Attachment exists-check result for {}: {}";
    public static final String ATTACHMENT_CONTENT_HASH_COMPUTING = "Computing SHA-256 hash for attachment content ({} bytes)";
    public static final String ATTACHMENT_STORING_CONTENT = "Storing attachment content {} ({} bytes) for {} {}";
    public static final String ATTACHMENT_STORED_CONTENT = "Stored attachment content {} as attachment {}";
    public static final String ATTACHMENT_CONTENT_UPLOAD_FAILED = "Blob upload failed for attachment {} of {} {}";
    public static final String ATTACHMENT_READING_CONTENTS = "Reading attachment contents for {} {}";
    public static final String ATTACHMENT_READ_CONTENTS = "Read {} attachment contents for {} {}";
    public static final String ATTACHMENT_BLOB_KEPT_SHARED = "Kept blob of attachment {}: another attachment still holds url=[{}]";
    public static final String ATTACHMENT_ENTITY_TYPE_NOT_ATTACHABLE = "Refused attachment access: entity type {} has no resource access type";
}
