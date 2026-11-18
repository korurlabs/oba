package com.korur.oba.apps.kaya.identity.repository;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.entity.UserAuditEntry;
import com.korur.oba.apps.kaya.identity.enums.UserAuditAction;

@Repository
public interface UserAuditEntryRepository
        extends JpaRepository<UserAuditEntry, UUID>, JpaSpecificationExecutor<UserAuditEntry> {

    Page<UserAuditEntry> findByOrganizationIdAndUser_Id(UUID organizationId, UUID userId, Pageable pageable);

    Page<UserAuditEntry> findByOrganizationIdAndUser_IdAndAction(UUID organizationId, UUID userId,
            UserAuditAction action, Pageable pageable);
}
