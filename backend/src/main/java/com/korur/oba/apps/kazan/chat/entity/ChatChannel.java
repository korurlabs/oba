package com.korur.oba.apps.kazan.chat.entity;

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
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelType;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * Entity representing one chat channel of an organization.
 *
 * Messages, threads and read states belong to exactly one channel. A
 * {@link ChatChannelVisibility#PUBLIC} channel is read by every active member
 * of the organization and has no member rows; a
 * {@link ChatChannelVisibility#PRIVATE} channel is read only by the users in
 * {@link ChatChannelMember}. Deleting a channel is soft, so its content is
 * kept and only hidden. A purpose other than
 * {@link ChatChannelPurpose#GENERAL} is held by at most one live
 * channel of the organization, and only by a public text channel, because
 * every member has to read what is posted to it automatically; the database
 * enforces both. The organization and the creator are plain ID columns, so a
 * channel is read without loading either.
 */
@Entity
@Table(name = "chat_channels", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class ChatChannel extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    /**
     * Display name, trimmed; two channels may share a name.
     */
    @Column(name = "name", length = 80, nullable = false)
    private String name;

    /**
     * URL segment of the channel in the web app, built from the name and unique among
     * the live channels of the organization. Only the server sets it, on create and on
     * a rename that changes the name's slug.
     */
    @Column(name = "slug", length = 80, nullable = false)
    private String slug;

    /**
     * Organization the channel belongs to.
     */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /**
     * User who created the channel; null for channels created with the
     * organization or by a migration, and once the creator is deleted.
     */
    @Column(name = "created_by_id", nullable = true)
    private UUID createdBy;

    // -------------Enums-----------//

    /**
     * How the channel behaves; set at creation and never changed.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false, updatable = false)
    private ChatChannelType type = ChatChannelType.TEXT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "purpose", nullable = false)
    private ChatChannelPurpose purpose = ChatChannelPurpose.GENERAL;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "visibility", nullable = false)
    private ChatChannelVisibility visibility;
}
