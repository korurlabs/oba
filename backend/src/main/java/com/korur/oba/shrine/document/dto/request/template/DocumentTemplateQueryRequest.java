package com.korur.oba.shrine.document.dto.request.template;

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
import com.korur.oba.shrine.document.constant.template.DocumentTemplateConstant;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;

/**
 * Query parameters of GET /document-templates: the one-based page and page size
 * inherited from {@link QueryRequest}, plus an optional name search and optional type,
 * language and legal entity filters. Every filter left out matches every template.
 *
 * The list is always scoped to the organization of the checked X-Organization-ID header,
 * which is not a parameter here. Templates are always ordered by name, then language, then
 * id, so the translations of one template sit next to each other and the pages stay stable;
 * the inherited sortBy and sortDirection are therefore ignored.
 *
 * Turned into a query by
 * {@link com.korur.oba.shrine.document.specification.template.DocumentTemplateSpecification#filterBy};
 * consumed by
 * {@link com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService#list}.
 */
@Schema(description = "Paging and filters of the document template list; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class DocumentTemplateQueryRequest extends QueryRequest {

    /**
     * Part of the name to search for, case ignored. Null searches nothing; a blank value is
     * refused ({@link NullOrNotBlank}), so the client sends null, never an empty string.
     */
    @Schema(description = "Part of the name, case ignored; absent for every template, never blank", nullable = true,
            example = "intro")
    @NullOrNotBlank(message = DocumentTemplateConstant.SEARCH_BLANK_MESSAGE)
    @Size(max = DocumentTemplateConstant.SEARCH_MAX_LENGTH, message = DocumentTemplateConstant.SEARCH_SIZE_MESSAGE)
    private String search;

    /**
     * Only templates of this type; null for every type.
     */
    @Schema(description = "Only templates of this type; null for every type", nullable = true, example = "PROPOSAL")
    private DocumentType type;

    /**
     * Only templates in this language; null for every language.
     */
    @Schema(description = "Only templates in this language; null for every language", nullable = true, example = "NL")
    private DocumentLanguage language;

    /**
     * Only templates this legal entity may use: its own and those without a legal entity,
     * which every legal entity of the organization may use. Null for every template.
     */
    @Schema(description = "Only templates this legal entity may use: its own and those without a legal entity; "
            + "null for every template", nullable = true, example = "2c4e6a8b-1d3f-4a5b-8c7d-9e0f1a2b3c4d")
    private UUID legalEntityId;
}
