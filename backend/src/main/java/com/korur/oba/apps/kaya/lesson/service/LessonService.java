package com.korur.oba.apps.kaya.lesson.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryCreatedResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDetailResponse;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * The writes of lessons. Every member creates; the author, an ADMIN or an OWNER edits and deletes.
 * A delete is final. A method that takes a user relies on its caller to have established that the
 * user acts in the organization; a method that takes a machine token checks it itself.
 */
public interface LessonService {

    /**
     * Creates a lesson written by a member, which no session recorded.
     *
     * @param user           the author
     * @param organizationId the organization of the request
     * @param request        the title, text and optional repository
     * @return the lesson
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 for
     *         an archived repository
     */
    LessonDetailResponse create(User user, UUID organizationId, LessonRequest request);

    /**
     * Charges one lesson a member adds in the web app to the member's budget, which the lessons
     * their machine tokens add share. It runs before the transaction of
     * {@link #create(User, UUID, LessonRequest)}, so a refused lesson takes no connection of the pool.
     *
     * @param user the signed-in member
     * @throws com.korur.oba.dojo.exception.internal.business.TooManyRequestsException KAY_SEC_004 when
     *         the member added more than
     *         {@link com.korur.oba.apps.kaya.lesson.constant.LessonConstant#USER_LESSONS_PER_MINUTE}
     *         lessons in the minute
     */
    void admitCreate(User user);

    /**
     * Creates a lesson recorded by a Claude Code session.
     *
     * @param user            the author
     * @param organizationId  the organization of the request
     * @param request         the title, text and optional repository
     * @param claudeSessionId the session that recorded the lesson, or null when it is not known;
     *                        Kaya need not have seen this session yet
     * @return the lesson
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 for
     *         an archived repository
     */
    LessonDetailResponse create(User user, UUID organizationId, LessonRequest request, String claudeSessionId);

    /**
     * Replaces the title, text and repository of a lesson. A lesson of a repository archived since
     * can still be edited; only moving a lesson to an archived repository is refused.
     *
     * @param user           the signed-in user
     * @param organizationId the organization of the request
     * @param lessonId       the lesson
     * @param request        the new title, text and repository
     * @return the lesson
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such lesson or repository
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_003 for a
     *         member who is neither the author nor ADMIN or OWNER
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 when
     *         the lesson moves to an archived repository
     */
    LessonDetailResponse update(User user, UUID organizationId, UUID lessonId, LessonRequest request);

    /**
     * Deletes a lesson for good.
     *
     * @param user           the signed-in user
     * @param organizationId the organization of the request
     * @param lessonId       the lesson
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such lesson
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_003 for a
     *         member who is neither the author nor ADMIN or OWNER
     */
    void delete(User user, UUID organizationId, UUID lessonId);

    /**
     * Adds a lesson from a checkout, written by the user of the machine token.
     *
     * @param principal the machine token
     * @param request   the lesson and the checkout's remote
     * @return the id of the lesson and its page in the web app
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when a
     *         lesson of the repository names a remote the organization does not have
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 for
     *         an archived repository
     */
    LessonDeliveryCreatedResponse create(MachinePrincipal principal, LessonDeliveryRequest request);

    /**
     * Charges one lesson a machine adds to the budget of its token and then to the budget of its
     * user, which the lessons the user adds in the web app share, so more tokens give a person no
     * more lessons. It runs before the transaction of
     * {@link #create(MachinePrincipal, LessonDeliveryRequest)}, so a refused lesson takes no
     * connection of the pool.
     *
     * @param principal the machine token
     * @throws com.korur.oba.dojo.exception.internal.business.TooManyRequestsException KAY_SEC_004
     *         when the token added more lessons in a minute than
     *         {@link com.korur.oba.apps.kaya.lesson.constant.LessonConstant#DELIVERY_PROPOSALS_PER_MINUTE},
     *         or its user more than
     *         {@link com.korur.oba.apps.kaya.lesson.constant.LessonConstant#USER_LESSONS_PER_MINUTE}, from
     *         every token and the web app together
     */
    void admitCreate(MachinePrincipal principal);

    /**
     * Deletes a lesson for good, under the same rule as a member's delete, for the user of the token.
     *
     * @param principal the machine token
     * @param lessonId  the lesson
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_003 when the
     *         user of the token is neither the author nor ADMIN or OWNER
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such lesson
     */
    void delete(MachinePrincipal principal, UUID lessonId);
}
