package com.korur.oba.apps.kazan.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelType;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;

/**
 * A channel as one reader sees it in the channel list.
 */
@Schema(description = "Chat channel response")
public record ChatChannelResponse(

        @Schema(description = "Channel id", example = "3b4c5d6e-7f80-4a91-b2c3-d4e5f6a7b8c9")
        UUID id,

        @Schema(description = "Channel name", example = "General")
        String name,

        @Schema(description = "URL slug of the channel, unique among the live channels of the organization; "
                + "built from the name, with a numeric suffix when another channel holds it",
                example = "general")
        String slug,

        @Schema(description = "How the channel behaves; fixed at creation", example = "TEXT")
        ChatChannelType type,

        @Schema(description = "GENERAL; DAILY_UPDATES for the channel that receives the daily update cards; "
                + "PULL_REQUESTS for the channel of the pull request talk",
                example = "GENERAL")
        ChatChannelPurpose purpose,

        @Schema(description = "PUBLIC or PRIVATE", example = "PUBLIC")
        ChatChannelVisibility visibility,

        @Schema(description = "True when the reader reads the channel: always for a public channel, for a private "
                + "one when the reader is on its member list; false for an owner or admin who only sees it listed",
                example = "true")
        boolean member,

        @Schema(description = "Members of a private channel; ACTIVE members of the organization for a public one",
                example = "12")
        int memberCount,

        @Schema(description = "Creation time (UTC), the order of the channel list", example = "2026-10-20T08:15:00Z")
        Instant createdAt
) {
}
