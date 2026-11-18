package com.korur.oba.apps.otak.outreach.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;

/**
 * Request payload used to change an outreach that was not sent: its contact, its title
 * and, for a MAIL outreach, the subject, body and language of its own mail. The relation and
 * the type are fixed after create; the attachments are replaced through their own endpoint.
 *
 * A full update: every field replaces the stored value. The mail is required for a MAIL
 * outreach and must be null for a WHATSAPP or CALL one, which has no mail; field annotations
 * cannot see the stored type, so
 * {@link com.korur.oba.apps.otak.outreach.validator.OutreachValidator#ensureUpdateMailFitsType}
 * checks it. The mail is written to the outreach's own mail, which keeps its template link;
 * the outreach must not be SENT, and the service reads it with a row lock so the update
 * cannot race a send.
 *
 * Bound by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#updateOutreach},
 * handled by {@link com.korur.oba.apps.otak.outreach.service.OutreachService#update} and
 * applied by {@link com.korur.oba.apps.otak.outreach.mapper.OutreachMapper#updateEntity}.
 *
 * @param contactId the contact of the outreach's relation the outreach goes to
 * @param title     the title of the outreach, stored trimmed
 * @param mail      the new subject, body and language of the outreach's mail; null for
 *                  WHATSAPP and CALL
 * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#update
 */
@Schema(description = "Outreach update request; replaces the contact, the title and, for MAIL, the mail")
public record OutreachUpdateRequest(

        @Schema(description = "Contact of the outreach's relation", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "7c6d5e4f-3a2b-4c1d-9e8f-7a6b5c4d3e2f")
        @NotNull(message = OutreachConstant.CONTACT_REQUIRED_MESSAGE)
        UUID contactId,

        @Schema(description = "Title of the outreach", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Intro security check")
        @NotBlank(message = OutreachConstant.TITLE_REQUIRED_MESSAGE)
        @Size(max = OutreachConstant.TITLE_MAX_LENGTH, message = OutreachConstant.TITLE_SIZE_MESSAGE)
        String title,

        @Schema(description = "New subject and body of the outreach's mail; required for MAIL, null for WHATSAPP "
                + "and CALL", nullable = true)
        @Valid
        MailRequest mail
) {
}
