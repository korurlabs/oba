package com.korur.oba.apps.otak.mailtemplate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;

/**
 * Every field of one mail template, answered by the detail and by the create and update of
 * {@link com.korur.oba.apps.otak.mailtemplate.controller.MailTemplateController}.
 *
 * Mapped by
 * {@link com.korur.oba.apps.otak.mailtemplate.mapper.MailTemplateMapper#toDetailResponse}.
 *
 * @param id        the mail template id
 * @param name      the name, unique within the organization and language, case ignored
 * @param language  the language of the mail
 * @param subject   the mail subject
 * @param body      the plain-text mail body
 * @param updatedAt the last change, UTC
 * @param createdAt the creation time, UTC
 * @see MailTemplateListResponse
 */
@Schema(description = "Mail template detail")
public record MailTemplateDetailResponse(

        @Schema(description = "Mail template id", example = "8b3e1f2a-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
        UUID id,

        @Schema(description = "Name of the mail template", example = "First contact")
        String name,

        @Schema(description = "Language of the mail", example = "NL")
        DocumentLanguage language,

        @Schema(description = "Mail subject", example = "A security check for your company")
        String subject,

        @Schema(description = "Plain-text mail body",
                example = "Hello, we help small companies find their security gaps before others do.")
        String body,

        @Schema(description = "Last change", example = "2026-11-14T09:30:00Z")
        Instant updatedAt,

        @Schema(description = "Creation timestamp", example = "2026-11-14T09:00:00Z")
        Instant createdAt
) {
}
