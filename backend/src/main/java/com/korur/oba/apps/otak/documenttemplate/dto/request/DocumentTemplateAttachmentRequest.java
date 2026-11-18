package com.korur.oba.apps.otak.documenttemplate.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.constant.DocumentTemplateConstant;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Body of POST /otak/document-templates/{templateId}/attachments: the mail or relation that
 * gets a copy of the template's PDF as a new attachment.
 *
 * Only {@link StorageEntityType#MAIL} and {@link StorageEntityType#RELATION} take a template;
 * {@link com.korur.oba.apps.otak.documenttemplate.validator.DocumentTemplateValidator#ensureAttachableEntityType}
 * refuses every other type, because the enum is shared with every app's attachments.
 *
 * Consumed by
 * {@link com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateService#copyToAttachment}.
 *
 * @param entityType the kind of parent, MAIL or RELATION
 * @param entityId   the id of the mail or the relation
 */
@Schema(description = "Target of a document template copied onto an attachment")
public record DocumentTemplateAttachmentRequest(

        @Schema(description = "Kind of parent that gets the attachment, MAIL or RELATION",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "MAIL")
        @NotNull(message = DocumentTemplateConstant.ENTITY_TYPE_REQUIRED_MESSAGE)
        StorageEntityType entityType,

        @Schema(description = "Id of the mail or the relation", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a")
        @NotNull(message = DocumentTemplateConstant.ENTITY_ID_REQUIRED_MESSAGE)
        UUID entityId
) {
}
