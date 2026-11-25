package com.korur.oba.apps.kazan.dailyupdate.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.dailyupdate.dto.projection.DailyUpdateFirstPostProjection;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateMissingRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateMissingMemberResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateMissingResponse;
import com.korur.oba.apps.kazan.dailyupdate.helper.DailyUpdateDeadlineResolver;
import com.korur.oba.apps.kazan.dailyupdate.mapper.DailyUpdateMapperHelper;
import com.korur.oba.apps.kazan.dailyupdate.mapper.DailyUpdateMapperImpl;
import com.korur.oba.apps.kazan.dailyupdate.mock_data.DailyUpdateMocks;
import com.korur.oba.apps.kazan.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kazan.dailyupdate.validator.DailyUpdateValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberKindSummaryProjection;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class DailyUpdateQueryServiceImplUnitMissingTest {

    private static final BusinessCalendar BUSINESS_CALENDAR = new BusinessCalendar(Clock.systemUTC());

    // Friday 2026-10-16, a past workday. Istanbul is UTC+3, so the deadline is 07:30Z
    // and the day runs from the 15th 21:00Z to the 16th 21:00Z.
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 17);
    private static final Instant DAY_START = Instant.parse("2026-10-15T21:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-10-16T21:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-10-16T07:30:00Z");
    // Friday 15:00 in Istanbul, after the deadline.
    private static final Instant FRIDAY_AFTERNOON = Instant.parse("2026-10-16T12:00:00Z");
    // Monday after the reported week, so FRIDAY and SATURDAY are past days.
    private static final Instant FOLLOWING_MONDAY = Instant.parse("2026-10-19T12:00:00Z");
    // Still Friday in UTC, but already Saturday 01:00 in Istanbul.
    private static final Instant FRIDAY_NIGHT_UTC = Instant.parse("2026-10-16T22:00:00Z");

    @Mock
    private DailyUpdateRepository dailyUpdateRepository;
    @Mock
    private TicketQueryService ticketQueryService;
    @Mock
    private BoardMemberQueryService boardMemberQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private OrganizationAccessService organizationAccessService;
    @Mock
    private AppAccessQueryService appAccessQueryService;

    private DailyUpdateQueryServiceImpl service;
    private User admin;
    private UUID organizationId;
    private Instant now;

    private record Summary(UUID userId, String email, String firstName, String lastName, String role, String kind)
            implements OrganizationMemberKindSummaryProjection {
        @Override
        public UUID getUserId() {
            return userId;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public String getFirstName() {
            return firstName;
        }

        @Override
        public String getLastName() {
            return lastName;
        }

        @Override
        public String getRole() {
            return role;
        }

        @Override
        public String getKind() {
            return kind;
        }

        @Override
        public String getAgentRole() {
            return null;
        }

        @Override
        public String getAgentFlow() {
            return null;
        }

        @Override
        public String getAgentProvider() {
            return null;
        }
    }

    private record FirstPost(UUID authorId, Instant firstPostedAt) implements DailyUpdateFirstPostProjection {
        @Override
        public UUID getAuthorId() {
            return authorId;
        }

        @Override
        public Instant getFirstPostedAt() {
            return firstPostedAt;
        }
    }

    @BeforeEach
    void setUp() {
        DailyUpdateValidator validator = new DailyUpdateValidator(dailyUpdateRepository, new LoggingService());
        DailyUpdateMapperImpl mapper = new DailyUpdateMapperImpl(new TicketEmbeddedMapperImpl());
        DailyUpdateMapperHelper mapperHelper = new DailyUpdateMapperHelper(ticketQueryService, mapper,
                new com.korur.oba.apps.kazan.dailyupdate.helper.DailyUpdateSummaryRenderer(), validator, organizationMemberQueryService);
        service = new DailyUpdateQueryServiceImpl(dailyUpdateRepository, validator, mapper, mapperHelper,
                fixedNowResolver(validator), ticketQueryService, boardMemberQueryService,
                organizationMemberQueryService, organizationAccessService, appAccessQueryService,
                BUSINESS_CALENDAR);
        admin = DailyUpdateMocks.user();
        organizationId = UUID.randomUUID();
        now = FOLLOWING_MONDAY;
        // Every member may open Kazan unless a test says otherwise; lenient,
        // because the tests that stop before the member list never ask.
        lenient().when(appAccessQueryService.hasAppAccess(eq(organizationId), any(UUID.class), eq(App.KAZAN)))
                .thenReturn(true);
    }

    /**
     * Resolver whose current time is the now field, so a request without a
     * date resolves the same day whatever day the suite runs on.
     */
    private DailyUpdateDeadlineResolver fixedNowResolver(DailyUpdateValidator validator) {
        return new DailyUpdateDeadlineResolver(validator, BUSINESS_CALENDAR, Clock.systemUTC()) {
            @Override
            public ReportDay resolve(LocalDate date) {
                return resolve(date, now);
            }
        };
    }

    private DailyUpdateMissingRequest request(LocalDate date) {
        DailyUpdateMissingRequest request = new DailyUpdateMissingRequest();
        request.setOrganizationId(organizationId);
        request.setDate(date);
        return request;
    }

    private static Summary member(String firstName, String lastName, String email, OrganizationMemberRole role) {
        return new Summary(UUID.randomUUID(), email, firstName, lastName, role.name(), "HUMAN");
    }

    private static Summary agent(String firstName, String email) {
        return new Summary(UUID.randomUUID(), email, firstName, "Agent", OrganizationMemberRole.MEMBER.name(),
                "AGENT");
    }

    private void givenMembers(Summary... members) {
        when(organizationMemberQueryService.listActiveMemberKindSummaries(organizationId, DEADLINE))
                .thenReturn(List.of(members));
    }

    private void givenFirstPosts(FirstPost... posts) {
        when(dailyUpdateRepository.findFirstPostsByAuthors(anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(posts));
    }

    @Nested
    class AccessCheck {

        @Test
        void admin_isCheckedBeforeAnyMemberOrUpdateQuery() {
            Summary member = member("Ada", "A", "ada@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(member);
            givenFirstPosts();

            service.getMissingDailyUpdates(admin, request(FRIDAY));

            InOrder order = inOrder(organizationAccessService, organizationMemberQueryService, dailyUpdateRepository);
            order.verify(organizationAccessService)
                    .verifyOrganizationAccess(organizationId, admin.getId(), OrganizationMemberRole.ADMIN,
                            App.KAZAN);
            order.verify(organizationMemberQueryService).listActiveMemberKindSummaries(organizationId, DEADLINE);
            order.verify(dailyUpdateRepository)
                    .findFirstPostsByAuthors(anyCollection(), any(Instant.class), any(Instant.class));
        }

        @Test
        void member_throwsForbiddenAndQueriesNothing() {
            doThrow(new ForbiddenException(ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS))
                    .when(organizationAccessService)
                    .verifyOrganizationAccess(organizationId, admin.getId(), OrganizationMemberRole.ADMIN,
                            App.KAZAN);

            assertThatThrownBy(() -> service.getMissingDailyUpdates(admin, request(FRIDAY)))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(organizationMemberQueryService, appAccessQueryService, dailyUpdateRepository);
        }

        @Test
        void memberAskingForFutureDate_getsForbiddenNotValidation() {
            doThrow(new ForbiddenException(ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS))
                    .when(organizationAccessService)
                    .verifyOrganizationAccess(organizationId, admin.getId(), OrganizationMemberRole.ADMIN,
                            App.KAZAN);

            assertThatThrownBy(() -> service.getMissingDailyUpdates(admin, request(FRIDAY.plusDays(5))))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class DateHandling {

        @Test
        void futureDate_throws422AfterTheAccessCheck() {
            LocalDate future = FRIDAY.plusDays(5);

            assertThatThrownBy(() -> service.getMissingDailyUpdates(admin, request(future)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ExceptionDomainKazanError.VAL_DAILY_UPDATE_REPORT_DATE_IN_FUTURE.getCode()));

            verify(organizationAccessService)
                    .verifyOrganizationAccess(organizationId, admin.getId(), OrganizationMemberRole.ADMIN,
                            App.KAZAN);
            verifyNoInteractions(organizationMemberQueryService, dailyUpdateRepository);
        }

        @Test
        void weekend_returnsEmptyReportWithoutQueryingMembersOrUpdates() {
            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(SATURDAY));

            assertThat(response.workday()).isFalse();
            assertThat(response.expectedCount()).isZero();
            assertThat(response.members()).isEmpty();
            assertThat(response.date()).isEqualTo(SATURDAY);
            assertThat(response.organizationId()).isEqualTo(organizationId);
            verifyNoInteractions(organizationMemberQueryService, appAccessQueryService, dailyUpdateRepository);
        }

        @Test
        void nullDate_onAWorkday_reportsTodayInIstanbul() {
            now = FRIDAY_AFTERNOON;
            givenMembers();

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(null));

            assertThat(response.date()).isEqualTo(FRIDAY);
            assertThat(response.workday()).isTrue();
            assertThat(response.deadline()).isEqualTo(DEADLINE);
            verify(organizationMemberQueryService).listActiveMemberKindSummaries(organizationId, DEADLINE);
        }

        @Test
        void nullDate_whenIstanbulIsAlreadySaturday_reportsTheWeekendDayWithoutQueries() {
            now = FRIDAY_NIGHT_UTC;

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(null));

            assertThat(response.date()).isEqualTo(SATURDAY);
            assertThat(response.workday()).isFalse();
            assertThat(response.expectedCount()).isZero();
            verifyNoInteractions(organizationMemberQueryService, dailyUpdateRepository);
        }

        @Test
        void pastWorkday_returnsDayFieldsAndDeadline() {
            givenMembers();

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(FRIDAY));

            assertThat(response.date()).isEqualTo(FRIDAY);
            assertThat(response.deadline()).isEqualTo(DEADLINE);
            assertThat(response.workday()).isTrue();
            assertThat(response.deadlinePassed()).isTrue();
        }
    }

    @Nested
    class ExpectedMembers {

        @Test
        void guestsAreNotExpectedAndNotQueried() {
            Summary owner = member("Olga", "O", "olga@example.com", OrganizationMemberRole.OWNER);
            Summary adminMember = member("Adam", "A", "adam@example.com", OrganizationMemberRole.ADMIN);
            Summary regular = member("Mia", "M", "mia@example.com", OrganizationMemberRole.MEMBER);
            Summary guest = member("Gus", "G", "gus@example.com", OrganizationMemberRole.GUEST);
            givenMembers(owner, adminMember, regular, guest);
            givenFirstPosts();

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(FRIDAY));

            assertThat(response.expectedCount()).isEqualTo(3);
            assertThat(response.members()).extracting(DailyUpdateMissingMemberResponse::userId)
                    .containsExactlyInAnyOrder(owner.userId(), adminMember.userId(), regular.userId());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<UUID>> authors = ArgumentCaptor.forClass(Collection.class);
            verify(dailyUpdateRepository).findFirstPostsByAuthors(authors.capture(), any(Instant.class),
                    any(Instant.class));
            assertThat(authors.getValue()).containsExactlyInAnyOrder(
                    owner.userId(), adminMember.userId(), regular.userId());
            verify(appAccessQueryService, never()).hasAppAccess(organizationId, guest.userId(), App.KAZAN);
        }

        @Test
        void agent_isNeitherListedNorCountedNorQueried() {
            Summary regular = member("Mia", "M", "mia@example.com", OrganizationMemberRole.MEMBER);
            Summary scout = agent("Scout", "scout@agents.oba.invalid");
            givenMembers(regular, scout);
            givenFirstPosts();

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(FRIDAY));

            assertThat(response.expectedCount()).isEqualTo(1);
            assertThat(response.members()).extracting(DailyUpdateMissingMemberResponse::userId)
                    .containsExactly(regular.userId());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<UUID>> authors = ArgumentCaptor.forClass(Collection.class);
            verify(dailyUpdateRepository).findFirstPostsByAuthors(authors.capture(), any(Instant.class),
                    any(Instant.class));
            assertThat(authors.getValue()).containsExactly(regular.userId());
            verify(appAccessQueryService, never()).hasAppAccess(organizationId, scout.userId(), App.KAZAN);
        }

        @Test
        void memberWithoutKazan_isNeitherListedNorCountedNorQueried() {
            Summary owner = member("Olga", "O", "olga@example.com", OrganizationMemberRole.OWNER);
            Summary withoutKazan = member("Nik", "N", "nik@example.com", OrganizationMemberRole.MEMBER);
            Summary regular = member("Mia", "M", "mia@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(owner, withoutKazan, regular);
            when(appAccessQueryService.hasAppAccess(organizationId, withoutKazan.userId(), App.KAZAN))
                    .thenReturn(false);
            givenFirstPosts();

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(FRIDAY));

            assertThat(response.expectedCount()).isEqualTo(2);
            assertThat(response.members()).extracting(DailyUpdateMissingMemberResponse::userId)
                    .containsExactlyInAnyOrder(owner.userId(), regular.userId());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<UUID>> authors = ArgumentCaptor.forClass(Collection.class);
            verify(dailyUpdateRepository).findFirstPostsByAuthors(authors.capture(), any(Instant.class),
                    any(Instant.class));
            assertThat(authors.getValue()).containsExactlyInAnyOrder(owner.userId(), regular.userId());
        }

        @Test
        void noMemberWithKazan_returnsEmptyReportWithoutUpdateQuery() {
            Summary withoutKazan = member("Nik", "N", "nik@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(withoutKazan);
            when(appAccessQueryService.hasAppAccess(organizationId, withoutKazan.userId(), App.KAZAN))
                    .thenReturn(false);

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(FRIDAY));

            assertThat(response.workday()).isTrue();
            assertThat(response.expectedCount()).isZero();
            assertThat(response.members()).isEmpty();
            verifyNoInteractions(dailyUpdateRepository);
        }

        @Test
        void membersAreRequestedWithTheDeadlineAsJoinCutoff() {
            givenMembers();

            service.getMissingDailyUpdates(admin, request(FRIDAY));

            verify(organizationMemberQueryService).listActiveMemberKindSummaries(organizationId, DEADLINE);
        }

        @Test
        void onlyGuests_returnsEmptyReportWithoutUpdateQuery() {
            givenMembers(member("Gus", "G", "gus@example.com", OrganizationMemberRole.GUEST));

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(FRIDAY));

            assertThat(response.expectedCount()).isZero();
            assertThat(response.members()).isEmpty();
            verifyNoInteractions(dailyUpdateRepository);
        }

        @Test
        void firstPostQueryUsesTheIstanbulDayWindow() {
            givenMembers(member("Mia", "M", "mia@example.com", OrganizationMemberRole.MEMBER));
            givenFirstPosts();

            service.getMissingDailyUpdates(admin, request(FRIDAY));

            verify(dailyUpdateRepository).findFirstPostsByAuthors(anyCollection(), org.mockito.ArgumentMatchers.eq(DAY_START),
                    org.mockito.ArgumentMatchers.eq(DAY_END));
        }
    }

    @Nested
    class Classification {

        @Test
        void postedBeforeDeadline_isOmittedButCounted() {
            Summary onTime = member("Ona", "T", "ona@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(onTime);
            givenFirstPosts(new FirstPost(onTime.userId(), Instant.parse("2026-10-16T06:35:00Z")));

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(FRIDAY));

            assertThat(response.members()).isEmpty();
            assertThat(response.expectedCount()).isEqualTo(1);
        }

        @Test
        void oneSecondBeforeDeadline_isOnTime() {
            Summary member = member("Ona", "T", "ona@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(member);
            givenFirstPosts(new FirstPost(member.userId(), Instant.parse("2026-10-16T07:29:59Z")));

            assertThat(service.getMissingDailyUpdates(admin, request(FRIDAY)).members()).isEmpty();
        }

        @Test
        void postedExactlyAtDeadline_isLate() {
            Summary member = member("Lena", "L", "lena@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(member);
            givenFirstPosts(new FirstPost(member.userId(), DEADLINE));

            List<DailyUpdateMissingMemberResponse> members =
                    service.getMissingDailyUpdates(admin, request(FRIDAY)).members();

            assertThat(members).hasSize(1);
            assertThat(members.get(0).postedAt()).isEqualTo(DEADLINE);
        }

        @Test
        void postedAfterDeadline_isLateWithPostedAt() {
            Summary late = member("Lena", "L", "lena@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(late);
            Instant postedAt = Instant.parse("2026-10-16T07:50:00Z");
            givenFirstPosts(new FirstPost(late.userId(), postedAt));

            List<DailyUpdateMissingMemberResponse> members =
                    service.getMissingDailyUpdates(admin, request(FRIDAY)).members();

            assertThat(members).singleElement().satisfies((DailyUpdateMissingMemberResponse entry) -> {
                assertThat(entry.userId()).isEqualTo(late.userId());
                assertThat(entry.email()).isEqualTo("lena@example.com");
                assertThat(entry.firstName()).isEqualTo("Lena");
                assertThat(entry.lastName()).isEqualTo("L");
                assertThat(entry.postedAt()).isEqualTo(postedAt);
            });
        }

        @Test
        void noPost_isMissingWithNullPostedAt() {
            Summary missing = member("Nora", "N", "nora@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(missing);
            givenFirstPosts();

            List<DailyUpdateMissingMemberResponse> members =
                    service.getMissingDailyUpdates(admin, request(FRIDAY)).members();

            assertThat(members).singleElement()
                    .satisfies((DailyUpdateMissingMemberResponse entry) -> assertThat(entry.postedAt()).isNull());
        }
    }

    @Nested
    class Sorting {

        @Test
        void missingFirstByName_thenLateByPostedAt() {
            Summary zoe = member("Zoe", "Z", "zoe@example.com", OrganizationMemberRole.MEMBER);
            Summary anna = member("anna", "B", "anna.b@example.com", OrganizationMemberRole.MEMBER);
            Summary annaA = member("Anna", "A", "anna.a@example.com", OrganizationMemberRole.MEMBER);
            Summary lateOne = member("Late", "One", "l1@example.com", OrganizationMemberRole.MEMBER);
            Summary lateTwo = member("Early", "Two", "l2@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(lateOne, zoe, lateTwo, anna, annaA);
            givenFirstPosts(
                    new FirstPost(lateOne.userId(), Instant.parse("2026-10-16T09:00:00Z")),
                    new FirstPost(lateTwo.userId(), Instant.parse("2026-10-16T08:00:00Z")));

            List<DailyUpdateMissingMemberResponse> members =
                    service.getMissingDailyUpdates(admin, request(FRIDAY)).members();

            assertThat(members).extracting(DailyUpdateMissingMemberResponse::userId).containsExactly(
                    annaA.userId(), anna.userId(), zoe.userId(), lateTwo.userId(), lateOne.userId());
        }

        @Test
        void nullNamesSortLastAmongMissingThenByEmail() {
            Summary named = member("Bob", "B", "bob@example.com", OrganizationMemberRole.MEMBER);
            Summary noFirstB = member(null, null, "b@example.com", OrganizationMemberRole.MEMBER);
            Summary noFirstA = member(null, null, "a@example.com", OrganizationMemberRole.MEMBER);
            givenMembers(noFirstB, named, noFirstA);
            givenFirstPosts();

            List<DailyUpdateMissingMemberResponse> members =
                    service.getMissingDailyUpdates(admin, request(FRIDAY)).members();

            assertThat(members).extracting(DailyUpdateMissingMemberResponse::email)
                    .containsExactly("bob@example.com", "a@example.com", "b@example.com");
        }
    }

    @Nested
    class QueryCount {

        @Test
        void manyMembers_useOneMemberQueryAndOneFirstPostQuery() {
            List<Summary> members = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                members.add(member("User" + i, "X", "user" + i + "@example.com", OrganizationMemberRole.MEMBER));
            }
            givenMembers(members.toArray(new Summary[0]));
            givenFirstPosts(new FirstPost(members.get(0).userId(), Instant.parse("2026-10-16T09:00:00Z")));

            DailyUpdateMissingResponse response = service.getMissingDailyUpdates(admin, request(FRIDAY));

            assertThat(response.members()).hasSize(40);
            verify(organizationAccessService, times(1))
                    .verifyOrganizationAccess(organizationId, admin.getId(), OrganizationMemberRole.ADMIN,
                            App.KAZAN);
            verify(organizationMemberQueryService, times(1)).listActiveMemberKindSummaries(organizationId, DEADLINE);
            verify(dailyUpdateRepository, times(1))
                    .findFirstPostsByAuthors(anyCollection(), any(Instant.class), any(Instant.class));
            verify(dailyUpdateRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                    any(org.springframework.data.domain.Pageable.class));
        }
    }
}
