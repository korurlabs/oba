package com.korur.oba.apps.kaizen.ticket.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to update ticket metadata.
 */
@Schema(description = "Ticket update request")
public record TicketUpdateRequest(

        // Updated ticket title if provided.
        @Schema(description = "Ticket title", nullable = true)
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String title,

        // Updated description if provided.
        @Schema(description = "Ticket description", nullable = true)
        @Size(max = 10000, message = "Description must be at most 10000 characters")
        String description,

        // Updated ticket type if provided.
        @Schema(description = "Ticket type: BUG, FEATURE, TECH_DEBT, IMPROVEMENT, INCIDENT, PROBLEM or REFACTOR. TASK is a deprecated alias of FEATURE and is stored as FEATURE", nullable = true)
        TicketType type,

        // Updated ticket status if provided.
        @Schema(description = "Ticket status: TODO, IN_PROGRESS, WAITING_FOR_THIRD_PARTY, IN_REVIEW or DONE", nullable = true)
        TicketStatus status,

        // Updated ticket priority if provided.
        @Schema(description = "Ticket priority", nullable = true)
        TicketPriority priority,

        // Updated project assignment if provided.
        @Schema(description = "Project id", nullable = true)
        UUID projectId,

        // Milestone to attach if provided; it must live on this board or a parent, child or sibling board.
        @Schema(description = "Milestone id", nullable = true, example = "3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70")
        UUID milestoneId,

        // True detaches the milestone; wins over milestoneId.
        @Schema(description = "Detach the milestone; wins over milestoneId", nullable = true, example = "false")
        Boolean clearMilestone,

        // Parent ticket identifier for sub-task relationships.
        @Schema(description = "Parent ticket id", nullable = true)
        UUID parentTicketId,

        // True detaches the parent ticket; wins over parentTicketId. A null parentTicketId alone keeps the parent.
        @Schema(description = "Detach the parent ticket; wins over parentTicketId", nullable = true, example = "false")
        Boolean clearParent,

        // Updated completion percentage if provided.
        @Schema(description = "Completion percentage", nullable = true)
        Integer percentage,

        // Updated weight if provided.
        @Schema(description = "Weight", nullable = true)
        Integer weight,

        // Updated mission rank; null keeps the current one.
        @Schema(description = "Mission rank", nullable = true)
        MissionRank missionRank,

        // Root cause analysis for this ticket.
        @Schema(description = "RCA", nullable = true)
        @Size(max = 10000, message = "RCA must be at most 10000 characters")
        String rca,

        // Lessons learned from this ticket.
        @Schema(description = "Lessons learned", nullable = true)
        @Size(max = 10000, message = "Lessons learned must be at most 10000 characters")
        String lessonsLearned,

        // Due date for the ticket.
        @Schema(description = "Due date", nullable = true)
        Instant dueDate,

        // List of user identifiers to assign to this ticket.
        @Schema(description = "Assignee user ids", nullable = true)
        List<UUID> assigneeIds,

        // List of user identifiers to set as reviewers.
        @Schema(description = "Reviewer user ids", nullable = true)
        List<UUID> reviewerIds,

        // List of label identifiers to attach to this ticket.
        @Schema(description = "Label ids", nullable = true)
        List<UUID> labelIds
) {
}
