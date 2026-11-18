package com.korur.oba.apps.kaizen.chat.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.request.ChatThreadCreateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatThreadStatusUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatThreadUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for chat threads: start a thread on a channel message,
 * rename it or change its auto-archive window, change its status, and join
 * or leave it.
 *
 * Every operation needs an ACTIVE membership with role MEMBER or higher in an
 * ACTIVE organization and a channel the user can read; a thread or message of
 * another organization or of an unreadable channel is reported as not found. Each change publishes a
 * {@link com.korur.oba.apps.kaizen.chat.event.ChatThreadChangedEvent}, which
 * the realtime layer sends after the commit.
 *
 * A thread is never archived by a job: an ACTIVE thread counts as archived
 * once its last reply and its last status change are older than its window,
 * and a new reply makes it active again, as it does for a thread archived by
 * hand. A CLOSED thread takes no replies and no edits until it is reopened.
 */
public interface ChatThreadService {

    /**
     * Starts a thread on a live channel message; the thread belongs to the
     * message's channel. A null title takes the default derived from the root
     * message. The creator becomes the first member, and so does the root's
     * author while they can still read the channel
     * ({@link ChatChannelQueryService#canRead}); the thread's activity time
     * starts now.
     *
     * @param user           the creator
     * @param organizationId the request's organization
     * @param request        the root message and the optional title
     * @return the new thread as the creator sees it
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         STA_CHAT_THREAD_EXISTS with the existing thread's ID when the message already has a thread
     */
    ChatThreadResponse createThread(User user, UUID organizationId, ChatThreadCreateRequest request);

    /**
     * Renames a thread and sets its auto-archive window. Only its creator and
     * the organization's owners and admins may.
     *
     * @param user           the current user
     * @param organizationId the request's organization
     * @param threadId       the thread
     * @param request        the title and the window in minutes
     * @return the thread after the change
     */
    ChatThreadResponse updateThread(User user, UUID organizationId, UUID threadId, ChatThreadUpdateRequest request);

    /**
     * Closes, reopens, archives or unarchives a thread. Only its creator and
     * the organization's owners and admins may. The change is recorded with
     * the user and the time, and the time restarts the auto-archive window,
     * so a quiet thread that is reopened or unarchived stays active for a
     * full window. Asking for the status the thread already shows changes
     * nothing. A closed thread cannot be archived, since a reply would then
     * revive it; it has to be reopened first. This also holds when the close
     * commits while the archive is under way.
     *
     * @param user           the current user
     * @param organizationId the request's organization
     * @param threadId       the thread
     * @param request        the new status
     * @return the thread after the change
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         STA_CHAT_THREAD_CLOSED with the thread's ID when ARCHIVED is asked for a closed thread
     */
    ChatThreadResponse changeStatus(User user, UUID organizationId, UUID threadId,
                                    ChatThreadStatusUpdateRequest request);

    /**
     * Makes the user a member of a thread, so its replies count as unread for
     * them. Joining a thread the user already follows changes nothing.
     *
     * @param user           the current user
     * @param organizationId the request's organization
     * @param threadId       the thread
     * @return the thread after the change
     */
    ChatThreadResponse joinThread(User user, UUID organizationId, UUID threadId);

    /**
     * Removes the user from a thread together with their read position in it,
     * so it drops out of their unread count. A later mention adds them again.
     * Leaving a thread the user does not follow changes nothing.
     *
     * @param user           the current user
     * @param organizationId the request's organization
     * @param threadId       the thread
     * @return the thread after the change
     */
    ChatThreadResponse leaveThread(User user, UUID organizationId, UUID threadId);
}
