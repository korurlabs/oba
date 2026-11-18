package com.korur.oba.apps.kaizen.ticket.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One assignment of a finished ticket: the ticket's size, the assignee and how many
 * assignees the ticket has, so its points can be split between them. The mission rank
 * is read as text.
 */
public interface LeaderboardTicketProjection {

    UUID getTicketId();

    String getMissionRank();

    UUID getUserId();

    String getFirstName();

    String getLastName();

    String getEmail();

    Long getAssigneeCount();
}
