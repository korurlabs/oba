package com.korur.oba.shrine.access.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;
import com.korur.oba.apps.kalkan.page.constant.service.KalkanPageServiceLogConstant;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.validator.MailValidator;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Validates access to an entity addressed by type and id once it is loaded: that it belongs to the request's
 * organization, and the rules of the app that owns it (board role, chat channel and authorship, Kalkan level,
 * the lock of a sent Otak mail).
 *
 * {@link ResourceAccessQueryService} loads the entity and decides the order; each check here throws on refusal
 * and returns nothing.
 */
@Component
@RequiredArgsConstructor
public class ResourceAccessValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ResourceAccessValidator.class);

    private final BoardAccessService boardAccessService;
    private final ChatChannelQueryService chatChannelQueryService;
    private final ChatValidator chatValidator;
    private final KalkanAccessQueryService kalkanAccessQueryService;
    private final MailValidator mailValidator;
    private final LoggingService loggingService;

    /**
     * Refuses an entity of another organization than the request's, before any membership or rule of that
     * organization is consulted, so a header for one organization never opens data of another.
     *
     * @param user                   the caller
     * @param organizationId         the request organization
     * @param type                   the type of the entity
     * @param resourceId             the entity identifier
     * @param resourceOrganizationId the organization the entity belongs to
     * @throws ForbiddenException APP_SEC_002 when the organizations differ
     */
    public void verifySameOrganization(User user, UUID organizationId, ResourceAccessType type, UUID resourceId,
                                       UUID resourceOrganizationId) {
        if (resourceOrganizationId.equals(organizationId)) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, AccessExceptionLogConstant.RESOURCE_ORGANIZATION_MISMATCH,
                user.getId(), type, resourceId, resourceOrganizationId, organizationId);
        throw new ForbiddenException(
                ExceptionDomainAccessError.SEC_RESOURCE_ORGANIZATION_MISMATCH,
                Map.of(
                        AccessConstant.FIELD_RESOURCE_TYPE, type.name(),
                        AccessConstant.FIELD_RESOURCE_ID, resourceId));
    }

    /**
     * Demands the board role the ticket endpoints demand: VIEWER to read, MEMBER to write or delete. A
     * non-member of the board gets the board's not-found error, so the ticket stays invisible.
     *
     * @param user   the caller
     * @param ticket the loaded ticket
     * @param action the action the caller wants to take
     */
    public void verifyTicketRules(User user, Ticket ticket, ResourceAccessAction action) {
        BoardMemberRole minimumBoardRole = switch (action) {
            case READ -> BoardMemberRole.VIEWER;
            case WRITE, DELETE -> BoardMemberRole.MEMBER;
        };
        boardAccessService.verifyBoardAccess(ticket.getBoard().getOrganizationId(), ticket.getBoard().getId(),
                user.getId(), minimumBoardRole);
    }

    /**
     * Demands a channel the user can read. A change of the message's files also needs the message to be a
     * plain message of the user in an open thread: a daily update message changes only through its daily
     * update, a pull request share entry only through its share, and the files of a reply in a closed thread
     * stay as they are, like its text, until the thread is reopened.
     *
     * @param user    the caller
     * @param message the loaded chat message
     * @param action  the action the caller wants to take
     */
    public void verifyChatMessageRules(User user, ChatMessage message, ResourceAccessAction action) {
        chatChannelQueryService.verifyChannelAccess(message.getOrganizationId(), user.getId(),
                message.getChannelId());
        if (action == ResourceAccessAction.READ) {
            return;
        }
        chatValidator.ensureNotDailyUpdate(message);
        chatValidator.ensureNotPullRequestShare(message);
        chatValidator.ensureAuthor(message, user);
        if (message.getThread() != null) {
            chatValidator.ensureThreadOpen(message.getThread());
        }
    }

    /**
     * Demands the page's root level: VIEW to read, EDIT to write. No file of a Kalkan page is ever deleted:
     * the images and evidence files of a documented information page are part of its record.
     *
     * @param user   the caller
     * @param page   the loaded Kalkan page
     * @param action the action the caller wants to take
     * @throws ForbiddenException KLK_SEC_002 on every DELETE
     */
    public void verifyKalkanPageRules(User user, KalkanPage page, ResourceAccessAction action) {
        KalkanLevel minimumLevel = switch (action) {
            case READ -> KalkanLevel.VIEW;
            case WRITE -> KalkanLevel.EDIT;
            case DELETE -> {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        KalkanPageServiceLogConstant.ATTACHMENT_DELETE_REFUSED, page.getId(), user.getId());
                throw new ForbiddenException(ExceptionDomainKalkanError.SEC_ATTACHMENT_DELETE_REFUSED,
                        Map.of(KalkanPageConstant.FIELD_PAGE_ID, page.getId().toString()));
            }
        };
        kalkanAccessQueryService.verifyLevel(page.getOrganizationId(), page, user, minimumLevel);
    }

    /**
     * Lets every reader of the mail read its files, and refuses a write or delete once the mail's outreach is
     * SENT: what the contact received stays as it was sent. The admin role a write needs is checked before,
     * through {@link ResourceAccessType#MAIL}.
     *
     * The lock is read with a row lock on the outreach, so a WRITE or DELETE must run inside a read-write
     * transaction; the attachment writes do.
     *
     * @param user   the caller
     * @param mail   the loaded mail
     * @param action the action the caller wants to take
     * @throws ConflictException OTK_STA_011 on a WRITE or DELETE when the outreach is SENT
     */
    public void verifyMailRules(User user, Mail mail, ResourceAccessAction action) {
        if (action == ResourceAccessAction.READ) {
            return;
        }
        mailValidator.ensureMailUnsent(mail);
    }
}
