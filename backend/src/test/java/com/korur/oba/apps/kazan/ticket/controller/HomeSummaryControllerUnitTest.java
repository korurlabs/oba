package com.korur.oba.apps.kazan.ticket.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.dto.response.HomeDeadlineResponse;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneHealth;
import com.korur.oba.apps.kazan.milestone.service.MilestoneQueryService;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeLeaderboardEntryResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeLeaderboardResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeSprintResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeStaleBacklogResponse;
import com.korur.oba.apps.kazan.ticket.enums.HomeLeaderboardPeriod;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.dojo.exception.handler.mvc_layer.GlobalExceptionHandler;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Unit tests for the home dashboard endpoints. The request is dispatched through a
 * standalone MockMvc with the real exception handler, so the period parsing and its 400
 * are exercised as the API serves them.
 */
@ExtendWith(MockitoExtension.class)
class HomeSummaryControllerUnitTest {

    private static final String HEADER = CommonConstant.HEADER_ORGANIZATION_ID;

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private TicketQueryService ticketQueryService;

    @Mock
    private MilestoneQueryService milestoneQueryService;

    private User user;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        user.setId(UUID.randomUUID());
        LoggingService loggingService = new LoggingService();
        HomeSummaryController controller =
                new HomeSummaryController(ticketQueryService, milestoneQueryService, loggingService);
        HandlerMethodArgumentResolver userResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(GetUser.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                          NativeWebRequest request, WebDataBinderFactory binderFactory) {
                return user;
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(userResolver)
                .setControllerAdvice(new GlobalExceptionHandler(loggingService))
                .build();
    }

    @Test
    void getStaleBacklog_authenticatedUser_returnsOwnScopedData() throws Exception {
        // Arrange
        when(ticketQueryService.getStaleBacklog(user.getId(), organizationId)).thenReturn(new HomeStaleBacklogResponse(0L, List.of()));

        // Act and assert
        mockMvc.perform(get("/kazan/home/stale-backlog").header(HEADER, organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.tickets").isEmpty());
        verify(ticketQueryService).getStaleBacklog(user.getId(), organizationId);
    }

    @Test
    void getLeaderboard_noPeriod_defaultsToSprint() throws Exception {
        // Arrange
        when(ticketQueryService.getLeaderboard(user.getId(), organizationId, HomeLeaderboardPeriod.SPRINT)).thenReturn(
                new HomeLeaderboardResponse(HomeLeaderboardPeriod.SPRINT, null, null, List.of(
                        new HomeLeaderboardEntryResponse(UUID.randomUUID(), "A", "B", "a@b.c",
                                new BigDecimal("1.5"), 1))));

        // Act and assert
        mockMvc.perform(get("/kazan/home/leaderboard").header(HEADER, organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("SPRINT"))
                .andExpect(jsonPath("$.data.entries[0].points").value(1.5))
                .andExpect(jsonPath("$.data.entries[0].tickets").value(1));
        verify(ticketQueryService).getLeaderboard(user.getId(), organizationId, HomeLeaderboardPeriod.SPRINT);
    }

    @Test
    void getLeaderboard_lastThirtyDays_passesPeriodToService() throws Exception {
        // Arrange
        when(ticketQueryService.getLeaderboard(user.getId(), organizationId, HomeLeaderboardPeriod.LAST_30_DAYS)).thenReturn(
                new HomeLeaderboardResponse(HomeLeaderboardPeriod.LAST_30_DAYS, null, null, List.of()));

        // Act and assert
        mockMvc.perform(get("/kazan/home/leaderboard").header(HEADER, organizationId).param("period", "LAST_30_DAYS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("LAST_30_DAYS"));
    }

    @Test
    void getLeaderboard_unknownPeriod_returns400WithoutCallingService() throws Exception {
        // Act and assert
        mockMvc.perform(get("/kazan/home/leaderboard").header(HEADER, organizationId).param("period", "YEAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(ticketQueryService);
    }

    @Test
    void getHomeSprint_boardIdGiven_passesItToService() throws Exception {
        // Arrange
        UUID boardId = UUID.randomUUID();
        when(ticketQueryService.getHomeSprint(user.getId(), organizationId, boardId)).thenReturn(emptySprint());

        // Act and assert
        mockMvc.perform(get("/kazan/home/sprint").header(HEADER, organizationId).param("boardId", boardId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTickets").value(0))
                .andExpect(jsonPath("$.data.burndown").isEmpty())
                .andExpect(jsonPath("$.data.boards").isEmpty());
        verify(ticketQueryService).getHomeSprint(user.getId(), organizationId, boardId);
    }

    @Test
    void getHomeSprint_noBoardId_passesNull() throws Exception {
        // Arrange
        when(ticketQueryService.getHomeSprint(user.getId(), organizationId, null)).thenReturn(emptySprint());

        // Act and assert
        mockMvc.perform(get("/kazan/home/sprint").header(HEADER, organizationId)).andExpect(status().isOk());
        verify(ticketQueryService).getHomeSprint(user.getId(), organizationId, null);
    }

    @Test
    void getHomeSprint_malformedBoardId_returns400() throws Exception {
        // Act and assert
        mockMvc.perform(get("/kazan/home/sprint").header(HEADER, organizationId).param("boardId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ticketQueryService);
    }

    @Test
    void getHomeDeadlines_authenticatedUser_returnsDeadlines() throws Exception {
        // Arrange
        when(milestoneQueryService.listHomeDeadlines(user.getId(), organizationId)).thenReturn(List.of(new HomeDeadlineResponse(
                UUID.randomUUID(), "Launch", LocalDate.of(2026, 12, 1), MilestoneHealth.AT_RISK, 1L, 4L,
                "#3B82F6", UUID.randomUUID(), "Kagi", "kagi")));

        // Act and assert
        mockMvc.perform(get("/kazan/home/deadlines").header(HEADER, organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Launch"))
                .andExpect(jsonPath("$.data[0].targetDate[0]").value(2026))
                .andExpect(jsonPath("$.data[0].boardSlug").value("kagi"));
        verify(milestoneQueryService).listHomeDeadlines(user.getId(), organizationId);
    }

    @Test
    void endpoints_everyHomeHandler_requireGuestAccessToTheHeaderOrganization() {
        // Arrange
        List<String> names = List.of("getHomeSummary", "getStaleBacklog", "getLeaderboard", "getHomeSprint",
                "getHomeDeadlines");

        // Act
        List<Method> handlers = java.util.Arrays.stream(HomeSummaryController.class.getDeclaredMethods())
                .filter(method -> names.contains(method.getName()))
                .toList();

        // Assert: any organization member may call them; the board membership filter narrows the data
        assertThat(handlers).hasSize(names.size());
        assertThat(handlers).allSatisfy(method -> {
            assertThat(method.isAnnotationPresent(PreAuthorize.class)).isFalse();
            RequiredUserAccess access = method.getAnnotation(RequiredUserAccess.class);
            assertThat(access).isNotNull();
            assertThat(access.value()).isEqualTo(OrganizationMemberRole.GUEST);
        });
    }

    private HomeSprintResponse emptySprint() {
        return new HomeSprintResponse(null, null, null, null, null, null, null, 0, 0, List.of(), List.of());
    }
}
