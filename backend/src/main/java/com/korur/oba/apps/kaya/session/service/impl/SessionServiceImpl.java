package com.korur.oba.apps.kaya.session.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;
import com.korur.oba.apps.kaya.session.constant.service.SessionServiceLogConstant;
import com.korur.oba.apps.kaya.session.dto.request.SessionLinkCreateRequest;
import com.korur.oba.apps.kaya.session.dto.response.SessionLinkResponse;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.entity.SessionLink;
import com.korur.oba.apps.kaya.session.enums.SessionLinkKind;
import com.korur.oba.apps.kaya.session.event.SessionUpdatedEvent;
import com.korur.oba.apps.kaya.session.mapper.SessionMapper;
import com.korur.oba.apps.kaya.session.repository.SessionLinkRepository;
import com.korur.oba.apps.kaya.session.repository.SessionRepository;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;
import com.korur.oba.apps.kaya.session.service.SessionService;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SessionServiceImpl.class);
    private static final Pattern KAZAN_TICKET = Pattern.compile(SessionConstant.KAZAN_TICKET_PATTERN);
    private static final Pattern PULL_REQUEST_URL = Pattern.compile(SessionConstant.PULL_REQUEST_URL_PATTERN);

    private final SessionRepository sessionRepository;
    private final SessionLinkRepository sessionLinkRepository;
    private final SessionQueryService sessionQueryService;
    private final UserAccessHelper userAccessHelper;
    private final RepositoryQueryService repositoryQueryService;
    private final SessionMapper sessionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public SessionLinkResponse addLink(User user, UUID organizationId, String sessionId, SessionLinkCreateRequest request) {
        Session session = sessionQueryService.getEntityBySessionId(organizationId, sessionId);
        String reference = request.reference().strip();
        String url = urlOf(session, request.kind(), reference);

        int added = sessionLinkRepository.insertManualLinkIfAbsent(session.getId(), request.kind().name(), reference, url, user.getId());
        SessionLink link = sessionLinkRepository.findBySession_IdAndKindAndReference(session.getId(), request.kind(), reference)
                .orElseThrow(() -> new IllegalStateException(SessionServiceLogConstant.LINK_MISSING_AFTER_INSERT.formatted(
                        request.kind(), reference, session.getId())));
        if (added == 0) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.LINK_ALREADY_PRESENT,
                    session.getId(), request.kind(), reference);
            return sessionMapper.toLinkResponse(link);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.LINK_ADDED,
                user.getId(), request.kind(), reference, session.getId());
        publishUpdated(session);
        return sessionMapper.toLinkResponse(link);
    }

    @Override
    @Transactional
    public void removeLink(User user, UUID organizationId, String sessionId, UUID linkId) {
        Session session = sessionQueryService.getEntityBySessionId(organizationId, sessionId);
        SessionLink link = sessionLinkRepository.findByIdAndSession_Id(linkId, session.getId()).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.LINK_NOT_FOUND,
                    linkId, session.getId(), organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                    Map.of(SessionConstant.FIELD_LINK_ID, linkId));
        });
        sessionLinkRepository.delete(link);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.LINK_REMOVED, user.getId(), linkId, session.getId());
        publishUpdated(session);
    }

    @Override
    @Transactional
    public void deleteSession(User user, UUID organizationId, String sessionId) {
        Session session = sessionQueryService.getEntityBySessionId(organizationId, sessionId);
        userAccessHelper.requireAdminOrSelf(user, organizationId, session.getUserId());

        // The events, subagents, skills, handoffs and links go with the session by ON DELETE CASCADE.
        sessionRepository.delete(session);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.SESSION_DELETED,
                user.getId(), session.getId(), sessionId, organizationId);
        // A null last event tells the stream's clients the session is gone.
        eventPublisher.publishEvent(new SessionUpdatedEvent(organizationId, sessionId, null));
    }

    @Override
    @Transactional
    public int deleteExpiredSessions(Instant now) {
        return sessionRepository.deleteExpired(now);
    }

    /**
     * The URL of a manual link, after checking the reference fits its kind. A ticket's URL comes
     * from the repository's template, as the recorder's do, and is left out when it would not fit
     * the column.
     */
    private String urlOf(Session session, SessionLinkKind kind, String reference) {
        if (kind == SessionLinkKind.KAZAN_TICKET) {
            if (!KAZAN_TICKET.matcher(reference).matches()) {
                throw invalidReference(session, kind, reference, SessionConstant.LINK_RULE_KAZAN_TICKET);
            }
            String template = repositoryQueryService.getEntityById(session.getOrganizationId(), session.getRepositoryId())
                    .getTicketUrlTemplate();
            String url = template == null ? null : template.replace(SessionConstant.TICKET_URL_KEY_PLACEHOLDER, reference);
            return url != null && url.length() > SessionConstant.LINK_URL_MAX_LENGTH ? null : url;
        }
        if (!PULL_REQUEST_URL.matcher(reference).matches()) {
            throw invalidReference(session, kind, reference, SessionConstant.LINK_RULE_PULL_REQUEST);
        }
        return reference;
    }

    private ValidationException invalidReference(Session session, SessionLinkKind kind, String reference, String rule) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SessionServiceLogConstant.LINK_REFERENCE_INVALID,
                kind, reference, session.getId(), rule);
        return new ValidationException(ExceptionDomainKayaError.VAL_LINK_REFERENCE_INVALID,
                Map.of(SessionConstant.FIELD_KIND, kind.name(), SessionConstant.FIELD_RULE, rule));
    }

    private void publishUpdated(Session session) {
        eventPublisher.publishEvent(new SessionUpdatedEvent(session.getOrganizationId(), session.getSessionId(), session.getLastEventAt()));
    }
}
