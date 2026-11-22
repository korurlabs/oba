package com.korur.oba.shrine.document.dto.request.template;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.constant.template.DocumentTemplateConstant;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;

/**
 * Body of PUT /document-templates/{templateId}: the full new content fields of a
 * document template.
 *
 * A full replace, as every PUT of this API: every field is written. The client sends the
 * whole template it shows, and the stored row then equals it, without a merge rule for
 * absent fields. The type may change, because every type holds the same pair of files.
 * The files are not part of this body; they stay and are replaced through
 * {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController#replaceDocumentTemplateFiles}.
 *
 * The annotations and the name and legal entity rules are those of
 * {@link DocumentTemplateCreateRequest}; the name check excludes the template itself, so
 * it may keep its own name.
 *
 * Consumed by
 * {@link com.korur.oba.shrine.document.service.template.DocumentTemplateService#update},
 * which writes the fields through the same helper as create.
 *
 * @param type          the kind of document
 * @param name          the name, stored trimmed; unique within the organization and language, case ignored
 * @param language      the language of the content
 * @param legalEntityId the legal entity of the organization that issues the template, or null for every one;
 *                      a full replace, so null clears it
 */
@Schema(description = "Document template update request; replaces every field")
public record DocumentTemplateUpdateRequest(

        @Schema(description = "Kind of document", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROPOSAL")
        @NotNull(message = DocumentTemplateConstant.TYPE_REQUIRED_MESSAGE)
        DocumentType type,

        @Schema(description = "Name of the template, unique within the organization and language with case ignored",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "First contact")
        @NotBlank(message = DocumentTemplateConstant.NAME_REQUIRED_MESSAGE)
        @Size(max = DocumentTemplateConstant.NAME_MAX_LENGTH, message = DocumentTemplateConstant.NAME_SIZE_MESSAGE)
        String name,

        @Schema(description = "Language of the content", requiredMode = Schema.RequiredMode.REQUIRED, example = "NL")
        @NotNull(message = DocumentTemplateConstant.LANGUAGE_REQUIRED_MESSAGE)
        DocumentLanguage language,

        @Schema(description = "Legal entity of the organization that issues the template; null for every legal "
                + "entity of the organization", nullable = true, example = "2c4e6a8b-1d3f-4a5b-8c7d-9e0f1a2b3c4d")
        UUID legalEntityId
) {
}
