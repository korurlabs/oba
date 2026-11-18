package com.korur.oba.apps.otak.event.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

/**
 * The event schema refuses rows the application must never write: an end date before the start
 * date, a duplicate name inside one organization, an unknown type, and a relation linked to the
 * event of another organization; and deleting an event keeps the relations that pointed at it.
 * The tests use SQL only, so they hold before and after the event entity exists.
 */
class EventMigrationIT extends AbstractTenantIT {

    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setUpTenants() {
        tenantA = newTenant();
        tenantB = newTenant();
    }

    private UUID event(Tenant tenant, String name, String startDate, String endDate) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.events (id, name, type, start_date, end_date, organization_id) "
                + "VALUES (?, ?, 'EXPO', ?::date, ?::date, ?)", id, name, startDate, endDate,
                tenant.organization().getId());
        return id;
    }

    private UUID lead(Tenant tenant, UUID eventId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO otak.relations (id, name, type, stage, event_id, organization_id) "
                + "VALUES (?, ?, 'LEAD', 'NEW', ?, ?)", id, "Relation " + uniqueSuffix(), eventId,
                tenant.organization().getId());
        return id;
    }

    @Test
    void insertEvent_endBeforeStart_isRejectedByTheCheckConstraint() {
        assertThrows(DataIntegrityViolationException.class,
                () -> event(tenantA, "Inverted " + uniqueSuffix(), "2026-11-10", "2026-11-09"));
    }

    @Test
    void insertEvent_sameDayOrNoEndDate_isAccepted() {
        event(tenantA, "SameDay " + uniqueSuffix(), "2026-11-10", "2026-11-10");
        event(tenantA, "OpenEnd " + uniqueSuffix(), "2026-11-10", null);

        assertEquals(2L, count("SELECT count(*) FROM otak.events WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void insertEvent_unknownType_isRejectedByTheEnum() {
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO otak.events (id, name, type, start_date, organization_id) "
                        + "VALUES (?, ?, 'PARTY'::otak_event_type_enum, '2026-11-10', ?)",
                UUID.randomUUID(), "Party " + uniqueSuffix(), tenantA.organization().getId()));
    }

    @Test
    void insertEvent_sameNameDifferentCaseInOneOrganization_isRejected() {
        String name = "IGEF " + uniqueSuffix();
        event(tenantA, name, "2026-11-10", null);

        assertThrows(DataIntegrityViolationException.class,
                () -> event(tenantA, name.toLowerCase(), "2026-12-10", null));
        assertEquals(1L, count("SELECT count(*) FROM otak.events WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void insertEvent_sameNameInTwoOrganizations_isAccepted() {
        String name = "IGEF " + uniqueSuffix();

        event(tenantA, name, "2026-11-10", null);
        event(tenantB, name, "2026-11-10", null);

        assertEquals(1L, count("SELECT count(*) FROM otak.events WHERE organization_id = ?",
                tenantB.organization().getId()));
    }

    @Test
    void deleteEvent_withLinkedRelation_clearsTheLinkAndKeepsTheRelation() {
        UUID event = event(tenantA, "Linked " + uniqueSuffix(), "2026-11-10", null);
        UUID relation = lead(tenantA, event);

        jdbcTemplate.update("DELETE FROM otak.events WHERE id = ?", event);

        assertEquals(1L, count("SELECT count(*) FROM otak.relations WHERE id = ? AND event_id IS NULL "
                + "AND organization_id = ?", relation, tenantA.organization().getId()));
    }

    @Test
    void insertRelation_linkedToEventOfAnotherOrganization_isRejectedByTheCompositeForeignKey() {
        UUID eventOfA = event(tenantA, "Of A " + uniqueSuffix(), "2026-11-10", null);

        assertThrows(DataIntegrityViolationException.class, () -> lead(tenantB, eventOfA));
        assertEquals(0L, count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId()));
    }

    @Test
    void insertRelation_withoutEvent_isAccepted() {
        lead(tenantA, null);

        assertEquals(1L, count("SELECT count(*) FROM otak.relations WHERE organization_id = ? AND event_id IS NULL",
                tenantA.organization().getId()));
    }
}
