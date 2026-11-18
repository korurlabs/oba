package com.korur.oba.apps.kaizen.board.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.constant.BoardMemberRoleConstant;
import com.korur.oba.apps.kaizen.board.dto.response.BoardMemberCandidateResponse;
import com.korur.oba.apps.kaizen.board.dto.response.BoardMemberResponse;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardMember;
import com.korur.oba.apps.kaizen.board.entity.BoardMemberSpecialty;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberSpecialtyType;
import com.korur.oba.apps.kaizen.board.mapper.BoardMemberMapper;
import com.korur.oba.apps.kaizen.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kaizen.board.repository.BoardMemberSpecialtyRepository;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Implementation of board member read operations.
 *
 * All methods run within a read-only transaction for optimal database
 * performance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardMemberQueryServiceImpl implements BoardMemberQueryService {

    // Candidates by first name, last name and email, whatever their case; the user ID settles ties, so
    // the order is the same on every call.
    private static final Comparator<BoardMemberCandidateResponse> CANDIDATE_ORDER = Comparator
            .comparing(BoardMemberCandidateResponse::firstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(BoardMemberCandidateResponse::lastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(BoardMemberCandidateResponse::email, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(BoardMemberCandidateResponse::userId);

    private final BoardMemberRepository boardMemberRepository;
    private final BoardMemberSpecialtyRepository boardMemberSpecialtyRepository;
    private final BoardAccessService boardAccessService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessQueryService appAccessQueryService;

    private final BoardMemberMapper boardMemberMapper;
    private final Clock clock;

    @Override
    public List<BoardMemberResponse> listMembers(User user, UUID organizationId, UUID boardId) {
        // Validate the board belongs to the organization and the user is a board member.
        Board board = boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
        // Board member rows of former organization members stay, so they are filtered here.
        Set<UUID> organizationUserIds = findActiveOrganizationUserIds(board.getOrganizationId());
        List<BoardMember> members = boardMemberRepository.findByBoard_Id(boardId)
                .stream()
                .filter((BoardMember membership) -> organizationUserIds.contains(membership.getUser().getId()))
                .toList();
        Set<UUID> kaizenUserIds = appAccessQueryService.findUserIdsWithAppAccess(board.getOrganizationId(),
                App.KAIZEN, members.stream().map((BoardMember membership) -> membership.getUser().getId()).toList());
        return members.stream()
                .map((BoardMember membership) -> boardMemberMapper.toResponse(membership,
                        kaizenUserIds.contains(membership.getUser().getId())))
                .toList();
    }

    @Override
    public List<BoardMemberCandidateResponse> listMemberCandidates(User user, UUID organizationId, UUID boardId) {
        // Only a caller who may add members reads the people to add.
        Board board = boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
        List<OrganizationMemberSummaryProjection> candidates = organizationMemberQueryService
                .listActiveMemberSummaries(board.getOrganizationId(), Instant.now(clock));
        Set<UUID> kaizenUserIds = appAccessQueryService.findUserIdsWithAppAccess(board.getOrganizationId(),
                App.KAIZEN, candidates.stream().map(OrganizationMemberSummaryProjection::getUserId).toList());
        return candidates.stream()
                .map((OrganizationMemberSummaryProjection candidate) -> boardMemberMapper.toCandidateResponse(
                        candidate, kaizenUserIds.contains(candidate.getUserId())))
                .sorted(CANDIDATE_ORDER)
                .toList();
    }

    @Override
    public List<BoardMember> findMembersByBoardIdAndSpecialty(UUID boardId, BoardMemberSpecialtyType specialty) {
        List<BoardMember> members = boardMemberSpecialtyRepository
                .findByBoardMember_Board_IdAndSpecialty(boardId, specialty)
                .stream()
                .map(BoardMemberSpecialty::getBoardMember)
                .toList();
        if (members.isEmpty()) {
            return members;
        }
        // Every row is of the same board, so one organization lookup covers them all.
        Set<UUID> organizationUserIds = findActiveOrganizationUserIds(
                members.getFirst().getBoard().getOrganizationId());
        return members.stream()
                .filter((BoardMember membership) -> organizationUserIds.contains(membership.getUser().getId()))
                .toList();
    }

    @Override
    public List<UUID> getMemberBoardIds(UUID userId, UUID organizationId) {
        return boardMemberRepository.findByUser_IdAndBoard_OrganizationId(userId, organizationId)
                .stream()
                .map((BoardMember membership) -> membership.getBoard().getId())
                .toList();
    }

    @Override
    public List<UUID> getMemberUserIds(Collection<UUID> boardIds) {
        // No boards means no members, so the query is skipped.
        if (boardIds.isEmpty()) {
            return List.of();
        }
        // The boards may in principle span organizations, so each membership is checked
        // against the organization of its own board, with one lookup per organization.
        Map<UUID, Set<UUID>> organizationUserIdsByOrganization = new HashMap<>();
        return boardMemberRepository.findByBoard_IdIn(boardIds)
                .stream()
                .filter((BoardMember membership) -> organizationUserIdsByOrganization
                        .computeIfAbsent(membership.getBoard().getOrganizationId(),
                                this::findActiveOrganizationUserIds)
                        .contains(membership.getUser().getId()))
                .map((BoardMember membership) -> membership.getUser().getId())
                .distinct()
                .toList();
    }

    @Override
    public List<UUID> getContributorBoardIds(UUID userId, UUID organizationId) {
        return boardMemberRepository.findByUser_IdAndBoard_OrganizationId(userId, organizationId)
                .stream()
                .filter((BoardMember membership) -> BoardMemberRoleConstant.BOARD_ROLES_WITH_CONTRIBUTE_ACCESS
                        .contains(membership.getRole()))
                .map((BoardMember membership) -> membership.getBoard().getId())
                .toList();
    }

    @Override
    public List<UUID> getAdminBoardIds(UUID userId, UUID organizationId) {
        return boardMemberRepository.findByUser_IdAndBoard_OrganizationId(userId, organizationId)
                .stream()
                .filter((BoardMember membership) -> BoardMemberRoleConstant.BOARD_ROLES_WITH_ADMIN_ACCESS
                        .contains(membership.getRole()))
                .map((BoardMember membership) -> membership.getBoard().getId())
                .toList();
    }

    /**
     * Returns the users with an ACTIVE membership of the organization, of any role. A board
     * member outside this set keeps the row but no longer counts as a member of the board.
     *
     * @param organizationId the organization that owns the board
     * @return the user IDs
     */
    private Set<UUID> findActiveOrganizationUserIds(UUID organizationId) {
        return organizationMemberQueryService.findActiveUserIdsByRoles(
                organizationId, EnumSet.allOf(OrganizationMemberRole.class));
    }
}
