package com.korur.oba.apps.kaya.machine.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.entity.MachineUsageSnapshot;

@Repository
public interface MachineUsageSnapshotRepository
        extends JpaRepository<MachineUsageSnapshot, UUID>, JpaSpecificationExecutor<MachineUsageSnapshot> {

    /**
     * Writes the snapshot of a machine unless the stored one was captured later. Sessions of one
     * machine send in any order, so an older capture arriving late never replaces a newer value.
     *
     * @return 1 when the snapshot was written, 0 when the stored one is newer
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.machine_usage_snapshots (organization_id, machine_id, account_id, session_id,
                                                     five_hour_used_percentage, five_hour_resets_at,
                                                     seven_day_used_percentage, seven_day_resets_at,
                                                     captured_at, received_at)
            VALUES (:organizationId, :machineId, :accountId, :sessionId, :fiveHourUsedPercentage,
                    :fiveHourResetsAt, :sevenDayUsedPercentage, :sevenDayResetsAt, :capturedAt, :receivedAt)
            ON CONFLICT (machine_id) DO UPDATE
            SET account_id = EXCLUDED.account_id,
                session_id = EXCLUDED.session_id,
                five_hour_used_percentage = EXCLUDED.five_hour_used_percentage,
                five_hour_resets_at = EXCLUDED.five_hour_resets_at,
                seven_day_used_percentage = EXCLUDED.seven_day_used_percentage,
                seven_day_resets_at = EXCLUDED.seven_day_resets_at,
                captured_at = EXCLUDED.captured_at,
                received_at = EXCLUDED.received_at,
                updated_at = CURRENT_TIMESTAMP
            WHERE kaya.machine_usage_snapshots.captured_at < EXCLUDED.captured_at
            """, nativeQuery = true)
    int upsertIfNewer(@Param("organizationId") UUID organizationId, @Param("machineId") UUID machineId,
            @Param("accountId") UUID accountId, @Param("sessionId") String sessionId,
            @Param("fiveHourUsedPercentage") BigDecimal fiveHourUsedPercentage,
            @Param("fiveHourResetsAt") Instant fiveHourResetsAt,
            @Param("sevenDayUsedPercentage") BigDecimal sevenDayUsedPercentage,
            @Param("sevenDayResetsAt") Instant sevenDayResetsAt,
            @Param("capturedAt") Instant capturedAt, @Param("receivedAt") Instant receivedAt);

    /**
     * The snapshots of the given machines and of every machine of the organization that reported one
     * of the given accounts, with the reporting machine loaded.
     *
     * @param machineIds   the machines, not empty
     * @param accountIds the accounts, not empty
     */
    @EntityGraph(attributePaths = {"machine"})
    @Query("""
            SELECT s FROM MachineUsageSnapshot s
            WHERE s.organizationId = :organizationId
              AND (s.machine.id IN :machineIds OR s.account.id IN :accountIds)
            """)
    List<MachineUsageSnapshot> findForMachinesOrAccounts(@Param("organizationId") UUID organizationId,
            @Param("machineIds") Collection<UUID> machineIds, @Param("accountIds") Collection<UUID> accountIds);

    @EntityGraph(attributePaths = {"machine"})
    List<MachineUsageSnapshot> findByOrganizationIdAndMachine_IdIn(UUID organizationId, Collection<UUID> machineIds);

    /**
     * The snapshots of the organization's machines that reported one of the given accounts, with the
     * reporting machine loaded.
     */
    @EntityGraph(attributePaths = {"machine"})
    List<MachineUsageSnapshot> findByOrganizationIdAndAccount_IdIn(UUID organizationId, Collection<UUID> accountIds);
}
