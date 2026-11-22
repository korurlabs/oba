package com.korur.oba.apps.otak.relation.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.enums.RelationAttachmentSource;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * One file of a relation's attachments list: the fields of
 * {@link com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse}, flat, plus
 * where it sits. A record can not extend another, so the fields are repeated in the same order.
 */
@Schema(description = "A file of a relation or of one of its mails")
public record RelationAttachmentResponse(

        @Schema(description = "Attachment id")
        UUID id,

        // Short-lived signed read URL; open it at once rather than storing it.
        @Schema(description = "Signed read URL of the file")
        String url,

        @Schema(description = "File name", example = "proposal.pdf")
        String fileName,

        @Schema(description = "Media type", example = "application/pdf")
        String contentType,

        @Schema(description = "Size in bytes")
        Long fileSize,

        @Schema(description = "Parent type: RELATION or MAIL")
        StorageEntityType entityType,

        @Schema(description = "Parent id: the relation or the mail")
        UUID entityId,

        @Schema(description = "Upload time")
        Instant createdAt,

        @Schema(description = "Image width in pixels, null for other files")
        Integer width,

        @Schema(description = "Image height in pixels, null for other files")
        Integer height,

        // Set on a snapshot; null for an upload, or once the document was deleted.
        @Schema(description = "Document the file is a snapshot of, or null")
        UUID documentId,

        @Schema(description = "RELATION for a file of the relation, MAIL for a file of one of its mails")
        RelationAttachmentSource source,

        @Schema(description = "The mail, null for RELATION")
        UUID mailId,

        @Schema(description = "Title of the outreach that sends the mail, null for RELATION or a mail without one")
        String outreachTitle
) {
}
