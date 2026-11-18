package com.korur.oba.apps.kazan.board.entity;

// 1. Framework-level
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Join entity representing a user's membership within a board.
 *
 * Uniqueness on (board_id, user_id) is enforced at DB level
 * via a partial unique index (WHERE deleted = false).
 */
@Entity
@Table(name = "board_members", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class BoardMember extends SoftDeleteBaseEntity {

    // -------------Relations-----------//

    /**
     * Board this membership belongs to.
     *
     * No cascade -- board lifecycle is independent. This entity is owned by
     * the board via the Board.members collection.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    /**
     * User who is a member of the board.
     *
     * No cascade -- user lifecycle is independent of the membership.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "role", nullable = false)
    private BoardMemberRole role;

    // -------------Specialties-----------//

    /**
     * Specialties held by this board member.
     *
     * Cascade: {PERSIST, MERGE, REMOVE} + orphanRemoval -- specialties are soft-deleted
     * when the board member is soft-deleted or when removed from the collection.
     * Restore: specialties are restored when the board member is restored.
     * Re-add: service checks for soft-deleted record and restores instead of creating duplicate.
     */
    @OneToMany(mappedBy = "boardMember",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    @Builder.Default
    private List<BoardMemberSpecialty> specialties = new ArrayList<>();
}
