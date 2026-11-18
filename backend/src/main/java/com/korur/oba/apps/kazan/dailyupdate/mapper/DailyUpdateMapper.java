package com.korur.oba.apps.kazan.dailyupdate.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateCreateRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateUpdateRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateFinishedTicketResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateMissingMemberResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateMissingResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdatePreviewResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateTicketResponse;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdateTicket;
import com.korur.oba.apps.kazan.dailyupdate.helper.DailyUpdateDeadlineResolver.ReportDay;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.MissionRank;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.mapper.TicketEmbeddedMapper;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;

/**
 * Maps daily updates and their ticket lines between requests, entities and responses.
 *
 * Blank focus, questions and notes are stored as null, so an empty field reads
 * the same whether the client sent null, an empty string or only whitespace.
 * The focus is also stored stripped of surrounding whitespace; questions and
 * notes are kept as written.
 * The author, the line collection, the per-reader canOpen flag and Mission
 * Rank, the finished tickets and the summary text are set by
 * {@link DailyUpdateMapperHelper}; editedAt, progressSynced and the
 * organization are set by the services. The organization is fixed at create
 * time, so an edit never moves an update to another channel. The chat entry
 * of a new update is built here as an ordinary chat send request, which the
 * chat validates like any message.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = { TicketEmbeddedMapper.class })
public interface DailyUpdateMapper {

    /**
     * Maps a create request to a new update without author or lines.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "editedAt", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "focus", source = "focus", qualifiedByName = "stripToNull")
    @Mapping(target = "questions", source = "questions", qualifiedByName = "blankToNull")
    DailyUpdate toEntity(DailyUpdateCreateRequest request);

    /**
     * Builds the chat send request of a new update's entry in the daily
     * updates channel. The entry has no text, because the card of the update
     * is its content, and no thread or quote. The update ID is the client
     * nonce: the server sends the entry, so no pending row waits for it, and
     * the ID is unique per entry.
     *
     * @param dailyUpdate the saved update
     * @param channelId   the organization's daily updates channel
     */
    @Mapping(target = "content", ignore = true)
    @Mapping(target = "channelId", source = "channelId")
    @Mapping(target = "threadId", ignore = true)
    @Mapping(target = "replyToMessageId", ignore = true)
    @Mapping(target = "dailyUpdateId", source = "dailyUpdate.id")
    @Mapping(target = "pullRequestShareId", ignore = true)
    @Mapping(target = "clientNonce", expression = "java(dailyUpdate.getId().toString())")
    ChatMessageCreateRequest toChatMessageCreateRequest(DailyUpdate dailyUpdate, UUID channelId);

    /**
     * Replaces focus and questions with the edit request's values; the request
     * refuses a blank focus, and null or blank questions clear them. Lines are
     * applied by DailyUpdateMapperHelper.setUpdateRelations.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "editedAt", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "focus", source = "focus", qualifiedByName = "stripToNull")
    @Mapping(target = "questions", source = "questions", qualifiedByName = "blankToNull")
    void updateEntity(@MappingTarget DailyUpdate dailyUpdate, DailyUpdateUpdateRequest request);

    /**
     * Builds a new line: the ticket's key, title, board ID and board name are
     * copied as a snapshot, the percentage and note come from the request line.
     * The line is not attached to an update.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "dailyUpdate", ignore = true)
    @Mapping(target = "progressSynced", ignore = true)
    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "boardId", source = "ticket.board.id")
    @Mapping(target = "boardName", source = "ticket.board.name")
    @Mapping(target = "ticketKey", source = "ticket.key")
    @Mapping(target = "ticketTitle", source = "ticket.title")
    @Mapping(target = "percentage", source = "line.percentage")
    @Mapping(target = "note", source = "line.note", qualifiedByName = "blankToNull")
    @Mapping(target = "position", source = "position")
    DailyUpdateTicket toItemEntity(DailyUpdateTicketRequest line, Ticket ticket, int position);

    /**
     * Applies an edit to a kept line in place: percentage, note and position
     * change, the snapshot and progressSynced stay.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "dailyUpdate", ignore = true)
    @Mapping(target = "progressSynced", ignore = true)
    @Mapping(target = "ticketId", ignore = true)
    @Mapping(target = "boardId", ignore = true)
    @Mapping(target = "boardName", ignore = true)
    @Mapping(target = "ticketKey", ignore = true)
    @Mapping(target = "ticketTitle", ignore = true)
    @Mapping(target = "percentage", source = "line.percentage")
    @Mapping(target = "note", source = "line.note", qualifiedByName = "blankToNull")
    @Mapping(target = "position", source = "position")
    void updateItemEntity(@MappingTarget DailyUpdateTicket item, DailyUpdateTicketRequest line, int position);

    /**
     * Builds a detached copy of a kept line with an edit applied, for an edit
     * preview that must leave the stored line untouched. progressSynced starts
     * false, because in a preview it tells whether this request would sync.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "dailyUpdate", ignore = true)
    @Mapping(target = "progressSynced", ignore = true)
    @Mapping(target = "ticketId", source = "item.ticketId")
    @Mapping(target = "boardId", source = "item.boardId")
    @Mapping(target = "boardName", source = "item.boardName")
    @Mapping(target = "ticketKey", source = "item.ticketKey")
    @Mapping(target = "ticketTitle", source = "item.ticketTitle")
    @Mapping(target = "percentage", source = "line.percentage")
    @Mapping(target = "note", source = "line.note", qualifiedByName = "blankToNull")
    @Mapping(target = "position", source = "position")
    DailyUpdateTicket toPreviewItem(DailyUpdateTicket item, DailyUpdateTicketRequest line, int position);

    /**
     * Maps a line with the reader's canOpen flag and the ticket's current Mission Rank and priority.
     *
     * @param item        the stored or preview line
     * @param canOpen     whether the reader can open the line's ticket
     * @param missionRank the ticket's rank now; null when the reader cannot open it
     * @param priority    the ticket's priority now; null when the reader cannot open it
     */
    @Mapping(target = "ticketId", source = "item.ticketId")
    @Mapping(target = "boardId", source = "item.boardId")
    @Mapping(target = "boardName", source = "item.boardName")
    @Mapping(target = "ticketKey", source = "item.ticketKey")
    @Mapping(target = "ticketTitle", source = "item.ticketTitle")
    @Mapping(target = "percentage", source = "item.percentage")
    @Mapping(target = "note", source = "item.note")
    @Mapping(target = "progressSynced", source = "item.progressSynced")
    @Mapping(target = "canOpen", source = "canOpen")
    @Mapping(target = "missionRank", source = "missionRank")
    @Mapping(target = "priority", source = "priority")
    DailyUpdateTicketResponse toTicketResponse(DailyUpdateTicket item, boolean canOpen, MissionRank missionRank,
                                               TicketPriority priority);

    /**
     * Maps a ticket the author finished in an active sprint, as it is now.
     */
    @Mapping(target = "ticketId", source = "id")
    @Mapping(target = "boardId", source = "board.id")
    @Mapping(target = "boardName", source = "board.name")
    @Mapping(target = "ticketKey", source = "key")
    @Mapping(target = "ticketTitle", source = "title")
    @Mapping(target = "missionRank", source = "missionRank")
    @Mapping(target = "priority", source = "priority")
    DailyUpdateFinishedTicketResponse toFinishedTicketResponse(Ticket ticket);

    /**
     * Maps finished tickets in the given order.
     */
    List<DailyUpdateFinishedTicketResponse> toFinishedTicketResponses(List<Ticket> tickets);

    /**
     * Maps an update with its mapped lines, the author's finished tickets and
     * the summary text.
     */
    @Mapping(target = "id", source = "dailyUpdate.id")
    @Mapping(target = "createdAt", source = "dailyUpdate.createdAt")
    @Mapping(target = "editedAt", source = "dailyUpdate.editedAt")
    @Mapping(target = "author", source = "dailyUpdate.author")
    @Mapping(target = "focus", source = "dailyUpdate.focus")
    @Mapping(target = "questions", source = "dailyUpdate.questions")
    @Mapping(target = "items", source = "items")
    @Mapping(target = "finishedTickets", source = "finishedTickets")
    @Mapping(target = "summaryText", source = "summaryText")
    DailyUpdateResponse toResponse(DailyUpdate dailyUpdate, List<DailyUpdateTicketResponse> items,
                                   List<DailyUpdateFinishedTicketResponse> finishedTickets, String summaryText);

    /**
     * Maps a preview: its lines, the author's finished tickets, focus and
     * questions in their stored form, and the summary text.
     */
    @Mapping(target = "items", source = "items")
    @Mapping(target = "finishedTickets", source = "finishedTickets")
    @Mapping(target = "focus", source = "focus", qualifiedByName = "stripToNull")
    @Mapping(target = "questions", source = "questions", qualifiedByName = "blankToNull")
    @Mapping(target = "summaryText", source = "summaryText")
    DailyUpdatePreviewResponse toPreviewResponse(List<DailyUpdateTicketResponse> items,
                                                 List<DailyUpdateFinishedTicketResponse> finishedTickets,
                                                 String focus, String questions, String summaryText);

    /**
     * Maps an expected member who has no update before the deadline.
     *
     * @param member   the member's name and email
     * @param postedAt time of the member's first update of the day, after the
     *                 deadline; null when the member has not posted
     */
    @Mapping(target = "userId", source = "member.userId")
    @Mapping(target = "email", source = "member.email")
    @Mapping(target = "firstName", source = "member.firstName")
    @Mapping(target = "lastName", source = "member.lastName")
    @Mapping(target = "postedAt", source = "postedAt")
    DailyUpdateMissingMemberResponse toMissingMemberResponse(OrganizationMemberSummaryProjection member,
                                                             Instant postedAt);

    /**
     * Maps the not-posted report of one organization and day.
     *
     * @param organizationId the reported organization
     * @param day            the resolved day
     * @param expectedCount  number of members expected to post that day
     * @param members        expected members without an update before the deadline, in display order
     */
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "date", source = "day.date")
    @Mapping(target = "deadline", source = "day.deadline")
    @Mapping(target = "workday", source = "day.workday")
    @Mapping(target = "deadlinePassed", source = "day.deadlinePassed")
    @Mapping(target = "expectedCount", source = "expectedCount")
    @Mapping(target = "members", source = "members")
    DailyUpdateMissingResponse toMissingResponse(UUID organizationId, ReportDay day, int expectedCount,
                                                 List<DailyUpdateMissingMemberResponse> members);

    /**
     * Turns a blank text into null and keeps any other text as written.
     */
    @Named("blankToNull")
    default String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Turns a blank text into null and strips surrounding whitespace from any
     * other text, keeping its inner line breaks. Blank follows String.isBlank,
     * the rule NotBlank applies to the request.
     */
    @Named("stripToNull")
    default String stripToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
