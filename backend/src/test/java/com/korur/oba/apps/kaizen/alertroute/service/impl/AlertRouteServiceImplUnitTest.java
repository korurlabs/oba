package com.korur.oba.apps.kaizen.alertroute.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteBoardProjection;
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteListProjection;
import com.korur.oba.apps.kaizen.alertroute.dto.request.AlertRouteCreateRequest;
import com.korur.oba.apps.kaizen.alertroute.dto.request.AlertRouteUpdateRequest;
import com.korur.oba.apps.kaizen.alertroute.dto.response.AlertRouteResponse;
import com.korur.oba.apps.kaizen.alertroute.entity.AlertRoute;
import com.korur.oba.apps.kaizen.alertroute.mapper.AlertRouteMapper;
import com.korur.oba.apps.kaizen.alertroute.repository.AlertRouteRepository;
import com.korur.oba.apps.kaizen.alertroute.validator.AlertRouteValidator;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

@ExtendWith(MockitoExtension.class)
class AlertRouteServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private AlertRouteRepository alertRouteRepository;

    @Mock
    private AlertRouteMapper alertRouteMapper;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private LoggingService loggingService;

    @Mock
    private OrganizationQueryService organizationQueryService;

    private AlertRouteServiceImpl service;
    private User user;
    private Board board;

    @BeforeEach
    void setUp() {
        // The real validator runs over the mocked repository, so the error codes are the production ones.
        AlertRouteValidator validator = new AlertRouteValidator(alertRouteRepository, organizationQueryService,
                loggingService);
        service = new AlertRouteServiceImpl(alertRouteRepository, alertRouteMapper, validator, boardAccessService,
                loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
        board = Board.builder().build();
        board.setId(UUID.randomUUID());
    }

    private AlertRoute route(String systemKey, boolean isDefault) {
        AlertRoute route = AlertRoute.builder().systemKey(systemKey).isDefault(isDefault).board(board).build();
        route.setId(UUID.randomUUID());
        return route;
    }

    private static AlertRouteResponse response() {
        return new AlertRouteResponse(UUID.randomUUID(), "kagi", UUID.randomUUID(), "Kagi", "KAG", null, false, null);
    }

    private void stubBoardAccess() {
        when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.MEMBER))
                .thenReturn(board);
    }

    @Nested
    class CreateAlertRoute {

        @Test
        void duplicateSystem_throwsConflictAndSavesNothing() {
            stubBoardAccess();
            when(alertRouteRepository.existsByOrganizationIdAndSystemKey(organizationId, "kagi")).thenReturn(true);

            assertThatThrownBy(() -> service.createAlertRoute(user,
                    organizationId,
                    new AlertRouteCreateRequest("kagi", board.getId(), false)))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKaizenError.STA_ALERT_ROUTE_SYSTEM_DUPLICATE.getCode()));

            verify(alertRouteRepository, never()).save(any());
            verify(alertRouteRepository, never()).clearDefault(organizationId);
        }

        @Test
        void boardWithoutMembership_throwsBoardMemberNotFoundAndSavesNothing() {
            when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(), BoardMemberRole.MEMBER))
                    .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_BOARD_MEMBER_FOUND, Map.of()));

            assertThatThrownBy(() -> service.createAlertRoute(user,
                    organizationId,
                    new AlertRouteCreateRequest("kagi", board.getId(), false)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("KAI_NOT_002"));

            verify(alertRouteRepository, never()).save(any());
        }

        @Test
        void defaultRoute_clearsPreviousDefaultBeforeSaving() {
            stubBoardAccess();
            AlertRouteCreateRequest request = new AlertRouteCreateRequest("kagi", board.getId(), true);
            AlertRoute entity = route("kagi", false);
            AlertRouteResponse expected = response();
            when(alertRouteRepository.existsByOrganizationIdAndSystemKey(organizationId, "kagi")).thenReturn(false);
            when(alertRouteMapper.toEntity(request)).thenReturn(entity);
            when(alertRouteRepository.save(entity)).thenReturn(entity);
            when(alertRouteMapper.toResponse(entity)).thenReturn(expected);

            AlertRouteResponse result = service.createAlertRoute(user, organizationId, request);

            assertThat(result).isSameAs(expected);
            assertThat(entity.getIsDefault()).isTrue();
            assertThat(entity.getBoard()).isSameAs(board);
            InOrder order = inOrder(alertRouteRepository);
            order.verify(alertRouteRepository).clearDefault(organizationId);
            order.verify(alertRouteRepository).save(entity);
        }

        @Test
        void nullIsDefault_savesNonDefaultWithoutClearing() {
            stubBoardAccess();
            AlertRouteCreateRequest request = new AlertRouteCreateRequest("kagi", board.getId(), null);
            AlertRoute entity = route("kagi", true);
            when(alertRouteRepository.existsByOrganizationIdAndSystemKey(organizationId, "kagi")).thenReturn(false);
            when(alertRouteMapper.toEntity(request)).thenReturn(entity);
            when(alertRouteRepository.save(entity)).thenReturn(entity);
            when(alertRouteMapper.toResponse(entity)).thenReturn(response());

            service.createAlertRoute(user, organizationId, request);

            assertThat(entity.getIsDefault()).isFalse();
            verify(alertRouteRepository, never()).clearDefault(organizationId);
        }

        @Test
        void validRequest_setsHeaderOrganizationOnTheRoute() {
            stubBoardAccess();
            AlertRouteCreateRequest request = new AlertRouteCreateRequest("kagi", board.getId(), false);
            AlertRoute entity = route("kagi", false);
            when(alertRouteRepository.existsByOrganizationIdAndSystemKey(organizationId, "kagi")).thenReturn(false);
            when(alertRouteMapper.toEntity(request)).thenReturn(entity);
            when(alertRouteRepository.save(entity)).thenReturn(entity);
            when(alertRouteMapper.toResponse(entity)).thenReturn(response());

            service.createAlertRoute(user, organizationId, request);

            assertThat(entity.getOrganizationId()).isEqualTo(organizationId);
        }

        @Test
        void systemKeyMappedByAnotherOrganization_isAcceptedForThisOne() {
            stubBoardAccess();
            UUID otherOrganizationId = UUID.randomUUID();
            AlertRouteCreateRequest request = new AlertRouteCreateRequest("api", board.getId(), false);
            AlertRoute entity = route("api", false);
            lenient().when(alertRouteRepository.existsByOrganizationIdAndSystemKey(otherOrganizationId, "api"))
                    .thenReturn(true);
            when(alertRouteRepository.existsByOrganizationIdAndSystemKey(organizationId, "api")).thenReturn(false);
            when(alertRouteMapper.toEntity(request)).thenReturn(entity);
            when(alertRouteRepository.save(entity)).thenReturn(entity);
            when(alertRouteMapper.toResponse(entity)).thenReturn(response());

            service.createAlertRoute(user, organizationId, request);

            verify(alertRouteRepository).save(entity);
        }

        @Test
        void boardOfAnotherOrganization_throwsBoardNotFoundAndSavesNothing() {
            when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(),
                    BoardMemberRole.MEMBER))
                    .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_BOARD_FOUND, Map.of()));

            assertThatThrownBy(() -> service.createAlertRoute(user, organizationId,
                    new AlertRouteCreateRequest("kagi", board.getId(), false)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("KAI_NOT_001"));

            verify(alertRouteRepository, never()).save(any());
            verify(alertRouteRepository, never()).clearDefault(any());
        }

        @Test
        void defaultRoute_clearsOnlyTheHeaderOrganizationsDefault() {
            stubBoardAccess();
            AlertRouteCreateRequest request = new AlertRouteCreateRequest("kagi", board.getId(), true);
            AlertRoute entity = route("kagi", false);
            when(alertRouteRepository.existsByOrganizationIdAndSystemKey(organizationId, "kagi")).thenReturn(false);
            when(alertRouteMapper.toEntity(request)).thenReturn(entity);
            when(alertRouteRepository.save(entity)).thenReturn(entity);
            when(alertRouteMapper.toResponse(entity)).thenReturn(response());

            service.createAlertRoute(user, organizationId, request);

            verify(alertRouteRepository).clearDefault(organizationId);
            verify(alertRouteRepository, never()).clearDefault(
                    org.mockito.ArgumentMatchers.argThat((UUID id) -> !organizationId.equals(id)));
        }
    }

    @Nested
    class UpdateAlertRoute {

        private AlertRouteListProjection listed(UUID routeId, boolean isDefault) {
            AlertRouteListProjection row = mock(AlertRouteListProjection.class);
            lenient().when(row.getId()).thenReturn(routeId);
            lenient().when(row.getSystemKey()).thenReturn("kagi");
            lenient().when(row.getIsDefault()).thenReturn(isDefault);
            return row;
        }

        @Test
        void routeOfAnotherOrganization_throwsAlertRouteNotFoundAndChangesNothing() {
            UUID routeId = UUID.randomUUID();
            UUID otherOrganizationId = UUID.randomUUID();
            AlertRouteListProjection foreign = listed(routeId, false);
            lenient().when(alertRouteRepository.findWithBoardById(otherOrganizationId, routeId))
                    .thenReturn(Optional.of(foreign));
            when(alertRouteRepository.findWithBoardById(organizationId, routeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAlertRoute(user, organizationId, routeId,
                    new AlertRouteUpdateRequest(board.getId(), true)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("KAI_NOT_012"));

            verify(alertRouteRepository, never()).updateRouteById(any(), any(), any(), anyBoolean());
            verify(alertRouteRepository, never()).clearDefault(any());
        }

        @Test
        void targetBoardOfAnotherOrganization_throwsBoardNotFoundAndUpdatesNothing() {
            UUID routeId = UUID.randomUUID();
            AlertRouteListProjection existing = listed(routeId, false);
            when(alertRouteRepository.findWithBoardById(organizationId, routeId))
                    .thenReturn(Optional.of(existing));
            when(boardAccessService.verifyBoardAccess(organizationId, board.getId(), user.getId(),
                    BoardMemberRole.MEMBER))
                    .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_BOARD_FOUND, Map.of()));

            assertThatThrownBy(() -> service.updateAlertRoute(user, organizationId, routeId,
                    new AlertRouteUpdateRequest(board.getId(), true)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("KAI_NOT_001"));

            verify(alertRouteRepository, never()).updateRouteById(any(), any(), any(), anyBoolean());
            verify(alertRouteRepository, never()).clearDefault(any());
        }

        @Test
        void unknownRoute_throwsAlertRouteNotFound() {
            UUID routeId = UUID.randomUUID();
            when(alertRouteRepository.findWithBoardById(organizationId, routeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAlertRoute(user, organizationId, routeId,
                    new AlertRouteUpdateRequest(board.getId(), true)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("KAI_NOT_012"));

            verify(alertRouteRepository, never()).updateRouteById(eq(organizationId), any(), any(), anyBoolean());
        }

        @Test
        void routeBecomesDefault_clearsPreviousDefaultBeforeUpdating() {
            stubBoardAccess();
            UUID routeId = UUID.randomUUID();
            AlertRouteListProjection existing = listed(routeId, false);
            AlertRouteListProjection updated = listed(routeId, true);
            AlertRouteResponse expected = response();
            when(alertRouteRepository.findWithBoardById(organizationId, routeId))
                    .thenReturn(Optional.of(existing), Optional.of(updated));
            when(alertRouteMapper.toResponse(updated)).thenReturn(expected);

            AlertRouteResponse result = service.updateAlertRoute(user, organizationId, routeId,
                    new AlertRouteUpdateRequest(board.getId(), true));

            assertThat(result).isSameAs(expected);
            InOrder order = inOrder(alertRouteRepository);
            order.verify(alertRouteRepository).clearDefault(organizationId);
            order.verify(alertRouteRepository).updateRouteById(organizationId, routeId, board.getId(), true);
        }

        @Test
        void routeAlreadyDefault_doesNotClear() {
            stubBoardAccess();
            UUID routeId = UUID.randomUUID();
            AlertRouteListProjection existing = listed(routeId, true);
            when(alertRouteRepository.findWithBoardById(organizationId, routeId)).thenReturn(Optional.of(existing));
            when(alertRouteMapper.toResponse(existing)).thenReturn(response());

            service.updateAlertRoute(user, organizationId, routeId, new AlertRouteUpdateRequest(board.getId(), true));

            verify(alertRouteRepository).updateRouteById(organizationId, routeId, board.getId(), true);
            verify(alertRouteRepository, never()).clearDefault(organizationId);
        }

        @Test
        void defaultFlagRemoved_updatesNonDefaultWithoutClearing() {
            stubBoardAccess();
            UUID routeId = UUID.randomUUID();
            AlertRouteListProjection existing = listed(routeId, true);
            when(alertRouteRepository.findWithBoardById(organizationId, routeId)).thenReturn(Optional.of(existing));
            when(alertRouteMapper.toResponse(existing)).thenReturn(response());

            service.updateAlertRoute(user, organizationId, routeId, new AlertRouteUpdateRequest(board.getId(), false));

            verify(alertRouteRepository).updateRouteById(organizationId, routeId, board.getId(), false);
            verify(alertRouteRepository, never()).clearDefault(organizationId);
        }

        @Test
        void routeExists_updatesWithoutLoadingTheEntity() {
            stubBoardAccess();
            UUID routeId = UUID.randomUUID();
            AlertRouteListProjection existing = listed(routeId, false);
            when(alertRouteRepository.findWithBoardById(organizationId, routeId)).thenReturn(Optional.of(existing));
            when(alertRouteMapper.toResponse(existing)).thenReturn(response());

            service.updateAlertRoute(user, organizationId, routeId, new AlertRouteUpdateRequest(board.getId(), false));

            verify(alertRouteRepository, never()).findById(any());
            verify(alertRouteRepository, never()).save(any());
        }

        @Test
        void boardWithoutMembership_throwsBoardMemberNotFoundAndUpdatesNothing() {
            UUID routeId = UUID.randomUUID();
            AlertRouteListProjection existing = listed(routeId, false);
            when(alertRouteRepository.findWithBoardById(organizationId, routeId)).thenReturn(Optional.of(existing));
            when(boardAccessService.verifyBoardAccess(eq(organizationId), any(), eq(user.getId()), eq(BoardMemberRole.MEMBER)))
                    .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_BOARD_MEMBER_FOUND, Map.of()));

            assertThatThrownBy(() -> service.updateAlertRoute(user, organizationId, routeId,
                    new AlertRouteUpdateRequest(UUID.randomUUID(), true)))
                    .isInstanceOf(NotFoundException.class);

            verify(alertRouteRepository, never()).updateRouteById(eq(organizationId), any(), any(), anyBoolean());
            verify(alertRouteRepository, never()).clearDefault(organizationId);
        }
    }

    @Nested
    class DeleteAlertRoute {

        @Test
        void routeOfAnotherOrganization_throwsAlertRouteNotFoundAndDeletesNothing() {
            UUID routeId = UUID.randomUUID();
            UUID otherOrganizationId = UUID.randomUUID();
            AlertRouteBoardProjection foreign = mock(AlertRouteBoardProjection.class);
            lenient().when(alertRouteRepository.findBoardRouteById(otherOrganizationId, routeId))
                    .thenReturn(Optional.of(foreign));
            when(alertRouteRepository.findBoardRouteById(organizationId, routeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteAlertRoute(user, organizationId, routeId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("KAI_NOT_012"));

            verify(alertRouteRepository, never()).deleteRouteById(any(), any());
        }

        @Test
        void unknownRoute_throwsAlertRouteNotFound() {
            UUID routeId = UUID.randomUUID();
            when(alertRouteRepository.findBoardRouteById(organizationId, routeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteAlertRoute(user, organizationId, routeId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("KAI_NOT_012"));

            verify(alertRouteRepository, never()).deleteRouteById(eq(organizationId), any());
        }

        @Test
        void routeExists_deletesItWithoutLoadingTheBoard() {
            UUID routeId = UUID.randomUUID();
            AlertRouteBoardProjection existing = mock(AlertRouteBoardProjection.class);
            when(existing.getSystemKey()).thenReturn("kagi");
            when(existing.getBoardId()).thenReturn(UUID.randomUUID());
            when(alertRouteRepository.findBoardRouteById(organizationId, routeId)).thenReturn(Optional.of(existing));

            service.deleteAlertRoute(user, organizationId, routeId);

            verify(alertRouteRepository).deleteRouteById(organizationId, routeId);
            verify(alertRouteRepository, never()).findById(any());
        }
    }
}
