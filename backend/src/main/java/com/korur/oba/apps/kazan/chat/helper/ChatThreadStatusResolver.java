package com.korur.oba.apps.kazan.chat.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus;

/**
 * Derives the status a reader sees for a chat thread from its stored status.
 *
 * - CLOSED when stored CLOSED: a closed thread never archives by itself.
 * - ARCHIVED when stored ARCHIVED, or stored ACTIVE and quiet for its window.
 * - ACTIVE otherwise.
 *
 * The quiet time counts from the later of the last reply and the last status
 * change, so a quiet thread that is reopened or unarchived gets a full window
 * before it archives again. The archive that follows from inactivity is never
 * stored, so no job has to run; the native queries in
 * {@link com.korur.oba.apps.kazan.chat.repository.ChatThreadRepository} and
 * {@link com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository} apply the same rule in SQL, and a thread counts as
 * archived from the exact end of its window in both.
 */
@Component
public class ChatThreadStatusResolver {

    /**
     * Resolves the status a reader sees.
     *
     * @param thread the thread
     * @param now    the read time
     * @return the effective status
     */
    public ChatThreadStatus resolve(ChatThread thread, Instant now) {
        return isArchivedByInactivity(thread, now) ? ChatThreadStatus.ARCHIVED : thread.getStatus();
    }

    /**
     * Resolves when the status a reader sees took effect: the end of the
     * window for a thread archived by inactivity, else the last status
     * change, null when the status was never changed.
     *
     * @param thread the thread
     * @param now    the read time
     * @return the time of the effective status, nullable
     */
    public Instant resolveChangedAt(ChatThread thread, Instant now) {
        if (isArchivedByInactivity(thread, now)) {
            return archivesAt(thread);
        }
        return thread.getStatusChangedAt();
    }

    /**
     * Resolves who set the status a reader sees: nobody for a thread archived
     * by inactivity, else the user of the last status change, null when the
     * status was never changed.
     *
     * @param thread the thread
     * @param now    the read time
     * @return the user, nullable
     */
    public UUID resolveChangedBy(ChatThread thread, Instant now) {
        if (isArchivedByInactivity(thread, now)) {
            return null;
        }
        return thread.getStatusChangedBy();
    }

    private boolean isArchivedByInactivity(ChatThread thread, Instant now) {
        return thread.getStatus() == ChatThreadStatus.ACTIVE && !archivesAt(thread).isAfter(now);
    }

    /**
     * The end of the window: the later of the last reply and the last status
     * change, plus the auto-archive minutes.
     */
    private static Instant archivesAt(ChatThread thread) {
        Instant activity = thread.getLastMessageAt();
        Instant changedAt = thread.getStatusChangedAt();
        if (changedAt != null && changedAt.isAfter(activity)) {
            activity = changedAt;
        }
        return activity.plus(Duration.ofMinutes(thread.getAutoArchiveMinutes()));
    }
}
