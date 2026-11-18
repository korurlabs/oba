package com.korur.oba.apps.kaizen.systemactor.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.systemactor.enums.SystemActorType;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing a non-human actor that writes to Kaizen, such as
 * Alertmanager creating incident tickets.
 *
 * A system actor is not a user: it has no row in shrine.users, so it can never
 * sign in, be invited, be assigned or appear in a user list. It is found by its
 * type, which is unique. The rows are seeded by migration and have no soft
 * delete.
 */
@Entity
@Table(name = "system_actors", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SystemActor extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false, unique = true)
    private SystemActorType type;
}
