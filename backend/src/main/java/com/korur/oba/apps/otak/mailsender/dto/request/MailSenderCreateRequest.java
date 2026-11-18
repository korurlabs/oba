package com.korur.oba.apps.otak.mailsender.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.mailsender.constant.MailSenderConstant;
import com.korur.oba.apps.otak.mailsender.enums.EmailType;

/**
 * Request payload used to add a mail sender. The organization comes from the checked
 * request header. The service refuses an address another sender of the organization
 * already has, case ignored, and an address without a mailbox of the chosen type in the
 * Microsoft 365 tenant.
 *
 * The type is chosen by the admin rather than detected, because Microsoft Graph finds a
 * personal mailbox by user principal name and a shared one by its mail property, and a
 * shared mailbox's address may differ from its user principal name. The address is trimmed
 * by the service before it is checked and stored.
 *
 * Bound by {@link com.korur.oba.apps.otak.mailsender.controller.MailSenderController#createMailSender}
 * and handled by {@link com.korur.oba.apps.otak.mailsender.service.MailSenderService#create}.
 *
 * @param type        the kind of mailbox, {@link EmailType#PERSONAL} or {@link EmailType#SHARED},
 *                    which decides how the address is looked up in the tenant
 * @param mailAddress the mail address of a mailbox in the Microsoft 365 tenant, unique
 *                    within the organization with case ignored
 * @see com.korur.oba.apps.otak.mailsender.service.impl.MailSenderServiceImpl#create
 * @see com.korur.oba.apps.otak.mailsender.validator.MailSenderValidator
 */
@Schema(description = "Mail sender create request")
public record MailSenderCreateRequest(

        @Schema(description = "Kind of mailbox, which decides how the address is looked up in the tenant: "
                + "PERSONAL by user principal name, SHARED by mail address", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "SHARED")
        @NotNull(message = MailSenderConstant.TYPE_REQUIRED_MESSAGE)
        EmailType type,

        @Schema(description = "Mail address of a mailbox in the Microsoft 365 tenant, unique within the "
                + "organization with case ignored", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "jane.doe@example.com")
        @NotBlank(message = MailSenderConstant.MAIL_ADDRESS_REQUIRED_MESSAGE)
        @Email(message = MailSenderConstant.MAIL_ADDRESS_FORMAT_MESSAGE)
        @Size(max = MailSenderConstant.MAIL_ADDRESS_MAX_LENGTH, message = MailSenderConstant.MAIL_ADDRESS_SIZE_MESSAGE)
        String mailAddress
) {
}
