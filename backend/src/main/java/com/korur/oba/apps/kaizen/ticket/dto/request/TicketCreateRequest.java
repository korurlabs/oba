package com.korur.oba.apps.kaizen.ticket.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * Request payload used to create a new ticket.
 */
@Schema(description = "Ticket create request")
public record TicketCreateRequest(

        // User-provided ticket title for display and lookup.
        @Schema(description = "Ticket title", nullable = false)
        @NotBlank(message = "Ticket title is required")
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String title,

        // Optional detailed description of the ticket.
        @Schema(description = "Ticket description", nullable = true)
        @Size(max = 10000, message = "Description must be at most 10000 characters")
        String description,

        // Ticket type classification; INCIDENT uses the P1 to P3 priorities, every other type LOWEST to HIGHEST.
        @Schema(description = "Ticket type: BUG, FEATURE, TECH_DEBT, IMPROVEMENT, INCIDENT, PROBLEM or REFACTOR. TASK is a deprecated alias of FEATURE and is stored as FEATURE", nullable = false)
        @NotNull(message = "Ticket type is required")
        TicketType type,

        // Ticket priority level.
        @Schema(description = "Ticket priority", nullable = false)
        @NotNull(message = "Ticket priority is required")
        TicketPriority priority,

        // Ticket status level.
        @Schema(description = "Ticket status: TODO, IN_PROGRESS, WAITING_FOR_THIRD_PARTY, IN_REVIEW or DONE", nullable = false)
        @NotNull(message = "Ticket status is required")
        TicketStatus status,

        // Ticket weight for prioritization.
        @Schema(description = "Ticket weight", nullable = true)
        Integer weight,

        // Mission rank, the size estimate every ticket carries.
        @Schema(description = "Mission rank: XS, S, M, L, XL or XXL", nullable = false)
        @NotNull(message = "Mission rank is required")
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

        // Sprint identifier to assign this ticket to.
        @Schema(description = "Sprint id", nullable = true)
        UUID sprintId,

        // Backlog identifier to assign this ticket to.
        @Schema(description = "Backlog id", nullable = true)
        UUID backlogId,

        // Parent ticket identifier for sub-task relationships.
        @Schema(description = "Parent ticket id", nullable = true)
        UUID parentTicketId,

        // Project identifier for project grouping.
        @Schema(description = "Project id", nullable = true)
        UUID projectId,

        // Milestone to attach; it must live on this board or a parent, child or sibling board.
        @Schema(description = "Milestone id", nullable = true, example = "3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70")
        UUID milestoneId,

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
