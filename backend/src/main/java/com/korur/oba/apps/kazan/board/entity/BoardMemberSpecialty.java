package com.korur.oba.apps.kazan.board.entity;

// 1. Framework-level
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.board.enums.BoardMemberSpecialtyType;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Join entity representing a specialty held by a board member.
 *
 * Uniqueness on (board_member_id, specialty) is enforced at DB level
 * via a partial unique index (WHERE deleted = false).
 */
@Entity
@Table(name = "board_member_specialties", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class BoardMemberSpecialty extends SoftDeleteBaseEntity {

    // -------------Relations-----------//

    /**
     * Board member this specialty belongs to.
     *
     * No cascade -- board member lifecycle is independent. This entity is owned
     * by the board member via the BoardMember.specialties collection.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_member_id", nullable = false)
    private BoardMember boardMember;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @Column(name = "specialty", nullable = false, length = 50)
    private BoardMemberSpecialtyType specialty;
}
