package com.korur.oba.connect.outbox.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.outbox.entity.OutboxDeadEvent;

@Repository
public interface OutboxDeadEventRepository extends JpaRepository<OutboxDeadEvent, UUID>,
                                                   JpaSpecificationExecutor<OutboxDeadEvent> {
}
