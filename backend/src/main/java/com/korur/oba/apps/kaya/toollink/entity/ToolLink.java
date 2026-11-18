package com.korur.oba.apps.kaya.toollink.entity;

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
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

/**
 * One tool link of a {@link com.korur.oba.apps.kaya.version.entity.Version}: a symlink such as
 * .claude/rules to ../.agents/rules that lets an AI tool find the shared source under .agents/.
 *
 * A link has no content, and its path and target follow from its {@link ToolLinkName}, so the row
 * holds only the name; a version holds each name at most once.
 *
 * The organization and the version are plain ids, so this domain sits below the version domain and
 * loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.toollink.repository.ToolLinkRepository} by
 * {@link com.korur.oba.apps.kaya.toollink.service.ToolLinkQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.toollink.service.ToolLinkService}, which the render domain calls on
 * parse.
 */
@Entity
@Table(name = "tool_links", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ToolLink extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    // Mapped to the PostgreSQL enum toollink_tool_link_name_enum.
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "name", nullable = false, updatable = false)
    private ToolLinkName name;
}
