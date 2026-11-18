package com.korur.oba.apps.kaizen.milestone.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneCreateRequest;
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneTicketsRequest;
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneUpdateRequest;
import com.korur.oba.apps.kaizen.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for milestones.
 */
public interface MilestoneService {

    /**
     * Creates a milestone on a board. Requires ADMIN on the board.
     */
    MilestoneDetailResponse createMilestone(
            User user, UUID organizationId, UUID boardId, MilestoneCreateRequest request);

    /**
     * Updates a milestone. Omitted fields are unchanged; the clear flags set fields to null.
     */
    MilestoneDetailResponse updateMilestone(
            User user, UUID organizationId, UUID boardId, UUID milestoneId, MilestoneUpdateRequest request);

    /**
     * Closes an open milestone.
     */
    MilestoneDetailResponse closeMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId);

    /**
     * Reopens a closed milestone.
     */
    MilestoneDetailResponse reopenMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId);

    /**
     * Attaches tickets to the milestone. Requires ADMIN on the milestone's board and
     * MEMBER on each ticket's board; each ticket's board must relate to the milestone's.
     */
    MilestoneDetailResponse linkTickets(
            User user, UUID organizationId, UUID boardId, UUID milestoneId, MilestoneTicketsRequest request);

    /**
     * Detaches tickets from the milestone. Tickets attached to another milestone are left alone.
     */
    MilestoneDetailResponse unlinkTickets(
            User user, UUID organizationId, UUID boardId, UUID milestoneId, MilestoneTicketsRequest request);

    /**
     * Makes the milestone depend on another one. Requires ADMIN on the milestone's
     * board and membership on the other milestone's board; the boards must be
     * related and the dependency must not close a cycle. Adding an existing
     * pair changes nothing.
     */
    MilestoneDetailResponse addDependency(
            User user, UUID organizationId, UUID boardId, UUID milestoneId, UUID dependsOnId);

    /**
     * Removes the dependency of the milestone on another one, with the same access as adding it.
     */
    MilestoneDetailResponse removeDependency(
            User user, UUID organizationId, UUID boardId, UUID milestoneId, UUID dependsOnId);

    /**
     * Detaches every ticket from the milestone and soft-deletes it.
     */
    void deleteMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId);

    /**
     * Restores a soft-deleted milestone. Ticket links are not restored.
     */
    void restoreMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId);

    /**
     * Detaches every ticket from the board's milestones and soft-deletes them.
     * Called by board deletion, which has already checked access.
     */
    void deleteBoardMilestones(UUID boardId);

    /**
     * Restores the milestones the board delete removed, without their ticket links.
     * Milestones deleted one by one before the board stay deleted.
     * Called by board restore, which has already checked access.
     */
    void restoreBoardMilestones(UUID boardId);
}
