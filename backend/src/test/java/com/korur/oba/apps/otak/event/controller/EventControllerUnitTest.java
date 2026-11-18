package com.korur.oba.apps.otak.event.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.otak.event.constant.controller.EventControllerResponseConstant;
import com.korur.oba.apps.otak.event.dto.request.EventQueryRequest;
import com.korur.oba.apps.otak.event.dto.request.EventUpdateRequest;
import com.korur.oba.apps.otak.event.dto.request.EventCreateRequest;
import com.korur.oba.apps.otak.event.dto.response.EventDetailResponse;
import com.korur.oba.apps.otak.event.dto.response.EventListResponse;
import com.korur.oba.apps.otak.event.service.EventQueryService;
import com.korur.oba.apps.otak.event.service.EventService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class EventControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private EventService eventService;

    @Mock
    private EventQueryService eventQueryService;

    private EventController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new EventController(eventService, eventQueryService, new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_deleteRequiresAdminAndEveryOtherHandlerMember() {
        // Arrange
        Map<String, OrganizationMemberRole> expected = Map.of(
                "listEvents", OrganizationMemberRole.MEMBER,
                "createEvent", OrganizationMemberRole.MEMBER,
                "getEvent", OrganizationMemberRole.MEMBER,
                "updateEvent", OrganizationMemberRole.MEMBER,
                "deleteEvent", OrganizationMemberRole.ADMIN);

        // Act
        List<Method> handlers = Arrays.stream(EventController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        // Assert
        assertThat(handlers.stream().map(Method::getName).collect(Collectors.toSet()))
                .isEqualTo(expected.keySet());
        assertThat(handlers).allSatisfy(method -> {
            assertThat(method.isAnnotationPresent(PreAuthorize.class)).isFalse();
            RequiredUserAccess access = method.getAnnotation(RequiredUserAccess.class);
            assertThat(access).as(method.getName()).isNotNull();
            assertThat(access.value()).as(method.getName()).isEqualTo(expected.get(method.getName()));
        });
    }

    @Test
    void controller_servesTheOtakEventsPath() {
        assertThat(EventController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/events");
    }

    @Test
    void listEvents_delegatesToTheQueryServiceAndReturns200() {
        // Arrange
        EventQueryRequest request = new EventQueryRequest();
        Page<EventListResponse> page = new PageImpl<>(List.of(mock(EventListResponse.class)));
        when(eventQueryService.list(organizationId, request)).thenReturn(page);

        // Act
        ResponseEntity<CustomResponse<List<EventListResponse>>> response =
                controller.listEvents(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(EventControllerResponseConstant.RESPONSE_EVENT_LIST_RETRIEVED);
        assertThat(response.getBody().data()).hasSize(1);
    }

    @Test
    void createEvent_delegatesWithHeaderOrganizationAndReturns201() {
        // Arrange
        EventCreateRequest request = mock(EventCreateRequest.class);
        EventDetailResponse detail = mock(EventDetailResponse.class);
        when(eventService.create(organizationId, user, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<EventDetailResponse>> response =
                controller.createEvent(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message()).isEqualTo(EventControllerResponseConstant.RESPONSE_EVENT_CREATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void getEvent_delegatesToTheQueryServiceAndReturns200() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        EventDetailResponse detail = mock(EventDetailResponse.class);
        when(eventQueryService.get(organizationId, eventId)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<EventDetailResponse>> response =
                controller.getEvent(user, organizationId, eventId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(EventControllerResponseConstant.RESPONSE_EVENT_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void updateEvent_delegatesWithPathIdAndReturns200() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        EventUpdateRequest request = mock(EventUpdateRequest.class);
        EventDetailResponse detail = mock(EventDetailResponse.class);
        when(eventService.update(organizationId, eventId, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<EventDetailResponse>> response =
                controller.updateEvent(user, organizationId, eventId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(EventControllerResponseConstant.RESPONSE_EVENT_UPDATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void deleteEvent_delegatesAndReturns200() {
        // Arrange
        UUID eventId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response = controller.deleteEvent(user, organizationId, eventId);

        // Assert
        verify(eventService).delete(organizationId, eventId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(EventControllerResponseConstant.RESPONSE_EVENT_DELETED);
    }
}
