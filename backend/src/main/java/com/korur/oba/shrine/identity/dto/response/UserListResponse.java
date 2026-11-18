package com.korur.oba.shrine.identity.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.dto.response.embedded.UserProfileEmbeddedDetailResponse;
import com.korur.oba.shrine.identity.enums.UserStatus;

@Schema(description = "User list response")
public record UserListResponse(
        @Schema(description = "User id", nullable = false)
        UUID id,

        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        @Schema(description = "Email address", nullable = false)
        String email,

        @Schema(description = "User status", nullable = false)
        UserStatus userStatus,

        @Schema(description = "User profile information", nullable = true)
        UserProfileEmbeddedDetailResponse profile
) {
}
