package com.korur.oba.shrine.document.dto.request.document;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.constant.document.DocumentConstant;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Body of POST /documents/{documentId}/attachments: the parent that gets a PDF snapshot of
 * the document. Only MAIL is accepted; the service refuses every other type.
 *
 * Consumed by {@link com.korur.oba.shrine.document.service.document.DocumentService#attachTo}.
 *
 * @param entityType the kind of parent, MAIL
 * @param entityId   the mail
 */
@Schema(description = "Document attach request")
public record DocumentAttachRequest(

        @Schema(description = "Kind of parent; only MAIL", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "MAIL")
        @NotNull(message = DocumentConstant.ENTITY_TYPE_REQUIRED_MESSAGE)
        StorageEntityType entityType,

        @Schema(description = "The mail that gets the snapshot", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "3a4b5c6d-7e8f-4a0b-9c1d-2e3f4a5b6c7d")
        @NotNull(message = DocumentConstant.ENTITY_ID_REQUIRED_MESSAGE)
        UUID entityId
) {
}
