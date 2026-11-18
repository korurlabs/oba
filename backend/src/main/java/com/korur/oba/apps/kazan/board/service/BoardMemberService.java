package com.korur.oba.apps.kazan.board.service;

import java.util.List;
import java.util.UUID;

import com.korur.oba.apps.kazan.board.dto.request.BoardMemberAddRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardMemberUpdateRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardMemberResponse;
import com.korur.oba.apps.kazan.board.enums.BoardMemberSpecialtyType;
import com.korur.oba.shrine.identity.entity.User;

public interface BoardMemberService {

    /**
     * Adds a member to a board, or restores a removed membership. The added user must be an
     * ACTIVE member of the board's organization; anyone else, an unknown user included, is
     * refused with STA_BOARD_MEMBER_NOT_IN_ORGANIZATION (KAZ_STA_019). A member who may not
     * open Kazan in that organization is refused with VAL_MEMBER_WITHOUT_APP_ACCESS
     * (APP_VAL_002).
     */
    BoardMemberResponse addMember(User user, UUID organizationId, UUID boardId, BoardMemberAddRequest request);

    /**
     * Removes a member from a board.
     */
    void removeMember(User user, UUID organizationId, UUID boardId, UUID userId);

    /**
     * Updates a member's role in a board.
     */
    BoardMemberResponse updateMemberRole(User user, UUID organizationId, UUID boardId, UUID userId, BoardMemberUpdateRequest request);

    /**
     * Replaces a board member's specialties with the provided list.
     *
     * @param user        the authenticated user (must be OWNER or ADMIN)
     * @param organizationId the organization of the request, which owns the board
     * @param boardId     the board identifier
     * @param userId      the target member's user identifier
     * @param specialties the new list of specialties to assign
     */
    void updateMemberSpecialties(User user, UUID organizationId, UUID boardId, UUID userId, List<BoardMemberSpecialtyType> specialties);
}