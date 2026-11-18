package com.korur.oba.dojo.entity;

// 1. Framework-level
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

// 2. Third-party
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
@Getter
@Setter
@MappedSuperclass
@SuperBuilder
@RequiredArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@SoftDelete(columnName = "deleted", strategy = SoftDeleteType.DELETED)
public abstract class SoftDeleteBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @Setter(AccessLevel.PUBLIC)
    private UUID id;

    /**
     * Instant is always stored as UTC, making it ideal for global applications.
     * For determing the creation date in UTC of an image on the Node.
     */
    @CreatedDate
    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted", nullable = false, insertable = false, updatable = false)
    @Builder.Default
    private boolean deleted = false;
}
