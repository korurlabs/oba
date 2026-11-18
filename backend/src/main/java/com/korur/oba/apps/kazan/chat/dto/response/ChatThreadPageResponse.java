package com.korur.oba.apps.kazan.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * One keyset page of threads, newest activity first. It travels in the data
 * of the response, with pagination null.
 */
@Schema(description = "Keyset page of chat threads")
public record ChatThreadPageResponse(

        @Schema(description = "Threads of the page")
        List<ChatThreadResponse> items,

        @Schema(description = "True when more threads exist after the last item", example = "false")
        boolean hasMore
) {
}
