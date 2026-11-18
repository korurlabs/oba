package com.korur.oba.apps.kaya.account.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.account.entity.AccountUser;

@Repository
public interface AccountUserRepository
        extends JpaRepository<AccountUser, UUID>, JpaSpecificationExecutor<AccountUser> {

    Optional<AccountUser> findByOrganizationIdAndAccount_IdAndUser_Id(UUID organizationId, UUID accountId,
            UUID userId);

    boolean existsByAccount_Id(UUID accountId);

    @EntityGraph(attributePaths = {"account"})
    List<AccountUser> findByOrganizationIdAndUser_IdInOrderByAccount_AccountEmailAscAccount_AccountOrganizationNameAsc(
            UUID organizationId, Collection<UUID> userIds);

    @EntityGraph(attributePaths = {"user"})
    List<AccountUser> findByOrganizationIdAndAccount_IdIn(
            UUID organizationId, Collection<UUID> accountIds);
}
