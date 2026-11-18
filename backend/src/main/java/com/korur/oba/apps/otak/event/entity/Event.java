package com.korur.oba.apps.otak.event.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.enums.EventType;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing an event (an expo, conference, meetup or webinar) where an
 * organization met companies, so relations can be followed up per event.
 *
 * Every event belongs to one organization and is only read and written within it. Its
 * name is unique per organization with case ignored, which a unique index on the
 * lower-case name enforces. The dates are calendar days without a timezone, and the end
 * date is never before the start date, which a check constraint enforces. Rows are
 * deleted outright; the database then clears the event link of the relations that
 * pointed at it and keeps the relations.
 */
@Entity
@Table(
        name = "events",
        schema = "otak",
        uniqueConstraints = @UniqueConstraint(
                name = "events_organization_id_lower_name_uk",
                columnNames = {"organization_id", "name"}))
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Event extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /**
     * First day of the event.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Last day of the event; null for a one-day event, whose last day is the start date.
     */
    @Column(name = "end_date", nullable = true)
    private LocalDate endDate;

    @Column(name = "city", length = 100, nullable = true)
    private String city;

    @Column(name = "website", length = 500, nullable = true)
    private String website;

    @Column(name = "notes", columnDefinition = "TEXT", nullable = true)
    private String notes;

    /**
     * Organization that owns the event, set once on create from the request's checked
     * organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "country", nullable = true)
    private Country country;
}
