package com.korur.oba.apps.kaizen.milestone.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.milestone.dto.response.HomeDeadlineResponse;
import com.korur.oba.apps.kaizen.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.apps.kaizen.milestone.dto.response.MilestoneListResponse;
import com.korur.oba.apps.kaizen.milestone.dto.response.RoadmapResponse;
import com.korur.oba.apps.kaizen.milestone.enums.MilestoneStatus;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for milestones and the board roadmap.
 */
public interface MilestoneQueryService {

    /**
     * Lists the board's milestones, optionally with those of related boards the
     * user is a member of, sorted by target date.
     */
    List<MilestoneListResponse> listMilestones(User user, UUID organizationId, UUID boardId,
            MilestoneStatus status, boolean includeRelatedBoards);

    /**
     * Retrieves a milestone of the board.
     */
    MilestoneDetailResponse getMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId);

    /**
     * Builds the board roadmap: the board's lane, then optionally one lane per
     * direct child board the user is a member of, limited to the window.
     */
    RoadmapResponse getRoadmap(
            User user, UUID organizationId, UUID boardId, boolean includeChildBoards, LocalDate from, LocalDate to);

    /**
     * Lists the upcoming deadlines of the home page: the OPEN milestones on the boards
     * where the user is a member whose target date has passed or falls within
     * {@link com.korur.oba.apps.kaizen.ticket.constant.HomeDashboardConstant#DEADLINE_WINDOW_DAYS}
     * days, by target date, then name, capped at
     * {@link com.korur.oba.apps.kaizen.ticket.constant.HomeDashboardConstant#DEADLINE_LIMIT}.
     *
     * @param userId         the user whose board memberships scope the milestones
     * @param organizationId the organization of the request; only its boards are read
     * @return the deadlines; empty when the user is a member of no board
     */
    List<HomeDeadlineResponse> listHomeDeadlines(UUID userId, UUID organizationId);
}
