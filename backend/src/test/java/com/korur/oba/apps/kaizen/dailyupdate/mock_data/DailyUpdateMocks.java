package com.korur.oba.apps.kaizen.dailyupdate.mock_data;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdateTicket;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * Builders for the entities and requests the daily update tests share.
 */
public final class DailyUpdateMocks {

    private DailyUpdateMocks() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static User user() {
        User user = User.builder().email("user-" + UUID.randomUUID() + "@example.com").build();
        user.setId(UUID.randomUUID());
        return user;
    }

    public static Board board(String name) {
        Board board = Board.builder().name(name).prefix("B").color("#112233").build();
        board.setId(UUID.randomUUID());
        return board;
    }

    public static Ticket ticket(Board board, String key, String title, int percentage, User... assignees) {
        Ticket ticket = Ticket.builder().title(title).key(key).type(TicketType.FEATURE)
                .board(board).percentage(percentage).build();
        ticket.setId(UUID.randomUUID());
        List<TicketAssignee> assigneeLinks = new ArrayList<>();
        for (User assignee : assignees) {
            assigneeLinks.add(TicketAssignee.builder().ticket(ticket).user(assignee).build());
        }
        ticket.setAssignees(assigneeLinks);
        return ticket;
    }

    public static DailyUpdateTicketRequest line(Ticket ticket, int percentage, String note) {
        return new DailyUpdateTicketRequest(ticket.getId(), percentage, note);
    }

    public static DailyUpdateTicket item(Ticket ticket, int percentage, String note, int position) {
        DailyUpdateTicket item = DailyUpdateTicket.builder()
                .ticketId(ticket.getId())
                .boardId(ticket.getBoard().getId())
                .boardName(ticket.getBoard().getName())
                .ticketKey(ticket.getKey())
                .ticketTitle(ticket.getTitle())
                .percentage(percentage)
                .note(note)
                .position(position)
                .build();
        item.setId(UUID.randomUUID());
        return item;
    }

    public static DailyUpdateTicket ticketlessItem(String key, String title, int percentage, int position) {
        DailyUpdateTicket item = DailyUpdateTicket.builder()
                .boardId(UUID.randomUUID())
                .boardName("Gone")
                .ticketKey(key)
                .ticketTitle(title)
                .percentage(percentage)
                .position(position)
                .build();
        item.setId(UUID.randomUUID());
        return item;
    }

    public static DailyUpdate dailyUpdate(User author, String focus, String questions, DailyUpdateTicket... items) {
        DailyUpdate dailyUpdate = DailyUpdate.builder().author(author).focus(focus).questions(questions).build();
        dailyUpdate.setId(UUID.randomUUID());
        for (DailyUpdateTicket item : items) {
            item.setDailyUpdate(dailyUpdate);
            dailyUpdate.getItems().add(item);
        }
        return dailyUpdate;
    }

    /**
     * Builds a membership of the given organization, so a test can state whether the
     * reader is active in an active organization.
     */
    public static OrganizationMember membership(OrganizationStatus organizationStatus,
                                                OrganizationMemberStatus memberStatus) {
        Organization organization = Organization.builder().status(organizationStatus).build();
        return OrganizationMember.builder().organization(organization).status(memberStatus).build();
    }
}
