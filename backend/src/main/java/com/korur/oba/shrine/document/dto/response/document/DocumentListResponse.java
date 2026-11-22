package com.korur.oba.shrine.document.dto.response.document;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.dto.response.document.embedded.DocumentUserEmbeddedResponse;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;

/**
 * One row of GET /documents. The file URLs are left out, so a page signs no blob URL; the
 * detail, {@link DocumentDetailResponse}, carries them and the snapshots.
 *
 * Mapped by {@link com.korur.oba.shrine.document.mapper.document.DocumentMapper#toListResponse}.
 *
 * @param id                   the document id
 * @param title                the title
 * @param type                 the kind of document
 * @param language             the language of the content
 * @param entityType           the kind of owner
 * @param entityId             the owner
 * @param relationId           the owning relation, or null for another kind of owner
 * @param relationName         the name of that relation
 * @param legalEntityId        the issuing legal entity
 * @param legalEntityShortName its short name
 * @param templateId           the template it was made from, or null
 * @param ready                true once both files are stored
 * @param snapshotCount        the number of PDF snapshots on mails
 * @param updatedBy            the user who changed it last
 * @param updatedAt            the last change, UTC
 * @param createdAt            the creation time, UTC
 */
@Schema(description = "Document list item")
public record DocumentListResponse(

        @Schema(description = "Document id", example = "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a")
        UUID id,

        @Schema(description = "Title of the document", example = "Proposal security check")
        String title,

        @Schema(description = "Kind of document", example = "PROPOSAL")
        DocumentType type,

        @Schema(description = "Language of the content", example = "NL")
        DocumentLanguage language,

        @Schema(description = "Kind of owner", example = "RELATION")
        DocumentEntityType entityType,

        @Schema(description = "Owner of the document", example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        UUID entityId,

        @Schema(description = "Relation that owns the document; null for another kind of owner", nullable = true,
                example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        UUID relationId,

        @Schema(description = "Name of that relation", nullable = true, example = "Fabrikam B.V.")
        String relationName,

        @Schema(description = "Legal entity that issues the document", example = "2c4e6a8b-1d3f-4a5b-8c7d-9e0f1a2b3c4d")
        UUID legalEntityId,

        @Schema(description = "Short name of that legal entity", example = "Korur B.V.")
        String legalEntityShortName,

        @Schema(description = "Template the document was made from; null for a local file or a deleted template",
                nullable = true, example = "8b3e1f2a-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
        UUID templateId,

        @Schema(description = "True once both the HTML and the PDF file are stored", example = "true")
        boolean ready,

        @Schema(description = "Number of PDF snapshots attached to mails", example = "2")
        long snapshotCount,

        @Schema(description = "User who changed the document last")
        DocumentUserEmbeddedResponse updatedBy,

        @Schema(description = "Last change", example = "2026-11-14T09:30:00Z")
        Instant updatedAt,

        @Schema(description = "Creation timestamp", example = "2026-11-14T09:00:00Z")
        Instant createdAt
) {
}
