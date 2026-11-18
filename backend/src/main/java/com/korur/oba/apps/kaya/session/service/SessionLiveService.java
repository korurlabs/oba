package com.korur.oba.apps.kaya.session.service;

// 1. Framework-level
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.session.event.SessionUpdatedEvent;

/**
 * The live session stream of an organization over server-sent events. A client keeps one stream
 * open per organization and reads a session again when a session-updated event names it.
 */
public interface SessionLiveService {

    /**
     * Opens a stream of the organization's session changes for a member. The first event is a
     * heartbeat, so the client knows the stream is open.
     *
     * @param user           the member, whose access the controller has checked
     * @param organizationId the organization whose sessions the stream follows
     * @return the open stream
     */
    SseEmitter subscribe(User user, UUID organizationId);

    /**
     * Sends a session change to every open stream of the session's organization. Called after the
     * transaction that made the change has committed.
     *
     * @param event the session change
     */
    void onSessionUpdated(SessionUpdatedEvent event);
}
