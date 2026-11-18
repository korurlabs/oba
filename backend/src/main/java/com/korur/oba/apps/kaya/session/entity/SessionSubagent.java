package com.korur.oba.apps.kaya.session.entity;

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
import java.time.Instant;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * One subagent a session started, with the model it ran on. Rows are derived from the session's events
 * on every batch, so a later batch fills in the stop time.
 */
@Entity
@Table(
        name = "session_subagents",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "session_subagents_session_id_agent_id_uk", columnNames = {"session_id", "agent_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SessionSubagent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private Session session;

    // Claude's id of the subagent, not a Kaya agent.
    @Column(name = "agent_id", length = 100, nullable = false, updatable = false)
    private String agentId;

    @Column(name = "subagent_type", length = 100, nullable = false)
    private String subagentType;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "started_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant endedAt;
}
