package com.korur.oba.apps.kazan.chat.service;

// 1. Framework-level
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for chat messages: post, edit, delete, pin, typing and the
 * chat entries of daily updates and pull request shares.
 *
 * Every operation is scoped to the request's organization and to a channel
 * the user can read. Writing needs an ACTIVE membership with role MEMBER or
 * higher in an ACTIVE organization; a channel, message or thread the user
 * cannot read is reported as not found. Each write publishes a
 * {@link com.korur.oba.apps.kazan.chat.event.ChatMessageChangedEvent}, which the realtime layer sends to the channel's
 * readers after the commit.
 */
public interface ChatMessageService {

    /**
     * Posts a message to a channel or to one of its threads, with its files,
     * in one transaction: a failed upload saves nothing. It is the only way a
     * message is created, the chat entries of daily updates and pull request
     * shares included.
     *
     * Mentioned users must be able to read the channel and referenced tickets
     * must be on the sender's boards. A here mention reaches the channel's
     * readers online now. In a thread, the sender and every user mentioned
     * directly become thread members, and the thread's reply count and last
     * activity move. The sender's read position does not move: the client
     * marks read when the reader reaches the bottom. With a dailyUpdateId the
     * update must be the sender's own, not posted yet, and the channel the
     * daily updates channel; the text may then be empty. With a
     * pullRequestShareId the share must be the sender's own, not posted yet,
     * and the channel the pull requests channel; the entry carries no thread,
     * file or daily update, and the text may be empty.
     *
     * A send whose client nonce the sender already used returns the message
     * that first send stored, or its deleted placeholder when it was deleted
     * since, and changes nothing: no row, file, thread change or event, and
     * it does not count toward the send rate. Of two concurrent sends with
     * one nonce, one stores the message and the other returns it.
     *
     * @param user           the sender
     * @param organizationId the request's organization
     * @param request        channel, text, thread, quoted message, daily update, pull request share and
     *                       client nonce
     * @param files          the attached files; null or empty for none
     * @return the message as the sender sees it, with the client nonce echoed
     * @throws com.korur.oba.dojo.exception.internal.business.TooManyRequestsException
     *         GEN_API_004 when the sender sends faster than the send rate
     */
    ChatMessageResponse postMessage(User user, UUID organizationId, ChatMessageCreateRequest request,
                                    List<MultipartFile> files);

    /**
     * Replaces the text of the user's own message. Mentions and ticket
     * references are resolved again with the same rules as a send; users newly
     * mentioned in a thread message join the thread. No time limit applies.
     *
     * @param user           the author
     * @param organizationId the request's organization
     * @param messageId      the message
     * @param request        the new text
     * @return the edited message
     */
    ChatMessageResponse editMessage(User user, UUID organizationId, UUID messageId, ChatMessageUpdateRequest request);

    /**
     * Soft-deletes a message for everyone. Its author may, and the
     * organization's owners and admins may delete any message to moderate the
     * channel. A thread started on it lives on, replies quoting it show it as
     * deleted, and its files stay in storage, hidden with the message. A
     * deleted message loses its pin.
     *
     * @param user           the author, or an owner or admin
     * @param organizationId the request's organization
     * @param messageId      the message
     */
    void deleteMessage(User user, UUID organizationId, UUID messageId);

    /**
     * Pins or unpins a message. Its author and the organization's owners and
     * admins may; pinning twice or unpinning an unpinned message changes
     * nothing and publishes nothing.
     *
     * @param user           the current user
     * @param organizationId the request's organization
     * @param messageId      the message
     * @param pinned         true to pin, false to unpin
     * @return the message after the change
     */
    ChatMessageResponse setPinned(User user, UUID organizationId, UUID messageId, boolean pinned);

    /**
     * Publishes that a user is typing in a channel or one of its threads to
     * the channel's topic. Typing needs the same role as sending, MEMBER or
     * higher, so a GUEST, who cannot send, signals nothing; the caller,
     * {@link com.korur.oba.apps.kazan.chat.controller.websocket.ChatTypingController},
     * checks that role before it calls this method. This method checks that
     * the user can read the channel and that the thread belongs to it.
     *
     * @param userId         the typing user, from the session's principal
     * @param organizationId the session's organization
     * @param channelId      the channel typed in, or the channel of the thread
     * @param threadId       the thread typed in; null for the channel itself
     */
    void signalTyping(UUID userId, UUID organizationId, UUID channelId, UUID threadId);

    /**
     * Marks the channel entry of an edited daily update as edited at the
     * update's edit time, in the caller's transaction, so readers refetch the
     * card. An update without an entry changes nothing.
     *
     * @param dailyUpdate the edited daily update, editedAt set
     */
    void touchDailyUpdateMessage(DailyUpdate dailyUpdate);

    /**
     * Soft-deletes the channel entry of a deleted daily update, in the
     * caller's transaction. A thread started on the entry lives on and a
     * pinned entry loses its pin, as for any deleted message. An update
     * without an entry changes nothing.
     *
     * @param dailyUpdate the deleted daily update
     */
    void removeDailyUpdateMessage(DailyUpdate dailyUpdate);

    /**
     * Tells the readers of the pull requests channel that a share changed, in
     * the caller's transaction, so they refetch its card. An edit by the
     * author marks the entry edited at the share's edit time; an approval or
     * its withdrawal only advances the entry's version, because the author
     * edited nothing. An edit also makes the share's current reviewers the
     * entry's mentions, and the reviewers who were added or removed get fresh
     * unread counts. A share without an entry changes nothing.
     *
     * @param share           the changed share, its reviewers already replaced
     * @param reviewersBefore the share's reviewers before the change
     * @param edited          true when the author edited the share, editedAt set; false for an approval change
     */
    void touchPullRequestShareMessage(PullRequestShare share, Set<UUID> reviewersBefore, boolean edited);

    /**
     * Soft-deletes the channel entry of a deleted pull request share, in the
     * caller's transaction. A thread started on the entry lives on and a
     * pinned entry loses its pin, as for any deleted message. A share without
     * an entry changes nothing.
     *
     * @param share the deleted share
     */
    void removePullRequestShareMessage(PullRequestShare share);
}
