package com.korur.oba.apps.otak.mailtemplate.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.mailtemplate.constant.MailTemplateConstant;
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Query parameters of GET /otak/mail-templates: the one-based page and page size inherited
 * from {@link QueryRequest}, plus an optional name search and an optional language filter.
 * Every filter left out matches every mail template.
 *
 * The list is always scoped to the organization of the checked X-Organization-ID header,
 * which is not a parameter here. Mail templates are always ordered by name, then language,
 * then id, so the translations of one mail sit next to each other and the pages stay
 * stable; the inherited sortBy and sortDirection are therefore ignored.
 *
 * Turned into a query by
 * {@link com.korur.oba.apps.otak.mailtemplate.specification.MailTemplateSpecification#filterBy};
 * consumed by
 * {@link com.korur.oba.apps.otak.mailtemplate.service.MailTemplateQueryService#list}.
 */
@Schema(description = "Paging and filters of the mail template list; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class MailTemplateQueryRequest extends QueryRequest {

    /**
     * Part of the name to search for, case ignored. Null searches nothing; a blank value is
     * refused ({@link NullOrNotBlank}), so the client sends null, never an empty string.
     */
    @Schema(description = "Part of the name, case ignored; absent for every mail template, never blank",
            nullable = true, example = "intro")
    @NullOrNotBlank(message = MailTemplateConstant.SEARCH_BLANK_MESSAGE)
    @Size(max = MailTemplateConstant.SEARCH_MAX_LENGTH, message = MailTemplateConstant.SEARCH_SIZE_MESSAGE)
    private String search;

    /**
     * Only mail templates in this language; null for every language.
     */
    @Schema(description = "Only mail templates in this language; null for every language", nullable = true,
            example = "NL")
    private DocumentLanguage language;
}
