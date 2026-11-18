package com.korur.oba.apps.kaya.machine.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.entity.MachineToken;

@Repository
public interface MachineTokenRepository extends JpaRepository<MachineToken, UUID> {

    Optional<MachineToken> findByMachine_IdAndRevokedAtIsNull(UUID machineId);

    List<MachineToken> findByMachine_IdInAndRevokedAtIsNull(Collection<UUID> machineIds);

    /**
     * Finds the live tokens of a user's machines in one organization. A token carries no
     * organization of its own, so the organization is the one of its machine.
     *
     * @param organizationId the organization of the machines
     * @param userId         the user
     * @return the live tokens
     */
    List<MachineToken> findByMachine_OrganizationIdAndUser_IdAndRevokedAtIsNull(UUID organizationId, UUID userId);

    /**
     * Finds a token by the hash of its plaintext, with its machine loaded.
     *
     * @param tokenHash the lower-case hex SHA-256 of the plaintext token
     * @return the token, revoked and expired ones included
     */
    @Query("SELECT t FROM MachineToken t JOIN FETCH t.machine WHERE t.tokenHash = :tokenHash")
    Optional<MachineToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Revokes every live token of a user's machines in one organization. A token carries no
     * organization of its own, so the organization is the one of its machine.
     *
     * @param organizationId the organization of the machines
     * @param userId         the user, whose machines the caller has locked
     * @param revokedAt      the moment of revocation
     * @return the number of tokens revoked
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE kaya.machine_tokens t
            SET revoked_at = :revokedAt, updated_at = :revokedAt
            FROM kaya.machines a
            WHERE a.id = t.machine_id
              AND a.organization_id = :organizationId
              AND t.user_id = :userId
              AND t.revoked_at IS NULL
            """, nativeQuery = true)
    int revokeActiveByOrganizationIdAndUserId(@Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);


    /**
     * Records the use of a token, at most once a minute: a sender posting batches every few
     * seconds would otherwise rewrite the row on every request.
     *
     * @param tokenId     the token that authenticated a request
     * @param usedAt      the moment of the request
     * @param staleBefore lastUsedAt values before this instant are refreshed
     * @return 1 when lastUsedAt moved, 0 when it was fresh enough
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE kaya.machine_tokens
            SET last_used_at = :usedAt
            WHERE id = :tokenId
              AND (last_used_at IS NULL OR last_used_at < :staleBefore)
            """, nativeQuery = true)
    int markUsed(@Param("tokenId") UUID tokenId, @Param("usedAt") Instant usedAt,
            @Param("staleBefore") Instant staleBefore);
}
