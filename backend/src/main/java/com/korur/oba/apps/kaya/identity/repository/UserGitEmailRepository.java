package com.korur.oba.apps.kaya.identity.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.entity.UserGitEmail;

@Repository
public interface UserGitEmailRepository extends JpaRepository<UserGitEmail, UUID>, JpaSpecificationExecutor<UserGitEmail> {

    /**
     * Finds the holder of a git email in the organization, with its user loaded.
     *
     * @param organizationId the organization
     * @param email          the email in lower case
     * @return the email row, or empty when no user of the organization holds it
     */
    @Query("SELECT e FROM UserGitEmail e JOIN FETCH e.user WHERE e.organizationId = :organizationId AND e.email = :email")
    Optional<UserGitEmail> findByOrganizationIdAndEmail(@Param("organizationId") UUID organizationId,
            @Param("email") String email);

    /**
     * Resolves the user id holding a git email in the organization without loading the user, so
     * the ingest lookup of every batch is one read of the unique key.
     *
     * @param organizationId the organization
     * @param email          the email in lower case
     * @return the holder's user id, or empty when no user of the organization holds it
     */
    @Query("SELECT e.user.id FROM UserGitEmail e WHERE e.organizationId = :organizationId AND e.email = :email")
    Optional<UUID> findUserIdByOrganizationIdAndEmail(@Param("organizationId") UUID organizationId,
            @Param("email") String email);

    Optional<UserGitEmail> findByIdAndOrganizationIdAndUser_Id(UUID id, UUID organizationId, UUID userId);

    List<UserGitEmail> findByOrganizationIdAndUser_IdOrderByEmailAsc(UUID organizationId, UUID userId);

    List<UserGitEmail> findByOrganizationIdAndUser_IdInOrderByEmailAsc(UUID organizationId, Collection<UUID> userIds);
}
