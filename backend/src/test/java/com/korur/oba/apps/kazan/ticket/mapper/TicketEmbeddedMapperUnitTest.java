package com.korur.oba.apps.kazan.ticket.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.dto.response.TicketAssigneeResponse;
import com.korur.oba.apps.kazan.ticket.entity.TicketAssignee;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;

class TicketEmbeddedMapperUnitTest {

    private TicketEmbeddedMapper ticketEmbeddedMapper;

    @BeforeEach
    void setUp() {
        ticketEmbeddedMapper = new TicketEmbeddedMapperImpl();
    }

    private TicketAssignee assignee(User user) {
        TicketAssignee assignee = TicketAssignee.builder().user(user).build();
        assignee.setId(UUID.randomUUID());
        return assignee;
    }

    @Test
    void toAssigneeResponse_person_isHumanWithoutAgentFields() {
        // Arrange
        User person = User.builder().email("ada@example.com").build();
        person.setId(UUID.randomUUID());
        TicketAssignee assignee = assignee(person);

        // Act
        TicketAssigneeResponse response = ticketEmbeddedMapper.toAssigneeResponse(assignee);

        // Assert
        assertEquals(assignee.getId(), response.id());
        assertEquals(person.getId(), response.userId());
        assertEquals("ada@example.com", response.email());
        assertEquals(UserKind.HUMAN, response.kind());
        assertNull(response.agentRole());
        assertNull(response.agentFlow());
        assertNull(response.agentProvider());
    }

    @Test
    void toAssigneeResponse_agent_carriesKindRoleFlowAndProvider() {
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
        TicketAssigneeResponse response = ticketEmbeddedMapper.toAssigneeResponse(assignee(agent));

        // Assert
        assertEquals(agent.getId(), response.userId());
        assertEquals(UserKind.AGENT, response.kind());
        assertEquals(AgentRole.CYBERSECURITY_ENGINEER, response.agentRole());
        assertEquals(AgentFlow.ROUTINE, response.agentFlow());
        assertEquals(AgentProvider.ANTHROPIC, response.agentProvider());
    }
}
