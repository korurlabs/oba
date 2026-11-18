package com.korur.oba.apps.kaya.repository.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;

@Schema(description = "A repository the organization records sessions for")
public record RepositoryResponse(
        @Schema(description = "Repository id", example = "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b")
        UUID id,

        @Schema(description = "Display name", example = "village")
        String name,

        @Schema(description = "Normalised git remote: host/owner/name in lower case", example = "github.com/korurlabs/village")
        String remoteUrl,

        @Schema(description = "Ticket URL template containing {key}", nullable = true,
                example = "https://kaizen.sensey.io/tickets/{key}")
        String ticketUrlTemplate,

        @Schema(description = "Repository status", example = "ACTIVE")
        RepositoryStatus status,

        @Schema(description = "When the repository was connected", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the repository was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt
) {
}
