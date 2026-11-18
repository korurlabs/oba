package com.korur.oba.apps.kazan.board.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.korur.oba.apps.kazan.board.dto.response.BoardMemberCandidateResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardMemberResponse;
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.enums.BoardMemberSpecialtyType;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for board memberships including member listing,
 * specialty lookups, and membership-based board ID resolution.
 */
public interface BoardMemberQueryService {

    /**
     * Lists members within a board. Members who are not an ACTIVE member of the board's
     * organization are left out. Each member says whether it may open Kazan in that
     * organization, read for all members with one query.
     *
     * @param user           the authenticated user requesting the member list
     * @param organizationId the organization of the request
     * @param boardId        the board identifier
     * @return list of board member response DTOs
     */
    List<BoardMemberResponse> listMembers(User user, UUID organizationId, UUID boardId);

    /**
     * Lists the members of the board's organization that the add-member picker offers: an
     * ACTIVE membership held by an ACTIVE account, of any role, ordered by first name, last
     * name and email. Each says whether it may open Kazan in the organization; the add
     * refuses one that may not. Only a board ADMIN or OWNER, who may add members, reads it.
     *
     * @param user           the authenticated user requesting the list
     * @param organizationId the organization of the request
     * @param boardId        the board identifier
     * @return the candidates, members of the board included
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException when the board
     *         is missing, of another organization, or the caller is not a member of it
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException when the caller is
     *         a member of the board below ADMIN
     */
    List<BoardMemberCandidateResponse> listMemberCandidates(User user, UUID organizationId, UUID boardId);

    /**
     * Finds board members that hold a given specialty on a board. Members who are not an
     * ACTIVE member of the board's organization are left out.
     *
     * @param boardId   the board identifier
     * @param specialty the specialty type to filter by
     * @return list of board members with the requested specialty
     */
    List<BoardMember> findMembersByBoardIdAndSpecialty(UUID boardId, BoardMemberSpecialtyType specialty);

    /**
     * Returns the IDs of the boards of one organization where the given user is a member.
     * Never throws and checks no organization membership; the caller has checked it.
     *
     * @param userId         the user identifier
     * @param organizationId the organization whose boards count
     * @return list of board IDs the user is a member of
     */
    List<UUID> getMemberBoardIds(UUID userId, UUID organizationId);

    /**
     * Returns the IDs of the users who are a member of any of the given boards, of any
     * role, each ID once. A user counts on a board only while an ACTIVE member of that
     * board's organization.
     *
     * @param boardIds the board identifiers; empty returns an empty list
     * @return the distinct member user IDs, in no particular order
     */
    List<UUID> getMemberUserIds(Collection<UUID> boardIds);

    /**
     * Returns the IDs of the boards where the given user may contribute, that is
     * holds role OWNER, ADMIN or MEMBER. Boards where the user is a VIEWER are left out.
     *
     * Unlike BoardAccessService.verifyBoardAccess, this never throws, so it serves as
     * an eligibility test over many boards at once.
     *
     * @param userId         the user identifier
     * @param organizationId the organization whose boards count
     * @return list of board IDs where the user holds a contributing role
     */
    List<UUID> getContributorBoardIds(UUID userId, UUID organizationId);

    /**
     * Returns the IDs of the boards where the given user may manage sprints, that is holds
     * role OWNER or ADMIN.
     *
     * Like getContributorBoardIds, this never throws, so it serves as an eligibility test
     * over many boards at once.
     *
     * @param userId         the user identifier
     * @param organizationId the organization whose boards count
     * @return list of board IDs where the user is OWNER or ADMIN
     */
    List<UUID> getAdminBoardIds(UUID userId, UUID organizationId);
}
