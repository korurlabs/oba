package com.korur.oba.apps.kaizen.milestone.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Ticket counts of one milestone: all non-deleted attached tickets and those with status DONE.
 */
public interface MilestoneTicketCountProjection {

    UUID getMilestoneId();

    Long getTotalCount();

    Long getDoneCount();
}
