package com.korur.oba.apps.kaizen.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A user named on a page: an author, a mentioned or a quoted user, or a
 * thread member. member is false for a user no longer active in the
 * organization, whom the client renders as "@unknown member".
 */
@Schema(description = "User referenced by a chat page")
public record ChatUserResponse(

        @Schema(description = "User id", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID userId,

        @Schema(description = "First name", example = "Ada")
        String firstName,

        @Schema(description = "Last name", example = "Lovelace")
        String lastName,

        @Schema(description = "Email address", example = "ada@example.com")
        String email,

        @Schema(description = "True when the user is an active member of the organization", example = "true")
        boolean member
) {
}
