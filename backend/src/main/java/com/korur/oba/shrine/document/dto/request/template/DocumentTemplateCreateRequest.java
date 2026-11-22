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
 * Body of POST /document-templates: the content fields of a new document template.
 *
 * The organization is not in the body: it comes from the checked X-Organization-ID header,
 * so a client can never create a template in an organization it was not checked against.
 * The files are not in the body either: a template is created without them and receives
 * its HTML and PDF through the multipart endpoint
 * {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController#replaceDocumentTemplateFiles},
 * because a JSON body and two binary parts are different request shapes.
 *
 * Checked here by annotations: the type, name and language are present, the name is not
 * blank, and its length stays within the column. A type the enum does not know fails to
 * bind and answers 400.
 *
 * Checked by the service, because the rules need the database: the trimmed name is free in
 * the language within the organization, case ignored
 * ({@link com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator#ensureNameFree}),
 * and a legal entity, when given, belongs to the organization
 * ({@link com.korur.oba.shrine.legalentity.service.LegalEntityQueryService#getEntity}).
 *
 * Mapped to the entity by
 * {@link com.korur.oba.shrine.document.mapper.template.DocumentTemplateMapper#toEntity};
 * consumed by
 * {@link com.korur.oba.shrine.document.service.template.DocumentTemplateService#create}.
 *
 * @param type          the kind of document
 * @param name          the name, stored trimmed; unique within the organization and language, case ignored
 * @param language      the language of the content; the same name may exist once per language
 * @param legalEntityId the legal entity of the organization that issues the template, or null for every one
 * @see DocumentTemplateUpdateRequest
 */
@Schema(description = "Document template create request")
public record DocumentTemplateCreateRequest(

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
