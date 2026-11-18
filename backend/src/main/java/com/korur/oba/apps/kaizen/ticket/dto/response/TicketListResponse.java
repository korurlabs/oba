package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.label.dto.response.LabelResponse;
import com.korur.oba.apps.kaizen.project.dto.response.ProjectListResponse;
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;

/**
 * Response payload for listing tickets.
 */
@Schema(description = "Ticket list response")
public record TicketListResponse(
    
        // Unique ticket identifier.
        @Schema(description = "Ticket id", nullable = false)
        UUID id,

        // Created at timestamp.
        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        // Ticket title shown in UI.
        @Schema(description = "Ticket title", nullable = false)
        String title,

        // Unique ticket key combining board prefix and number.
        @Schema(description = "Ticket key", nullable = false)
        String key,

        // Display order position within the sprint or backlog.
        @Schema(description = "Display order", nullable = false)
        int displayOrder,

        // Ticket type classification.
        @Schema(description = "Ticket type", nullable = false)
        TicketType type,

        // Current ticket status.
        @Schema(description = "Ticket status", nullable = false)
        TicketStatus status,

        // Ticket priority level.
        @Schema(description = "Ticket priority", nullable = false)
        TicketPriority priority,

        // Mission rank for prioritization within a mission.
        @Schema(description = "Mission rank", nullable = false)
        MissionRank missionRank,

        // Completion percentage of the ticket.
        @Schema(description = "Completion percentage", nullable = false)
        int percentage,

        // Weight for prioritization within a backlog.
        @Schema(description = "Weight", nullable = true)
        Integer weight,

        // Due date of the ticket.
        @Schema(description = "Due date", nullable = true)
        Instant dueDate,

        // User who created the ticket; null when a system actor created it.
        @Schema(description = "Created by", nullable = true)
        TicketCreatedByResponse createdBy,

        // System actor that created the ticket; null when a user created it.
        @Schema(description = "Created by system actor", nullable = true)
        TicketCreatedBySystemActorResponse createdBySystemActor,

        // Assignees of the ticket.
        @Schema(description = "Assignees", nullable = true)
        List<TicketAssigneeResponse> assignees,

        // Reviewers of the ticket.
        @Schema(description = "Reviewers", nullable = true)
        List<TicketReviewerResponse> reviewers,

        // Project the ticket belongs to.
        @Schema(description = "Project", nullable = true)
        ProjectListResponse project,

        // Milestone the ticket counts towards.
        @Schema(description = "Milestone", nullable = true)
        TicketMilestoneResponse milestone,

        // Board the ticket belongs to.
        @Schema(description = "Board", nullable = true)
        TicketBoardResponse board,

        // Parent ticket id of the ticket
        @Schema(description = "Parent ticket id", nullable = true)
        UUID parentTicketId,

        // Sub tickets of the ticket.
        @Schema(description = "Sub tickets", nullable = true)
        List<TicketListResponse> subTickets,

        // Labels of the ticket.
        @Schema(description = "Labels", nullable = true)
        List<LabelResponse> labels,

        // The ticket's own sprint; null when it is in the backlog.
        @Schema(description = "Sprint the ticket is planned in; null in the backlog", nullable = true)
        TicketSprintResponse sprint,

        // Parent of a sub-ticket with the placement of its sub-tickets; null for a top-level ticket.
        @Schema(description = "Parent ticket with the status and placement of its sub-tickets; "
                + "null for a top-level ticket", nullable = true)
        TicketParentResponse parentTicket
    ) {
}
