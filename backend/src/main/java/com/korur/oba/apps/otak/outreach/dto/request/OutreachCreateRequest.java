package com.korur.oba.apps.otak.outreach.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;

/**
 * Request payload used to create a DRAFT outreach to one contact of a relation. The
 * organization comes from the checked request header. The type names the channel and never
 * changes afterwards. A MAIL outreach takes its mail from exactly one source: a mail
 * template, which the server copies into a new mail of the outreach, or a subject and body
 * written by hand, plus optional document templates whose PDFs the server copies onto the
 * new mail as attachments. A WHATSAPP or CALL outreach happens outside Oba and takes no mail
 * source and no templates.
 *
 * Field annotations cannot express "exactly one of mailTemplateId and mail, and only for
 * MAIL", so that rule is checked by
 * {@link com.korur.oba.apps.otak.outreach.validator.OutreachValidator#ensureMailFitsType}
 * before anything is written. The organization, the status and the mail link are set by
 * the server, never by this payload.
 *
 * Bound by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#createOutreach},
 * handled by {@link com.korur.oba.apps.otak.outreach.service.OutreachService#create} and mapped
 * by {@link com.korur.oba.apps.otak.outreach.mapper.OutreachMapper#toEntity}.
 *
 * @param relationId     the relation the outreach goes to; must belong to the organization
 *                       and never changes after create
 * @param contactId      the contact the outreach goes to; must be a contact of the relation
 * @param title          the title of the outreach, stored trimmed
 * @param type           the channel of the outreach; required, fixed after create
 * @param mailTemplateId the mail template to copy into the outreach's mail, or null when
 *                       mail is given; always null for WHATSAPP and CALL
 * @param mail           the mail written by hand, or null when mailTemplateId is given;
 *                       always null for WHATSAPP and CALL
 * @param documentTemplateIds the document templates of the organization whose PDFs are copied
 *                       onto the new mail as attachments, in this order; null or empty for none,
 *                       a repeated id counts once; null or empty for WHATSAPP and CALL
 * @param campaignId     the campaign of the organization the outreach belongs to, or null for none
 * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#create
 */
@Schema(description = "Outreach create request; a MAIL outreach gives exactly one of mailTemplateId and mail, "
        + "a WHATSAPP or CALL outreach neither and no document templates")
public record OutreachCreateRequest(

        @Schema(description = "Relation the outreach goes to", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b")
        @NotNull(message = OutreachConstant.RELATION_REQUIRED_MESSAGE)
        UUID relationId,

        @Schema(description = "Contact of the relation the outreach goes to",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "7c6d5e4f-3a2b-4c1d-9e8f-7a6b5c4d3e2f")
        @NotNull(message = OutreachConstant.CONTACT_REQUIRED_MESSAGE)
        UUID contactId,

        @Schema(description = "Title of the outreach", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Intro security check")
        @NotBlank(message = OutreachConstant.TITLE_REQUIRED_MESSAGE)
        @Size(max = OutreachConstant.TITLE_MAX_LENGTH, message = OutreachConstant.TITLE_SIZE_MESSAGE)
        String title,

        @Schema(description = "Channel of the outreach, fixed after create", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "MAIL")
        @NotNull(message = OutreachConstant.TYPE_REQUIRED_MESSAGE)
        OutreachType type,

        @Schema(description = "Mail template to copy into the outreach's mail; null when mail is given and for WHATSAPP and CALL",
                nullable = true, example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID mailTemplateId,

        @Schema(description = "Mail written by hand; null when mailTemplateId is given and for WHATSAPP and CALL", nullable = true)
        @Valid
        MailRequest mail,

        @Schema(description = "Document templates whose PDFs are copied onto the new mail as attachments, in this "
                + "order; null or empty for none and for WHATSAPP and CALL", nullable = true)
        List<@NotNull(message = OutreachConstant.DOCUMENT_TEMPLATE_ID_REQUIRED_MESSAGE) UUID> documentTemplateIds,

        @Schema(description = "Campaign of the organization the outreach belongs to; null for none", nullable = true,
                example = "3b7c9d1e-4f5a-4b6c-8d7e-9f0a1b2c3d4e")
        UUID campaignId
) {
}
