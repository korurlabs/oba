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

/**
 * Body of PUT /documents/{documentId}: a full replace of the title, type, language and legal
 * entity. The owner and the files are not part of it; the files change through
 * PUT /documents/{documentId}/files.
 *
 * Consumed by {@link com.korur.oba.shrine.document.service.document.DocumentService#update}.
 *
 * @param title         the title, stored trimmed
 * @param type          the kind of document
 * @param language      the language of the content
 * @param legalEntityId the legal entity of the organization that issues the document
 */
@Schema(description = "Document update request; replaces every field")
public record DocumentUpdateRequest(

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

        @Schema(description = "Legal entity of the organization that issues the document",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "2c4e6a8b-1d3f-4a5b-8c7d-9e0f1a2b3c4d")
        @NotNull(message = DocumentConstant.LEGAL_ENTITY_REQUIRED_MESSAGE)
        UUID legalEntityId
) {
}
