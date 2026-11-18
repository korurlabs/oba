package com.korur.oba.apps.kazan.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.label.dto.response.LabelResponse;
import com.korur.oba.apps.kazan.project.dto.response.ProjectListResponse;
import com.korur.oba.apps.kazan.ticket.enums.MissionRank;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;

/**
 * Response payload for a detailed ticket view.
 */
@Schema(description = "Ticket detail response")
public record TicketDetailResponse(

        // Unique ticket identifier.
        @Schema(description = "Ticket id", nullable = false)
        UUID id,

        // Ticket creation timestamp.
        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        // Ticket update timestamp.
        @Schema(description = "Updated at", nullable = false)
        Instant updatedAt,

        // Ticket title shown in UI.
        @Schema(description = "Ticket title", nullable = false)
        String title,

        // Detailed description of the ticket.
        @Schema(description = "Ticket description", nullable = true)
        String description,

        // Unique ticket key combining board prefix and number (e.g., "PROJ-123").
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

        // Weight used for prioritization.
        @Schema(description = "Weight", nullable = true)
        Integer weight,

        // Due date of the ticket.
        @Schema(description = "Due date", nullable = true)
        Instant dueDate,

        // Root cause analysis for this ticket.
        @Schema(description = "RCA", nullable = true)
        String rca,

        // Lessons learned from this ticket.
        @Schema(description = "Lessons learned", nullable = true)
        String lessonsLearned,

        // User who created the ticket; null when a system actor created it.
        @Schema(description = "Created by", nullable = true)
        TicketCreatedByResponse createdBy,

        // System actor that created the ticket; null when a user created it.
        @Schema(description = "Created by system actor", nullable = true)
        TicketCreatedBySystemActorResponse createdBySystemActor,

        // List of users assigned to this ticket.
        @Schema(description = "Assignees", nullable = true)
        List<TicketAssigneeResponse> assignees,

        // List of users set as reviewers.
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

        // List of sub-tickets.
        @Schema(description = "Sub tickets", nullable = true)
        List<TicketListResponse> subTickets,

        // List of labels attached to this ticket.
        @Schema(description = "Labels", nullable = true)
        List<LabelResponse> labels,

        // Sprint the ticket belongs to. Exactly one of sprint / backlog is set.
        @Schema(description = "Sprint", nullable = true)
        TicketSprintResponse sprint,

        // Backlog the ticket belongs to. Exactly one of sprint / backlog is set.
        @Schema(description = "Backlog", nullable = true)
        TicketBacklogResponse backlog
) {
}
