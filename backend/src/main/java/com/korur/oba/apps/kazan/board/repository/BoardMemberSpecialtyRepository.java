package com.korur.oba.apps.kazan.board.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.BoardMemberSpecialty;
import com.korur.oba.apps.kazan.board.enums.BoardMemberSpecialtyType;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository for board member specialty operations.
 */
@Repository
public interface BoardMemberSpecialtyRepository extends SoftDeleteRepository<BoardMemberSpecialty, UUID> {

    List<BoardMemberSpecialty> findByBoardMember_Id(UUID boardMemberId);

    List<BoardMemberSpecialty> findByBoardMember_Board_IdAndSpecialty(UUID boardId, BoardMemberSpecialtyType specialty);

    void deleteByBoardMember_Id(UUID boardMemberId);

    /**
     * Restores all soft-deleted specialties for the specified board member.
     *
     * @param boardMemberId ID of the board member
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kazan.board_member_specialties SET deleted = false
            WHERE board_member_id = :boardMemberId AND deleted = true
            """, nativeQuery = true)
    int restoreAllByBoardMemberId(@Param("boardMemberId") UUID boardMemberId);
}
