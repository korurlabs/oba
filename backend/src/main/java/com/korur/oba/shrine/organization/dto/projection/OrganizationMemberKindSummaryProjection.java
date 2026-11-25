package com.korur.oba.shrine.organization.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * One active member of an organization with the user fields needed to name them and whether the user
 * is a person or an agent, read without loading the membership or user entities.
 *
 * Kind and the agent fields are enum names as text, as the role is: the kind is the
 * {@link com.korur.oba.shrine.identity.enums.UserKind} name, and agent role, flow and provider are the
 * {@link com.korur.oba.shrine.identity.enums.AgentRole}, {@link com.korur.oba.shrine.identity.enums.AgentFlow}
 * and {@link com.korur.oba.shrine.identity.enums.AgentProvider} names, null for a person.
 */
public interface OrganizationMemberKindSummaryProjection extends OrganizationMemberSummaryProjection {

    String getKind();

    String getAgentRole();

    String getAgentFlow();

    String getAgentProvider();
}
