package com.korur.oba.apps.kaya.machine.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.account.entity.Account;

/**
 * The latest subscription usage limits the recorder of a machine reported, one row per machine. A
 * window holds the used percentage and the instant it resets, both set or both null. The Claude
 * account is the one of the batch that carried the snapshot, because the limits belong to the
 * account, not to the machine. The row is written only by a native upsert that keeps the newer
 * capture, so it has no setter path of its own.
 */
@Entity
@Table(
        name = "machine_usage_snapshots",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(name = "machine_usage_snapshots_machine_id_uk", columnNames = "machine_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MachineUsageSnapshot extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false, updatable = false)
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "session_id", length = 100, nullable = false)
    private String sessionId;

    @Column(name = "five_hour_used_percentage", precision = 5, scale = 2)
    private BigDecimal fiveHourUsedPercentage;

    @Column(name = "five_hour_resets_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant fiveHourResetsAt;

    @Column(name = "seven_day_used_percentage", precision = 5, scale = 2)
    private BigDecimal sevenDayUsedPercentage;

    @Column(name = "seven_day_resets_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant sevenDayResetsAt;

    // The machine's clock, clamped to receivedAt so a clock running ahead cannot pin a snapshot.
    @Column(name = "captured_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant capturedAt;

    @Column(name = "received_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant receivedAt;
}
