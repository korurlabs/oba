package com.korur.oba.apps.kaya.lesson.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.lesson.dto.derived.LessonDelivery;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliverySearchResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDetailResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonFiltersResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonListResponse;
import com.korur.oba.apps.kaya.lesson.entity.Lesson;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;

/**
 * Reads the lessons of an organization for the lessons list, the lesson page and the delivery to
 * the CLI. A lesson of another organization answers the same 404 as an unknown id, so ids leak
 * nothing.
 */
public interface LessonQueryService {

    /**
     * Lists the lessons of the organization, newest first.
     *
     * @param user           the signed-in user, for canEdit
     * @param organizationId the organization of the request
     * @param request        paging (25 by default, at most 100) and filters
     * @return one page of lessons with the names of their repository and author and their sessions
     */
    Page<LessonListResponse> listLessons(User user, UUID organizationId, LessonQueryRequest request);

    /**
     * The options of the list's repository and user filters: the values the organization's
     * lessons hold.
     *
     * @param organizationId the organization of the request
     * @return the options, each list ordered by name
     */
    LessonFiltersResponse getFilters(UUID organizationId);

    /**
     * Reads one lesson with the session that recorded it.
     *
     * @param user           the signed-in user, for canEdit
     * @param organizationId the organization of the request
     * @param lessonId       the lesson
     * @return the lesson
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such lesson
     */
    LessonDetailResponse getLesson(User user, UUID organizationId, UUID lessonId);

    /**
     * Builds the response of a lesson already loaded, with the names of its repository and author
     * and its session.
     *
     * @param user           the signed-in user, for canEdit
     * @param organizationId the organization of the lesson
     * @param lesson         the lesson
     * @return the response
     */
    LessonDetailResponse toDetailResponse(User user, UUID organizationId, Lesson lesson);

    /**
     * Resolves a lesson within the organization.
     *
     * @param organizationId the organization the lesson must belong to
     * @param lessonId       the lesson
     * @return the lesson
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 otherwise
     */
    Lesson getEntity(UUID organizationId, UUID lessonId);

    /**
     * One page of the titles a checkout reads at session start: those of its repository plus the
     * organization-wide ones, newest first with the id as tie-breaker, with the ETag of every
     * applicable lesson. The ETag ignores since and the page, so a CLI that asks with a newer since
     * still gets 304, and every page of one state of the lessons carries the same ETag.
     *
     * A CLI that reads several pages sends the ETag of page 1 with every later page. The ETag and
     * the page come from one snapshot, so a page whose ETag matches belongs to the same state as page
     * 1; a set that changed in between is refused, so the CLI never stores pages of two states
     * under one ETag.
     *
     * A request without page reads page 1, but a CLI that sends none stores only that page, so its
     * answer carries the same digest under the unpaged form. An ETag stored next to the newest 100
     * then never answers 304 to a CLI that reads every page, and etag accepts only the paged form.
     *
     * @param principal the machine token
     * @param request   the checkout's git remote, raw (an unknown or unreadable one gives the
     *                  organization-wide lessons only); since, an ISO-8601 instant or null, after
     *                  which a created or updated lesson is marked changed; the page (1-based, null
     *                  when the request names none) and amount (100 by default and at most); and
     *                  etag, the paged ETag of page 1, required from page 2 on
     * @return the page and the ETag, of the paged form when the request names a page and of the
     *         unpaged form otherwise
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException GEN_VAL_001 for an
     *         unreadable since or a later page without etag, KAY_VAL_002 for an archived repository
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_VAL_017 when etag
     *         no longer names the lessons
     * @throws com.korur.oba.dojo.exception.internal.business.TooManyRequestsException KAY_SEC_004
     *         when the token made more lesson reads in a minute than
     *         {@link com.korur.oba.apps.kaya.lesson.constant.LessonConstant#DELIVERY_READS_PER_MINUTE},
     *         or the tokens of its organization together more than
     *         {@link com.korur.oba.apps.kaya.lesson.constant.LessonConstant#DELIVERY_ORGANIZATION_READS_PER_MINUTE}
     * @throws org.springframework.dao.QueryTimeoutException when the read runs past
     *         {@link com.korur.oba.apps.kaya.lesson.constant.LessonConstant#DELIVERY_READ_TIMEOUT_SECONDS},
     *         answered 503 GEN_UPS_004
     */
    LessonDelivery listForCheckout(MachinePrincipal principal, LessonDeliveryQueryRequest request);

    /**
     * Searches the lessons of a checkout by title and text, newest first, at most 100.
     *
     * @param principal the machine token
     * @param remoteUrl the checkout's git remote, raw; an unknown or unreadable one searches the
     *                  organization-wide lessons only
     * @param search    the text to find
     * @return the matching lessons with their text
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 for
     *         an archived repository
     * @throws com.korur.oba.dojo.exception.internal.business.TooManyRequestsException KAY_SEC_004
     *         when the token made more lesson reads in a minute than
     *         {@link com.korur.oba.apps.kaya.lesson.constant.LessonConstant#DELIVERY_READS_PER_MINUTE},
     *         or the tokens of its organization together more than
     *         {@link com.korur.oba.apps.kaya.lesson.constant.LessonConstant#DELIVERY_ORGANIZATION_READS_PER_MINUTE};
     *         a search counts as a read like a page of the list
     * @throws org.springframework.dao.QueryTimeoutException when the search runs past
     *         {@link com.korur.oba.apps.kaya.lesson.constant.LessonConstant#DELIVERY_READ_TIMEOUT_SECONDS},
     *         answered 503 GEN_UPS_004
     */
    List<LessonDeliverySearchResponse> searchForCheckout(MachinePrincipal principal, String remoteUrl, String search);

    /**
     * Reads one lesson for the CLI.
     *
     * @param principal the machine token
     * @param lessonId  the lesson
     * @return the lesson, with canEdit of the token's user
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such lesson
     */
    LessonDetailResponse getLesson(MachinePrincipal principal, UUID lessonId);

    /**
     * The user of a machine token. The machine token chain has already refused a token whose user
     * no longer acts in the organization with the Kaya app: a token outlives a user disabled, a
     * membership ended or a Kaya grant revoked after it was issued.
     *
     * @param principal the machine token
     * @return the user
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException when no user has the
     *         id of the token's user
     */
    User getActingUser(MachinePrincipal principal);

    /**
     * Finds the repository of a checkout's remote. Unlike the rest of delivery, an unknown remote
     * is not an error: it has no lessons of its own, but the organization-wide ones still apply.
     *
     * @param organizationId the organization of the machine token
     * @param remoteUrl      the checkout's git remote, raw
     * @return the repository, or empty when the remote is unknown or unreadable
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 for
     *         an archived repository
     */
    Optional<RepositoryResponse> findCheckoutRepository(UUID organizationId, String remoteUrl);
}
