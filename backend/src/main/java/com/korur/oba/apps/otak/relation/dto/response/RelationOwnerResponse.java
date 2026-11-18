package com.korur.oba.apps.otak.relation.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The member who looks after a relation, with the name to show.
 */
@Schema(description = "Relation owner")
public record RelationOwnerResponse(

        @Schema(description = "User id", example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        UUID id,

        // First and last name, or the email while the profile has no name.
        @Schema(description = "Display name", example = "Jane Doe")
        String name
) {
}
