package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.service.KalkanPageQueryService;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.validator.ResourceAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAccessQueryServiceImplUnitTest {

    @Mock
    private OrganizationAccessService organizationAccessService;
    @Mock
    private TicketQueryService ticketQueryService;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private KalkanPageQueryService kalkanPageQueryService;
    @Mock
    private MailQueryService mailQueryService;
    @Mock
    private RelationQueryService relationQueryService;
    @Mock
    private ResourceAccessValidator resourceAccessValidator;

    private ResourceAccessQueryServiceImpl service;

    private User user;
    private UUID userId;
    private UUID organizationId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        service = new ResourceAccessQueryServiceImpl(organizationAccessService, ticketQueryService,
                chatChannelQueryService, kalkanPageQueryService, mailQueryService, relationQueryService,
                resourceAccessValidator);
        userId = UUID.randomUUID();
        user = User.builder().id(userId).build();
        organizationId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
    }

    private Ticket ticketOf(UUID boardOrganizationId) {
        Board board = mock(Board.class);
        when(board.getOrganizationId()).thenReturn(boardOrganizationId);
        Ticket ticket = mock(Ticket.class);
        when(ticket.getBoard()).thenReturn(board);
        return ticket;
    }

    private ChatMessage messageOf(UUID messageOrganizationId) {
        ChatMessage message = mock(ChatMessage.class);
        when(message.getOrganizationId()).thenReturn(messageOrganizationId);
        return message;
    }

    private KalkanPage pageOf(UUID pageOrganizationId) {
        KalkanPage page = mock(KalkanPage.class);
        when(page.getOrganizationId()).thenReturn(pageOrganizationId);
        return page;
    }

    @Nested
    class NotFound {

        @Test
        void ticketMissing_throwsNotFoundBeforeAnyOrganizationCheck() {
            when(ticketQueryService.getEntityById(resourceId)).thenThrow(mock(NotFoundException.class));

            assertThrows(NotFoundException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, resourceId, ResourceAccessAction.READ));

            verifyNoInteractions(organizationAccessService);
        }

        @Test
        void chatMessageMissing_throwsNotFoundBeforeAnyOrganizationCheck() {
            when(chatChannelQueryService.getMessageEntityById(resourceId)).thenThrow(mock(NotFoundException.class));

            assertThrows(NotFoundException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.CHAT_MESSAGE, resourceId, ResourceAccessAction.WRITE));

            verifyNoInteractions(organizationAccessService);
        }

        @Test
        void kalkanPageMissing_throwsNotFoundBeforeAnyOrganizationCheck() {
            when(kalkanPageQueryService.getEntityById(resourceId)).thenThrow(mock(NotFoundException.class));

            assertThrows(NotFoundException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.KALKAN_PAGE, resourceId, ResourceAccessAction.READ));

            verifyNoInteractions(organizationAccessService);
        }
    }

    @Nested
    class OrganizationMismatch {

        @Test
        void ticketOfAnotherOrganization_stopsBeforeMembershipAndRules() {
            UUID ticketOrganizationId = UUID.randomUUID();
            Ticket ticket = ticketOf(ticketOrganizationId);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);
            doThrow(mock(ForbiddenException.class)).when(resourceAccessValidator).verifySameOrganization(user,
                    organizationId, ResourceAccessType.TICKET, resourceId, ticketOrganizationId);

            assertThrows(ForbiddenException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, resourceId, ResourceAccessAction.READ));

            verifyNoInteractions(organizationAccessService);
            verify(resourceAccessValidator, never()).verifyTicketRules(any(), any(), any());
        }

        @Test
        void chatMessageOfAnotherOrganization_stopsBeforeMembershipAndRules() {
            UUID messageOrganizationId = UUID.randomUUID();
            ChatMessage message = messageOf(messageOrganizationId);
            when(chatChannelQueryService.getMessageEntityById(resourceId)).thenReturn(message);
            doThrow(mock(ForbiddenException.class)).when(resourceAccessValidator).verifySameOrganization(user,
                    organizationId, ResourceAccessType.CHAT_MESSAGE, resourceId, messageOrganizationId);

            assertThrows(ForbiddenException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.CHAT_MESSAGE, resourceId, ResourceAccessAction.DELETE));

            verifyNoInteractions(organizationAccessService);
            verify(resourceAccessValidator, never()).verifyChatMessageRules(any(), any(), any());
        }

        @Test
        void kalkanPageOfAnotherOrganization_stopsBeforeMembershipAndRules() {
            UUID pageOrganizationId = UUID.randomUUID();
            KalkanPage page = pageOf(pageOrganizationId);
            when(kalkanPageQueryService.getEntityById(resourceId)).thenReturn(page);
            doThrow(mock(ForbiddenException.class)).when(resourceAccessValidator).verifySameOrganization(user,
                    organizationId, ResourceAccessType.KALKAN_PAGE, resourceId, pageOrganizationId);

            assertThrows(ForbiddenException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.KALKAN_PAGE, resourceId, ResourceAccessAction.READ));

            verifyNoInteractions(organizationAccessService);
            verify(resourceAccessValidator, never()).verifyKalkanPageRules(any(), any(), any());
        }
    }

    @Nested
    class RoleAndApp {

        @Test
        void ticketRead_checksTheAppMinimumRoleOfKaizen() {
            Ticket ticket = ticketOf(organizationId);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);

            service.verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, resourceId,
                    ResourceAccessAction.READ);

            verify(organizationAccessService).verifyOrganizationAccess(organizationId, userId,
                    OrganizationMemberRole.GUEST, App.KAIZEN);
        }

        @Test
        void ticketWrite_checksMemberRole() {
            Ticket ticket = ticketOf(organizationId);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);

            service.verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, resourceId,
                    ResourceAccessAction.WRITE);

            verify(organizationAccessService).verifyOrganizationAccess(organizationId, userId,
                    OrganizationMemberRole.MEMBER, App.KAIZEN);
        }

        @Test
        void kalkanPageDelete_checksMemberRoleOfKalkan() {
            KalkanPage page = pageOf(organizationId);
            when(kalkanPageQueryService.getEntityById(resourceId)).thenReturn(page);

            service.verifyResourceAccess(user, organizationId, ResourceAccessType.KALKAN_PAGE, resourceId,
                    ResourceAccessAction.DELETE);

            verify(organizationAccessService).verifyOrganizationAccess(organizationId, userId,
                    OrganizationMemberRole.MEMBER, App.KALKAN);
        }

        @Test
        void roleOrAppRefused_rulesDoNotRun() {
            Ticket ticket = ticketOf(organizationId);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);
            doThrow(mock(ForbiddenException.class)).when(organizationAccessService)
                    .verifyOrganizationAccess(organizationId, userId, OrganizationMemberRole.MEMBER, App.KAIZEN);

            assertThrows(ForbiddenException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, resourceId, ResourceAccessAction.WRITE));

            verify(resourceAccessValidator, never()).verifyTicketRules(any(), any(), any());
        }
    }

    @Nested
    class Order {

        @Test
        void ticket_organizationMatchThenMembershipThenTicketRules() {
            Ticket ticket = ticketOf(organizationId);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);

            service.verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, resourceId,
                    ResourceAccessAction.READ);

            InOrder order = inOrder(ticketQueryService, resourceAccessValidator, organizationAccessService);
            order.verify(ticketQueryService).getEntityById(resourceId);
            order.verify(resourceAccessValidator).verifySameOrganization(user, organizationId,
                    ResourceAccessType.TICKET, resourceId, organizationId);
            order.verify(organizationAccessService).verifyOrganizationAccess(organizationId, userId,
                    OrganizationMemberRole.GUEST, App.KAIZEN);
            order.verify(resourceAccessValidator).verifyTicketRules(user, ticket, ResourceAccessAction.READ);
        }

        @Test
        void chatMessage_organizationMatchThenMembershipThenChatRules() {
            ChatMessage message = messageOf(organizationId);
            when(chatChannelQueryService.getMessageEntityById(resourceId)).thenReturn(message);

            service.verifyResourceAccess(user, organizationId, ResourceAccessType.CHAT_MESSAGE, resourceId,
                    ResourceAccessAction.WRITE);

            InOrder order = inOrder(chatChannelQueryService, resourceAccessValidator, organizationAccessService);
            order.verify(chatChannelQueryService).getMessageEntityById(resourceId);
            order.verify(resourceAccessValidator).verifySameOrganization(user, organizationId,
                    ResourceAccessType.CHAT_MESSAGE, resourceId, organizationId);
            order.verify(organizationAccessService).verifyOrganizationAccess(organizationId, userId,
                    OrganizationMemberRole.MEMBER, App.KAIZEN);
            order.verify(resourceAccessValidator).verifyChatMessageRules(user, message,
                    ResourceAccessAction.WRITE);
        }

        @Test
        void kalkanPage_organizationMatchThenMembershipThenKalkanRules() {
            KalkanPage page = pageOf(organizationId);
            when(kalkanPageQueryService.getEntityById(resourceId)).thenReturn(page);

            service.verifyResourceAccess(user, organizationId, ResourceAccessType.KALKAN_PAGE, resourceId,
                    ResourceAccessAction.READ);

            InOrder order = inOrder(kalkanPageQueryService, resourceAccessValidator, organizationAccessService);
            order.verify(kalkanPageQueryService).getEntityById(resourceId);
            order.verify(resourceAccessValidator).verifySameOrganization(user, organizationId,
                    ResourceAccessType.KALKAN_PAGE, resourceId, organizationId);
            order.verify(organizationAccessService).verifyOrganizationAccess(organizationId, userId,
                    OrganizationMemberRole.GUEST, App.KALKAN);
            order.verify(resourceAccessValidator).verifyKalkanPageRules(user, page, ResourceAccessAction.READ);
        }

        @Test
        void rulesRefuse_errorPropagates() {
            Ticket ticket = ticketOf(organizationId);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);
            doThrow(mock(NotFoundException.class)).when(resourceAccessValidator)
                    .verifyTicketRules(user, ticket, ResourceAccessAction.READ);

            assertThrows(NotFoundException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, resourceId, ResourceAccessAction.READ));
        }
    }

    @Nested
    class MailAndRelation {

        private Mail mailOf(UUID mailOrganizationId) {
            return Mail.builder().id(resourceId).organizationId(mailOrganizationId).build();
        }

        private Relation relationOf(UUID relationOrganizationId) {
            return Relation.builder().id(resourceId).organizationId(relationOrganizationId).build();
        }

        @Test
        void mailMissing_throwsNotFoundBeforeAnyOrganizationCheck() {
            when(mailQueryService.getEntityById(resourceId)).thenThrow(mock(NotFoundException.class));

            assertThrows(NotFoundException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.MAIL, resourceId, ResourceAccessAction.READ));

            verifyNoInteractions(organizationAccessService);
        }

        @Test
        void relationMissing_throwsNotFoundBeforeAnyOrganizationCheck() {
            when(relationQueryService.getEntityById(resourceId)).thenThrow(mock(NotFoundException.class));

            assertThrows(NotFoundException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.RELATION, resourceId, ResourceAccessAction.READ));

            verifyNoInteractions(organizationAccessService);
        }

        @Test
        void mailOfAnotherOrganization_stopsBeforeMembershipAndRules() {
            UUID mailOrganizationId = UUID.randomUUID();
            when(mailQueryService.getEntityById(resourceId)).thenReturn(mailOf(mailOrganizationId));
            doThrow(mock(ForbiddenException.class)).when(resourceAccessValidator).verifySameOrganization(user,
                    organizationId, ResourceAccessType.MAIL, resourceId, mailOrganizationId);

            assertThrows(ForbiddenException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.MAIL, resourceId, ResourceAccessAction.WRITE));

            verifyNoInteractions(organizationAccessService);
            verify(resourceAccessValidator, never()).verifyMailRules(any(), any(), any());
        }

        @Test
        void relationOfAnotherOrganization_stopsBeforeMembership() {
            UUID relationOrganizationId = UUID.randomUUID();
            when(relationQueryService.getEntityById(resourceId)).thenReturn(relationOf(relationOrganizationId));
            doThrow(mock(ForbiddenException.class)).when(resourceAccessValidator).verifySameOrganization(user,
                    organizationId, ResourceAccessType.RELATION, resourceId, relationOrganizationId);

            assertThrows(ForbiddenException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.RELATION, resourceId, ResourceAccessAction.READ));

            verifyNoInteractions(organizationAccessService);
        }

        @Test
        void mailRead_checksMemberRoleOfOtakThenMailRules() {
            Mail mail = mailOf(organizationId);
            when(mailQueryService.getEntityById(resourceId)).thenReturn(mail);

            service.verifyResourceAccess(user, organizationId, ResourceAccessType.MAIL, resourceId,
                    ResourceAccessAction.READ);

            InOrder order = inOrder(mailQueryService, resourceAccessValidator, organizationAccessService);
            order.verify(mailQueryService).getEntityById(resourceId);
            order.verify(resourceAccessValidator).verifySameOrganization(user, organizationId,
                    ResourceAccessType.MAIL, resourceId, organizationId);
            order.verify(organizationAccessService).verifyOrganizationAccess(organizationId, userId,
                    OrganizationMemberRole.MEMBER, App.OTAK);
            order.verify(resourceAccessValidator).verifyMailRules(user, mail, ResourceAccessAction.READ);
        }

        @Test
        void mailWriteAndDelete_checkAdminRoleOfOtak() {
            Mail mail = mailOf(organizationId);
            when(mailQueryService.getEntityById(resourceId)).thenReturn(mail);

            service.verifyResourceAccess(user, organizationId, ResourceAccessType.MAIL, resourceId,
                    ResourceAccessAction.WRITE);
            service.verifyResourceAccess(user, organizationId, ResourceAccessType.MAIL, resourceId,
                    ResourceAccessAction.DELETE);

            verify(organizationAccessService, times(2)).verifyOrganizationAccess(organizationId, userId,
                    OrganizationMemberRole.ADMIN, App.OTAK);
            verify(resourceAccessValidator).verifyMailRules(user, mail, ResourceAccessAction.WRITE);
            verify(resourceAccessValidator).verifyMailRules(user, mail, ResourceAccessAction.DELETE);
        }

        @Test
        void mailRoleRefused_rulesDoNotRun() {
            when(mailQueryService.getEntityById(resourceId)).thenReturn(mailOf(organizationId));
            doThrow(mock(ForbiddenException.class)).when(organizationAccessService)
                    .verifyOrganizationAccess(organizationId, userId, OrganizationMemberRole.ADMIN, App.OTAK);

            assertThrows(ForbiddenException.class, () -> service.verifyResourceAccess(user, organizationId,
                    ResourceAccessType.MAIL, resourceId, ResourceAccessAction.WRITE));

            verify(resourceAccessValidator, never()).verifyMailRules(any(), any(), any());
        }

        @Test
        void relationWriteDeleteAndRead_checkMemberRoleOfOtakAndRunNoRules() {
            when(relationQueryService.getEntityById(resourceId)).thenReturn(relationOf(organizationId));

            for (ResourceAccessAction action : ResourceAccessAction.values()) {
                service.verifyResourceAccess(user, organizationId, ResourceAccessType.RELATION, resourceId, action);
            }

            verify(organizationAccessService, times(ResourceAccessAction.values().length))
                    .verifyOrganizationAccess(organizationId, userId, OrganizationMemberRole.MEMBER, App.OTAK);
            verify(resourceAccessValidator, never()).verifyMailRules(any(), any(), any());
        }
    }

    @Nested
    class ReadGrant {

        @Test
        void withReadGrant_coveredRead_skipsEveryQuery() {
            service.withReadGrant(user, ResourceAccessType.TICKET, List.of(resourceId), () -> {
                service.verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, resourceId,
                        ResourceAccessAction.READ);
                return null;
            });

            verifyNoInteractions(ticketQueryService, organizationAccessService, resourceAccessValidator);
        }

        @Test
        void withReadGrant_write_isStillChecked() {
            Ticket ticket = ticketOf(organizationId);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);

            service.withReadGrant(user, ResourceAccessType.TICKET, List.of(resourceId), () -> {
                service.verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, resourceId,
                        ResourceAccessAction.WRITE);
                return null;
            });

            verify(ticketQueryService).getEntityById(resourceId);
        }

        @Test
        void withReadGrant_otherUserTypeOrId_isStillChecked() {
            Ticket ticket = ticketOf(organizationId);
            UUID otherId = UUID.randomUUID();
            User otherUser = User.builder().id(UUID.randomUUID()).build();
            when(ticketQueryService.getEntityById(otherId)).thenReturn(ticket);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);
            KalkanPage page = pageOf(organizationId);
            when(kalkanPageQueryService.getEntityById(resourceId)).thenReturn(page);

            service.withReadGrant(user, ResourceAccessType.TICKET, List.of(resourceId), () -> {
                service.verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, otherId,
                        ResourceAccessAction.READ);
                service.verifyResourceAccess(otherUser, organizationId, ResourceAccessType.TICKET, resourceId,
                        ResourceAccessAction.READ);
                service.verifyResourceAccess(user, organizationId, ResourceAccessType.KALKAN_PAGE, resourceId,
                        ResourceAccessAction.READ);
                return null;
            });

            verify(ticketQueryService).getEntityById(otherId);
            verify(ticketQueryService).getEntityById(resourceId);
            verify(kalkanPageQueryService).getEntityById(resourceId);
        }

        @Test
        void withReadGrant_afterTheReadEnds_nothingIsGranted() {
            Ticket ticket = ticketOf(organizationId);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);

            service.withReadGrant(user, ResourceAccessType.TICKET, List.of(resourceId), () -> "read");
            service.verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, resourceId,
                    ResourceAccessAction.READ);

            verify(ticketQueryService).getEntityById(resourceId);
        }

        @Test
        void withReadGrant_readFails_grantIsStillRemoved() {
            Ticket ticket = ticketOf(organizationId);
            when(ticketQueryService.getEntityById(resourceId)).thenReturn(ticket);

            assertThrows(IllegalStateException.class, () -> service.withReadGrant(user, ResourceAccessType.TICKET,
                    List.of(resourceId), () -> {
                        throw new IllegalStateException("read failed");
                    }));
            service.verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, resourceId,
                    ResourceAccessAction.READ);

            verify(ticketQueryService).getEntityById(resourceId);
        }

        @Test
        void withReadGrant_nested_restoresTheOuterGrant() {
            UUID innerId = UUID.randomUUID();
            AtomicInteger outerCoveredAfterInner = new AtomicInteger();

            service.withReadGrant(user, ResourceAccessType.TICKET, List.of(resourceId), () ->
                    service.withReadGrant(user, ResourceAccessType.TICKET, List.of(innerId), () -> null));
            service.withReadGrant(user, ResourceAccessType.TICKET, List.of(resourceId), () -> {
                service.withReadGrant(user, ResourceAccessType.TICKET, List.of(innerId), () -> null);
                service.verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, resourceId,
                        ResourceAccessAction.READ);
                outerCoveredAfterInner.incrementAndGet();
                return null;
            });

            assertEquals(1, outerCoveredAfterInner.get());
            verifyNoInteractions(ticketQueryService);
        }

        @Test
        void withCheckedReadGrant_checksTheOrganizationBeforeTheRead() {
            List<String> events = new java.util.ArrayList<>();
            when(organizationAccessService.verifyOrganizationAccess(organizationId, userId,
                    OrganizationMemberRole.GUEST, App.KAIZEN)).thenAnswer(invocation -> {
                events.add("organization");
                return null;
            });

            String result = service.withCheckedReadGrant(user, ResourceAccessType.TICKET, organizationId,
                    List.of(resourceId), () -> {
                        events.add("read");
                        return "done";
                    });

            assertEquals("done", result);
            assertEquals(List.of("organization", "read"), events);
        }

        @Test
        void withCheckedReadGrant_organizationRefused_readNeverRuns() {
            doThrow(mock(ForbiddenException.class)).when(organizationAccessService)
                    .verifyOrganizationAccess(organizationId, userId, OrganizationMemberRole.GUEST, App.KAIZEN);
            AtomicInteger reads = new AtomicInteger();

            assertThrows(ForbiddenException.class, () -> service.withCheckedReadGrant(user,
                    ResourceAccessType.TICKET, organizationId, List.of(resourceId), () -> {
                        reads.incrementAndGet();
                        return null;
                    }));

            assertTrue(reads.get() == 0);
        }
    }
}
