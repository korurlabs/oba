package com.korur.oba.shrine.document.dto.request.document;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.constant.document.DocumentConstant;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;

/**
 * Body of POST /documents: the owner, the issuing legal entity and the fields of a new
 * document, optionally made from a template.
 *
 * The organization comes from the checked X-Organization-ID header, never from the body.
 * Checked by the service, because the rules need the database: WRITE on the owner, a legal
 * entity of the organization, and a template of the organization when one is given. With a
 * template, its files are copied to new blobs; without one the document starts without files
 * and receives them through PUT /documents/{documentId}/files.
 *
 * Consumed by {@link com.korur.oba.shrine.document.service.document.DocumentService#create}.
 *
 * @param entityType    the kind of owner
 * @param entityId      the owner
 * @param legalEntityId the legal entity of the organization that issues the document
 * @param title         the title, stored trimmed
 * @param type          the kind of document
 * @param language      the language of the content
 * @param templateId    the template whose files are copied, or null
 */
@Schema(description = "Document create request")
public record DocumentCreateRequest(

        @Schema(description = "Kind of owner", requiredMode = Schema.RequiredMode.REQUIRED, example = "RELATION")
        @NotNull(message = DocumentConstant.ENTITY_TYPE_REQUIRED_MESSAGE)
        DocumentEntityType entityType,

        @Schema(description = "Owner of the document", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        @NotNull(message = DocumentConstant.ENTITY_ID_REQUIRED_MESSAGE)
        UUID entityId,

        @Schema(description = "Legal entity of the organization that issues the document",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "2c4e6a8b-1d3f-4a5b-8c7d-9e0f1a2b3c4d")
        @NotNull(message = DocumentConstant.LEGAL_ENTITY_REQUIRED_MESSAGE)
        UUID legalEntityId,

        @Schema(description = "Title of the document", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Proposal security check")
        @NotBlank(message = DocumentConstant.TITLE_REQUIRED_MESSAGE)
        @Size(max = DocumentConstant.TITLE_MAX_LENGTH, message = DocumentConstant.TITLE_SIZE_MESSAGE)
        String title,

        @Schema(description = "Kind of document", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROPOSAL")
        @NotNull(message = DocumentConstant.TYPE_REQUIRED_MESSAGE)
        DocumentType type,

        @Schema(description = "Language of the content", requiredMode = Schema.RequiredMode.REQUIRED, example = "NL")
        @NotNull(message = DocumentConstant.LANGUAGE_REQUIRED_MESSAGE)
        DocumentLanguage language,

        @Schema(description = "Template whose files are copied to the new document; null to start without files",
                nullable = true, example = "8b3e1f2a-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
        UUID templateId
) {
}
