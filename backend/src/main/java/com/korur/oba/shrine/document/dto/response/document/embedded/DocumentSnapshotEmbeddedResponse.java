package com.korur.oba.shrine.document.dto.response.document.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One PDF snapshot of a document: the attachment stored on a mail when the document was
 * attached to it, with the outreach that sends the mail.
 *
 * @param attachmentId  the attachment holding the snapshot
 * @param mailId        the mail it is attached to
 * @param outreachTitle the title of the outreach that sends the mail; null when none is found
 * @param createdAt     when the snapshot was made, UTC
 */
@Schema(description = "PDF snapshot of a document on a mail")
public record DocumentSnapshotEmbeddedResponse(

        @Schema(description = "Attachment id of the snapshot", example = "9c8b7a6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d")
        UUID attachmentId,

        @Schema(description = "Mail the snapshot is attached to", example = "3a4b5c6d-7e8f-4a0b-9c1d-2e3f4a5b6c7d")
        UUID mailId,

        @Schema(description = "Title of the outreach that sends the mail", nullable = true, example = "Intro")
        String outreachTitle,

        @Schema(description = "When the snapshot was made", example = "2026-11-14T09:30:00Z")
        Instant createdAt
) {
}
