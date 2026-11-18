package com.korur.oba.shrine.access.validator.unit;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kalkan.page.constant.service.KalkanPageServiceLogConstant;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.validator.MailValidator;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.validator.ResourceAccessValidator;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class ResourceAccessValidatorUnitTest {

    @Mock
    private BoardAccessService boardAccessService;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private ChatValidator chatValidator;
    @Mock
    private KalkanAccessQueryService kalkanAccessQueryService;
    @Mock
    private MailValidator mailValidator;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private ResourceAccessValidator resourceAccessValidator;

    private User user;
    private UUID userId;
    private UUID organizationId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().id(userId).build();
        organizationId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
    }

    @Nested
    class VerifySameOrganization {

        @Test
        void sameOrganization_passesWithoutALog() {
            // Act and assert
            assertDoesNotThrow(() -> resourceAccessValidator.verifySameOrganization(user, organizationId,
                    ResourceAccessType.TICKET, resourceId, organizationId));
            verifyNoInteractions(loggingService);
        }

        @Test
        void otherOrganization_throwsAppSec002AndLogsWarn() {
            // Arrange
            UUID resourceOrganizationId = UUID.randomUUID();

            // Act
            ForbiddenException thrown = assertThrows(ForbiddenException.class, () ->
                    resourceAccessValidator.verifySameOrganization(user, organizationId,
                            ResourceAccessType.CHAT_MESSAGE, resourceId, resourceOrganizationId));

            // Assert
            assertEquals("APP_SEC_002", thrown.getErrorCode());
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(AccessExceptionLogConstant.RESOURCE_ORGANIZATION_MISMATCH), eq(userId),
                    eq(ResourceAccessType.CHAT_MESSAGE), eq(resourceId), eq(resourceOrganizationId),
                    eq(organizationId));
        }
    }

    @Nested
    class VerifyTicketRules {

        private Ticket ticket;
        private UUID boardId;

        @BeforeEach
        void setUpTicket() {
            boardId = UUID.randomUUID();
            Board board = mock(Board.class);
            when(board.getOrganizationId()).thenReturn(organizationId);
            when(board.getId()).thenReturn(boardId);
            ticket = mock(Ticket.class);
            when(ticket.getBoard()).thenReturn(board);
        }

        @Test
        void read_requiresBoardViewer() {
            // Act
            resourceAccessValidator.verifyTicketRules(user, ticket, ResourceAccessAction.READ);

            // Assert
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, userId, BoardMemberRole.VIEWER);
        }

        @Test
        void writeAndDelete_requireBoardMember() {
            // Act
            resourceAccessValidator.verifyTicketRules(user, ticket, ResourceAccessAction.WRITE);
            resourceAccessValidator.verifyTicketRules(user, ticket, ResourceAccessAction.DELETE);

            // Assert
            verify(boardAccessService, times(2)).verifyBoardAccess(organizationId, boardId, userId,
                    BoardMemberRole.MEMBER);
        }

        @Test
        void boardRefusesUser_propagatesTheBoardError() {
            // Arrange
            doThrow(mock(NotFoundException.class)).when(boardAccessService)
                    .verifyBoardAccess(any(), any(), any(), any());

            // Act and assert
            assertThrows(NotFoundException.class, () ->
                    resourceAccessValidator.verifyTicketRules(user, ticket, ResourceAccessAction.READ));
        }
    }

    @Nested
    class VerifyChatMessageRules {

        private ChatMessage message;
        private UUID channelId;

        @BeforeEach
        void setUpMessage() {
            channelId = UUID.randomUUID();
            message = mock(ChatMessage.class);
            when(message.getOrganizationId()).thenReturn(organizationId);
            when(message.getChannelId()).thenReturn(channelId);
        }

        @Test
        void read_checksOnlyTheChannel() {
            // Act
            resourceAccessValidator.verifyChatMessageRules(user, message, ResourceAccessAction.READ);

            // Assert
            verify(chatChannelQueryService).verifyChannelAccess(organizationId, userId, channelId);
            verifyNoInteractions(chatValidator);
        }

        @Test
        void writeInOpenThread_runsAllFourChecks() {
            // Arrange
            ChatThread thread = mock(ChatThread.class);
            when(message.getThread()).thenReturn(thread);

            // Act
            resourceAccessValidator.verifyChatMessageRules(user, message, ResourceAccessAction.WRITE);

            // Assert
            verify(chatChannelQueryService).verifyChannelAccess(organizationId, userId, channelId);
            verify(chatValidator).ensureNotDailyUpdate(message);
            verify(chatValidator).ensureNotPullRequestShare(message);
            verify(chatValidator).ensureAuthor(message, user);
            verify(chatValidator).ensureThreadOpen(thread);
        }

        @Test
        void deleteWithoutThread_skipsTheThreadCheck() {
            // Arrange
            when(message.getThread()).thenReturn(null);

            // Act
            resourceAccessValidator.verifyChatMessageRules(user, message, ResourceAccessAction.DELETE);

            // Assert
            verify(chatValidator).ensureAuthor(message, user);
            verify(chatValidator, never()).ensureThreadOpen(any());
        }

        @Test
        void channelRefused_stopsBeforeTheWriteChecks() {
            // Arrange
            doThrow(mock(NotFoundException.class)).when(chatChannelQueryService)
                    .verifyChannelAccess(organizationId, userId, channelId);

            // Act and assert
            assertThrows(NotFoundException.class, () ->
                    resourceAccessValidator.verifyChatMessageRules(user, message, ResourceAccessAction.WRITE));
            verifyNoInteractions(chatValidator);
        }

        @Test
        void writeByAnotherUser_propagatesTheAuthorError() {
            // Arrange
            doThrow(mock(ForbiddenException.class)).when(chatValidator).ensureAuthor(message, user);

            // Act and assert
            assertThrows(ForbiddenException.class, () ->
                    resourceAccessValidator.verifyChatMessageRules(user, message, ResourceAccessAction.WRITE));
        }

        @Test
        void writeOnDailyUpdate_stopsBeforeTheAuthorCheck() {
            // Arrange
            doThrow(mock(ForbiddenException.class)).when(chatValidator).ensureNotDailyUpdate(message);

            // Act and assert
            assertThrows(ForbiddenException.class, () ->
                    resourceAccessValidator.verifyChatMessageRules(user, message, ResourceAccessAction.WRITE));
            verify(chatValidator, never()).ensureAuthor(any(), any());
        }
    }

    @Nested
    class VerifyKalkanPageRules {

        private KalkanPage page;

        @BeforeEach
        void setUpPage() {
            page = mock(KalkanPage.class);
        }

        @Test
        void read_requiresViewLevel() {
            // Arrange
            when(page.getOrganizationId()).thenReturn(organizationId);

            // Act
            resourceAccessValidator.verifyKalkanPageRules(user, page, ResourceAccessAction.READ);

            // Assert
            verify(kalkanAccessQueryService).verifyLevel(organizationId, page, user, KalkanLevel.VIEW);
        }

        @Test
        void write_requiresEditLevel() {
            // Arrange
            when(page.getOrganizationId()).thenReturn(organizationId);

            // Act
            resourceAccessValidator.verifyKalkanPageRules(user, page, ResourceAccessAction.WRITE);

            // Assert
            verify(kalkanAccessQueryService).verifyLevel(organizationId, page, user, KalkanLevel.EDIT);
        }

        @Test
        void delete_isRefusedWithKlkSec002WithoutALevelCheckAndLogsWarn() {
            // Arrange
            when(page.getId()).thenReturn(resourceId);

            // Act
            ForbiddenException thrown = assertThrows(ForbiddenException.class, () ->
                    resourceAccessValidator.verifyKalkanPageRules(user, page, ResourceAccessAction.DELETE));

            // Assert
            assertEquals("KLK_SEC_002", thrown.getErrorCode());
            verifyNoInteractions(kalkanAccessQueryService);
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(KalkanPageServiceLogConstant.ATTACHMENT_DELETE_REFUSED), eq(resourceId), eq(userId));
        }
    }

    @Nested
    class VerifyMailRules {

        private Mail mail;

        @BeforeEach
        void setUpMail() {
            mail = Mail.builder().id(resourceId).organizationId(organizationId).build();
        }

        @Test
        void read_passesWithoutTheLockCheck() {
            assertDoesNotThrow(() -> resourceAccessValidator.verifyMailRules(user, mail, ResourceAccessAction.READ));

            verifyNoInteractions(mailValidator);
        }

        @Test
        void write_checksTheMailIsUnsent() {
            resourceAccessValidator.verifyMailRules(user, mail, ResourceAccessAction.WRITE);

            verify(mailValidator).ensureMailUnsent(mail);
        }

        @Test
        void delete_checksTheMailIsUnsent() {
            resourceAccessValidator.verifyMailRules(user, mail, ResourceAccessAction.DELETE);

            verify(mailValidator).ensureMailUnsent(mail);
        }

        @Test
        void writeOnSentMail_throwsTheConflictOfTheValidator() {
            doThrow(new ConflictException(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE))
                    .when(mailValidator).ensureMailUnsent(mail);

            ConflictException thrown = assertThrows(ConflictException.class, () ->
                    resourceAccessValidator.verifyMailRules(user, mail, ResourceAccessAction.WRITE));

            assertEquals("OTK_STA_011", thrown.getErrorCode());
        }
    }
}
