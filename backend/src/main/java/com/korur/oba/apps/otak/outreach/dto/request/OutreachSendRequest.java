package com.korur.oba.apps.otak.outreach.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;

/**
 * Request payload that sends an outreach from one of the organization's mail senders.
 *
 * The sender is chosen per send, not stored on a draft, so the same outreach can be sent
 * again from another mailbox after a failure. It must be a mail sender of the request's
 * organization; the mail never goes out from the user who clicks send. The recipient is not
 * part of the payload: it is the outreach's contact, at the address the contact has at send
 * time.
 *
 * Bound by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#sendOutreach}
 * and handled by {@link com.korur.oba.apps.otak.outreach.service.OutreachService#send}.
 *
 * @param mailSenderId the mail sender of the organization the mail goes out from
 * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send
 * @see com.korur.oba.apps.otak.mailsender.entity.MailSender
 */
@Schema(description = "Sends an outreach from a mail sender of the organization")
public record OutreachSendRequest(

        @Schema(description = "Mail sender of the organization the mail goes out from",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = OutreachConstant.MAIL_SENDER_REQUIRED_MESSAGE)
        UUID mailSenderId
) {
}
