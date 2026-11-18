package com.korur.oba.connect.notification.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.korur.oba.connect.notification.entity.EmailNotification;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

/**
 * Repository for EmailNotification entity database operations.
 * <p>
 * Provides standard CRUD operations for email notification persistence.
 */
@Repository
public interface EmailNotificationRepository extends JpaRepository<EmailNotification, UUID> {
}
