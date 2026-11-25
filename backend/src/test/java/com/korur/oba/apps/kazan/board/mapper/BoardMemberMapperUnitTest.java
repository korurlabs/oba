package com.korur.oba.apps.kazan.board.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.dto.response.BoardMemberCandidateResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardMemberResponse;
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberKindSummaryProjection;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

class BoardMemberMapperUnitTest {

    private BoardMemberMapper boardMemberMapper;

    @BeforeEach
    void setUp() {
        boardMemberMapper = new BoardMemberMapperImpl();
    }

    private BoardMember boardMember(User user) {
        BoardMember member = BoardMember.builder().user(user).role(BoardMemberRole.MEMBER).build();
        member.setId(UUID.randomUUID());
        return member;
    }

    @Test
    void toResponse_person_isHumanWithoutAgentFields() {
        // Arrange
        User person = User.builder().email("ada@example.com").build();
        person.setId(UUID.randomUUID());

        // Act
        BoardMemberResponse response = boardMemberMapper.toResponse(boardMember(person), true);

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
                        .role(AgentRole.SALES_ENGINEER)
                        .flow(AgentFlow.API)
                        .provider(AgentProvider.OPENAI)
                        .organizationId(UUID.randomUUID())
                        .build())
                .build();
        agent.setId(UUID.randomUUID());

        // Act
        BoardMemberResponse response = boardMemberMapper.toResponse(boardMember(agent), false);

        // Assert
        assertEquals(UserKind.AGENT, response.kind());
        assertEquals(AgentRole.SALES_ENGINEER, response.agentRole());
        assertEquals(AgentFlow.API, response.agentFlow());
        assertEquals(AgentProvider.OPENAI, response.agentProvider());
    }

    @Test
    void toCandidateResponse_agentSummary_convertsEnumNames() {
        // Arrange
        OrganizationMemberKindSummaryProjection summary = mock(OrganizationMemberKindSummaryProjection.class);
        when(summary.getUserId()).thenReturn(UUID.randomUUID());
        when(summary.getRole()).thenReturn("MEMBER");
        when(summary.getKind()).thenReturn("AGENT");
        when(summary.getAgentRole()).thenReturn("CYBERSECURITY_ENGINEER");
        when(summary.getAgentFlow()).thenReturn("ROUTINE");
        when(summary.getAgentProvider()).thenReturn("ANTHROPIC");

        // Act
        BoardMemberCandidateResponse response = boardMemberMapper.toCandidateResponse(summary, true);

        // Assert
        assertEquals(OrganizationMemberRole.MEMBER, response.role());
        assertEquals(UserKind.AGENT, response.kind());
        assertEquals(AgentRole.CYBERSECURITY_ENGINEER, response.agentRole());
        assertEquals(AgentFlow.ROUTINE, response.agentFlow());
        assertEquals(AgentProvider.ANTHROPIC, response.agentProvider());
    }

    @Test
    void toCandidateResponse_personSummary_leavesAgentFieldsNull() {
        // Arrange
        OrganizationMemberKindSummaryProjection summary = mock(OrganizationMemberKindSummaryProjection.class);
        when(summary.getRole()).thenReturn("GUEST");
        when(summary.getKind()).thenReturn("HUMAN");

        // Act
        BoardMemberCandidateResponse response = boardMemberMapper.toCandidateResponse(summary, false);

        // Assert
        assertEquals(UserKind.HUMAN, response.kind());
        assertNull(response.agentRole());
        assertNull(response.agentFlow());
        assertNull(response.agentProvider());
    }
}
