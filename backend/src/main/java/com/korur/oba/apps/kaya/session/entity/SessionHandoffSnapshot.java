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
 * The last captured content of one handoff file a session named or wrote. The session's handoff
 * list shows its status line, so a snapshot is the current truth, not a history.
 */
@Entity
@Table(
        name = "session_handoff_snapshots",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "session_handoff_snapshots_session_id_path_uk", columnNames = {"session_id", "path"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SessionHandoffSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private Session session;

    // The path below handoffs/, starting with the feature directory.
    @Column(name = "path", length = 500, nullable = false, updatable = false)
    private String path;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "captured_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant capturedAt;
}
