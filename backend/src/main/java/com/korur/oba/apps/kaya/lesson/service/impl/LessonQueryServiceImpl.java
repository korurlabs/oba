package com.korur.oba.apps.kaya.lesson.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.constant.service.LessonServiceLogConstant;
import com.korur.oba.apps.kaya.lesson.dto.derived.LessonDelivery;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryProjection;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryStateProjection;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliverySearchResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDetailResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonFiltersResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonListResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.embedded.LessonReferenceResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.embedded.LessonSessionResponse;
import com.korur.oba.apps.kaya.lesson.entity.Lesson;
import com.korur.oba.apps.kaya.lesson.helper.LessonDeliveryRateLimiter;
import com.korur.oba.apps.kaya.lesson.helper.LessonEtagHelper;
import com.korur.oba.apps.kaya.lesson.mapper.LessonMapper;
import com.korur.oba.apps.kaya.lesson.repository.LessonRepository;
import com.korur.oba.apps.kaya.lesson.service.LessonQueryService;
import com.korur.oba.apps.kaya.lesson.specification.LessonSpecification;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.helper.RemoteUrlHelper;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonQueryServiceImpl implements LessonQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LessonQueryServiceImpl.class);
    // A fixed order, with the id as tie-breaker, keeps pages stable and keeps arbitrary sortBy
    // values from reaching the query.
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Direction.DESC, LessonConstant.FIELD_CREATED_AT).and(Sort.by(Sort.Direction.DESC, LessonConstant.FIELD_ID));

    private final LessonRepository lessonRepository;
    private final SessionQueryService sessionQueryService;
    private final KayaUserQueryService kayaUserQueryService;
    private final RepositoryQueryService repositoryQueryService;
    private final OrganizationAccessQueryService organizationAccessQueryService;
    private final LessonDeliveryRateLimiter lessonDeliveryRateLimiter;
    private final LessonMapper lessonMapper;
    private final LoggingService loggingService;

    @Override
    public Page<LessonListResponse> listLessons(User user, UUID organizationId, LessonQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.LISTING_LESSONS, organizationId, request);

        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), NEWEST_FIRST);
        Page<Lesson> lessons = lessonRepository.findAll(LessonSpecification.filterBy(organizationId, request), pageable);

        // The names, the sessions and the role come once for the whole page instead of once per lesson.
        Map<UUID, String> repositoryNames = repositoryNames(organizationId);
        Map<UUID, String> userNames = kayaUserQueryService.getDisplayNames(organizationId,
                lessons.getContent().stream().map(Lesson::getUserId).collect(Collectors.toSet()));
        Map<String, Session> sessions = sessionQueryService.findEntitiesBySessionIds(organizationId,
                lessons.getContent().stream().map(Lesson::getClaudeSessionId).filter(Objects::nonNull).collect(Collectors.toSet()));
        boolean admin = isAdmin(user, organizationId);

        return lessons.map(lesson -> lessonMapper.toListResponse(lesson,
                reference(lesson.getRepositoryId(), repositoryNames),
                reference(lesson.getUserId(), userNames),
                sessionOf(lesson, sessions),
                canEdit(user, lesson, admin)));
    }

    @Override
    public LessonFiltersResponse getFilters(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.READING_FILTERS, organizationId);
        List<UUID> userIds = lessonRepository.findDistinctUserIds(organizationId);
        return lessonMapper.toFiltersResponse(
                references(lessonRepository.findDistinctRepositoryIds(organizationId), repositoryNames(organizationId)),
                references(userIds, kayaUserQueryService.getDisplayNames(organizationId, userIds)));
    }

    @Override
    public LessonDetailResponse getLesson(User user, UUID organizationId, UUID lessonId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.READING_LESSON, lessonId, organizationId);
        return toDetailResponse(user, organizationId, getEntity(organizationId, lessonId));
    }

    @Override
    public LessonDetailResponse toDetailResponse(User user, UUID organizationId, Lesson lesson) {
        Map<UUID, String> userNames = kayaUserQueryService.getDisplayNames(organizationId, List.of(lesson.getUserId()));
        return lessonMapper.toDetailResponse(lesson,
                reference(lesson.getRepositoryId(), repositoryNames(organizationId)),
                reference(lesson.getUserId(), userNames),
                sessionOf(organizationId, lesson),
                canEdit(user, lesson, isAdmin(user, organizationId)));
    }

    @Override
    public Lesson getEntity(UUID organizationId, UUID lessonId) {
        // Looked up within the organization, so a lesson of another organization answers the same
        // 404 as an unknown id.
        return lessonRepository.findByIdAndOrganizationId(lessonId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.LESSON_NOT_FOUND, lessonId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_LESSON_FOUND,
                    Map.of(LessonConstant.FIELD_LESSON_ID, lessonId.toString()));
        });
    }

    /**
     * Reads the ETag and the page in two statements of one snapshot. Under READ COMMITTED each
     * statement sees the lessons as they are when it starts, so a change between the two would pair
     * a page with the ETag of another state; REPEATABLE READ keeps the snapshot of the first
     * statement for the whole transaction. A transaction that only reads never fails its
     * serialization check in PostgreSQL, so nothing has to be retried.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LessonDelivery listForCheckout(MachinePrincipal principal, LessonDeliveryQueryRequest request) {
        UUID organizationId = principal.organizationId();
        Instant seenAt = parseSince(principal, request.getSince());
        boolean paged = request.getPage() != null;
        int page = paged ? request.getPage() : LessonConstant.DELIVERY_FIRST_PAGE;
        requireEtagAfterFirstPage(principal, page, request.getEtag());
        chargeRead(principal);
        Optional<RepositoryResponse> repository = findCheckoutRepository(organizationId, request.getRemoteUrl());
        UUID repositoryId = repository.map(RepositoryResponse::id).orElse(null);
        String repositoryName = repository.map(RepositoryResponse::name).orElse(null);
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.READING_DELIVERY, repositoryId, organizationId);

        LessonDeliveryStateProjection state = lessonRepository.findDeliveryState(organizationId, repositoryId);
        String pagedEtag = LessonEtagHelper.pagedEtagOf(state);
        if (request.getEtag() != null && !LessonEtagHelper.matches(request.getEtag(), pagedEtag)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_LESSONS_CHANGED,
                    repositoryId, organizationId, page, principal.machineId());
            throw new ConflictException(ExceptionDomainKayaError.VAL_LESSONS_CHANGED);
        }
        Pageable pageable = PageRequest.of(page - 1, request.getAmount(), NEWEST_FIRST);
        // A page at or past the end is empty without a query, which also keeps a page number far past
        // the end from reaching the database as an offset beyond the int range.
        List<LessonDeliveryProjection> rows = pageable.getOffset() >= state.getTotal() ? List.of()
                : lessonRepository.findDeliveryLessons(organizationId, repositoryId,
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        List<LessonDeliveryResponse> lessons = rows.stream()
                .map(lesson -> lessonMapper.toDeliveryResponse(lesson,
                        lesson.getRepositoryId() == null ? null : repositoryName,
                        seenAt != null && lesson.getUpdatedAt().isAfter(seenAt)))
                .toList();
        // A CLI that sends no page stores only this page; one that reads every page stores the whole
        // set. Each answer is named by its own form, so an If-None-Match of one never matches the other.
        String etag = paged ? pagedEtag : LessonEtagHelper.unpagedEtagOf(state);
        return lessonMapper.toDelivery(new PageImpl<>(lessons, pageable, state.getTotal()), etag);
    }

    @Override
    public List<LessonDeliverySearchResponse> searchForCheckout(MachinePrincipal principal, String remoteUrl, String search) {
        UUID organizationId = principal.organizationId();
        chargeRead(principal);
        Optional<RepositoryResponse> repository = findCheckoutRepository(organizationId, remoteUrl);
        UUID repositoryId = repository.map(RepositoryResponse::id).orElse(null);
        String repositoryName = repository.map(RepositoryResponse::name).orElse(null);
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.SEARCHING_DELIVERY, repositoryId, organizationId);

        Pageable capped = PageRequest.of(0, LessonConstant.DELIVERY_ITEMS_MAX, NEWEST_FIRST);
        return lessonRepository.findAll(
                        LessonSpecification.applicableTo(organizationId, repositoryId).and(LessonSpecification.searchSpecification(search)),
                        capped)
                .map(lesson -> lessonMapper.toDeliverySearchResponse(lesson,
                        lesson.getRepositoryId() == null ? null : repositoryName))
                .getContent();
    }

    @Override
    public LessonDetailResponse getLesson(MachinePrincipal principal, UUID lessonId) {
        return getLesson(getActingUser(principal), principal.organizationId(), lessonId);
    }

    @Override
    public User getActingUser(MachinePrincipal principal) {
        return kayaUserQueryService.getEntityById(principal.userId());
    }

    @Override
    public Optional<RepositoryResponse> findCheckoutRepository(UUID organizationId, String remoteUrl) {
        // Resolved without the throwing lookup of delivery, and the raw remote is neither logged nor
        // echoed: it may carry an access token.
        String normalizedRemoteUrl = RemoteUrlHelper.normalize(remoteUrl);
        Optional<RepositoryResponse> repository = normalizedRemoteUrl == null ? Optional.empty()
                : repositoryQueryService.getRepositories(organizationId).stream()
                        .filter(candidate -> candidate.remoteUrl().equals(normalizedRemoteUrl))
                        .findFirst();
        if (repository.isEmpty()) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_REPOSITORY_UNKNOWN, organizationId);
            return repository;
        }
        if (repository.get().status() == RepositoryStatus.ARCHIVED) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.REPOSITORY_ARCHIVED,
                    repository.get().id(), organizationId);
            throw new ValidationException(ExceptionDomainKayaError.VAL_REPOSITORY_ARCHIVED,
                    Map.of(LessonConstant.FIELD_REPOSITORY_ID, repository.get().id().toString()));
        }
        return repository;
    }

    private Instant parseSince(MachinePrincipal principal, String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(since);
        } catch (DateTimeParseException exception) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_SINCE_INVALID, principal.machineId());
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(LessonConstant.FIELD_SINCE, LessonConstant.SINCE_INVALID_MESSAGE));
        }
    }

    /**
     * Refuses a page after the first without the ETag of page 1: without it a change between the
     * requests would go unnoticed, and the CLI would store pages of two states of the lessons.
     */
    private void requireEtagAfterFirstPage(MachinePrincipal principal, int page, String etag) {
        if (page > LessonConstant.DELIVERY_FIRST_PAGE && etag == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_ETAG_MISSING,
                    page, principal.machineId());
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(LessonConstant.FIELD_ETAG, LessonConstant.ETAG_REQUIRED_MESSAGE));
        }
    }

    /**
     * Charges one read to the token before any lesson is read, so a refused read costs no pass over
     * the lessons.
     */
    private void chargeRead(MachinePrincipal principal) {
        if (!lessonDeliveryRateLimiter.tryCharge(principal.tokenId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_RATE_LIMITED,
                    principal.machineId(), principal.tokenId(), LessonConstant.DELIVERY_READS_PER_MINUTE);
            throw new TooManyRequestsException(ExceptionDomainKayaError.SEC_RATE_LIMITED,
                    Map.of(LessonConstant.FIELD_LIMIT, LessonConstant.DELIVERY_READS_PER_MINUTE));
        }
    }

    private LessonSessionResponse sessionOf(UUID organizationId, Lesson lesson) {
        if (lesson.getClaudeSessionId() == null) {
            return null;
        }
        return sessionQueryService.findEntityBySessionId(organizationId, lesson.getClaudeSessionId())
                .map(lessonMapper::toSessionResponse)
                .orElse(null);
    }

    private LessonSessionResponse sessionOf(Lesson lesson, Map<String, Session> sessions) {
        return lesson.getClaudeSessionId() == null ? null : lessonMapper.toSessionResponse(sessions.get(lesson.getClaudeSessionId()));
    }

    private boolean isAdmin(User user, UUID organizationId) {
        return organizationAccessQueryService.verifyTenantAccess(user, organizationId).getRole().isAtLeast(OrganizationMemberRole.ADMIN);
    }

    /**
     * The edit rule of a lesson without the refusal: its author, or an ADMIN or OWNER. It is the
     * rule LessonService applies, so the page offers Edit and Delete exactly when the API accepts them.
     */
    private static boolean canEdit(User user, Lesson lesson, boolean admin) {
        return admin || user.getId().equals(lesson.getUserId());
    }

    private Map<UUID, String> repositoryNames(UUID organizationId) {
        return repositoryQueryService.getRepositories(organizationId).stream()
                .collect(Collectors.toMap(RepositoryResponse::id, RepositoryResponse::name, (first, second) -> first));
    }

    private LessonReferenceResponse reference(UUID id, Map<UUID, String> names) {
        return id == null ? null : lessonMapper.toReferenceResponse(id, names.get(id));
    }

    private List<LessonReferenceResponse> references(Collection<UUID> ids, Map<UUID, String> names) {
        return ids.stream()
                .map(id -> reference(id, names))
                .sorted(Comparator.comparing(LessonReferenceResponse::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(LessonReferenceResponse::id))
                .toList();
    }
}
