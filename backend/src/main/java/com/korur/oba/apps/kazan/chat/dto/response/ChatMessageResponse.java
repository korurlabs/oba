package com.korur.oba.apps.kazan.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketCreatedByResponse;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;

/**
 * One message as one reader sees it. The reader-specific fields (mentionsMe,
 * repliesToMe, canOpen of the ticket references, linkedTickets, reactedByMe) are computed
 * per request, so the response is never shared between readers; the one
 * exception is the reader-neutral copy a realtime signal carries, where all of
 * them are false or empty and each client fills in its own. A deleted
 * thread root comes back as a placeholder: deleted true, content empty,
 * author null and thread set.
 */
@Schema(description = "Chat message response")
public record ChatMessageResponse(

        @Schema(description = "Message id", example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
        UUID id,

        @Schema(description = "Organization whose channel holds the message", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID organizationId,

        @Schema(description = "Channel that holds the message; for a thread message the thread's channel; null "
                + "for the placeholder of a deleted thread root read outside its channel", nullable = true,
                example = "3b4c5d6e-7f80-4a91-b2c3-d4e5f6a7b8c9")
        UUID channelId,

        @Schema(description = "Thread the message is in; null for a channel message", nullable = true,
                example = "6a7b8c9d-0e1f-4a2b-8c3d-4e5f6a7b8c9d")
        UUID threadId,

        @Schema(description = "True for the placeholder of a deleted thread root", example = "false")
        boolean deleted,

        @Schema(description = "Author; null for the placeholder of a deleted thread root", nullable = true)
        TicketCreatedByResponse author,

        @Schema(description = "Markdown text with tokens as stored; may be empty for a daily update, empty for a "
                + "pull request share and for a placeholder",
                example = "Release is out, thanks <@3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f>!")
        String content,

        @Schema(description = "Creation time (UTC)", example = "2026-10-20T08:15:00Z")
        Instant createdAt,

        @Schema(description = "Time of the last edit (UTC); null when never edited", nullable = true,
                example = "2026-10-20T08:20:00Z")
        Instant editedAt,

        @Schema(description = "Time the message was pinned (UTC); null when not pinned", nullable = true,
                example = "2026-10-20T09:00:00Z")
        Instant pinnedAt,

        @Schema(description = "Client nonce of the send that created the message; null outside that response",
                nullable = true, example = "c0a8f2b4-7d1e-4f3a-9b6c-1e2d3f4a5b6c")
        String clientNonce,

        @Schema(description = "Preview of the message this one replies to; null when it is not a reply", nullable = true)
        ChatReplyPreviewResponse replyTo,

        @Schema(description = "Users mentioned directly, in order of first mention")
        List<UUID> mentionedUserIds,

        @Schema(description = "True when the message mentions everyone", example = "false")
        boolean mentionsEveryone,

        @Schema(description = "True when the message mentions the members online at send time", example = "false")
        boolean mentionsHere,

        @Schema(description = "True when the message mentions the reader directly, through everyone, or through "
                + "here while the reader was online", example = "true")
        boolean mentionsMe,

        @Schema(description = "True when the message, written by someone else, replies to a live message of the "
                + "reader; false when the quoted message was deleted", example = "false")
        boolean repliesToMe,

        @Schema(description = "Ticket references in token order, with the snapshot taken at send time")
        List<ChatTicketRefResponse> ticketRefs,

        @Schema(description = "Ticket keys typed as plain text that the reader can open; keys the reader cannot "
                + "open are absent")
        List<ChatLinkedTicketResponse> linkedTickets,

        @Schema(description = "Attached files")
        List<AttachmentResponse> attachments,

        @Schema(description = "Reactions in first-reaction order")
        List<ChatReactionSummaryResponse> reactions,

        @Schema(description = "Summary of the thread started on this message; null when there is none", nullable = true)
        ChatThreadSummaryResponse thread,

        @Schema(description = "Daily update the message stands for; null for every other message", nullable = true,
                example = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f")
        UUID dailyUpdateId,

        @Schema(description = "The daily update the message stands for, when the reader may read it; null otherwise",
                nullable = true)
        DailyUpdateResponse dailyUpdate,

        @Schema(description = "Pull request share the message stands for; null for every other message",
                nullable = true, example = "4d5e6f70-8192-4a3b-9c4d-5e6f7a8b9c0d")
        UUID pullRequestShareId,

        @Schema(description = "The pull request share the message stands for, with the reader's linked ticket; "
                + "null for every other message and when the share was deleted", nullable = true)
        PullRequestShareResponse pullRequestShare,

        @Schema(description = "Every author, mentioned and quoted user of the response, by user id")
        Map<UUID, ChatUserResponse> users,

        @Schema(description = "Time of the row's last change (UTC); REST responses and realtime payloads carry the "
                + "same value, so a client keeps the newer copy; null for the placeholder of a deleted thread root "
                + "read without its row", nullable = true, example = "2026-10-20T08:20:00Z")
        Instant version
) {
}
