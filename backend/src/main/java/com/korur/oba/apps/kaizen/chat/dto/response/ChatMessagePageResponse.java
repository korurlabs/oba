package com.korur.oba.apps.kaizen.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * One keyset page of messages. Channel and thread pages are oldest first;
 * the same record carries pins (newest pin first) and search results (newest
 * first). It travels in the data of the response, with pagination null.
 */
@Schema(description = "Keyset page of chat messages")
public record ChatMessagePageResponse(

        @Schema(description = "Messages of the page")
        List<ChatMessageResponse> items,

        @Schema(description = "True when older messages exist before the first item", example = "true")
        boolean hasOlder,

        @Schema(description = "True when newer messages exist after the last item", example = "false")
        boolean hasNewer
) {
}
