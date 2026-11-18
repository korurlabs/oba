package com.korur.oba.apps.kaizen.organizationsetting.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.organizationsetting.entity.OrganizationSetting;

/**
 * Repository for the Kaizen settings of organizations, keyed by organization id.
 */
@Repository
public interface OrganizationSettingRepository
        extends JpaRepository<OrganizationSetting, UUID>, JpaSpecificationExecutor<OrganizationSetting> {
}
