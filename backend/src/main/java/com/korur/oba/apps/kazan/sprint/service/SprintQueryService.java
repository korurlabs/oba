package com.korur.oba.apps.kazan.sprint.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.korur.oba.apps.kazan.sprint.dto.response.SprintChildBoardResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintDetailResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintListResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintStartPreviewResponse;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for sprints.
 */
public interface SprintQueryService {

    /**
     * Lists sprints for a board.
     */
    List<SprintListResponse> listSprints(User user, UUID organizationId, UUID boardId);

    /**
     * Lists the descendant boards, at every depth, that a sprint created on the given board can
     * also be linked to. Only boards the user is a member of are listed; a listed board can sit
     * under a board the user is not a member of. The order is depth-first, each parent before
     * its children. Requires ADMIN or OWNER on the given board.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param boardId        the board the sprint is created on
     * @return the candidate boards with depth, link permission and active sprint
     */
    List<SprintChildBoardResponse> listChildBoards(User user, UUID organizationId, UUID boardId);

    /**
     * Previews what starting a PLANNED sprint would close: one entry per board the user is a
     * member of that is linked to another ACTIVE sprint sharing a board with this sprint, in
     * the link order of this sprint, then boards it is not linked to. Requires ADMIN or OWNER
     * on the given board.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param boardId        the board of the request path
     * @param sprintId       the sprint to start
     * @return the blocking boards and whether the caller may take them over; no entries when
     *         a plain start works
     */
    SprintStartPreviewResponse getStartPreview(User user, UUID organizationId, UUID boardId, UUID sprintId);

    /**
     * Retrieves sprint details by id.
     */
    SprintDetailResponse getSprint(User user, UUID organizationId, UUID boardId, UUID sprintId);

    /**
     * Returns the IDs of the ACTIVE sprints linked to any of the given boards, each sprint
     * once. Used by other domains that count work in the running sprints; the caller is
     * responsible for scoping the boards to what the user may read.
     *
     * @param boardIds the boards whose active sprints are resolved; null or empty returns an
     *                 empty list
     * @return the distinct active sprint IDs, in no particular order
     */
    List<UUID> getActiveSprintIdsByBoardIds(Collection<UUID> boardIds);

    /**
     * Returns the ACTIVE sprints linked to any of the given boards, each sprint once, with
     * their live board links readable in the caller's transaction. A board may still hold
     * two ACTIVE sprints, so a caller that needs one sprint per board chooses among them.
     * The caller is responsible for scoping the boards to what the user may read.
     *
     * @param boardIds the boards whose active sprints are resolved; null or empty returns an
     *                 empty list
     * @return the distinct active sprints, in no particular order
     */
    List<Sprint> getActiveSprintsByBoardIds(Collection<UUID> boardIds);
}
