package com.korur.oba.shrine.document.dto.response.template;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;

/**
 * One row of the document template list, answered by GET /document-templates.
 *
 * The file URLs are left out: a page of templates stays small, and no
 * blob URL has to be signed for a row the user may never open. The detail,
 * {@link DocumentTemplateDetailResponse}, carries them.
 *
 * Mapped by
 * {@link com.korur.oba.shrine.document.mapper.template.DocumentTemplateMapper#toListResponse}.
 *
 * @param id        the template id
 * @param name      the name, unique within the organization and language, case ignored
 * @param type      the kind of content
 * @param language  the language of the content
 * @param legalEntityId        the legal entity that issues the template, or null when every legal entity may use it
 * @param legalEntityShortName the short name of that legal entity, or null
 * @param ready     true once both files are stored, so the template's PDF can be copied onto an attachment, see
 *                  {@link com.korur.oba.shrine.document.entity.template.DocumentTemplate#isReady()}
 * @param updatedAt the last change, UTC
 */
@Schema(description = "Document template list item")
public record DocumentTemplateListResponse(

        @Schema(description = "Template id", example = "8b3e1f2a-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
        UUID id,

        @Schema(description = "Name of the template", example = "First contact")
        String name,

        @Schema(description = "Kind of document", example = "PROPOSAL")
        DocumentType type,

        @Schema(description = "Language of the content", example = "NL")
        DocumentLanguage language,

        @Schema(description = "Legal entity of the organization that issues the template; null when every legal "
                + "entity of the organization may use it", nullable = true,
                example = "2c4e6a8b-1d3f-4a5b-8c7d-9e0f1a2b3c4d")
        UUID legalEntityId,

        @Schema(description = "Short name of that legal entity; null when the template has none", nullable = true,
                example = "Korur B.V.")
        String legalEntityShortName,

        @Schema(description = "True once both the HTML and the PDF file are stored", example = "true")
        boolean ready,

        @Schema(description = "Last change", example = "2026-11-14T09:30:00Z")
        Instant updatedAt
) {
}
