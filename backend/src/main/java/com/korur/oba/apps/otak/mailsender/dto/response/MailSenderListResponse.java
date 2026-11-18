package com.korur.oba.apps.otak.mailsender.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailsender.enums.EmailType;

/**
 * One mail sender of the organization, as the list shows it and as adding one returns it.
 */
@Schema(description = "Mail sender")
public record MailSenderListResponse(

        @Schema(description = "Mail sender id", example = "8b3e1f2a-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
        UUID id,

        @Schema(description = "Mail address as it was entered, trimmed", example = "jane.doe@example.com")
        String mailAddress,

        @Schema(description = "Kind of mailbox, chosen when the sender was added", example = "PERSONAL")
        EmailType type,

        @Schema(description = "Id of the user who added the sender", example = "2f6c1a9e-3b4d-4e5f-8a7b-9c0d1e2f3a4b")
        UUID addedBy,

        @Schema(description = "Creation timestamp", example = "2026-11-12T09:00:00Z")
        Instant createdAt
) {
}
