package com.korur.oba.apps.kaya.account.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.account.enums.AccountUserSource;

@Schema(description = "A user who uses the account")
public record AccountUserEmbeddedResponse(
        @Schema(description = "User id", example = "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID userId,

        @Schema(description = "Display name of the user", example = "Ada Lovelace")
        String displayName,

        @Schema(description = "How the user was linked to the account", example = "RECORDER")
        AccountUserSource source,

        @Schema(description = "When this user last used the account")
        Instant lastSeenAt
) {
}
