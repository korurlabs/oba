package com.korur.oba.apps.kazan.ticket.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.label.mapper.LabelMapper;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.project.mapper.ProjectMapper;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketCreateRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketUpdateRequest;
import com.korur.oba.apps.kazan.ticket.dto.response.AllTicketListResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeSummaryResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketDetailResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketListResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketMilestoneResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketParentResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketSubTicketPlacementResponse;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.specification.TicketSearchText;
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;
import com.korur.oba.shrine.identity.mapper.UserMapper;

/**
 * Maps ticket entities to DTOs and applies update requests to entities.
 * <p>
 * Uses LabelMapper for label collection mapping, TicketEmbeddedMapper for
 * nested user relation mappings (assignees, reviewers, comments, createdBy,
 * createdBySystemActor), and BlobUrlRewriterMapper to rewrite blob URLs
 * embedded inside the rich-text description field on the detail response.
 */
@Mapper(componentModel = "spring", uses = {
        LabelMapper.class,
        TicketEmbeddedMapper.class,
        UserMapper.class,
        ProjectMapper.class,
        BlobUrlRewriterMapper.class
}, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface TicketMapper {

    /**
     * Maps a ticket creation request to a new entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "key", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "displayOrder", ignore = true)
    @Mapping(target = "sprint", ignore = true)
    @Mapping(target = "backlog", ignore = true)
    @Mapping(target = "parentTicket", ignore = true)
    @Mapping(target = "subTickets", ignore = true)
    @Mapping(target = "assignees", ignore = true)
    @Mapping(target = "reviewers", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "milestone", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "ticketLabels", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdBySystemActor", ignore = true)
    @Mapping(target = "percentage", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    Ticket toEntity(TicketCreateRequest request);

    /**
     * Maps a ticket entity to a list response DTO. Sprint and parentTicket are mapped by
     * name, so a sub-ticket listed apart from its parent carries the parent's title and
     * the placement of the parent's sub-tickets.
     */
    @Mapping(source = "parentTicket.id", target = "parentTicketId")
    @Mapping(source = "ticketLabels", target = "labels")
    @Mapping(target = "searchMatch", ignore = true)
    TicketListResponse toListResponse(Ticket ticket);

    /**
     * Maps a ticket of a searched list, marking it and each of its sub-tickets, at any depth,
     * with whether its own text contains the search.
     */
    @Named("searched")
    @Mapping(source = "parentTicket.id", target = "parentTicketId")
    @Mapping(source = "ticketLabels", target = "labels")
    @Mapping(target = "subTickets", qualifiedByName = "searchedList")
    @Mapping(target = "searchMatch", expression = "java(search.matches(ticket))")
    TicketListResponse toListResponse(Ticket ticket, @Context TicketSearchText search);

    /**
     * Maps the sub-tickets of a searched list.
     */
    @Named("searchedList")
    @IterableMapping(qualifiedByName = "searched")
    List<TicketListResponse> toListResponses(List<Ticket> tickets, @Context TicketSearchText search);

    /**
     * Maps the parent of a listed sub-ticket, with the status and placement of each of
     * its direct sub-tickets.
     */
    TicketParentResponse toParentResponse(Ticket parentTicket);

    /**
     * Maps a sub-ticket to its status and placement.
     */
    TicketSubTicketPlacementResponse toSubTicketPlacementResponse(Ticket subTicket);

    /**
     * Maps a ticket entity to an all-ticket list response DTO including sprint and backlog.
     */
    @Mapping(source = "parentTicket.id", target = "parentTicketId")
    @Mapping(source = "ticketLabels", target = "labels")
    AllTicketListResponse toAllTicketListResponse(Ticket ticket);

    /**
     * Maps a ticket of a searched all-ticket list, marking each of its sub-tickets with
     * whether its own text contains the search.
     */
    @Mapping(source = "parentTicket.id", target = "parentTicketId")
    @Mapping(source = "ticketLabels", target = "labels")
    @Mapping(target = "subTickets", qualifiedByName = "searchedList")
    AllTicketListResponse toAllTicketListResponse(Ticket ticket, @Context TicketSearchText search);

    /**
     * Maps the open work counts to the home page summary.
     */
    @Mapping(source = "openIncidents", target = "openIncidents")
    @Mapping(source = "openSprintTickets", target = "openSprintTickets")
    HomeSummaryResponse toHomeSummaryResponse(long openIncidents, long openSprintTickets);

    /**
     * Maps a board to the entry of the home page's focus board select.
     */
    BoardListResponse toHomeBoardResponse(Board board);

    /**
     * Maps a ticket entity to a detail response DTO.
     * <p>
     * Assignees, reviewers, comments, createdBy and createdBySystemActor are
     * mapped via dedicated mapping methods that resolve the nested entities.
     * The description field is rich-text HTML that may embed blob URLs;
     * BlobUrlRewriterMapper rewrites each embedded blob URL into a SAS-signed
     * URL before the response leaves the backend.
     * <p>
     * Sprint and backlog are mapped by name onto TicketSprintResponse and
     * TicketBacklogResponse, exactly as toAllTicketListResponse does, so the
     * detail response carries the ticket's placement for sub-tickets too --
     * those never appear in the cross-board list.
     */
    @Mapping(source = "parentTicket.id", target = "parentTicketId")
    @Mapping(source = "ticketLabels", target = "labels")
    @Mapping(source = "description", target = "description", qualifiedByName = "rewriteHtmlBlobUrls")
    TicketDetailResponse toDetailResponse(Ticket ticket);

    /**
     * Updates an existing ticket entity from an update request.
     * <p>
     * Relational fields (sprint, backlog, project, milestone) referenced by ID in the request
     * are ignored here and must be resolved manually in the service layer.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "key", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "displayOrder", ignore = true)
    @Mapping(target = "sprint", ignore = true)
    @Mapping(target = "backlog", ignore = true)
    @Mapping(target = "parentTicket", ignore = true)
    @Mapping(target = "subTickets", ignore = true)
    @Mapping(target = "assignees", ignore = true)
    @Mapping(target = "reviewers", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "milestone", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "ticketLabels", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdBySystemActor", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Ticket ticket, TicketUpdateRequest request);

    /**
     * Maps the ticket's milestone to its embedded response.
     * MapStruct applies it to the milestone property of every ticket response.
     */
    @Mapping(source = "board.id", target = "boardId")
    TicketMilestoneResponse toMilestoneResponse(Milestone milestone);
}
