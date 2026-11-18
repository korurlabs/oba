package com.korur.oba.apps.kaizen.ticket.specification;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.dto.request.AllTicketQueryRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketQueryRequest;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kaizen.ticket.entity.TicketLabel;
import com.korur.oba.apps.kaizen.ticket.entity.TicketReviewer;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;

/**
 * Checks the shape of the criteria each filter builds against a mocked Criteria API: no PostgreSQL
 * runs here, so row counts are covered by the shape that cannot multiply rows, an EXISTS
 * subquery with no join on the ticket root.
 */
class TicketSpecificationUnitTest {

    private Root<Ticket> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Subquery<UUID> subquery;
    private Predicate exists;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        root = mock(Root.class, RETURNS_DEEP_STUBS);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        subquery = mock(Subquery.class, RETURNS_DEEP_STUBS);
        exists = mock(Predicate.class);
        when(query.subquery(UUID.class)).thenReturn((Subquery) subquery);
        when(cb.exists(any(Subquery.class))).thenReturn(exists);
    }

    private Predicate apply(Specification<Ticket> specification) {
        return specification.toPredicate(root, query, cb);
    }

    @Nested
    class LabelAssigneeAndReviewerFilters {

        @Test
        void labelIds_matchThroughOneExistsSubqueryAndNeverJoinTheTicketRoot() {
            TicketQueryRequest request = new TicketQueryRequest();
            request.setLabelIds(List.of(UUID.randomUUID(), UUID.randomUUID()));

            apply(TicketSpecification.filterBy(null, null, request));

            verify(subquery).from(TicketLabel.class);
            verify(cb).exists(subquery);
            verify(root, never()).join(anyString());
            verify(root, never()).get("ticketLabels");
            verify(query, never()).distinct(true);
        }

        @Test
        void assigneeIds_matchThroughOneExistsSubqueryAndNeverJoinTheTicketRoot() {
            AllTicketQueryRequest request = new AllTicketQueryRequest();
            request.setAssigneeIds(List.of(UUID.randomUUID(), UUID.randomUUID()));

            apply(TicketSpecification.filterByBoards(null, request));

            verify(subquery).from(TicketAssignee.class);
            verify(cb).exists(subquery);
            verify(root, never()).join(anyString());
            verify(root, never()).get("assignees");
            verify(query, never()).distinct(true);
        }

        @Test
        void reviewerIds_matchThroughOneExistsSubqueryAndNeverJoinTheTicketRoot() {
            TicketQueryRequest request = new TicketQueryRequest();
            request.setReviewerIds(List.of(UUID.randomUUID(), UUID.randomUUID()));

            apply(TicketSpecification.filterBy(null, null, request));

            verify(subquery).from(TicketReviewer.class);
            verify(cb).exists(subquery);
            verify(root, never()).join(anyString());
            verify(root, never()).get("reviewers");
            verify(query, never()).distinct(true);
        }

        @Test
        void emptyLabelAssigneeAndReviewerLists_buildNoSubquery() {
            TicketQueryRequest request = new TicketQueryRequest();
            request.setLabelIds(List.of());
            request.setAssigneeIds(List.of());
            request.setReviewerIds(List.of());

            apply(TicketSpecification.filterBy(null, null, request));

            verify(query, never()).subquery(UUID.class);
        }
    }

    @Nested
    class TypeAndStatusFilters {

        @Test
        void types_newTypes_filterOnThoseTypes() {
            TicketQueryRequest request = new TicketQueryRequest();
            request.setTypes(List.of(TicketType.INCIDENT, TicketType.REFACTOR));

            apply(TicketSpecification.filterBy(null, null, request));

            verify(root.get("type")).in(List.of(TicketType.INCIDENT, TicketType.REFACTOR));
        }

        @Test
        void types_problem_filtersOnProblem() {
            TicketQueryRequest request = new TicketQueryRequest();
            request.setTypes(List.of(TicketType.PROBLEM));

            apply(TicketSpecification.filterBy(null, null, request));

            verify(root.get("type")).in(List.of(TicketType.PROBLEM));
        }

        @Test
        void statuses_waitingForThirdParty_filtersOnThatStatus() {
            TicketQueryRequest request = new TicketQueryRequest();
            request.setStatuses(List.of(TicketStatus.WAITING_FOR_THIRD_PARTY));

            apply(TicketSpecification.filterBy(null, null, request));

            verify(root.get("status")).in(List.of(TicketStatus.WAITING_FOR_THIRD_PARTY));
        }

        @Test
        @SuppressWarnings("deprecation")
        void types_onlyTask_matchesNothingAndNeverBindsTask() {
            TicketQueryRequest request = new TicketQueryRequest();
            request.setTypes(List.of(TicketType.TASK));

            apply(TicketSpecification.filterBy(List.of(UUID.randomUUID()), null, request));

            verify(cb).disjunction();
            verify(root, never()).get("type");
        }

        @Test
        @SuppressWarnings("deprecation")
        void types_taskAndBug_filtersOnBugOnly() {
            TicketQueryRequest request = new TicketQueryRequest();
            request.setTypes(List.of(TicketType.TASK, TicketType.BUG));

            apply(TicketSpecification.filterBy(List.of(UUID.randomUUID()), null, request));

            verify(root.get("type")).in(List.of(TicketType.BUG));
            verify(cb, never()).disjunction();
        }
    }

    @Nested
    class SuggestedTicketFilters {

        @Test
        void boardsAssigneeAndStatuses_filterOnInProgressAndDoNotRestrictToRootTickets() {
            UUID boardId = UUID.randomUUID();

            apply(TicketSpecification.filterByBoardsAssigneeAndStatuses(
                    List.of(boardId), UUID.randomUUID(), List.of(TicketStatus.IN_PROGRESS)));

            verify(root.get("status")).in(List.of(TicketStatus.IN_PROGRESS));
            verify(root.get("board").get("id")).in(List.of(boardId));
            verify(subquery).from(TicketAssignee.class);
            verify(root, never()).get("parentTicket");
        }

        @Test
        void boardsAndIds_filterOnTheIdsAndDoNotRestrictToRootTickets() {
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();

            apply(TicketSpecification.filterByBoardsAndIds(List.of(boardId), List.of(ticketId)));

            verify(root.get("board").get("id")).in(List.of(boardId));
            verify(root.get("id")).in(List.of(ticketId));
            verify(root, never()).get("parentTicket");
        }

        @Test
        void boardsAndIds_emptyIdsOrBoards_matchNothing() {
            Predicate nothing = mock(Predicate.class);
            when(cb.disjunction()).thenReturn(nothing);
            when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(nothing);

            Predicate noIds = apply(TicketSpecification.filterByBoardsAndIds(List.of(UUID.randomUUID()), List.of()));
            Predicate noBoards = apply(TicketSpecification.filterByBoardsAndIds(List.of(), List.of(UUID.randomUUID())));

            assertThat(noIds).isSameAs(nothing);
            assertThat(noBoards).isSameAs(nothing);
        }

        @Test
        void boardsAndKeys_filterOnTheExactKeysAndDoNotRestrictToRootTickets() {
            UUID boardId = UUID.randomUUID();
            List<String> keys = List.of("ACME-1", "ACME-2");

            apply(TicketSpecification.filterByBoardsAndKeys(List.of(boardId), keys));

            verify(root.get("board").get("id")).in(List.of(boardId));
            verify(root.get("key")).in(keys);
            verify(cb, never()).like(any(), anyString());
            verify(root, never()).get("parentTicket");
        }

        @Test
        void boardsAndKeys_emptyKeysOrBoards_matchNothing() {
            Predicate nothing = mock(Predicate.class);
            when(cb.disjunction()).thenReturn(nothing);
            when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(nothing);

            Predicate noKeys = apply(TicketSpecification.filterByBoardsAndKeys(List.of(UUID.randomUUID()), List.of()));
            Predicate noBoards = apply(TicketSpecification.filterByBoardsAndKeys(List.of(), List.of("ACME-1")));

            assertThat(noKeys).isSameAs(nothing);
            assertThat(noBoards).isSameAs(nothing);
        }
    }

    @Nested
    class CreatedByIdsFilter {

        @Test
        void nullCreatedByIds_addsNoCreatorPredicate() {
            AllTicketQueryRequest request = new AllTicketQueryRequest();
            request.setCreatedByIds(null);

            apply(TicketSpecification.filterByBoards(null, request));

            verify(root, never()).get("createdBy");
        }

        @Test
        void emptyCreatedByIds_addsNoCreatorPredicate() {
            AllTicketQueryRequest request = new AllTicketQueryRequest();
            request.setCreatedByIds(List.of());

            apply(TicketSpecification.filterByBoards(null, request));

            verify(root, never()).get("createdBy");
        }

        @Test
        void createdByIds_matchOnlyTheCreatorIdsThroughAnInPredicate() {
            List<UUID> creatorIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            AllTicketQueryRequest request = new AllTicketQueryRequest();
            request.setCreatedByIds(creatorIds);

            apply(TicketSpecification.filterByBoards(null, request));

            // An IN on the creator id never matches a NULL creator, so system actor tickets are excluded.
            verify(root.get("createdBy").get("id")).in(creatorIds);
            verify(root, never()).join(anyString());
            verify(query, never()).distinct(true);
        }

        @Test
        void createdByIds_areAndCombinedWithTheBoardScopeAndTheTypeFilter() {
            UUID boardId = UUID.randomUUID();
            UUID creatorId = UUID.randomUUID();
            AllTicketQueryRequest request = new AllTicketQueryRequest();
            request.setTypes(List.of(TicketType.INCIDENT));
            request.setCreatedByIds(List.of(creatorId));

            apply(TicketSpecification.filterByBoards(List.of(boardId), request));

            verify(root.get("board").get("id")).in(List.of(boardId));
            verify(root.get("type")).in(List.of(TicketType.INCIDENT));
            verify(root.get("createdBy").get("id")).in(List.of(creatorId));
        }
    }
}
