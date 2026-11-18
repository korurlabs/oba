package com.korur.oba.apps.kaya.version.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.version.entity.ItemOverride;

public interface ItemOverrideRepository extends JpaRepository<ItemOverride, UUID>, JpaSpecificationExecutor<ItemOverride> {

    List<ItemOverride> findByOrganizationIdAndVersionIdOrderByPathAsc(UUID organizationId, UUID versionId);

    List<ItemOverride> findByOrganizationIdAndVersionIdIn(UUID organizationId, Collection<UUID> versionIds);

    Optional<ItemOverride> findByVersionIdAndPath(UUID versionId, String path);

    List<ItemOverride> findByVersionId(UUID versionId);
}
