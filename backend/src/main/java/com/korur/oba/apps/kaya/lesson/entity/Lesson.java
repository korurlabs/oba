package com.korur.oba.apps.kaya.lesson.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * A short markdown note that later sessions of an organization, or of one repository, read: the
 * title states the rule and the body says what went wrong and the correct approach. A lesson is
 * edited in place and deleted for good, so it has no status and no history; updatedAt is its only
 * change trace.
 *
 * The organization, repository and user are plain id columns, so the lesson domain loads no
 * entity of another domain.
 */
@Entity
@Table(name = "lessons", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Lesson extends BaseEntity {

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    // The Claude Code session that recorded the lesson. Text and not a foreign key, because a
    // lesson can arrive before the first batch of its session reaches Kaya.
    @Column(name = "claude_session_id", length = 100, updatable = false)
    private String claudeSessionId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // Null means the lesson applies to the whole organization.
    @Column(name = "repository_id")
    private UUID repositoryId;

    // The author: the member who wrote the lesson, or the user of the machine token that added it.
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
}
