package com.korur.oba.apps.kazan.organizationsetting.entity;

// 1. Framework-level
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Kazan settings of one platform organization, one row per organization.
 *
 * The primary key is the organization id itself, so the entity cannot extend
 * {@link com.korur.oba.dojo.entity.BaseEntity}, whose id is generated; it keeps
 * the same audit columns. A missing row means the organization has no Kazan
 * settings yet, which reads as no security board.
 */
@Entity
@Table(name = "organization_settings", schema = "kazan")
@EntityListeners(AuditingEntityListener.class)
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationSetting {

    /**
     * Platform organization these settings belong to; the row is removed with it.
     */
    @Id
    @Column(name = "organization_id", updatable = false, nullable = false)
    private UUID organizationId;

    /**
     * Board that receives the organization's security tickets. The database clears
     * it when the board is deleted.
     */
    @Column(name = "security_board_id", nullable = true)
    private UUID securityBoardId;

    @CreatedDate
    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false, updatable = false)
    @Setter(AccessLevel.PRIVATE)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    @Setter(AccessLevel.PRIVATE)
    private Instant updatedAt;
}
