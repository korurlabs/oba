package com.korur.oba.apps.otak.mail.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.mail.constant.MailConstant;

/**
 * Subject, body and language of an outreach's mail, written by hand. They are stored in
 * the outreach's own {@link com.korur.oba.apps.otak.mail.entity.Mail}, never in the outreach.
 *
 * The body is plain text; the send escapes it into HTML, so markup typed here reaches the
 * contact as text.
 *
 * Nested in {@link com.korur.oba.apps.otak.outreach.dto.request.OutreachCreateRequest} as the
 * hand-written mail source and in
 * {@link com.korur.oba.apps.otak.outreach.dto.request.OutreachUpdateRequest} as the new mail;
 * written by {@link com.korur.oba.apps.otak.mail.service.MailService#create} and
 * {@link com.korur.oba.apps.otak.mail.service.MailService#update}.
 *
 * @param subject  the mail subject, not blank
 * @param body     the plain-text mail body, not blank
 * @param language the language of the mail, required
 */
@Schema(description = "Mail of an outreach written by hand")
public record MailRequest(

        @Schema(description = "Mail subject", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "A security check for your company")
        @NotBlank(message = MailConstant.SUBJECT_REQUIRED_MESSAGE)
        @Size(max = MailConstant.SUBJECT_MAX_LENGTH, message = MailConstant.SUBJECT_SIZE_MESSAGE)
        String subject,

        @Schema(description = "Plain-text mail body", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Hello, we help small companies find their security gaps before others do.")
        @NotBlank(message = MailConstant.BODY_REQUIRED_MESSAGE)
        @Size(max = MailConstant.BODY_MAX_LENGTH, message = MailConstant.BODY_SIZE_MESSAGE)
        String body,

        @Schema(description = "Language of the mail", requiredMode = Schema.RequiredMode.REQUIRED, example = "NL")
        @NotNull(message = MailConstant.LANGUAGE_REQUIRED_MESSAGE)
        DocumentLanguage language
) {
}
