package com.korur.oba.dojo.security.token.repository;

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
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {

    /**
     * Finds a token by the hash of its plaintext, on the unique index of the hash.
     *
     * @param tokenHash the lower-case hex SHA-256 of the plaintext token
     * @return the token, revoked and expired ones included
     */
    Optional<ApiToken> findByTokenHash(String tokenHash);

    Optional<ApiToken> findByKindAndSubjectIdAndRevokedAtIsNull(ApiTokenKind kind, UUID subjectId);

    List<ApiToken> findByKindAndSubjectIdInAndRevokedAtIsNull(ApiTokenKind kind, Collection<UUID> subjectIds);

    /**
     * Revokes every live token a user owns in one organization, of every kind.
     *
     * @param organizationId the organization of the tokens
     * @param userId         the owner
     * @param revokedAt      the moment of revocation
     * @return the number of tokens revoked
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE dojo.api_tokens
            SET revoked_at = :revokedAt, updated_at = :revokedAt
            WHERE organization_id = :organizationId
              AND user_id = :userId
              AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeLiveByOrganizationIdAndUserId(@Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    /**
     * Records the use of a token, at most once per interval: a sender posting every few seconds
     * would otherwise rewrite the row on every request.
     *
     * @param tokenId     the token that authenticated a request
     * @param usedAt      the moment of the request
     * @param staleBefore lastUsedAt values before this instant are refreshed
     * @return 1 when lastUsedAt moved, 0 when it was fresh enough
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE dojo.api_tokens
            SET last_used_at = :usedAt
            WHERE id = :tokenId
              AND (last_used_at IS NULL OR last_used_at < :staleBefore)
            """, nativeQuery = true)
    int markUsed(@Param("tokenId") UUID tokenId, @Param("usedAt") Instant usedAt,
            @Param("staleBefore") Instant staleBefore);
}
