package com.korur.oba.apps.otak.mailtemplate.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.mailtemplate.constant.MailTemplateConstant;

/**
 * Body of POST /otak/mail-templates: every field of a new mail template.
 *
 * The organization is not in the body: it comes from the checked X-Organization-ID header,
 * so a client can never create a mail template in an organization it was not checked against.
 *
 * Checked here by annotations: every field is present, the name, subject and body are not
 * blank, and the lengths stay within the columns, so a mail template is always usable.
 * Checked by the service, because it needs the database: the trimmed name is free in the
 * language within the organization, case ignored
 * ({@link com.korur.oba.apps.otak.mailtemplate.validator.MailTemplateValidator#ensureNameFree}).
 *
 * Consumed by {@link com.korur.oba.apps.otak.mailtemplate.mapper.MailTemplateMapper#toEntity}, then
 * {@link com.korur.oba.apps.otak.mailtemplate.service.MailTemplateService#create}.
 *
 * @param name     the name, stored trimmed; unique within the organization and language, case ignored
 * @param language the language of the mail; the same name may exist once per language
 * @param subject  the mail subject
 * @param body     the plain-text mail body
 * @see MailTemplateUpdateRequest
 */
@Schema(description = "Mail template create request")
public record MailTemplateCreateRequest(

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
