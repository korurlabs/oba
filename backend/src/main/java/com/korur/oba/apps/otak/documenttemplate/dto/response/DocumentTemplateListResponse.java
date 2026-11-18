package com.korur.oba.apps.otak.documenttemplate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentType;

/**
 * One row of the document template list, answered by GET /otak/document-templates.
 *
 * The file URLs are left out: a page of templates stays small, and no
 * blob URL has to be signed for a row the user may never open. The detail,
 * {@link DocumentTemplateDetailResponse}, carries them.
 *
 * Mapped by
 * {@link com.korur.oba.apps.otak.documenttemplate.mapper.DocumentTemplateMapper#toListResponse}.
 *
 * @param id        the template id
 * @param name      the name, unique within the organization and language, case ignored
 * @param type      the kind of content
 * @param language  the language of the content
 * @param ready     true once both files are stored, so the template's PDF can be copied onto an attachment, see
 *                  {@link com.korur.oba.apps.otak.documenttemplate.entity.DocumentTemplate#isReady()}
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

        @Schema(description = "True once both the HTML and the PDF file are stored", example = "true")
        boolean ready,

        @Schema(description = "Last change", example = "2026-11-14T09:30:00Z")
        Instant updatedAt
) {
}
