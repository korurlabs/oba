package com.korur.oba.connect.push.dto.expo;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.enums.PushKind;

/**
 * The "data" object of a push, which the app reads to route a tap and to match a READ withdrawal.
 * Every key is always sent, null when it does not apply, so the app can rely on the shape.
 *
 * @param type           the kind of push
 * @param organizationId the organization to switch to before navigating
 * @param channelId      the chat channel, or null
 * @param threadId       the chat thread, or null
 * @param messageId      the chat message, or null
 * @param ticketKey      the incident ticket key, or null
 * @param conversation   the conversation or board the push is about, or null
 * @param sentAt         when the server built the push, an ISO 8601 instant in UTC
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExpoPushData(
        @JsonProperty("type") PushKind type,
        @JsonProperty("organizationId") UUID organizationId,
        @JsonProperty("channelId") UUID channelId,
        @JsonProperty("threadId") UUID threadId,
        @JsonProperty("messageId") UUID messageId,
        @JsonProperty("ticketKey") String ticketKey,
        @JsonProperty("conversation") String conversation,
        @JsonProperty("sentAt") Instant sentAt
) {
}
