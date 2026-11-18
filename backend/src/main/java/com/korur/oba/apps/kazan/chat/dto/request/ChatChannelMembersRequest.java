package com.korur.oba.apps.kazan.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;

/**
 * Request payload used to add users to a private channel's member list.
 * Users already on the list stay as they are.
 */
@Schema(description = "Chat channel members request")
public record ChatChannelMembersRequest(

        @Schema(description = "Users to add; ACTIVE members of the organization",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f\"]")
        @NotEmpty(message = "At least one user is required")
        @Size(max = ChatConstant.MAX_CHANNEL_MEMBERS_PER_REQUEST, message = "At most 200 members per request")
        List<UUID> userIds
) {
}
