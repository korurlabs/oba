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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * One recorded hook event of a session. The recorder numbers the events of a session from 1, so a
 * replayed batch finds its events by (session, seq) and stores each of them once. The payload is
 * the event as the recorder wrote it, which the subagent and skill-use derivation and the pipeline
 * run checks replay.
 */
@Entity
@Table(
        name = "session_events",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(name = "session_events_session_id_seq_uk", columnNames = {"session_id", "seq"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SessionEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private Session session;

    @Column(name = "seq", nullable = false, updatable = false)
    private Long seq;

    @Column(name = "at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false, updatable = false)
    private Instant at;

    @Column(name = "event", length = 40, nullable = false, updatable = false)
    private String event;

    // Claude's id of the subagent the hook fired in; null for the session itself.
    @Column(name = "agent_id", length = 100, updatable = false)
    private String agentId;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false, updatable = false)
    private Map<String, Object> payload = new HashMap<>();
}
