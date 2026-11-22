package com.korur.oba.shrine.document.dto.response.document.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The user who changed a document last, as the document responses show it: "edited 5
 * minutes ago by Merthan".
 *
 * @param id          the user id
 * @param displayName first and last name, or the email when the profile has no name; null when the
 *                    user no longer exists
 */
@Schema(description = "User who changed the document last")
public record DocumentUserEmbeddedResponse(

        @Schema(description = "User id", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID id,

        @Schema(description = "First and last name, or the email when the profile has no name", nullable = true,
                example = "Merthan Tukus")
        String displayName
) {
}
