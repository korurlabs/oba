package com.korur.oba.apps.kaya.lesson.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.delivery.DeliveryProposalProperties;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.identity.helper.UserAccessHelper;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.constant.service.LessonServiceLogConstant;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryCreatedResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDetailResponse;
import com.korur.oba.apps.kaya.lesson.entity.Lesson;
import com.korur.oba.apps.kaya.lesson.helper.LessonDeliveryRateLimiter;
import com.korur.oba.apps.kaya.lesson.mapper.LessonMapper;
import com.korur.oba.apps.kaya.lesson.repository.LessonRepository;
import com.korur.oba.apps.kaya.lesson.service.LessonQueryService;
import com.korur.oba.apps.kaya.lesson.service.LessonService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.validator.AgentValidator;

@Service
@RequiredArgsConstructor
public class LessonServiceImpl implements LessonService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LessonServiceImpl.class);

    private final LessonRepository lessonRepository;
    private final LessonQueryService lessonQueryService;
    private final RepositoryQueryService repositoryQueryService;
    private final UserAccessHelper userAccessHelper;
    private final AgentValidator agentValidator;
    private final LessonMapper lessonMapper;
    private final DeliveryProposalProperties deliveryProposalProperties;
    private final LessonDeliveryRateLimiter lessonDeliveryRateLimiter;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public LessonDetailResponse create(User user, UUID organizationId, LessonRequest request) {
        return create(user, organizationId, request, null);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void admitCreate(User user) {
        Optional<Duration> wait = lessonDeliveryRateLimiter.tryChargeUserLesson(user.getId());
        if (wait.isPresent()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.LESSON_CREATE_RATE_LIMITED,
                    user.getId(), LessonConstant.USER_LESSONS_PER_MINUTE);
            throw new TooManyRequestsException(ExceptionDomainKayaError.SEC_RATE_LIMITED,
                    Map.of(LessonConstant.FIELD_LIMIT, LessonConstant.USER_LESSONS_PER_MINUTE,
                            LessonConstant.FIELD_POLICY, LessonConstant.POLICY_USER_LESSONS_PER_MINUTE),
                    wait.get());
        }
    }

    @Override
    @Transactional
    public LessonDetailResponse create(User user, UUID organizationId, LessonRequest request, String claudeSessionId) {
        // A lesson reaches every member's next session with no approval step, so an agent writes none,
        // through the web API or its machine token, which both end here.
        agentValidator.ensureCallerIsNotAgent(user, AgentConstant.ACTION_KAYA_LESSON_CREATE);
        requireWritableRepository(organizationId, request.repositoryId());
        Lesson lesson = lessonRepository.save(lessonMapper.toEntity(request, organizationId, user.getId(), claudeSessionId));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.LESSON_CREATED,
                user.getId(), lesson.getId(), organizationId);
        return lessonQueryService.toDetailResponse(user, organizationId, lesson);
    }

    @Override
    @Transactional
    public LessonDetailResponse update(User user, UUID organizationId, UUID lessonId, LessonRequest request) {
        // Lessons reach every later session, so an agent never rewrites one.
        agentValidator.ensureCallerIsNotAgent(user, AgentConstant.ACTION_KAYA_LESSON_UPDATE);
        Lesson lesson = lessonQueryService.getEntity(organizationId, lessonId);
        userAccessHelper.requireAdminOrSelf(user, organizationId, lesson.getUserId());
        // Only a move is checked, so a lesson whose repository was archived stays correctable.
        if (!Objects.equals(lesson.getRepositoryId(), request.repositoryId())) {
            requireWritableRepository(organizationId, request.repositoryId());
        }
        lessonMapper.updateEntity(lesson, request);
        // Flushed so that the response carries the updatedAt the auditing listener sets on write.
        Lesson saved = lessonRepository.saveAndFlush(lesson);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.LESSON_UPDATED,
                user.getId(), lessonId, organizationId);
        return lessonQueryService.toDetailResponse(user, organizationId, saved);
    }

    @Override
    @Transactional
    public void delete(User user, UUID organizationId, UUID lessonId) {
        // Also refused through the machine token, so an agent cannot remove a lesson people rely on.
        agentValidator.ensureCallerIsNotAgent(user, AgentConstant.ACTION_KAYA_LESSON_DELETE);
        Lesson lesson = lessonQueryService.getEntity(organizationId, lessonId);
        userAccessHelper.requireAdminOrSelf(user, organizationId, lesson.getUserId());
        lessonRepository.delete(lesson);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.LESSON_DELETED,
                user.getId(), lessonId, organizationId);
    }

    @Override
    @Transactional
    public LessonDeliveryCreatedResponse create(MachinePrincipal principal, LessonDeliveryRequest request) {
        User user = lessonQueryService.getActingUser(principal);
        // Refused before the checkout is looked up, so an agent gets the same 403 whatever remote it names.
        agentValidator.ensureCallerIsNotAgent(user, AgentConstant.ACTION_KAYA_LESSON_CREATE);
        UUID organizationId = principal.organizationId();
        UUID repositoryId = request.repoWide() ? null : requireCheckoutRepository(organizationId, request.remoteUrl());
        LessonDetailResponse lesson = create(user, organizationId,
                lessonMapper.toLessonRequest(request, repositoryId), request.claudeSessionId());
        return lessonMapper.toDeliveryCreatedResponse(lesson.id(), lessonUrlOf(lesson.id()));
    }

    @Override
    @Transactional
    public void delete(MachinePrincipal principal, UUID lessonId) {
        delete(lessonQueryService.getActingUser(principal), principal.organizationId(), lessonId);
    }

    /**
     * Without a transaction, for the reasons of {@link #admitCreate(User)}. The token is charged
     * before its user, so the refused lessons of one runaway token never use up the lessons of its
     * user's other machines.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void admitCreate(MachinePrincipal principal) {
        Optional<Duration> tokenWait = lessonDeliveryRateLimiter.tryChargeProposal(principal.tokenId());
        if (tokenWait.isPresent()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_PROPOSAL_RATE_LIMITED,
                    principal.machineId(), principal.tokenId(), LessonConstant.DELIVERY_PROPOSALS_PER_MINUTE);
            throw new TooManyRequestsException(ExceptionDomainKayaError.SEC_RATE_LIMITED,
                    Map.of(LessonConstant.FIELD_LIMIT, LessonConstant.DELIVERY_PROPOSALS_PER_MINUTE,
                            LessonConstant.FIELD_POLICY, LessonConstant.POLICY_TOKEN_LESSONS_PER_MINUTE), tokenWait.get());
        }
        Optional<Duration> userWait = lessonDeliveryRateLimiter.tryChargeUserLesson(principal.userId());
        if (userWait.isPresent()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.DELIVERY_PROPOSAL_USER_RATE_LIMITED,
                    principal.machineId(), principal.userId(), LessonConstant.USER_LESSONS_PER_MINUTE);
            throw new TooManyRequestsException(ExceptionDomainKayaError.SEC_RATE_LIMITED,
                    Map.of(LessonConstant.FIELD_LIMIT, LessonConstant.USER_LESSONS_PER_MINUTE,
                            LessonConstant.FIELD_POLICY, LessonConstant.POLICY_USER_LESSONS_PER_MINUTE), userWait.get());
        }
    }

    /**
     * The repository a lesson of a checkout belongs to. Without one the lesson could only fall back
     * to the whole organization, which only --repo-wide may ask for.
     */
    private UUID requireCheckoutRepository(UUID organizationId, String remoteUrl) {
        return lessonQueryService.findCheckoutRepository(organizationId, remoteUrl)
                .map(RepositoryResponse::id)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainKayaError.NOT_LESSON_FOUND,
                        Map.of(LessonConstant.FIELD_REMOTE_URL, LessonConstant.REMOTE_URL_UNKNOWN_MESSAGE)));
    }

    private String lessonUrlOf(UUID lessonId) {
        String webUrl = deliveryProposalProperties.webUrl();
        String origin = webUrl.endsWith(LessonConstant.URL_PATH_SEPARATOR)
                ? webUrl.substring(0, webUrl.length() - LessonConstant.URL_PATH_SEPARATOR.length())
                : webUrl;
        return origin + LessonConstant.LESSON_PAGE_PATH + lessonId;
    }

    /**
     * A lesson names a repository of its own organization that is not archived, or none. Lessons
     * and archived repositories follow the same rule as proposals: an archived repository takes no
     * new content.
     */
    private void requireWritableRepository(UUID organizationId, UUID repositoryId) {
        if (repositoryId == null) {
            return;
        }
        RepositoryResponse repository = repositoryQueryService.getRepositories(organizationId).stream()
                .filter(candidate -> candidate.id().equals(repositoryId))
                .findFirst()
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.REPOSITORY_NOT_IN_ORGANIZATION,
                            repositoryId, organizationId);
                    return new NotFoundException(ExceptionDomainKayaError.NOT_LESSON_FOUND,
                            Map.of(LessonConstant.FIELD_REPOSITORY_ID, repositoryId.toString()));
                });
        if (repository.status() == RepositoryStatus.ARCHIVED) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LessonServiceLogConstant.REPOSITORY_ARCHIVED,
                    repositoryId, organizationId);
            throw new ValidationException(ExceptionDomainKayaError.VAL_REPOSITORY_ARCHIVED,
                    Map.of(LessonConstant.FIELD_REPOSITORY_ID, repositoryId.toString()));
        }
    }
}
