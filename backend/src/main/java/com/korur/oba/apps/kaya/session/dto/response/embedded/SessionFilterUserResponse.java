package com.korur.oba.apps.kaya.session.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

@Schema(description = "A user that has sessions, as a wall filter option")
public record SessionFilterUserResponse(
        @Schema(description = "User id")
        UUID id,

        @Schema(description = "Display name", example = "Ada Lovelace")
        String displayName
) {
}
