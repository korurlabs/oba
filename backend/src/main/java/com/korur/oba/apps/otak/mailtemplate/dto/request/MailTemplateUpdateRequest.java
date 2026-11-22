package com.korur.oba.apps.otak.mailtemplate.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.mailtemplate.constant.MailTemplateConstant;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Body of PUT /otak/mail-templates/{mailTemplateId}: every field of the mail template.
 *
 * A full replace, as every PUT of this API: the client sends the whole mail template it
 * shows, and the stored row then equals it.
 *
 * The name check excludes the mail template itself, so it may keep its own name. Mails
 * already copied from it keep their own content.
 *
 * Checked here by annotations: every field is present, the name, subject and body are not
 * blank, and the lengths stay within the columns, so a mail template is always usable.
 * Checked by the service, because it needs the database: the trimmed name is free in the
 * language within the organization, case ignored
 * ({@link com.korur.oba.apps.otak.mailtemplate.validator.MailTemplateValidator#ensureNameFree}).
 *
 * Consumed by {@link com.korur.oba.apps.otak.mailtemplate.service.MailTemplateService#update}.
 *
 * @param name     the name, stored trimmed; unique within the organization and language, case ignored
 * @param language the language of the mail; the same name may exist once per language
 * @param subject  the mail subject
 * @param body     the plain-text mail body
 * @see MailTemplateCreateRequest
 */
@Schema(description = "Mail template update request; replaces every field")
public record MailTemplateUpdateRequest(

        @Schema(description = "Name of the mail template, unique within the organization and language with case "
                + "ignored", requiredMode = Schema.RequiredMode.REQUIRED, example = "First contact")
        @NotBlank(message = MailTemplateConstant.NAME_REQUIRED_MESSAGE)
        @Size(max = MailTemplateConstant.NAME_MAX_LENGTH, message = MailTemplateConstant.NAME_SIZE_MESSAGE)
        String name,

        @Schema(description = "Language of the mail", requiredMode = Schema.RequiredMode.REQUIRED, example = "NL")
        @NotNull(message = MailTemplateConstant.LANGUAGE_REQUIRED_MESSAGE)
        DocumentLanguage language,

        @Schema(description = "Mail subject", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "A security check for your company")
        @NotBlank(message = MailTemplateConstant.SUBJECT_REQUIRED_MESSAGE)
        @Size(max = MailTemplateConstant.SUBJECT_MAX_LENGTH, message = MailTemplateConstant.SUBJECT_SIZE_MESSAGE)
        String subject,

        @Schema(description = "Plain-text mail body", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Hello, we help small companies find their security gaps before others do.")
        @NotBlank(message = MailTemplateConstant.BODY_REQUIRED_MESSAGE)
        @Size(max = MailTemplateConstant.BODY_MAX_LENGTH, message = MailTemplateConstant.BODY_SIZE_MESSAGE)
        String body
) {
}
