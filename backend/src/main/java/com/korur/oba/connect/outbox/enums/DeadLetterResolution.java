package com.korur.oba.connect.outbox.enums;

/**
 * Resolution state for dead letter events.
 *
 * <p>Used by both outbox and retry dead letter entities to track
 * whether a permanently failed event has been acted upon.</p>
 *
 * <ul>
 *   <li>REPLAYED — Re-created as a new event for reprocessing.</li>
 *   <li>DISCARDED — Manually marked as no longer needed.</li>
 * </ul>
 */
public enum DeadLetterResolution {
    REPLAYED,
    DISCARDED
}
