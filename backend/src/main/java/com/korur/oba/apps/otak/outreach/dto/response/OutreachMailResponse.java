package com.korur.oba.apps.otak.outreach.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * The mail of an outreach, read from its own {@link com.korur.oba.apps.otak.mail.entity.Mail}.
 *
 * The outreach holds no subject or body itself; they live in a mail that belongs to this
 * outreach alone, copied from a mail template or written by hand. For a SENT outreach it is
 * the exact text that went out, because no write changes the mail of a SENT outreach.
 *
 * Built by {@link com.korur.oba.apps.otak.outreach.mapper.OutreachMapper#toMailResponse} for
 * {@link OutreachDetailResponse#mail()}.
 *
 * @param id             the id of the outreach's mail
 * @param mailTemplateId the mail template the mail was copied from; null when written by
 *                       hand or once that template is deleted
 * @param subject        the mail subject
 * @param body           the plain-text mail body, as typed; the send turns it into HTML
 * @param language       the language of the mail
 */
@Schema(description = "Mail of an outreach")
public record OutreachMailResponse(

        @Schema(description = "Id of the outreach's mail", example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
        UUID id,

        @Schema(description = "Mail template the mail was copied from; null when written by hand or the template "
                + "was deleted", nullable = true, example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID mailTemplateId,

        @Schema(description = "Mail subject", example = "A security check for your company")
        String subject,

        @Schema(description = "Plain-text mail body",
                example = "Hello, we help small companies find their security gaps before others do.")
        String body,

        @Schema(description = "Language of the mail", example = "NL")
        DocumentLanguage language
) {
}
