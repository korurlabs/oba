package com.korur.oba.apps.otak.mailtemplate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * One row of the mail template list, answered by GET /otak/mail-templates.
 *
 * The body is left out, so a page of mail templates stays small; the subject stays, so a
 * user can pick a mail from the list. The detail, {@link MailTemplateDetailResponse},
 * carries the body.
 *
 * Mapped by
 * {@link com.korur.oba.apps.otak.mailtemplate.mapper.MailTemplateMapper#toListResponse}.
 *
 * @param id        the mail template id
 * @param name      the name, unique within the organization and language, case ignored
 * @param language  the language of the mail
 * @param subject   the mail subject
 * @param updatedAt the last change, UTC
 */
@Schema(description = "Mail template list item")
public record MailTemplateListResponse(

        @Schema(description = "Mail template id", example = "8b3e1f2a-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
        UUID id,

        @Schema(description = "Name of the mail template", example = "First contact")
        String name,

        @Schema(description = "Language of the mail", example = "NL")
        DocumentLanguage language,

        @Schema(description = "Mail subject", example = "A security check for your company")
        String subject,

        @Schema(description = "Last change", example = "2026-11-14T09:30:00Z")
        Instant updatedAt
) {
}
