package com.korur.oba.apps.otak.documenttemplate.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.constant.DocumentTemplateConstant;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentType;
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Query parameters of GET /otak/document-templates: the one-based page and page size
 * inherited from {@link QueryRequest}, plus an optional name search and optional type and
 * language filters. Every filter left out matches every template.
 *
 * The list is always scoped to the organization of the checked X-Organization-ID header,
 * which is not a parameter here. Templates are always ordered by name, then language, then
 * id, so the translations of one template sit next to each other and the pages stay stable;
 * the inherited sortBy and sortDirection are therefore ignored.
 *
 * Turned into a query by
 * {@link com.korur.oba.apps.otak.documenttemplate.specification.DocumentTemplateSpecification#filterBy};
 * consumed by
 * {@link com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateQueryService#list}.
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
}
