package com.korur.oba.apps.kazan.pullrequest.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

// 4. Internal
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kazan.pullrequest.mock_data.PullRequestMocks;
import com.korur.oba.apps.kazan.pullrequest.repository.PullRequestShareRepository;
import com.korur.oba.apps.kazan.pullrequest.repository.PullRequestShareReviewerRepository;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.connect.github.client.GithubClient;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class PullRequestShareValidatorUnitTest {

    @Mock
    private PullRequestShareRepository pullRequestShareRepository;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private PullRequestShareReviewerRepository pullRequestShareReviewerRepository;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private GithubClient githubClient;

    private PullRequestShareValidator validator;

    private final UUID organizationId = UUID.randomUUID();
    private final User author = PullRequestMocks.user("Ada", "Lovelace");
    private final User reviewer = PullRequestMocks.user("Alan", "Turing");

    @BeforeEach
    void setUp() {
        validator = new PullRequestShareValidator(pullRequestShareRepository, pullRequestShareReviewerRepository,
                organizationMemberQueryService, appAccessValidator, chatChannelQueryService, githubClient, new LoggingService());
    }

    private static void assertRefusal(Throwable thrown, Class<? extends Exception> type,
                                      ExceptionDomainKazanError error) {
        assertThat(thrown).isInstanceOf(type).extracting("errorCode").isEqualTo(error.getCode());
    }

    @Nested
    class EnsureRelatedTicketsValid {

        @Test
        void tenDifferentIds_passes() {
            // Arrange
            List<UUID> ids = Stream.generate(UUID::randomUUID).limit(10).toList();

            // Act and Assert
            assertThatCode(() -> validator.ensureRelatedTicketsValid(ids)).doesNotThrowAnyException();
        }

        @Test
        void emptyList_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensureRelatedTicketsValid(List.of())).doesNotThrowAnyException();
        }

        @Test
        void elevenIds_throwsValidationNamingTheField() {
            // Arrange
            List<UUID> ids = Stream.generate(UUID::randomUUID).limit(11).toList();

            // Act
            Throwable thrown = catchThrowable(() -> validator.ensureRelatedTicketsValid(ids));

            // Assert
            assertRefusal(thrown, ValidationException.class, ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID);
            assertThat(((ValidationException) thrown).getErrorValues())
                    .containsKey(PullRequestShareConstant.FIELD_RELATED_TICKET_IDS);
        }

        @Test
        void repeatedId_throwsValidationNamingTheRepeatedId() {
            // Arrange
            UUID repeated = UUID.randomUUID();

            // Act
            Throwable thrown = catchThrowable(
                    () -> validator.ensureRelatedTicketsValid(List.of(repeated, UUID.randomUUID(), repeated)));

            // Assert
            assertRefusal(thrown, ValidationException.class, ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID);
            assertThat(((ValidationException) thrown).getErrorValues())
                    .containsEntry(PullRequestShareConstant.FIELD_RELATED_TICKET_IDS, repeated);
        }

        @Test
        void nullId_throwsValidation() {
            // Arrange
            List<UUID> ids = Arrays.asList(UUID.randomUUID(), null);

            // Act and Assert
            assertRefusal(catchThrowable(() -> validator.ensureRelatedTicketsValid(ids)),
                    ValidationException.class, ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID);
        }

        @Test
        void nullList_throwsValidation() {
            // Act and Assert
            assertRefusal(catchThrowable(() -> validator.ensureRelatedTicketsValid(null)),
                    ValidationException.class, ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID);
        }
    }

    @Nested
    class EnsureRelatedTicketsAccessible {

        @Test
        void everyRequestedTicketFound_passes() {
            // Arrange
            Ticket ticket = PullRequestMocks.ticket(PullRequestMocks.board("Fabrikam"), "ACME-1", "One");

            // Act and Assert
            assertThatCode(() -> validator.ensureRelatedTicketsAccessible(List.of(ticket.getId()), List.of(ticket)))
                    .doesNotThrowAnyException();
        }

        @Test
        void oneTicketMissing_throwsNotFoundNamingTheFirstMissing() {
            // Arrange
            Ticket found = PullRequestMocks.ticket(PullRequestMocks.board("Fabrikam"), "ACME-1", "One");
            UUID missing = UUID.randomUUID();

            // Act
            Throwable thrown = catchThrowable(() -> validator.ensureRelatedTicketsAccessible(
                    List.of(found.getId(), missing, UUID.randomUUID()), List.of(found)));

            // Assert
            assertRefusal(thrown, NotFoundException.class, ExceptionDomainKazanError.NOT_TICKET_FOUND);
            assertThat(((NotFoundException) thrown).getErrorValues())
                    .containsEntry(PullRequestShareConstant.FIELD_TICKET_ID, missing);
        }
    }

    @Nested
    class EnsureShareExists {

        @Test
        void shareInOrganization_returnsShare() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            when(pullRequestShareRepository.findByIdAndOrganizationId(share.getId(), organizationId))
                    .thenReturn(Optional.of(share));

            // Act and Assert
            assertThat(validator.ensureShareExists(organizationId, share.getId())).isSameAs(share);
        }

        @Test
        void shareOfAnotherOrganizationOrDeleted_throwsNotFound() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            when(pullRequestShareRepository.findByIdAndOrganizationId(shareId, organizationId))
                    .thenReturn(Optional.empty());

            // Act
            Throwable thrown = catchThrowable(
                    () -> validator.ensureShareExists(organizationId, shareId));

            // Assert
            assertRefusal(thrown, NotFoundException.class, ExceptionDomainKazanError.NOT_PULL_REQUEST_SHARE_FOUND);
        }
    }

    @Nested
    class EnsureAuthor {

        @Test
        void author_passes() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);

            // Act and Assert
            assertThatCode(() -> validator.ensureAuthor(share, author)).doesNotThrowAnyException();
        }

        @Test
        void otherUser_throwsForbidden() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureAuthor(share, reviewer))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_EDIT_OTHERS_SHARE.getCode());
        }
    }

    @Nested
    class EnsureReviewer {

        @Test
        void reviewerRow_isReturned() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            PullRequestShareReviewer row = PullRequestMocks.reviewer(share, reviewer, 0, null);
            when(pullRequestShareReviewerRepository.findByShareIdAndUserId(share.getId(), reviewer.getId()))
                    .thenReturn(Optional.of(row));

            // Act and Assert
            assertThat(validator.ensureReviewer(share, reviewer)).isSameAs(row);
        }

        @Test
        void nonReviewer_throwsForbidden() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            when(pullRequestShareReviewerRepository.findByShareIdAndUserId(share.getId(), author.getId()))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureReviewer(share, author))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_NOT_PULL_REQUEST_REVIEWER.getCode());
        }
    }

    @Nested
    class EnsureWithinLimits {

        @Test
        void noteAtLimitAndTenReviewers_passes() {
            // Arrange
            List<UUID> ids = new ArrayList<>();
            for (int index = 0; index < PullRequestShareConstant.MAX_REVIEWERS; index++) {
                ids.add(UUID.randomUUID());
            }

            // Act and Assert
            assertThatCode(() -> validator.ensureWithinLimits("n".repeat(PullRequestShareConstant.MAX_NOTE_LENGTH),
                    ids)).doesNotThrowAnyException();
        }

        @Test
        void noteOf501Characters_throwsValidation() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensureWithinLimits("n".repeat(501), List.of()))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID.getCode());
        }

        @Test
        void elevenReviewers_throwsValidation() {
            // Arrange
            List<UUID> ids = new ArrayList<>();
            for (int index = 0; index < 11; index++) {
                ids.add(UUID.randomUUID());
            }

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureWithinLimits(null, ids))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void nullReviewerList_throwsValidation() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensureWithinLimits(null, null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void immutableReviewerList_passesWithoutNullPointerException() {
            // Act and Assert
            assertThatCode(() -> validator.ensureWithinLimits(null, List.of(UUID.randomUUID())))
                    .doesNotThrowAnyException();
        }

        @Test
        void nullReviewerId_throwsValidation() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensureWithinLimits(null, Arrays.asList(UUID.randomUUID(), null)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class EnsureReviewersEligible {

        @Test
        void emptyList_passesWithoutLookup() {
            // Act and Assert
            assertThatCode(() -> validator.ensureReviewersEligible(organizationId, author, List.of()))
                    .doesNotThrowAnyException();
        }

        @Test
        void authorAsReviewer_throwsValidation() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensureReviewersEligible(organizationId, author,
                    List.of(author.getId())))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID.getCode());
        }

        @Test
        void duplicateReviewer_throwsValidation() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensureReviewersEligible(organizationId, author,
                    List.of(reviewer.getId(), reviewer.getId())))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void reviewerWithoutKazan_throwsMemberWithoutAppAccess() {
            // Arrange
            when(organizationMemberQueryService.findActiveUserIdsByRoles(organizationId,
                    PullRequestShareConstant.REVIEWER_ROLES)).thenReturn(Set.of(reviewer.getId()));
            doThrow(new ValidationException(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS))
                    .when(appAccessValidator)
                    .ensureUsersHaveAppAccess(organizationId, App.KAZAN, List.of(reviewer.getId()));

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureReviewersEligible(organizationId, author,
                    List.of(reviewer.getId())))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS.getCode());
        }

        @Test
        void guestOrInactiveReviewer_throwsValidation() {
            // Arrange
            when(organizationMemberQueryService.findActiveUserIdsByRoles(organizationId,
                    PullRequestShareConstant.REVIEWER_ROLES)).thenReturn(Set.of());

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureReviewersEligible(organizationId, author,
                    List.of(reviewer.getId())))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID.getCode());
        }

        @Test
        void activeOwnerAdminOrMember_passes() {
            // Arrange
            when(organizationMemberQueryService.findActiveUserIdsByRoles(organizationId,
                    PullRequestShareConstant.REVIEWER_ROLES)).thenReturn(Set.of(reviewer.getId()));

            // Act and Assert
            assertThatCode(() -> validator.ensureReviewersEligible(organizationId, author,
                    List.of(reviewer.getId()))).doesNotThrowAnyException();
        }

        @Test
        void reviewerRolesExcludeGuest() {
            // Assert
            assertThat(PullRequestShareConstant.REVIEWER_ROLES).extracting(Enum::name)
                    .containsExactlyInAnyOrder("OWNER", "ADMIN", "MEMBER");
        }
    }

    @Nested
    class EnsurePickerQueryValid {

        @Test
        void searchOf101Characters_throwsValidation() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensurePickerQueryValid("s".repeat(101), null, 1))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void pageZero_throwsValidation() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensurePickerQueryValid(null, null, 0))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void fiveAuthorsThatAreLogins_pass() {
            // Act and Assert
            assertThatCode(() -> validator.ensurePickerQueryValid(null,
                    List.of("a", "Ada-Dev", "dependabot[bot]", "x1", "z"), 1)).doesNotThrowAnyException();
        }

        @Test
        void sixAuthors_throwValidation() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensurePickerQueryValid(null,
                    List.of("a", "b", "c", "d", "e", "f"), 1)).isInstanceOf(ValidationException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a b", "a:b", "", "-a", "a-b[bot", "a\n", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "[bot]", "a OR b"})
        void authorThatIsNotALogin_throwsValidation(String login) {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensurePickerQueryValid(null, List.of(login), 1))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void nullEntryAmongAuthors_isTolerated() {
            // Arrange
            List<String> authors = new ArrayList<>();
            authors.add(null);
            authors.add("octo");

            // Act and Assert
            assertThatCode(() -> validator.ensurePickerQueryValid(null, authors, 1)).doesNotThrowAnyException();
        }

        @Test
        void searchAtLimitAndFirstPage_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensurePickerQueryValid("s".repeat(100), null, 1))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class GithubLookups {

        @Test
        void organizationNotEnabled_throwsNotFound() {
            // Arrange
            when(githubClient.isEnabledFor(organizationId)).thenReturn(false);

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureGithubEnabled(organizationId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_NOT_CONFIGURED.getCode());
        }

        @Test
        void organizationEnabled_passes() {
            // Arrange
            when(githubClient.isEnabledFor(organizationId)).thenReturn(true);

            // Act and Assert
            assertThatCode(() -> validator.ensureGithubEnabled(organizationId)).doesNotThrowAnyException();
        }

        @Test
        void repositoryOutsideInstallation_throwsNotFound() {
            // Arrange
            when(githubClient.findRepository(5L)).thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureRepositoryExists(5L))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_REPOSITORY_FOUND.getCode());
        }

        @Test
        void repositoryInInstallation_returnsIt() {
            // Arrange
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));

            // Act and Assert
            assertThat(validator.ensureRepositoryExists(repository.githubRepositoryId())).isSameAs(repository);
        }

        @Test
        void unknownPullRequest_throwsNotFound() {
            // Arrange
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            when(githubClient.getPullRequest(repository.fullName(), 9)).thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> validator.ensurePullRequestExists(repository, 9))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_PULL_REQUEST_FOUND.getCode());
        }

        @Test
        void knownPullRequest_returnsIt() {
            // Arrange
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            GithubPullRequestPayload pullRequest = PullRequestMocks.pullRequest(9, "Title", "branch");
            when(githubClient.getPullRequest(repository.fullName(), 9)).thenReturn(Optional.of(pullRequest));

            // Act and Assert
            assertThat(validator.ensurePullRequestExists(repository, 9)).isSameAs(pullRequest);
        }
    }

    @Nested
    class EnsurePullRequestChannelExists {

        @Test
        void channelPresent_returnsId() {
            // Arrange
            UUID channelId = UUID.randomUUID();
            when(chatChannelQueryService.findPullRequestChannelId(organizationId))
                    .thenReturn(Optional.of(channelId));

            // Act and Assert
            assertThat(validator.ensurePullRequestChannelExists(organizationId)).isEqualTo(channelId);
        }

        @Test
        void noChannel_throwsValidation() {
            // Arrange
            when(chatChannelQueryService.findPullRequestChannelId(organizationId)).thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> validator.ensurePullRequestChannelExists(organizationId))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_CHANNEL_ABSENT.getCode());
        }
    }
}
