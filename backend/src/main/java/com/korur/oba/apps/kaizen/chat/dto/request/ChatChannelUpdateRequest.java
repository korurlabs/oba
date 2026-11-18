package com.korur.oba.apps.kaizen.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelVisibility;

/**
 * Full update of a channel: name, visibility and purpose; the type never
 * changes. memberIds is taken only when the channel turns from public to
 * private, and its members are then the admin plus memberIds.
 */
@Schema(description = "Chat channel update request")
public record ChatChannelUpdateRequest(

        @Schema(description = "Channel name, stored trimmed; two channels may share a name",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "release")
        @NotBlank(message = "Name is required")
        @Size(max = ChatConstant.MAX_CHANNEL_NAME_LENGTH, message = "Name must be at most 80 characters")
        String name,

        @Schema(description = "PUBLIC: every member of the organization reads it; PRIVATE: only its members",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "PUBLIC")
        @NotNull(message = "Visibility is required")
        ChatChannelVisibility visibility,

        @Schema(description = "GENERAL; DAILY_UPDATES to receive the daily update cards; PULL_REQUESTS for the "
                + "pull request talk. Every purpose but GENERAL needs a public channel and is held by one live "
                + "channel of the organization at most",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "GENERAL")
        @NotNull(message = "Purpose is required")
        ChatChannelPurpose purpose,

        // Null or empty sets no members.
        @Schema(description = "Members of a private channel besides the admin; ACTIVE members of the organization",
                nullable = true, example = "[\"3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f\"]")
        @Size(max = ChatConstant.MAX_CHANNEL_MEMBERS_PER_REQUEST, message = "At most 200 members per request")
        List<UUID> memberIds
) {
}
