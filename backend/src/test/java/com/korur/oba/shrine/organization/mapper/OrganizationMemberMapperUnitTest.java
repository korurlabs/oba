package com.korur.oba.shrine.organization.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

class OrganizationMemberMapperUnitTest {

    private OrganizationMemberMapper organizationMemberMapper;

    @BeforeEach
    void setUp() {
        organizationMemberMapper = new OrganizationMemberMapperImpl();
    }

    private OrganizationMember member(User user) {
        OrganizationMember member = OrganizationMember.builder()
                .user(user)
                .role(OrganizationMemberRole.MEMBER)
                .status(OrganizationMemberStatus.ACTIVE)
                .build();
        member.setId(UUID.randomUUID());
        return member;
    }

    @Test
    void toResponse_person_isHumanWithoutAgentFields() {
        // Arrange
        User person = User.builder().email("ada@example.com").build();
        person.setId(UUID.randomUUID());

        // Act
        OrganizationMemberResponse response = organizationMemberMapper.toResponse(member(person));

        // Assert
        assertEquals(UserKind.HUMAN, response.kind());
        assertNull(response.agentRole());
        assertNull(response.agentFlow());
        assertNull(response.agentProvider());
    }

    @Test
    void toResponse_agent_carriesKindRoleFlowAndProvider() {
        // Arrange
        User agent = User.builder()
                .email("agent@agents.oba.invalid")
                .kind(UserKind.AGENT)
                .agentDetail(UserAgentDetail.builder()
                        .role(AgentRole.CYBERSECURITY_ENGINEER)
                        .flow(AgentFlow.ROUTINE)
                        .provider(AgentProvider.ANTHROPIC)
                        .organizationId(UUID.randomUUID())
                        .build())
                .build();
        agent.setId(UUID.randomUUID());

        // Act
        OrganizationMemberResponse response = organizationMemberMapper.toResponse(member(agent));

        // Assert
        assertEquals(UserKind.AGENT, response.kind());
        assertEquals(AgentRole.CYBERSECURITY_ENGINEER, response.agentRole());
        assertEquals(AgentFlow.ROUTINE, response.agentFlow());
        assertEquals(AgentProvider.ANTHROPIC, response.agentProvider());
        assertEquals(agent.getId(), response.userId());
    }
}
