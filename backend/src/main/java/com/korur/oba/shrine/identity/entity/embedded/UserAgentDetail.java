package com.korur.oba.shrine.identity.entity.embedded;

// 1. Framework-level
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;

/**
 * What makes a user an agent. Null on a person: Hibernate loads an embedded value whose columns are
 * all null as null, and the database check on the users table keeps role, flow, provider and
 * organization either all set (kind AGENT) or all null (kind HUMAN).
 *
 * The organization and the creator are plain ids, not relations, so loading any user adds no join.
 * The organization stays on the row although an agent has one membership, because the unique agent
 * name per organization and the check need it there, and the default organization of a user can
 * change or be cleared.
 */
@Embeddable
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAgentDetail {

    /**
     * The job the agent is registered for; a label that grants nothing.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "agent_role", nullable = true)
    private AgentRole role;

    /**
     * Who runs the agent; another flow is another agent.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "agent_flow", nullable = true, updatable = false)
    private AgentFlow flow;

    /**
     * The vendor behind the agent; another provider is another agent.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "agent_provider", nullable = true, updatable = false)
    private AgentProvider provider;

    /**
     * The only organization the agent belongs to.
     */
    @Column(name = "agent_organization_id", nullable = true, updatable = false)
    private UUID organizationId;

    /**
     * The person who registered the agent; null once that person is deleted.
     */
    @Column(name = "agent_created_by_user_id", nullable = true, updatable = false)
    private UUID createdByUserId;
}
