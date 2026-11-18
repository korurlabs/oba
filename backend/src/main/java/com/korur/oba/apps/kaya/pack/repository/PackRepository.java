package com.korur.oba.apps.kaya.pack.repository;

// 1. Framework-level
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
import com.korur.oba.apps.kaya.pack.entity.Pack;

/**
 * Reads and writes {@link Pack} rows, always within one organization.
 */
@Repository
public interface PackRepository extends JpaRepository<Pack, UUID>, JpaSpecificationExecutor<Pack> {

    Optional<Pack> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Pack> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    List<Pack> findByOrganizationIdAndRequiredTrueOrderByNameAsc(UUID organizationId);

    List<Pack> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

    List<Pack> findByOrganizationIdAndParentPackIdIn(UUID organizationId, Collection<UUID> parentPackIds);

    long countByOrganizationId(UUID organizationId);

    Optional<Pack> findByOrganizationIdAndName(UUID organizationId, String name);

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);
}
