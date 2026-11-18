package com.korur.oba.connect.push.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.push.dto.PushRequest;

/**
 * Sends push notifications to users' phones through the Expo Push Service.
 */
public interface PushDispatchService {

    /**
     * Sends one push to every device of every recipient that should get it.
     *
     * - The actor never gets the push, except a READ push, which goes to the actor's own devices.
     * - A recipient whose switch for the kind is off is skipped (MENTION, REPLY, REVIEW and NUDGE
     *   follow mentionsAndReplies; EVERYONE, HERE and INCIDENT their own switch); READ ignores them.
     * - With showPreviews off, a chat push carries no message text.
     * - Messages go to Expo in chunks of 100. A device Expo reports as DeviceNotRegistered is removed;
     *   the other tickets are followed up by their receipts.
     *
     * Synchronous and never throws for a delivery failure: a failed chunk is logged at error level and
     * recorded as a retry dead event, and the other chunks are still sent. Call it after the
     * publishing transaction committed, on the push executor, so a push never delays or rolls back
     * the message, incident or nudge. With the Expo access token blank it sends nothing.
     *
     * @param request the recipients and the copy inputs
     */
    void dispatch(PushRequest request);
}
