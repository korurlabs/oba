package com.korur.oba.apps.kazan.ticket.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.dojo.dto.QuerySortRequest;

/**
 * Query request for filtering tickets across all boards the user is a member of.
 * <p>
 * Extends QuerySortRequest to inherit sorting fields (sortBy, sortDirection).
 * Adds ticket-specific filters for search, status, type, priority, assignees,
 * reviewers, and an optional board subset filter.
 */
@Getter
@Setter
@NoArgsConstructor
public class AllTicketQueryRequest extends QuerySortRequest {

    /**
     * Search keyword to match against ticket title or key. A listed ticket
     * also matches when a sub-ticket nested under it does.
     * Null or blank means no search filter.
     */
    @Schema(description = "Search keyword to match against ticket title or key; "
            + "a listed ticket also matches when a sub-ticket nested under it does")
    private String search;

    /**
     * Filter tickets by statuses.
     * Null means no status filter applied.
     */
    @Schema(description = "Filter tickets by statuses")
    private List<TicketStatus> statuses;

    /**
     * Filter tickets by types.
     * Null means no type filter applied.
     */
    @Schema(description = "Filter tickets by types")
    private List<TicketType> types;

    /**
     * Filter tickets by priorities.
     * Null means no priority filter applied.
     */
    @Schema(description = "Filter tickets by priorities")
    private List<TicketPriority> priorities;

    /**
     * Filter tickets by assignee identifiers.
     * Null means no assignee filter applied.
     */
    @Schema(description = "Filter tickets by assignee identifiers")
    private List<UUID> assigneeIds;

    /**
     * Filter tickets by reviewer identifiers.
     * Null means no reviewer filter applied.
     */
    @Schema(description = "Filter tickets by reviewer identifiers")
    private List<UUID> reviewerIds;

    /**
     * Filter tickets by the users who created them.
     * Null means no creator filter applied. Tickets created by a system actor
     * have no user creator and never match.
     */
    @Schema(description = "Filter tickets by the identifiers of the users who created them")
    private List<UUID> createdByIds;

    /**
     * Optional subset of board identifiers to filter tickets.
     * Null or empty means all boards the user is a member of.
     */
    @Schema(description = "Optional subset of board identifiers to filter tickets")
    private List<UUID> boardIds;

    /**
     * Filter tickets by milestone identifier, sub-tickets included so the list
     * matches the milestone's counts.
     * Null means no milestone filter applied.
     */
    @Schema(description = "Filter tickets by milestone identifier (sub-tickets included)")
    private UUID milestoneId;

    /**
     * Filter tickets by label identifiers.
     * Null means no label filter applied.
     */
    @Schema(description = "Filter tickets by label identifiers")
    private List<UUID> labelIds;

    /**
     * Filter tickets with due date on or after this date (inclusive).
     * Null means no lower-bound due date filter applied.
     */
    @Schema(description = "Filter tickets with due date on or after this date (inclusive)")
    private LocalDate dueDateStart;

    /**
     * Filter tickets with due date on or before this date (inclusive).
     * Null means no upper-bound due date filter applied.
     */
    @Schema(description = "Filter tickets with due date on or before this date (inclusive)")
    private LocalDate dueDateEnd;
}
