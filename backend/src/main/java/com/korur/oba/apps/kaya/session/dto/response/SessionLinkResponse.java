package com.korur.oba.apps.kaya.session.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.session.enums.SessionLinkKind;
import com.korur.oba.apps.kaya.session.enums.SessionLinkSource;

@Schema(description = "A pull request or Kaizen ticket linked to a session")
public record SessionLinkResponse(
        @Schema(description = "Link id")
        UUID id,

        @Schema(description = "What the link points at", example = "PULL_REQUEST")
        SessionLinkKind kind,

        @Schema(description = "The ticket key or the pull request URL", example = "KAYA-42")
        String reference,

        @Schema(description = "Where the link opens; null for a ticket key when the repository has no ticket URL template",
                nullable = true)
        String url,

        @Schema(description = "RECORDER when found on ingest, MANUAL when a member added it", example = "RECORDER")
        SessionLinkSource source,

        @Schema(description = "The user who added a MANUAL link; null for the recorder", nullable = true)
        UUID createdBy,

        @Schema(description = "When the link was added")
        Instant createdAt
) {
}
