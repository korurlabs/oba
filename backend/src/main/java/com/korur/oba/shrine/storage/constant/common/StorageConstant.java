package com.korur.oba.shrine.storage.constant.common;

// 1. Framework-level
import org.springframework.http.MediaType;

// 2. Third-party

// 3. Java SDK
import java.util.Set;

// 4. Internal

/**
 * Field-name constants for the storage domain.
 *
 * Centralised so validation messages, exception detail maps, and any
 * other consumer reference a single canonical string per field —
 * eliminates the drift that comes from inlining the same literal in
 * multiple places when a field is renamed.
 */
public final class StorageConstant {

    private StorageConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String FIELD_ATTACHMENT_ID = "attachmentId";
    public static final String FIELD_FILE_NAME = "fileName";
    public static final String FIELD_METADATA_COUNT = "metadataCount";
    public static final String FIELD_FILE_COUNT = "fileCount";
    public static final String FIELD_FILE_HASH = "fileHash";
    public static final String FIELD_ENTITY_TYPE = "entityType";

    /**
     * Content types whose pixel width and height are read on upload. Other
     * image types (SVG, HEIC) keep null dimensions without a warning.
     */
    public static final Set<String> DIMENSIONED_IMAGE_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_GIF_VALUE,
            "image/webp");
}
