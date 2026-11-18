package com.korur.oba.apps.kaizen.dailyupdate.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateFinishedTicketResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdatePreviewResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateTicketResponse;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdateTicket;
import com.korur.oba.apps.kaizen.dailyupdate.helper.DailyUpdateSummaryRenderer;
import com.korur.oba.apps.kaizen.dailyupdate.mock_data.DailyUpdateMocks;
import com.korur.oba.apps.kaizen.dailyupdate.validator.DailyUpdateValidator;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class DailyUpdateMapperHelperUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private TicketQueryService ticketQueryService;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    private DailyUpdateMapperHelper helper;
    private User reader;
    private Board board;

    @BeforeEach
    void setUp() {
        DailyUpdateValidator validator = new DailyUpdateValidator(null, new LoggingService());
        helper = new DailyUpdateMapperHelper(ticketQueryService,
                new DailyUpdateMapperImpl(new TicketEmbeddedMapperImpl()), new DailyUpdateSummaryRenderer(),
                validator, organizationMemberQueryService);
        reader = DailyUpdateMocks.user();
        lenient().when(organizationMemberQueryService.findMember(organizationId, reader.getId()))
                .thenReturn(Optional.of(DailyUpdateMocks.membership(OrganizationStatus.ACTIVE,
                        OrganizationMemberStatus.ACTIVE)));
        board = DailyUpdateMocks.board("Member board");
    }

    private Ticket rankedTicket(String key, MissionRank rank) {
        Ticket ticket = DailyUpdateMocks.ticket(board, key, "Title " + key, 50);
        ticket.setMissionRank(rank);
        return ticket;
    }

    private Ticket prioritizedTicket(String key, TicketPriority priority) {
        Ticket ticket = DailyUpdateMocks.ticket(board, key, "Title " + key, 50);
        ticket.setPriority(priority);
        return ticket;
    }

    @Test
    void toResponses_openableTicket_carriesTicketPriority() {
        // Arrange
        User ada = DailyUpdateMocks.user();
        Ticket urgent = prioritizedTicket("B-1", TicketPriority.P1);
        Ticket low = prioritizedTicket("B-2", TicketPriority.LOW);
        DailyUpdate update = DailyUpdateMocks.dailyUpdate(ada, "focus", null,
                DailyUpdateMocks.item(urgent, 40, null, 0), DailyUpdateMocks.item(low, 60, null, 1));
        update.setOrganizationId(organizationId);
        when(ticketQueryService.getAccessibleTickets(any(), eq(organizationId), any()))
                .thenReturn(List.of(urgent, low));
        when(ticketQueryService.getDoneActiveSprintTicketsByAssignees(any(), eq(organizationId), any())).thenReturn(Map.of());

        // Act
        DailyUpdateResponse response = helper.toResponses(List.of(update), reader.getId()).getFirst();

        // Assert
        assertThat(response.items()).extracting(DailyUpdateTicketResponse::priority)
                .containsExactly(TicketPriority.P1, TicketPriority.LOW);
    }

    @Test
    void toResponses_unopenableAndRemovedTickets_havePriorityNull() {
        // Arrange
        User ada = DailyUpdateMocks.user();
        Ticket openable = prioritizedTicket("B-1", TicketPriority.HIGH);
        Ticket hidden = prioritizedTicket("B-2", TicketPriority.HIGHEST);
        DailyUpdate update = DailyUpdateMocks.dailyUpdate(ada, "focus", null,
                DailyUpdateMocks.item(openable, 40, null, 0),
                DailyUpdateMocks.item(hidden, 60, null, 1),
                DailyUpdateMocks.ticketlessItem("B-3", "Removed", 10, 2));
        update.setOrganizationId(organizationId);
        when(ticketQueryService.getAccessibleTickets(any(), eq(organizationId), any())).thenReturn(List.of(openable));
        when(ticketQueryService.getDoneActiveSprintTicketsByAssignees(any(), eq(organizationId), any())).thenReturn(Map.of());

        // Act
        DailyUpdateResponse response = helper.toResponses(List.of(update), reader.getId()).getFirst();

        // Assert
        assertThat(response.items()).extracting(DailyUpdateTicketResponse::priority)
                .containsExactly(TicketPriority.HIGH, null, null);
    }

    @Test
    void toResponses_finishedTickets_carryTicketPriority() {
        // Arrange
        User ada = DailyUpdateMocks.user();
        DailyUpdate update = DailyUpdateMocks.dailyUpdate(ada, "focus", null);
        update.setOrganizationId(organizationId);
        Ticket finished = prioritizedTicket("B-9", TicketPriority.P2);
        when(ticketQueryService.getDoneActiveSprintTicketsByAssignees(any(), eq(organizationId), any()))
                .thenReturn(Map.of(ada.getId(), List.of(finished)));

        // Act
        DailyUpdateResponse response = helper.toResponses(List.of(update), reader.getId()).getFirst();

        // Assert
        assertThat(response.finishedTickets()).extracting(DailyUpdateFinishedTicketResponse::priority)
                .containsExactly(TicketPriority.P2);
    }

    @Test
    void toPreviewResponse_linesAndFinishedTickets_carryTicketPriority() {
        // Arrange
        Ticket lineTicket = prioritizedTicket("B-1", TicketPriority.LOWEST);
        Ticket finished = prioritizedTicket("B-7", TicketPriority.HIGH);
        DailyUpdateTicket line = DailyUpdateMocks.item(lineTicket, 70, null, 0);
        when(ticketQueryService.getAccessibleTickets(any(), eq(organizationId), any())).thenReturn(List.of(lineTicket));
        when(ticketQueryService.getDoneActiveSprintTicketsByAssignees(reader.getId(), organizationId, List.of(reader.getId())))
                .thenReturn(Map.of(reader.getId(), List.of(finished)));

        // Act
        DailyUpdatePreviewResponse response = helper.toPreviewResponse(List.of(line), "focus", null, organizationId,
                reader.getId());

        // Assert
        assertThat(response.items().getFirst().priority()).isEqualTo(TicketPriority.LOWEST);
        assertThat(response.finishedTickets().getFirst().priority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    void toResponses_severalUpdates_loadsFinishedTicketsOnce() {
        // Arrange
        User ada = DailyUpdateMocks.user();
        User alan = DailyUpdateMocks.user();
        DailyUpdate first = DailyUpdateMocks.dailyUpdate(ada, "focus", null);
        DailyUpdate second = DailyUpdateMocks.dailyUpdate(alan, "focus", null);
        DailyUpdate third = DailyUpdateMocks.dailyUpdate(ada, "focus", null);
        first.setOrganizationId(organizationId);
        second.setOrganizationId(organizationId);
        third.setOrganizationId(organizationId);
        Ticket finished = rankedTicket("B-9", MissionRank.XL);
        when(ticketQueryService.getDoneActiveSprintTicketsByAssignees(any(), eq(organizationId), any()))
                .thenReturn(Map.of(ada.getId(), List.of(finished)));

        // Act
        List<DailyUpdateResponse> responses = helper.toResponses(List.of(first, second, third), reader.getId());

        // Assert
        verify(ticketQueryService, times(1)).getDoneActiveSprintTicketsByAssignees(reader.getId(),
                organizationId,
                Set.of(ada.getId(), alan.getId()));
        assertThat(responses).hasSize(3);
        assertThat(responses.get(0).finishedTickets()).extracting(DailyUpdateFinishedTicketResponse::ticketKey)
                .containsExactly("B-9");
        assertThat(responses.get(0).finishedTickets().getFirst().missionRank()).isEqualTo(MissionRank.XL);
        assertThat(responses.get(2).finishedTickets()).hasSize(1);
    }

    @Test
    void toResponses_authorWithoutFinished_setsEmptyList() {
        // Arrange
        User ada = DailyUpdateMocks.user();
        DailyUpdate update = DailyUpdateMocks.dailyUpdate(ada, "focus", null);
        update.setOrganizationId(organizationId);
        when(ticketQueryService.getDoneActiveSprintTicketsByAssignees(any(), eq(organizationId), any())).thenReturn(Map.of());

        // Act
        List<DailyUpdateResponse> responses = helper.toResponses(List.of(update), reader.getId());

        // Assert
        assertThat(responses.getFirst().finishedTickets()).isNotNull().isEmpty();
    }

    @Test
    void toResponses_unopenableTicket_hasNoMissionRank() {
        // Arrange
        User ada = DailyUpdateMocks.user();
        Ticket openable = rankedTicket("B-1", MissionRank.S);
        Ticket hidden = rankedTicket("B-2", MissionRank.XXL);
        DailyUpdateTicket openableLine = DailyUpdateMocks.item(openable, 40, null, 0);
        DailyUpdateTicket hiddenLine = DailyUpdateMocks.item(hidden, 60, null, 1);
        DailyUpdateTicket goneLine = DailyUpdateMocks.ticketlessItem("B-3", "Removed", 10, 2);
        DailyUpdate update = DailyUpdateMocks.dailyUpdate(ada, "focus", null, openableLine, hiddenLine, goneLine);
        update.setOrganizationId(organizationId);
        when(ticketQueryService.getAccessibleTickets(any(), eq(organizationId), any())).thenReturn(List.of(openable));
        when(ticketQueryService.getDoneActiveSprintTicketsByAssignees(any(), eq(organizationId), any())).thenReturn(Map.of());

        // Act
        DailyUpdateResponse response = helper.toResponses(List.of(update), reader.getId()).getFirst();

        // Assert
        assertThat(response.items().get(0).missionRank()).isEqualTo(MissionRank.S);
        assertThat(response.items().get(0).canOpen()).isTrue();
        assertThat(response.items().get(1).missionRank()).isNull();
        assertThat(response.items().get(1).canOpen()).isFalse();
        assertThat(response.items().get(2).missionRank()).isNull();
    }

    @Test
    void toPreviewResponse_readerIsAuthor_setsFinishedTicketsAndRanks() {
        // Arrange
        Ticket lineTicket = rankedTicket("B-1", MissionRank.L);
        Ticket finished = rankedTicket("B-7", MissionRank.XS);
        DailyUpdateTicket line = DailyUpdateMocks.item(lineTicket, 70, "note", 0);
        when(ticketQueryService.getAccessibleTickets(any(), eq(organizationId), any())).thenReturn(List.of(lineTicket));
        when(ticketQueryService.getDoneActiveSprintTicketsByAssignees(reader.getId(), organizationId, List.of(reader.getId())))
                .thenReturn(Map.of(reader.getId(), List.of(finished)));

        // Act
        DailyUpdatePreviewResponse response = helper.toPreviewResponse(List.of(line), "Ship it", "Who reviews?",
                organizationId,
                reader.getId());

        // Assert
        assertThat(response.items().getFirst().missionRank()).isEqualTo(MissionRank.L);
        assertThat(response.finishedTickets()).extracting(DailyUpdateFinishedTicketResponse::ticketKey)
                .containsExactly("B-7");
        assertThat(response.finishedTickets().getFirst().missionRank()).isEqualTo(MissionRank.XS);
        assertThat(response.focus()).isEqualTo("Ship it");
        assertThat(response.questions()).isEqualTo("Who reviews?");
    }

    @Test
    void toPreviewResponse_blankFocus_returnsNull() {
        // Arrange
        when(ticketQueryService.getDoneActiveSprintTicketsByAssignees(any(), eq(organizationId), any())).thenReturn(Map.of());

        // Act
        DailyUpdatePreviewResponse response = helper.toPreviewResponse(List.of(), "   ", "", organizationId, reader.getId());

        // Assert
        assertThat(response.focus()).isNull();
        assertThat(response.questions()).isNull();
        assertThat(response.finishedTickets()).isEmpty();
    }

    @Test
    void resolveTicketOrganizationId_activeMemberOfActiveOrganization_returnsOrganization() {
        // Act
        UUID result = helper.resolveTicketOrganizationId(organizationId, reader.getId());

        // Assert
        assertThat(result).isEqualTo(organizationId);
    }

    @Test
    void resolveTicketOrganizationId_nullOrganization_returnsNull() {
        // Act
        UUID result = helper.resolveTicketOrganizationId(null, reader.getId());

        // Assert
        assertThat(result).isNull();
        verify(organizationMemberQueryService, never()).findMember(any(), any());
    }

    @Test
    void resolveTicketOrganizationId_disabledOrganization_returnsNull() {
        // Arrange
        when(organizationMemberQueryService.findMember(organizationId, reader.getId()))
                .thenReturn(Optional.of(DailyUpdateMocks.membership(OrganizationStatus.DISABLED,
                        OrganizationMemberStatus.ACTIVE)));

        // Act
        UUID result = helper.resolveTicketOrganizationId(organizationId, reader.getId());

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void resolveTicketOrganizationId_membershipNotActive_returnsNull() {
        // Arrange
        when(organizationMemberQueryService.findMember(organizationId, reader.getId()))
                .thenReturn(Optional.of(DailyUpdateMocks.membership(OrganizationStatus.ACTIVE,
                        OrganizationMemberStatus.DISABLED)));

        // Act
        UUID result = helper.resolveTicketOrganizationId(organizationId, reader.getId());

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void resolveTicketOrganizationId_notAMember_returnsNull() {
        // Arrange
        when(organizationMemberQueryService.findMember(organizationId, reader.getId()))
                .thenReturn(Optional.empty());

        // Act
        UUID result = helper.resolveTicketOrganizationId(organizationId, reader.getId());

        // Assert
        assertThat(result).isNull();
    }

    @Test
    void toResponses_disabledOrganization_showsNoOpenableOrFinishedTickets() {
        // Arrange
        User ada = DailyUpdateMocks.user();
        Ticket ticket = rankedTicket("B-1", MissionRank.S);
        DailyUpdate update = DailyUpdateMocks.dailyUpdate(ada, "focus", null,
                DailyUpdateMocks.item(ticket, 40, null, 0));
        update.setOrganizationId(organizationId);
        when(organizationMemberQueryService.findMember(organizationId, reader.getId()))
                .thenReturn(Optional.of(DailyUpdateMocks.membership(OrganizationStatus.DISABLED,
                        OrganizationMemberStatus.ACTIVE)));

        // Act
        DailyUpdateResponse response = helper.toResponses(List.of(update), reader.getId()).getFirst();

        // Assert
        assertThat(response.items().getFirst().canOpen()).isFalse();
        assertThat(response.items().getFirst().missionRank()).isNull();
        assertThat(response.finishedTickets()).isEmpty();
        verify(ticketQueryService, never()).getAccessibleTickets(any(), any(), any());
        verify(ticketQueryService, never()).getDoneActiveSprintTicketsByAssignees(any(), any(), any());
    }

    @Test
    void toResponses_readerNotActiveInOrganization_showsNoTickets() {
        // Arrange
        User ada = DailyUpdateMocks.user();
        Ticket ticket = rankedTicket("B-1", MissionRank.S);
        DailyUpdate update = DailyUpdateMocks.dailyUpdate(ada, "focus", null,
                DailyUpdateMocks.item(ticket, 40, null, 0));
        update.setOrganizationId(organizationId);
        when(organizationMemberQueryService.findMember(organizationId, reader.getId()))
                .thenReturn(Optional.empty());

        // Act
        DailyUpdateResponse response = helper.toResponses(List.of(update), reader.getId()).getFirst();

        // Assert
        assertThat(response.items().getFirst().canOpen()).isFalse();
        verify(ticketQueryService, never()).getAccessibleTickets(any(), any(), any());
    }

    @Test
    void toPreviewResponse_nullOrganization_showsNoTickets() {
        // Arrange
        Ticket ticket = rankedTicket("B-1", MissionRank.L);
        DailyUpdateTicket line = DailyUpdateMocks.item(ticket, 70, "note", 0);

        // Act
        DailyUpdatePreviewResponse response = helper.toPreviewResponse(List.of(line), "focus", "", null,
                reader.getId());

        // Assert
        assertThat(response.finishedTickets()).isEmpty();
        assertThat(response.items().getFirst().canOpen()).isFalse();
        verify(ticketQueryService, never()).getAccessibleTickets(any(), any(), any());
    }
}
