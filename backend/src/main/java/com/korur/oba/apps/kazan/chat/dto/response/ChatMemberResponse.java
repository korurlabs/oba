package com.korur.oba.apps.kazan.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * An active member of the organization for the mention list and the member
 * popover, with whether at least one socket of theirs is connected.
 */
@Schema(description = "Chat member response")
public record ChatMemberResponse(

        @Schema(description = "User id", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID userId,

        @Schema(description = "First name", example = "Ada")
        String firstName,

        @Schema(description = "Last name", example = "Lovelace")
        String lastName,

        @Schema(description = "Email address", example = "ada@example.com")
        String email,

        @Schema(description = "Role in the organization", example = "MEMBER")
        OrganizationMemberRole role,

        @Schema(description = "True while at least one socket of the member is connected to this organization",
                example = "true")
        boolean online,

        @Schema(description = "True when the member may open Kazan in this organization; a member without it "
                + "cannot be mentioned or added to a channel", example = "true")
        boolean kazanAccess
) {
}
