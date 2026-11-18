package com.korur.oba.connect.retry.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.retry.entity.RetryDeadEvent;

@Repository
public interface RetryDeadEventRepository extends JpaRepository<RetryDeadEvent, UUID>,
        JpaSpecificationExecutor<RetryDeadEvent> {
}
