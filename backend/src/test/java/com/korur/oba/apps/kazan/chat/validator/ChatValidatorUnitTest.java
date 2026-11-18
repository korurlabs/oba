package com.korur.oba.apps.kazan.chat.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageQueryRequest;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.helper.ChatTokenParser.ChatTokens;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class ChatValidatorUnitTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private AppAccessValidator appAccessValidator;

    @Mock
    private ChatThreadRepository chatThreadRepository;

    @Mock
    private DailyUpdateQueryService dailyUpdateQueryService;

    private ChatValidator validator;

    private final UUID organizationId = UUID.randomUUID();
    private final User author = ChatMocks.user("Ada", "Lovelace");
    private final User other = ChatMocks.user("Alan", "Turing");

    @BeforeEach
    void setUp() {
        validator = new ChatValidator(chatMessageRepository, chatThreadRepository,
                dailyUpdateQueryService, null, appAccessValidator, new LoggingService());
    }

    private static ChatTokens tokens(int users, int tickets) {
        List<UUID> userIds = new ArrayList<>();
        for (int index = 0; index < users; index++) {
            userIds.add(UUID.randomUUID());
        }
        List<UUID> ticketIds = new ArrayList<>();
        for (int index = 0; index < tickets; index++) {
            ticketIds.add(UUID.randomUUID());
        }
        return new ChatTokens(userIds, false, false, ticketIds);
    }

    private static void assertInvalidMessage(Throwable thrown) {
        assertThat(thrown).isInstanceOf(ValidationException.class)
                .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.VAL_CHAT_MESSAGE_INVALID.getCode());
    }

    @Nested
    class EnsureWithinLimits {

        @Test
        void allLimitsAtBoundary_passes() {
            assertThatCode(() -> validator.ensureWithinLimits(author.getId(), "a".repeat(2000), 10, tokens(20, 10),
                    null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void blankTextWithoutFiles_throwsValidationException() {
            assertInvalidMessage(catchThrowable(
                    () -> validator.ensureWithinLimits(author.getId(), "  \n ", 0, tokens(0, 0), null, null)));
        }

        @Test
        void nullTextWithoutFiles_throwsValidationException() {
            assertInvalidMessage(catchThrowable(
                    () -> validator.ensureWithinLimits(author.getId(), null, 0, tokens(0, 0), null, null)));
        }

        @Test
        void blankTextWithOneFile_passes() {
            assertThatCode(() -> validator.ensureWithinLimits(author.getId(), "", 1, tokens(0, 0), null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void textOf2001Characters_throwsValidationException() {
            assertInvalidMessage(catchThrowable(
                    () -> validator.ensureWithinLimits(author.getId(), "a".repeat(2001), 0, tokens(0, 0), null, null)));
        }

        @Test
        void elevenFiles_throwsValidationException() {
            assertInvalidMessage(catchThrowable(
                    () -> validator.ensureWithinLimits(author.getId(), "hi", 11, tokens(0, 0), null, null)));
        }

        @Test
        void twentyOneMentions_throwsValidationException() {
            assertInvalidMessage(catchThrowable(
                    () -> validator.ensureWithinLimits(author.getId(), "hi", 0, tokens(21, 0), null, null)));
        }

        @Test
        void elevenTicketRefs_throwsValidationException() {
            assertInvalidMessage(catchThrowable(
                    () -> validator.ensureWithinLimits(author.getId(), "hi", 0, tokens(0, 11), null, null)));
        }
    }

    @Nested
    class Lookups {

        @Test
        void messageOfAnotherOrganization_throwsNotFound() {
            UUID messageId = UUID.randomUUID();
            when(chatMessageRepository.findByIdAndOrganizationId(messageId, organizationId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureMessageExists(organizationId, messageId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_MESSAGE_FOUND.getCode());
        }

        @Test
        void threadOfAnotherOrganization_throwsNotFound() {
            UUID threadId = UUID.randomUUID();
            when(chatThreadRepository.findByIdAndOrganizationId(threadId, organizationId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureThreadExists(organizationId, threadId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_THREAD_FOUND.getCode());
        }

        @Test
        void mentionOfNonMember_throwsValidationException() {
            UUID member = UUID.randomUUID();
            UUID stranger = UUID.randomUUID();

            assertThatThrownBy(() -> validator.ensureMentionsAreMembers(organizationId, List.of(member, stranger),
                    List.of(member)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_MENTION_NOT_MEMBER.getCode());
        }

        @Test
        void mentionOfReaderWithoutKazan_throwsMemberWithoutAppAccess() {
            UUID reader = UUID.randomUUID();
            doThrow(new ValidationException(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS))
                    .when(appAccessValidator).ensureUsersHaveAppAccess(organizationId, App.KAZAN, List.of(reader));

            assertThatThrownBy(() -> validator.ensureMentionsAreMembers(organizationId, List.of(reader),
                    List.of(reader)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS.getCode());
        }

        @Test
        void mentionOfNonMember_isRefusedBeforeTheKazanCheck() {
            UUID stranger = UUID.randomUUID();

            assertThatThrownBy(() -> validator.ensureMentionsAreMembers(organizationId, List.of(stranger),
                    List.of()))
                    .isInstanceOf(ValidationException.class);
            verify(appAccessValidator, never()).ensureUsersHaveAppAccess(any(), any(), any());
        }

        @Test
        void ticketNotAccessible_throwsNotFound() {
            Ticket found = ChatMocks.ticket("ACME-1", "Found");

            assertThatThrownBy(() -> validator.ensureTicketsAccessible(List.of(found.getId(), UUID.randomUUID()),
                    List.of(found)))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_TICKET_FOUND.getCode());
        }

        @Test
        void allTicketsAccessible_passes() {
            Ticket found = ChatMocks.ticket("ACME-1", "Found");

            assertThatCode(() -> validator.ensureTicketsAccessible(List.of(found.getId()), List.of(found)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class MessageRules {

        @Test
        void replyToThreadMessageFromChannel_throwsValidationException() {
            ChatMessage target = ChatMocks.message(author, organizationId, "in thread");
            target.setThread(ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId()));

            assertThatThrownBy(() -> validator.ensureReplyInConversation(target, null))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_REPLY_OUTSIDE_CONVERSATION.getCode());
        }

        @Test
        void replyInSameThread_passes() {
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());
            ChatMessage target = ChatMocks.message(author, organizationId, "in thread");
            target.setThread(thread);

            assertThatCode(() -> validator.ensureReplyInConversation(target, thread.getId()))
                    .doesNotThrowAnyException();
        }

        @Test
        void editByNonAuthor_throwsForbidden() {
            ChatMessage message = ChatMocks.message(author, organizationId, "hi");

            assertThatThrownBy(() -> validator.ensureAuthor(message, other))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_MODIFY_OTHERS_MESSAGE.getCode());
        }

        @Test
        void dailyUpdateMessage_throwsValidationException() {
            ChatMessage message = ChatMocks.dailyUpdateMessage(author, organizationId, UUID.randomUUID());

            assertThatThrownBy(() -> validator.ensureNotDailyUpdate(message))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_DAILY_UPDATE_MESSAGE_READ_ONLY.getCode());
        }

        @Test
        void cursorConflict_throwsValidationException() {
            ChatMessageQueryRequest request = new ChatMessageQueryRequest();
            request.setBefore(UUID.randomUUID());
            request.setAround(UUID.randomUUID());

            assertThatThrownBy(() -> validator.ensureSingleCursor(request))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CURSOR_CONFLICT.getCode());
        }

        @Test
        void singleCursor_passes() {
            ChatMessageQueryRequest request = new ChatMessageQueryRequest();
            request.setAfter(UUID.randomUUID());

            assertThatCode(() -> validator.ensureSingleCursor(request)).doesNotThrowAnyException();
        }
    }

    @Nested
    class Deletes {

        @Test
        void authorMemberMayDeleteOwnMessage() {
            ChatMessage message = ChatMocks.message(author, organizationId, "hi");

            assertThatCode(() -> validator.ensureCanDelete(message, author, OrganizationMemberRole.MEMBER))
                    .doesNotThrowAnyException();
        }

        @Test
        void adminAndOwnerMayDeleteOthersMessage() {
            ChatMessage message = ChatMocks.message(author, organizationId, "hi");

            assertThatCode(() -> validator.ensureCanDelete(message, other, OrganizationMemberRole.ADMIN))
                    .doesNotThrowAnyException();
            assertThatCode(() -> validator.ensureCanDelete(message, other, OrganizationMemberRole.OWNER))
                    .doesNotThrowAnyException();
        }

        @Test
        void memberGuestOrNonMemberDeletingOthersMessage_throwsForbidden() {
            ChatMessage message = ChatMocks.message(author, organizationId, "hi");

            OrganizationMemberRole[] refusedRoles = {OrganizationMemberRole.MEMBER, OrganizationMemberRole.GUEST, null};
            for (OrganizationMemberRole role : refusedRoles) {
                assertThatThrownBy(() -> validator.ensureCanDelete(message, other, role))
                        .isInstanceOf(ForbiddenException.class)
                        .extracting("errorCode")
                        .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_DELETE_OTHERS_MESSAGE.getCode());
            }
        }

        @Test
        void refusal_isLoggedAtWarnNeverAtError() {
            // Arrange
            LoggingService loggingService = mock(LoggingService.class);
            ChatValidator observed = new ChatValidator(chatMessageRepository, chatThreadRepository,
                    dailyUpdateQueryService, null, appAccessValidator, loggingService);
            ChatMessage message = ChatMocks.message(author, organizationId, "hi");

            // Act
            catchThrowable(() -> observed.ensureCanDelete(message, other, OrganizationMemberRole.MEMBER));

            // Assert
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(ChatExceptionLogConstant.CHAT_DELETE_NOT_ALLOWED), eq(message.getId()), eq(other.getId()),
                    eq(OrganizationMemberRole.MEMBER));
            verify(loggingService, never()).error(any(), any(), any(), any(Object[].class));
        }
    }

    @Nested
    class Pins {

        @Test
        void authorMayPin() {
            ChatMessage message = ChatMocks.message(author, organizationId, "hi");

            assertThatCode(() -> validator.ensureCanPin(message, author, OrganizationMemberRole.MEMBER))
                    .doesNotThrowAnyException();
        }

        @Test
        void adminMayPinOthersMessage() {
            ChatMessage message = ChatMocks.message(author, organizationId, "hi");

            assertThatCode(() -> validator.ensureCanPin(message, other, OrganizationMemberRole.ADMIN))
                    .doesNotThrowAnyException();
        }

        @Test
        void memberPinningOthersMessage_throwsForbidden() {
            ChatMessage message = ChatMocks.message(author, organizationId, "hi");

            assertThatThrownBy(() -> validator.ensureCanPin(message, other, OrganizationMemberRole.MEMBER))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_PIN_MESSAGE.getCode());
        }

        @Test
        void fortyNinePins_passes() {
            when(chatMessageRepository.countPinnedMessages(organizationId)).thenReturn(49L);

            assertThatCode(() -> validator.ensurePinLimitNotReached(organizationId)).doesNotThrowAnyException();
        }

        @Test
        void fiftyPins_throwsValidationException() {
            when(chatMessageRepository.countPinnedMessages(organizationId)).thenReturn(50L);

            assertThatThrownBy(() -> validator.ensurePinLimitNotReached(organizationId))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_PIN_LIMIT_REACHED.getCode());
        }
    }

    @Nested
    class Reactions {

        @Test
        void singleEmoji_passes() {
            assertThatCode(() -> validator.ensureEmojiValid(UUID.randomUUID(), "👍"))
                    .doesNotThrowAnyException();
        }

        @Test
        void familyEmojiOfSeveralCodePoints_passes() {
            String family = "👨‍👩‍👧";

            assertThatCode(() -> validator.ensureEmojiValid(UUID.randomUUID(), family)).doesNotThrowAnyException();
        }

        @Test
        void twoEmoji_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureEmojiValid(UUID.randomUUID(), "👍👍"))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.VAL_CHAT_REACTION_INVALID.getCode());
        }

        @Test
        void emptyEmoji_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureEmojiValid(UUID.randomUUID(), ""))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void sixtyFiveUnits_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureEmojiValid(UUID.randomUUID(), "a".repeat(65)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void twentiethDistinctEmoji_passes() {
            assertThatCode(() -> validator.ensureReactionLimitNotReached(UUID.randomUUID(), false, 19))
                    .doesNotThrowAnyException();
        }

        @Test
        void twentyFirstDistinctEmoji_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureReactionLimitNotReached(UUID.randomUUID(), false, 20))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.VAL_CHAT_REACTION_INVALID.getCode());
        }

        @Test
        void presentEmojiAtLimit_passes() {
            assertThatCode(() -> validator.ensureReactionLimitNotReached(UUID.randomUUID(), true, 20))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Threads {

        @Test
        void threadMessageAsRoot_throwsValidationException() {
            ChatMessage root = ChatMocks.message(author, organizationId, "in thread");
            root.setThread(ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId()));

            assertThatThrownBy(() -> validator.ensureThreadRoot(root))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.VAL_CHAT_THREAD_INVALID.getCode());
        }

        @Test
        void existingThread_throwsConflictNamingIt() {
            UUID rootId = UUID.randomUUID();
            ChatThread existing = ChatMocks.thread(organizationId, rootId, author.getId());
            when(chatThreadRepository.findByRootMessageId(rootId)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> validator.ensureNoThreadOn(rootId))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorValues")
                    .isEqualTo(Map.of(ChatConstant.FIELD_THREAD_ID, existing.getId()));
        }

        @Test
        void allowedAutoArchiveWindows_pass() {
            for (int minutes : List.of(60, 1440, 4320, 10080)) {
                assertThatCode(() -> validator.ensureAutoArchiveMinutesValid(null, minutes))
                        .doesNotThrowAnyException();
            }
        }

        @Test
        void otherAutoArchiveWindow_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureAutoArchiveMinutesValid(UUID.randomUUID(), 61))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.VAL_CHAT_THREAD_INVALID.getCode());
        }

        @Test
        void nullAutoArchiveWindow_throwsValidationException() {
            assertThatThrownBy(() -> validator.ensureAutoArchiveMinutesValid(UUID.randomUUID(), null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void creatorOwnerAndAdmin_canManage_memberCannot() {
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());

            assertThat(validator.canManageThread(thread, author.getId(), OrganizationMemberRole.MEMBER)).isTrue();
            assertThat(validator.canManageThread(thread, other.getId(), OrganizationMemberRole.OWNER)).isTrue();
            assertThat(validator.canManageThread(thread, other.getId(), OrganizationMemberRole.ADMIN)).isTrue();
            assertThat(validator.canManageThread(thread, other.getId(), OrganizationMemberRole.MEMBER)).isFalse();
            assertThat(validator.canManageThread(thread, other.getId(), null)).isFalse();
        }

        @Test
        void memberManagingOthersThread_throwsForbidden() {
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());

            assertThatThrownBy(() -> validator.ensureCanManageThread(thread, other, OrganizationMemberRole.MEMBER))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_MANAGE_THREAD.getCode());
        }
    }

    private ChatChannel channel(ChatChannelVisibility visibility, ChatChannelPurpose purpose) {
        ChatChannel channel = ChatChannel.builder()
                .name("General")
                .organizationId(organizationId)
                .visibility(visibility)
                .purpose(purpose)
                .build();
        channel.setId(UUID.randomUUID());
        return channel;
    }

    @Nested
    class EnsureWithinLimitsWithDailyUpdate {

        @Test
        void emptyTextWithDailyUpdate_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensureWithinLimits(author.getId(), null, 0, tokens(0, 0),
                    UUID.randomUUID(), null)).doesNotThrowAnyException();
        }

        @Test
        void emptyTextWithoutDailyUpdateOrFiles_isRefused() {
            // Act and Assert
            assertInvalidMessage(catchThrowable(
                    () -> validator.ensureWithinLimits(author.getId(), " ", 0, tokens(0, 0), null, null)));
        }
    }

    @Nested
    class EnsureThreadInChannel {

        @Test
        void threadOfTheChannel_passes() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());

            // Act and Assert
            assertThatCode(() -> validator.ensureThreadInChannel(thread, thread.getChannelId()))
                    .doesNotThrowAnyException();
        }

        @Test
        void threadOfAnotherChannel_isNotFound() {
            // Arrange
            ChatThread thread = ChatMocks.thread(organizationId, UUID.randomUUID(), author.getId());

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureThreadInChannel(thread, UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_CHAT_THREAD_FOUND.getCode());
        }
    }

    @Nested
    class EnsureChannelMembersExpected {

        @Test
        void newPrivateChannelWithMembers_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensureChannelMembersExpected(null, ChatChannelVisibility.PRIVATE,
                    List.of(other.getId()))).doesNotThrowAnyException();
        }

        @Test
        void publicChannelTurningPrivateWithMembers_passes() {
            // Arrange
            ChatChannel channel = channel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL);

            // Act and Assert
            assertThatCode(() -> validator.ensureChannelMembersExpected(channel, ChatChannelVisibility.PRIVATE,
                    List.of(other.getId()))).doesNotThrowAnyException();
        }

        @Test
        void noMembers_passesForEveryChange() {
            // Arrange
            ChatChannel channel = channel(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);

            // Act and Assert
            assertThatCode(() -> validator.ensureChannelMembersExpected(channel, ChatChannelVisibility.PRIVATE,
                    null)).doesNotThrowAnyException();
            assertThatCode(() -> validator.ensureChannelMembersExpected(channel, ChatChannelVisibility.PUBLIC,
                    List.of())).doesNotThrowAnyException();
        }

        @Test
        void privateChannelStayingPrivateWithMembers_isRefused() {
            // Arrange
            ChatChannel channel = channel(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL);

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureChannelMembersExpected(channel, ChatChannelVisibility.PRIVATE,
                    List.of(other.getId())))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_MEMBERS_NOT_EXPECTED.getCode());
        }

        @Test
        void newPublicChannelWithMembers_isRefused() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensureChannelMembersExpected(null, ChatChannelVisibility.PUBLIC,
                    List.of(other.getId())))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_MEMBERS_NOT_EXPECTED.getCode());
        }
    }

    @Nested
    class EnsureChannelPrivate {

        @Test
        void privateChannel_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensureChannelPrivate(
                    channel(ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL))).doesNotThrowAnyException();
        }

        @Test
        void publicChannel_isRefused() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensureChannelPrivate(
                    channel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_NOT_PRIVATE.getCode());
        }
    }

    @Nested
    class EnsureDailyUpdatesChannelPublic {

        @Test
        void publicDailyUpdatesChannel_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensureAutoPostChannelPublic(null, ChatChannelVisibility.PUBLIC,
                    ChatChannelPurpose.DAILY_UPDATES)).doesNotThrowAnyException();
        }

        @Test
        void privateGeneralChannel_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensureAutoPostChannelPublic(UUID.randomUUID(),
                    ChatChannelVisibility.PRIVATE, ChatChannelPurpose.GENERAL)).doesNotThrowAnyException();
        }

        @Test
        void privateDailyUpdatesChannel_isRefused() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensureAutoPostChannelPublic(UUID.randomUUID(),
                    ChatChannelVisibility.PRIVATE, ChatChannelPurpose.DAILY_UPDATES))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_PURPOSE_PRIVATE.getCode());
        }

        @Test
        void privatePullRequestsChannel_isRefused() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensureAutoPostChannelPublic(UUID.randomUUID(),
                    ChatChannelVisibility.PRIVATE, ChatChannelPurpose.PULL_REQUESTS))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_PURPOSE_PRIVATE.getCode());
        }

        @Test
        void publicPullRequestsChannel_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensureAutoPostChannelPublic(null, ChatChannelVisibility.PUBLIC,
                    ChatChannelPurpose.PULL_REQUESTS)).doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureDailyUpdatesPurposeFree {

        @Test
        void noLiveHolder_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensurePurposeFree(UUID.randomUUID(), null, ChatChannelPurpose.DAILY_UPDATES, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void channelHoldingThePurposeItself_passes() {
            // Arrange
            UUID channelId = UUID.randomUUID();

            // Act and Assert
            assertThatCode(() -> validator.ensurePurposeFree(UUID.randomUUID(), channelId, ChatChannelPurpose.DAILY_UPDATES, channelId))
                    .doesNotThrowAnyException();
        }

        @Test
        void anotherChannelHoldsThePurposeOnCreate_isRefusedWithSta016() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensurePurposeFree(UUID.randomUUID(), null, ChatChannelPurpose.DAILY_UPDATES,
                    UUID.randomUUID()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
        }

        @Test
        void anotherChannelHoldsThePurposeOnChange_isRefusedWithSta016() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensurePurposeFree(UUID.randomUUID(),
                    UUID.randomUUID(), ChatChannelPurpose.DAILY_UPDATES, UUID.randomUUID()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
        }

        @Test
        void pullRequestsNoLiveHolder_passes() {
            // Act and Assert
            assertThatCode(() -> validator.ensurePurposeFree(UUID.randomUUID(), null, ChatChannelPurpose.PULL_REQUESTS, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void pullRequestsChannelHoldingThePurposeItself_passes() {
            // Arrange
            UUID channelId = UUID.randomUUID();

            // Act and Assert
            assertThatCode(() -> validator.ensurePurposeFree(UUID.randomUUID(), channelId, ChatChannelPurpose.PULL_REQUESTS, channelId))
                    .doesNotThrowAnyException();
        }

        @Test
        void pullRequestsHeldByAnotherChannel_isRefusedWithSta016() {
            // Act and Assert
            assertThatThrownBy(() -> validator.ensurePurposeFree(UUID.randomUUID(), null, ChatChannelPurpose.PULL_REQUESTS,
                    UUID.randomUUID()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN.getCode());
        }
    }

    @Nested
    class EnsureChannelMembersActive {

        @Test
        void activeMembers_pass() {
            // Act and Assert
            assertThatCode(() -> validator.ensureChannelMembersActive(organizationId, List.of(other.getId()),
                    List.of(author.getId(), other.getId()))).doesNotThrowAnyException();
        }

        @Test
        void inactiveUser_isRefusedWithTheMentionCode() {
            // Arrange
            UUID formerId = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureChannelMembersActive(organizationId, List.of(formerId),
                    List.of(author.getId())))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.VAL_CHAT_MENTION_NOT_MEMBER.getCode());
        }
    }

    @Nested
    class EnsureDailyUpdateEntry {

        private final UUID dailyUpdateId = UUID.randomUUID();

        private void givenUpdateBy(User updateAuthor) {
            givenUpdate(updateAuthor, organizationId);
        }

        private void givenUpdate(User updateAuthor, UUID updateOrganizationId) {
            DailyUpdate dailyUpdate = DailyUpdate.builder()
                    .author(updateAuthor)
                    .organizationId(updateOrganizationId)
                    .focus("focus")
                    .build();
            dailyUpdate.setId(dailyUpdateId);
            when(dailyUpdateQueryService.findDailyUpdate(dailyUpdateId)).thenReturn(Optional.of(dailyUpdate));
        }

        @Test
        void ownUpdateInTheUpdatesChannel_passes() {
            // Arrange
            givenUpdateBy(author);
            when(chatMessageRepository.existsByDailyUpdateIdWithDeleted(dailyUpdateId)).thenReturn(false);

            // Act and Assert
            assertThatCode(() -> validator.ensureDailyUpdateEntry(author, organizationId,
                    channel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES), dailyUpdateId))
                    .doesNotThrowAnyException();
        }

        @Test
        void anotherAuthorsUpdate_isForbidden() {
            // Arrange
            givenUpdateBy(other);

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureDailyUpdateEntry(author, organizationId,
                    channel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES), dailyUpdateId))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_POST_OTHERS_UPDATE.getCode());
            verify(chatMessageRepository, never()).existsByDailyUpdateIdWithDeleted(any());
        }

        @Test
        void generalChannel_isRefused() {
            // Arrange
            givenUpdateBy(author);

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureDailyUpdateEntry(author, organizationId,
                    channel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.GENERAL), dailyUpdateId))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_CHAT_DAILY_UPDATE_CHANNEL.getCode());
        }

        @Test
        void secondEntry_isAConflict() {
            // Arrange
            givenUpdateBy(author);
            when(chatMessageRepository.existsByDailyUpdateIdWithDeleted(dailyUpdateId)).thenReturn(true);

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureDailyUpdateEntry(author, organizationId,
                    channel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES), dailyUpdateId))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.STA_CHAT_DAILY_UPDATE_POSTED.getCode());
        }

        @Test
        void missingUpdate_isNotFound() {
            // Arrange
            when(dailyUpdateQueryService.findDailyUpdate(dailyUpdateId)).thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureDailyUpdateEntry(author, organizationId,
                    channel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES), dailyUpdateId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_DAILY_UPDATE_FOUND.getCode());
        }

        @Test
        void ownUpdateOfAnotherOrganization_isNotFound() {
            // Arrange
            givenUpdate(author, UUID.randomUUID());

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureDailyUpdateEntry(author, organizationId,
                    channel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES), dailyUpdateId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_DAILY_UPDATE_FOUND.getCode());
            verify(chatMessageRepository, never()).existsByDailyUpdateIdWithDeleted(any());
        }

        @Test
        void ownUpdateWithoutOrganization_isNotFound() {
            // Arrange
            givenUpdate(author, null);

            // Act and Assert
            assertThatThrownBy(() -> validator.ensureDailyUpdateEntry(author, organizationId,
                    channel(ChatChannelVisibility.PUBLIC, ChatChannelPurpose.DAILY_UPDATES), dailyUpdateId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainKazanError.NOT_DAILY_UPDATE_FOUND.getCode());
        }
    }
}
