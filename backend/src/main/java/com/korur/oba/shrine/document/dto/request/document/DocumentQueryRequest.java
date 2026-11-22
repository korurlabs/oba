package com.korur.oba.shrine.document.dto.request.document;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.shrine.document.constant.document.DocumentConstant;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;

/**
 * Query parameters of GET /documents: the one-based page and page size of
 * {@link QueryRequest} and optional filters. Every filter left out matches every document.
 * Documents are always ordered by their last change, newest first, so the inherited sortBy
 * and sortDirection are ignored.
 *
 * Turned into a query by
 * {@link com.korur.oba.shrine.document.specification.document.DocumentSpecification#filterBy}.
 */
@Schema(description = "Paging and filters of the document list; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class DocumentQueryRequest extends QueryRequest {

    /**
     * Only documents of this relation; null for every owner.
     */
    @Schema(description = "Only documents of this relation", nullable = true,
            example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
    private UUID relationId;

    /**
     * Only documents this legal entity issues; null for every legal entity.
     */
    @Schema(description = "Only documents of this legal entity", nullable = true,
            example = "2c4e6a8b-1d3f-4a5b-8c7d-9e0f1a2b3c4d")
    private UUID legalEntityId;

    /**
     * Only documents in this language; null for every language.
     */
    @Schema(description = "Only documents in this language", nullable = true, example = "NL")
    private DocumentLanguage language;

    /**
     * Only documents of this type; null for every type.
     */
    @Schema(description = "Only documents of this type", nullable = true, example = "PROPOSAL")
    private DocumentType type;

    /**
     * Part of the title, case ignored. A blank value is refused, so the client sends null.
     */
    @Schema(description = "Part of the title, case ignored; absent for every document, never blank",
            nullable = true, example = "proposal")
    @NullOrNotBlank(message = DocumentConstant.SEARCH_BLANK_MESSAGE)
    @Size(max = DocumentConstant.SEARCH_MAX_LENGTH, message = DocumentConstant.SEARCH_SIZE_MESSAGE)
    private String search;
}
