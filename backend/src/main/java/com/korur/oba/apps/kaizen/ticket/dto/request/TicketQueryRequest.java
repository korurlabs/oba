package com.korur.oba.apps.kaizen.ticket.dto.request;

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
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.dojo.dto.QuerySortRequest;

/**
 * Query request for filtering and paginating tickets.
 * <p>
 * Extends QueryRequest to inherit pagination fields (page, amount, sortBy, sortDirection).
 * Adds ticket-specific filters for search, status, type, priority, sprint, backlog, and project.
 */
@Getter
@Setter
@NoArgsConstructor
public class TicketQueryRequest extends QuerySortRequest {

    /**
     * Search keyword to match against ticket title or key.
     * Null or blank means no search filter.
     */
    @Schema(description = "Search keyword to match against ticket title or key")
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
     * Filter tickets by sprint identifier.
     * Null means no sprint filter applied.
     */
    @Schema(description = "Filter tickets by sprint identifier")
    private UUID sprintId;

    /**
     * Filter tickets by backlog identifier.
     * Null means no backlog filter applied.
     */
    @Schema(description = "Filter tickets by backlog identifier")
    private UUID backlogId;

    /**
     * Filter tickets by project identifier.
     * Null means no project filter applied.
     */
    @Schema(description = "Filter tickets by project identifier")
    private UUID projectId;

    /**
     * Filter tickets by milestone identifier, sub-tickets included so the list
     * matches the milestone's counts.
     * Null means no milestone filter applied.
     */
    @Schema(description = "Filter tickets by milestone identifier (sub-tickets included)")
    private UUID milestoneId;

    /**
     * Include all boards in the response.
     * true means no board filter applied.
     * false or null means provided board will be used as filter.
     */
    @Schema(description = "Include all boards in the response")
    private Boolean includeAllBoards;

    /**
     * Optional subset of board identifiers to filter tickets.
     * Only boards the list already covers are kept: the current board, plus its
     * related boards when includeAllBoards is true. An identifier outside them is
     * ignored, so this filter never widens access.
     * Null or empty means no board subset filter applied.
     */
    @Schema(description = "Optional subset of board identifiers to filter tickets, within the boards the list covers")
    private List<UUID> boardIds;

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
