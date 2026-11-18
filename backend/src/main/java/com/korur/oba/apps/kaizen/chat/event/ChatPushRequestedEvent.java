package com.korur.oba.apps.kaizen.chat.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.enums.PushKind;

/**
 * Published when a new chat message calls on users who should hear of it on
 * their phones: users mentioned directly, the author of the quoted message
 * or of the thread's root, the reviewers of a pull request share, and the
 * readers reached by everyone or here.
 *
 * The recipients and the copy are resolved inside the sending transaction,
 * where the message, its mention rows and the online readers of a here
 * mention are at hand; consumers send the pushes after the commit, so a
 * rolled-back send pushes nothing. Each recipient has one kind, the first
 * that applies of MENTION, REPLY, REVIEW, EVERYONE and HERE, and the author
 * is never among them.
 *
 * @param organizationId organization of the channel
 * @param channelId      channel of the message
 * @param threadId       thread of the message; null for a channel message
 * @param messageId      the new message
 * @param authorId       author of the message
 * @param recipients     each recipient with the kind of push they get; never empty
 * @param conversation   title of the push: "#general", or the thread title and the channel
 * @param senderName     display name of the author
 * @param text           the message as plain text, tokens rendered as names and ticket keys;
 *                       empty for a message with files only
 * @param attachmentsOnly true for an ordinary message with files and no text, so its push can say
 *                        that an attachment was sent; false for a pull request share or a daily
 *                        update message, which carry no text either
 */
public record ChatPushRequestedEvent(UUID organizationId,
                                     UUID channelId,
                                     UUID threadId,
                                     UUID messageId,
                                     UUID authorId,
                                     Map<UUID, PushKind> recipients,
                                     String conversation,
                                     String senderName,
                                     String text,
                                     boolean attachmentsOnly) {

    /**
     * Keeps the message text and the names out of every log line that prints
     * this record.
     */
    @Override
    public String toString() {
        return "ChatPushRequestedEvent[organizationId=" + organizationId + ", channelId=" + channelId
                + ", threadId=" + threadId + ", messageId=" + messageId + ", recipients="
                + (recipients == null ? 0 : recipients.size()) + "]";
    }
}
