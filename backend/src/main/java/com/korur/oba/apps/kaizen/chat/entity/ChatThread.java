package com.korur.oba.apps.kaizen.chat.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing a thread started from a message of a chat channel.
 *
 * The stored status is set by the thread's managers:
 * {@link ChatThreadStatus#ACTIVE}, {@link ChatThreadStatus#CLOSED} or
 * {@link ChatThreadStatus#ARCHIVED}. An ACTIVE thread also counts as archived
 * once it has been quiet for autoArchiveMinutes after its last reply or its
 * last status change; that archive is derived at read time by
 * {@link com.korur.oba.apps.kaizen.chat.helper.ChatThreadStatusResolver} and never stored. The organization, root message and creator are plain ID columns: the
 * root may be soft-deleted while the thread lives on, and the thread is read
 * without loading either.
 *
 * The reply counter, the activity time and the status change through single
 * UPDATE statements in
 * {@link com.korur.oba.apps.kaizen.chat.repository.ChatThreadRepository}: a reply moves the counter and the
 * activity time and revives an archived thread, a status change writes the
 * status with who changed it and when. They are not updatable here, so saving
 * a loaded thread (a rename, a new archive window) never writes back the
 * values it read over a reply or a status change committed meanwhile.
 */
@Entity
@Table(name = "chat_threads", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatThread extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    /**
     * Organization whose channel the thread was started in.
     */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /**
     * Channel of the root message; the thread and its replies belong to it.
     */
    @Column(name = "channel_id", nullable = false, updatable = false)
    private UUID channelId;

    /**
     * Channel message the thread was started from; one thread per message.
     */
    @Column(name = "root_message_id", nullable = false, unique = true)
    private UUID rootMessageId;

    /**
     * User who started the thread; together with
     * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberRole#OWNER} and
     * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberRole#ADMIN} of the organization the only one who may manage it.
     */
    @Column(name = "created_by_id", nullable = false)
    private UUID createdBy;

    /**
     * Time of the newest reply; the creation time until the first reply.
     */
    @Column(name = "last_message_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false,
            updatable = false)
    private Instant lastMessageAt;

    /**
     * Number of live replies in the thread.
     */
    @Builder.Default
    @Column(name = "message_count", nullable = false, updatable = false)
    private Integer messageCount = 0;

    /**
     * Quiet time after which the thread counts as archived: 60, 1440, 4320 or
     * 10080 minutes, one week by default.
     */
    @Builder.Default
    @Column(name = "auto_archive_minutes", nullable = false)
    private Integer autoArchiveMinutes = 10080;

    /**
     * Time of the last status change; null until the status is first changed.
     * An {@link ChatThreadStatus#ACTIVE} thread's auto-archive window counts from this time as well,
     * so reopening or unarchiving a quiet thread does not archive it again at
     * once.
     */
    @Column(name = "status_changed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = true,
            updatable = false)
    private Instant statusChangedAt;

    /**
     * User who last changed the status, by hand or with a reply that revived
     * an archived thread; null until the status is first changed.
     */
    @Column(name = "status_changed_by_id", nullable = true, updatable = false)
    private UUID statusChangedBy;

    // -------------Enums-----------//

    /**
     * Stored status; see {@link com.korur.oba.apps.kaizen.chat.helper.ChatThreadStatusResolver} for the status readers get.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, updatable = false)
    private ChatThreadStatus status = ChatThreadStatus.ACTIVE;
}
