package com.korur.oba.apps.otak.outreach.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;

/**
 * Every field of one outreach, with its mail. The files the mail carries are attachments with
 * the mail as parent; a client lists them through /storage/attachments with entityType MAIL
 * and the id of {@link #mail()}, so this response holds none.
 *
 * The answer of the detail endpoint and of every write that returns an outreach: each
 * write of {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl} reads it
 * back through {@link com.korur.oba.apps.otak.outreach.service.OutreachQueryService#get}, so
 * a write answers exactly what a later read shows. Built by
 * {@link com.korur.oba.apps.otak.outreach.mapper.OutreachMapper#toDetailResponse}.
 *
 * The send fields are set by the claim of a send, or by recording a WHATSAPP or CALL outreach
 * as done, and describe that moment, not the current state of the contact or sender: sentTo
 * keeps the address or phone it went to after the contact changes, and sentAt is cleared
 * again when a send failed. A WHATSAPP or CALL outreach has no mail, no attachments and no
 * mail sender.
 *
 * @param id                the outreach id
 * @param title             the title of the outreach
 * @param type              the channel: {@link OutreachType#MAIL}, {@link OutreachType#WHATSAPP}
 *                          or {@link OutreachType#CALL}
 * @param status            {@link OutreachStatus#DRAFT}, {@link OutreachStatus#SENT} (for
 *                          WHATSAPP and CALL: done) or {@link OutreachStatus#FAILED} (MAIL only)
 * @param relationId        the relation the outreach goes to
 * @param campaignId        the campaign the outreach belongs to, or null for none
 * @param contact           the contact the outreach goes to, or null once the contact was deleted
 * @param sentTo            the mail address (MAIL) or phone (WHATSAPP, CALL) it went to, or
 *                          null until sent or done
 * @param sentAt            the moment of sending, or null until sent and after a failed send
 * @param mailSenderAddress the address of the mail sender of the last send, or null until
 *                          sent or once that sender was deleted
 * @param mail              the outreach's mail, read from its own mail; null for WHATSAPP and CALL
 * @param updatedAt         the last change
 * @param createdAt         the creation timestamp
 */
@Schema(description = "Outreach detail")
public record OutreachDetailResponse(

        @Schema(description = "Outreach id", example = "8b3e1f2a-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
        UUID id,

        @Schema(description = "Title of the outreach", example = "Intro security check")
        String title,

        @Schema(description = "Channel of the outreach", example = "MAIL")
        OutreachType type,

        @Schema(description = "Where the outreach stands in sending", example = "DRAFT")
        OutreachStatus status,

        @Schema(description = "Relation the outreach goes to", example = "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b")
        UUID relationId,

        @Schema(description = "Campaign the outreach belongs to; null for none", nullable = true,
                example = "3b7c9d1e-4f5a-4b6c-8d7e-9f0a1b2c3d4e")
        UUID campaignId,

        @Schema(description = "Contact the outreach goes to; null once the contact was deleted", nullable = true)
        OutreachContactResponse contact,

        @Schema(description = "Mail address (MAIL) or phone (WHATSAPP, CALL) it went to; null until sent or done",
                nullable = true,
                example = "jan@example.com")
        String sentTo,

        @Schema(description = "Moment of sending; null until sent", nullable = true, example = "2026-11-12T09:30:00Z")
        Instant sentAt,

        @Schema(description = "Address of the mail sender it was sent from; null until sent or once the sender "
                + "was deleted", nullable = true, example = "sales@example.com")
        String mailSenderAddress,

        @Schema(description = "The outreach's mail; null for WHATSAPP and CALL", nullable = true)
        OutreachMailResponse mail,

        @Schema(description = "Last change", example = "2026-11-12T09:30:00Z")
        Instant updatedAt,

        @Schema(description = "Creation timestamp", example = "2026-11-12T09:00:00Z")
        Instant createdAt
) {
}
