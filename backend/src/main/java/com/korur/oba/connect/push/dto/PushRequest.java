package com.korur.oba.connect.push.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.enums.PushKind;

/**
 * One event that pushes to a set of users, handed to
 * {@link com.korur.oba.connect.push.service.PushDispatchService#dispatch}. The publisher resolves the
 * recipients and the copy inputs inside its own transaction; the dispatcher drops the actor, applies
 * each recipient's switches and sends one message to each of their devices.
 *
 * Build it with the factory of its kind ({@link #chat}, {@link #incident}, {@link #nudge},
 * {@link #read}); each documents which fields it fills. The push module knows no app, so every app
 * value arrives here already as text.
 *
 * @param organizationId the organization the event belongs to; the app switches to it on a tap
 * @param recipients     each recipient with the one kind of push it gets
 * @param actorId        the user who caused the event, who never gets its push; null for none
 * @param conversation   chat: the push title, "#general" or "KZN-311 root cause · #general";
 *                       incident: the board name; null otherwise
 * @param senderName     chat and nudge: the display name of the author or the sender
 * @param text           chat: the message text; incident: the ticket title; nudge: the posting
 *                       deadline as HH:mm in the business zone
 * @param attachmentsOnly chat: true for a message with files and no text; false otherwise
 * @param priority       incident: the ticket priority as shown, "P1"; null otherwise
 * @param channelId      chat and read: the channel
 * @param threadId       chat and read: the thread, null in the channel itself
 * @param messageId      chat: the message
 * @param ticketKey      incident: the ticket key, "KZN-311"
 * @param badges         optional app badge per recipient, the recipient's total mention count;
 *                       null or a missing entry sends no badge
 */
public record PushRequest(
        UUID organizationId,
        Map<UUID, PushKind> recipients,
        UUID actorId,
        String conversation,
        String senderName,
        String text,
        boolean attachmentsOnly,
        String priority,
        UUID channelId,
        UUID threadId,
        UUID messageId,
        String ticketKey,
        Map<UUID, Integer> badges
) {

    /**
     * A chat message that mentions, replies to, asks a review of, or calls out the recipients.
     *
     * @param organizationId the organization of the channel
     * @param recipients     each recipient with one of MENTION, REPLY, REVIEW, EVERYONE, HERE
     * @param authorId       the author, who never gets the push
     * @param conversation   the push title: "#general", or "KZN-311 root cause · #general" in a thread
     * @param senderName     the author's display name
     * @param text           the message text, cut by the dispatcher; left out when previews are off
     * @param attachmentsOnly true for a message with files and no text, false for any other
     *                        message, including a pull request share or a daily update message
     * @param channelId      the channel
     * @param threadId       the thread, null for a message in the channel itself
     * @param messageId      the message
     * @param badges         optional total mention count per recipient, null for no badge
     * @return the request
     */
    public static PushRequest chat(UUID organizationId, Map<UUID, PushKind> recipients, UUID authorId,
                                   String conversation, String senderName, String text, boolean attachmentsOnly,
                                   UUID channelId, UUID threadId, UUID messageId, Map<UUID, Integer> badges) {
        return new PushRequest(organizationId, recipients, authorId, conversation, senderName, text,
                attachmentsOnly, null, channelId, threadId, messageId, null, badges);
    }

    /**
     * An urgent incident opened or raised on a board: "P1 incident on Kazan Platform",
     * "KZN-311 Payment webhooks return 502".
     *
     * @param organizationId the organization of the board
     * @param recipients     the board members to alert
     * @param actorId        the user who opened or raised it, null for a system actor
     * @param boardName      the board name
     * @param priority       the priority as shown, "P1"
     * @param ticketKey      the ticket key
     * @param ticketTitle    the ticket title
     * @return the request
     */
    public static PushRequest incident(UUID organizationId, Collection<UUID> recipients, UUID actorId,
                                       String boardName, String priority, String ticketKey, String ticketTitle) {
        return new PushRequest(organizationId, sameKind(recipients, PushKind.INCIDENT), actorId, boardName, null,
                ticketTitle, false, priority, null, null, null, ticketKey, null);
    }

    /**
     * A reminder to post today's daily update: "Ayşe Kaya reminds you to post today's update.
     * Deadline 10:30."
     *
     * @param organizationId the organization of the daily update
     * @param recipientId    the member reminded
     * @param senderId       the member who sent the reminder
     * @param senderName     the sender's display name
     * @param deadline       the posting deadline as HH:mm in the business zone, "10:30"
     * @return the request
     */
    public static PushRequest nudge(UUID organizationId, UUID recipientId, UUID senderId, String senderName,
                                    String deadline) {
        return new PushRequest(organizationId, Map.of(recipientId, PushKind.NUDGE), senderId, null, senderName,
                deadline, false, null, null, null, null, null, null);
    }

    /**
     * A data-only push to the reader's own devices after the reader read a conversation, so every
     * device withdraws that conversation's notifications. It ignores the switches and is not dropped
     * as the actor's own push.
     *
     * @param organizationId the organization of the channel
     * @param readerId       the user who read the conversation
     * @param channelId      the channel
     * @param threadId       the thread, null for the channel itself
     * @return the request
     */
    public static PushRequest read(UUID organizationId, UUID readerId, UUID channelId, UUID threadId) {
        return new PushRequest(organizationId, Map.of(readerId, PushKind.READ), readerId, null, null, null, false,
                null, channelId, threadId, null, null, null);
    }

    private static Map<UUID, PushKind> sameKind(Collection<UUID> userIds, PushKind kind) {
        Map<UUID, PushKind> recipients = new LinkedHashMap<>();
        userIds.forEach(userId -> recipients.put(userId, kind));
        return recipients;
    }

    /**
     * Keeps the message text out of every log line that prints this record.
     */
    @Override
    public String toString() {
        return "PushRequest[organizationId=" + organizationId + ", recipients=" + (recipients == null ? 0 : recipients.size())
                + ", actorId=" + actorId + "]";
    }
}
