package com.korur.oba.apps.kazan.board.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.constant.BoardConstant;
import com.korur.oba.apps.kazan.board.constant.service.BoardServiceLogConstant;
import com.korur.oba.apps.kazan.board.dto.request.BoardMemberAddRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardMemberUpdateRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardMemberResponse;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.entity.BoardMemberSpecialty;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.enums.BoardMemberSpecialtyType;
import com.korur.oba.apps.kazan.board.mapper.BoardMemberMapper;
import com.korur.oba.apps.kazan.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberService;
import com.korur.oba.apps.kazan.board.validator.BoardValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Implementation of board member write operations.
 * <p>
 * Manages board lifecycle and membership with role-based access control.
 */
@Service
@RequiredArgsConstructor
public class BoardMemberServiceImpl implements BoardMemberService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BoardMemberServiceImpl.class);

    private final BoardMemberRepository boardMemberRepository;
    private final BoardAccessService boardAccessService;
    private final BoardMemberMapper boardMemberMapper;
    private final BoardValidator boardValidator;
    private final UserQueryService userQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final AppAccessValidator appAccessValidator;
    private final AgentValidator agentValidator;
    private final LoggingService loggingService;

    // -------------------------------------------------------------------------
    // Member management
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public BoardMemberResponse addMember(User user, UUID organizationId, UUID boardId, BoardMemberAddRequest request) {
        // Validate board exists and requesting user has management permissions.
        Board board = boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        // Only a colleague of the board's organization who may open Kazan there may join it, also on the
        // restore path below.
        boardValidator.ensureUserIsOrganizationMember(board.getOrganizationId(), request.userId());
        appAccessValidator.ensureUsersHaveAppAccess(board.getOrganizationId(), App.KAZAN, List.of(request.userId()));

        // An agent works tickets as a board MEMBER only, so it can never manage the board or its members.
        if (request.role() != BoardMemberRole.MEMBER) {
            agentValidator.ensureNoAgentAmong(List.of(request.userId()), AgentConstant.ACTION_BOARD_ROLE);
        }

        // Prevent duplicate membership -- but check for soft-deleted record first.
        if (boardMemberRepository.existsByBoard_IdAndUser_Id(boardId, request.userId())) {
            throw new ConflictException(
                    ExceptionDomainKazanError.STA_BOARD_MEMBER_ALREADY_EXISTS,
                    Map.of(BoardConstant.FIELD_BOARD_ID, boardId,
                            BoardConstant.FIELD_USER_ID, request.userId()));
        }

        // Check for a soft-deleted membership and restore it if found.
        Optional<BoardMember> deletedMember = boardMemberRepository.findDeletedByBoardIdAndUserId(boardId, request.userId());
        if (deletedMember.isPresent()) {
            UUID restoredId = deletedMember.get().getId();
            // Restore the deleted record and update role in a single native query.
            boardMemberRepository.restoreById(restoredId);
            // Re-fetch the now-active member through JPA to get a clean managed entity.
            BoardMember restored = boardMemberRepository.findById(restoredId)
                    .orElseThrow(() -> new NotFoundException(
                            ExceptionDomainKazanError.NOT_BOARD_MEMBER_FOUND,
                            Map.of(BoardConstant.FIELD_BOARD_ID, boardId,
                                    BoardConstant.FIELD_USER_ID, request.userId())));

            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    BoardServiceLogConstant.BOARD_MEMBER_RESTORED, restored.getId());

            return boardMemberMapper.toResponse(restored, true);
        }

        // Resolve the target user and map request to entity.
        User targetUser = userQueryService.getUserEntityById(request.userId());
        BoardMember member = boardMemberMapper.toEntity(request);
        member.setBoard(board);
        member.setUser(targetUser);
        member = boardMemberRepository.save(member);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_MEMBER_ADDED, member.getId());

        // The Kazan check above passed in this transaction.
        return boardMemberMapper.toResponse(member, true);
    }

    @Override
    @Transactional
    public void removeMember(User user, UUID organizationId, UUID boardId, UUID userId) {
        // Validate board exists and requesting user has management permissions.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        // Validate the target member exists.
        BoardMember member = boardValidator.ensureBoardMemberExists(boardId, userId);

        // Prevent removing the board owner.
        if (member.getRole() == BoardMemberRole.OWNER) {
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CANNOT_DELETE_OWNER,
                    Map.of(BoardConstant.FIELD_BOARD_ID, boardId,
                            BoardConstant.FIELD_USER_ID, userId));
        }

        boardMemberRepository.delete(member);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_MEMBER_REMOVED, userId);
    }

    @Override
    @Transactional
    public BoardMemberResponse updateMemberRole(User user, UUID organizationId, UUID boardId, UUID userId,
            BoardMemberUpdateRequest request) {
        // Validate board exists and requesting user has management permissions.
        Board board = boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        // Validate the target member exists and update the role.
        BoardMember member = boardValidator.ensureBoardMemberExists(boardId, userId);
        // An agent works tickets as a board MEMBER only, so it can never manage the board or its members.
        if (request.role() != BoardMemberRole.MEMBER) {
            agentValidator.ensureTargetIsNotAgent(member.getUser(), AgentConstant.ACTION_BOARD_ROLE);
        }
        member.setRole(request.role());
        member = boardMemberRepository.save(member);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_MEMBER_UPDATED, member.getId());

        // A role change on the board does not need Kazan, so the response reports it as it is.
        boolean kazanAccess = appAccessQueryService.hasAppAccess(board.getOrganizationId(), userId, App.KAZAN);
        return boardMemberMapper.toResponse(member, kazanAccess);
    }

    // -------------------------------------------------------------------------
    // Specialty management
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void updateMemberSpecialties(User user, UUID organizationId, UUID boardId, UUID userId,
            List<BoardMemberSpecialtyType> specialties) {
        // Validate board exists and requesting user has management permissions.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        // Validate the target member exists.
        BoardMember member = boardValidator.ensureBoardMemberExists(boardId, userId);

        // Clear existing specialties and replace with new ones.
        member.getSpecialties().clear();
        for (BoardMemberSpecialtyType specialty : specialties) {
            BoardMemberSpecialty entity = BoardMemberSpecialty.builder()
                    .boardMember(member)
                    .specialty(specialty)
                    .build();
            member.getSpecialties().add(entity);
        }
        boardMemberRepository.save(member);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                BoardServiceLogConstant.BOARD_MEMBER_SPECIALTIES_UPDATED, member.getId());
    }

}
