package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.service.KalkanPageQueryService;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.service.CampaignContentQueryService;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.access.validator.ResourceAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;

/**
 * Implements the access check for entities addressed by type and id.
 *
 * Shrine orchestrates the apps here: each entity is loaded through the QueryService of the app that owns
 * it, and {@link ResourceAccessValidator} checks its organization and the rules of that app (board role, chat
 * channel and authorship, Kalkan level, the lock of a sent Otak mail). This class decides only the order of the checks. The switch over
 * {@link ResourceAccessType} has no default, so a new type does not compile until it has a loader and rules.
 *
 * The read grant lets a chat page read the files of the messages it has just checked without one access
 * check per message. It is bound to the calling thread and ends with the read.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceAccessQueryServiceImpl implements ResourceAccessQueryService {

    /**
     * The entities whose READ the current thread passes without a check, set only while
     * {@link #withReadGrant} runs.
     */
    private static final ThreadLocal<ReadGrant> READ_GRANT = new ThreadLocal<>();

    private final OrganizationAccessService organizationAccessService;
    private final TicketQueryService ticketQueryService;
    private final ChatChannelQueryService chatChannelQueryService;
    private final KalkanPageQueryService kalkanPageQueryService;
    private final MailQueryService mailQueryService;
    private final RelationQueryService relationQueryService;
    private final CampaignContentQueryService campaignContentQueryService;
    private final ResourceAccessValidator resourceAccessValidator;

    /**
     * A read grant of one user for entities of one type.
     */
    private record ReadGrant(UUID userId, ResourceAccessType type, Set<UUID> resourceIds) {

        boolean covers(User user, ResourceAccessType resourceType, UUID resourceId) {
            return userId.equals(user.getId()) && type == resourceType && resourceIds.contains(resourceId);
        }
    }

    /**
     * A loaded entity: the organization it belongs to and its type rules, bound to the user and the action.
     * The rules run only after the organization checks.
     */
    private record LoadedResource(UUID organizationId, Runnable rules) {
    }

    @Override
    public void verifyResourceAccess(User user, UUID organizationId, ResourceAccessType type, UUID resourceId,
                                     ResourceAccessAction action) {
        ReadGrant grant = READ_GRANT.get();
        // The grant's issuer has checked the organization and the type rules of the granted entities.
        if (action == ResourceAccessAction.READ && grant != null && grant.covers(user, type, resourceId)) {
            return;
        }

        // Loads before any membership check, so an absent or deleted entity is the owning app's 404.
        LoadedResource resource = switch (type) {
            case TICKET -> {
                Ticket ticket = ticketQueryService.getEntityById(resourceId);
                yield new LoadedResource(ticket.getBoard().getOrganizationId(),
                        () -> resourceAccessValidator.verifyTicketRules(user, ticket, action));
            }
            case CHAT_MESSAGE -> {
                ChatMessage message = chatChannelQueryService.getMessageEntityById(resourceId);
                yield new LoadedResource(message.getOrganizationId(),
                        () -> resourceAccessValidator.verifyChatMessageRules(user, message, action));
            }
            case KALKAN_PAGE -> {
                KalkanPage page = kalkanPageQueryService.getEntityById(resourceId);
                yield new LoadedResource(page.getOrganizationId(),
                        () -> resourceAccessValidator.verifyKalkanPageRules(user, page, action));
            }
            case MAIL -> {
                Mail mail = mailQueryService.getEntityById(resourceId);
                yield new LoadedResource(mail.getOrganizationId(),
                        () -> resourceAccessValidator.verifyMailRules(user, mail, action));
            }
            case RELATION -> {
                Relation relation = relationQueryService.getEntityById(resourceId);
                // Otak has no per-user visibility of relations: the organization, role and app checks
                // below are the whole rule.
                yield new LoadedResource(relation.getOrganizationId(), () -> { });
            }
            case CAMPAIGN_CONTENT -> {
                CampaignContent content = campaignContentQueryService.getEntityById(resourceId);
                // Every member who may open Otak sees and edits every campaign content of the organization, so
                // the organization, role and app checks below are the whole rule.
                yield new LoadedResource(content.getOrganizationId(), () -> { });
            }
        };

        resourceAccessValidator.verifySameOrganization(user, organizationId, type, resourceId,
                resource.organizationId());
        // Repeats the interceptor's membership query on purpose: it adds the action's role and the app, which
        // the interceptor skips for platform handlers, and covers callers that pass no interceptor.
        organizationAccessService.verifyOrganizationAccess(organizationId, user.getId(),
                type.minimumRole(action), type.getApp());
        resource.rules().run();
    }

    @Override
    public <T> T withReadGrant(User user, ResourceAccessType type, Collection<UUID> resourceIds, Supplier<T> read) {
        ReadGrant previous = READ_GRANT.get();
        READ_GRANT.set(new ReadGrant(user.getId(), type, Set.copyOf(resourceIds)));
        try {
            return read.get();
        } finally {
            // Restores the grant this one replaced, also when the read fails, so that a grant never outlives
            // its call on a pooled thread.
            if (previous == null) {
                READ_GRANT.remove();
            } else {
                READ_GRANT.set(previous);
            }
        }
    }

    @Override
    public <T> T withCheckedReadGrant(User user, ResourceAccessType type, UUID organizationId,
                                      Collection<UUID> resourceIds, Supplier<T> read) {
        organizationAccessService.verifyOrganizationAccess(organizationId, user.getId(),
                type.minimumRole(ResourceAccessAction.READ), type.getApp());
        return withReadGrant(user, type, resourceIds, read);
    }
}
