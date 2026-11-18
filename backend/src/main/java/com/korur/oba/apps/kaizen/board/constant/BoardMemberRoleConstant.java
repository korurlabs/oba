package com.korur.oba.apps.kaizen.board.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

// 4. Internal
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;

/**
 * Board member role sets, one for each board access level.
 *
 * Each set holds the roles that pass a check at that access level, so the access check
 * ({@link com.korur.oba.apps.kaizen.board.service.BoardAccessService}) and the board
 * eligibility lists ({@link com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService})
 * use the same roles and cannot drift apart. The sets are unmodifiable, because a shared
 * mutable set would let one caller change the access rules of every other caller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class BoardMemberRoleConstant {

    private BoardMemberRoleConstant() {
    }

    /**
     * Roles that may view a board: every board member.
     */
    public static final Set<BoardMemberRole> BOARD_ROLES_WITH_VIEW_ACCESS = Collections.unmodifiableSet(
            EnumSet.of(
                    BoardMemberRole.OWNER,
                    BoardMemberRole.ADMIN,
                    BoardMemberRole.MEMBER,
                    BoardMemberRole.VIEWER));

    /**
     * Roles that may contribute to a board: create, update and delete its content.
     * This is the set a ticket update requires.
     */
    public static final Set<BoardMemberRole> BOARD_ROLES_WITH_CONTRIBUTE_ACCESS = Collections.unmodifiableSet(
            EnumSet.of(
                    BoardMemberRole.OWNER,
                    BoardMemberRole.ADMIN,
                    BoardMemberRole.MEMBER));

    /**
     * Roles that may manage a board's settings, members and sprints.
     * This is the set a sprint create or link requires.
     */
    public static final Set<BoardMemberRole> BOARD_ROLES_WITH_ADMIN_ACCESS = Collections.unmodifiableSet(
            EnumSet.of(
                    BoardMemberRole.OWNER,
                    BoardMemberRole.ADMIN));

    /**
     * Roles that may do what only the board owner may do.
     */
    public static final Set<BoardMemberRole> BOARD_ROLES_WITH_OWNER_ACCESS = Collections.unmodifiableSet(
            EnumSet.of(
                    BoardMemberRole.OWNER));
}
