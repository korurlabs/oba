package com.korur.oba.apps.kaizen.ticket.enums;

/**
 * Defines which finished tickets the home leaderboard counts.
 */
public enum HomeLeaderboardPeriod {

    // Tickets that are DONE in a sprint that is ACTIVE now on the reader's boards.
    SPRINT,

    // Tickets that reached DONE in the last 30 days on the reader's boards.
    LAST_30_DAYS
}
