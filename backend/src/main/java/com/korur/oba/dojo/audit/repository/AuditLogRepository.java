package com.korur.oba.dojo.audit.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.entity.AuditLog;

@Repository("dojoAuditLogRepository")
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>,
                                             JpaSpecificationExecutor<AuditLog> {
}
