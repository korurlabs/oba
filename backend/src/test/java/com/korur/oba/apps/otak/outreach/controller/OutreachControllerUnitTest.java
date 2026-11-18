package com.korur.oba.apps.otak.outreach.controller;

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
import com.korur.oba.apps.otak.outreach.constant.controller.OutreachControllerResponseConstant;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachCreateRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachQueryRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachSendRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachUpdateRequest;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachDetailResponse;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachListResponse;
import com.korur.oba.apps.otak.outreach.service.OutreachQueryService;
import com.korur.oba.apps.otak.outreach.service.OutreachService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class OutreachControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private OutreachService outreachService;

    @Mock
    private OutreachQueryService outreachQueryService;

    private OutreachController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new OutreachController(outreachService, outreachQueryService, new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_readsRequireMemberAndWritesRequireAdmin() {
        // Arrange
        Map<String, OrganizationMemberRole> expected = Map.of(
                "listOutreaches", OrganizationMemberRole.MEMBER,
                "getOutreach", OrganizationMemberRole.MEMBER,
                "createOutreach", OrganizationMemberRole.ADMIN,
                "updateOutreach", OrganizationMemberRole.ADMIN,
                "sendOutreach", OrganizationMemberRole.ADMIN,
                "completeOutreach", OrganizationMemberRole.ADMIN,
                "deleteOutreach", OrganizationMemberRole.ADMIN);

        // Act
        List<Method> handlers = Arrays.stream(OutreachController.class.getDeclaredMethods())
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
    void controller_servesTheOtakOutreachesPath() {
        assertThat(OutreachController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/outreaches");
    }

    @Test
    void listOutreaches_delegatesToTheQueryServiceAndReturns200() {
        // Arrange
        OutreachQueryRequest request = new OutreachQueryRequest();
        OutreachListResponse item = mock(OutreachListResponse.class);
        Page<OutreachListResponse> page = new PageImpl<>(List.of(item));
        when(outreachQueryService.list(organizationId, request)).thenReturn(page);

        // Act
        ResponseEntity<CustomResponse<List<OutreachListResponse>>> response =
                controller.listOutreaches(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(OutreachControllerResponseConstant.RESPONSE_OUTREACH_LIST_RETRIEVED);
        assertThat(response.getBody().data()).containsExactly(item);
    }

    @Test
    void createOutreach_delegatesWithHeaderOrganizationAndReturns201() {
        // Arrange
        OutreachCreateRequest request = mock(OutreachCreateRequest.class);
        OutreachDetailResponse detail = mock(OutreachDetailResponse.class);
        when(outreachService.create(organizationId, user, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<OutreachDetailResponse>> response =
                controller.createOutreach(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message())
                .isEqualTo(OutreachControllerResponseConstant.RESPONSE_OUTREACH_CREATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void updateOutreach_delegatesWithPathIdAndReturns200() {
        // Arrange
        UUID outreachId = UUID.randomUUID();
        OutreachUpdateRequest request = mock(OutreachUpdateRequest.class);
        OutreachDetailResponse detail = mock(OutreachDetailResponse.class);
        when(outreachService.update(organizationId, outreachId, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<OutreachDetailResponse>> response =
                controller.updateOutreach(user, organizationId, outreachId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(OutreachControllerResponseConstant.RESPONSE_OUTREACH_UPDATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void getOutreach_delegatesToTheQueryServiceAndReturns200() {
        // Arrange
        UUID outreachId = UUID.randomUUID();
        OutreachDetailResponse detail = mock(OutreachDetailResponse.class);
        when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<OutreachDetailResponse>> response =
                controller.getOutreach(user, organizationId, outreachId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(OutreachControllerResponseConstant.RESPONSE_OUTREACH_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void deleteOutreach_delegatesAndReturns200() {
        // Arrange
        UUID outreachId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response = controller.deleteOutreach(user, organizationId, outreachId);

        // Assert
        verify(outreachService).delete(organizationId, outreachId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(OutreachControllerResponseConstant.RESPONSE_OUTREACH_DELETED);
    }

    @Test
    void sendOutreach_delegatesWithPathIdAndReturns200WithTheNewStatus() {
        // Arrange
        UUID outreachId = UUID.randomUUID();
        OutreachSendRequest request = new OutreachSendRequest(UUID.randomUUID());
        OutreachDetailResponse detail = mock(OutreachDetailResponse.class);
        when(outreachService.send(organizationId, outreachId, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<OutreachDetailResponse>> response =
                controller.sendOutreach(user, organizationId, outreachId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(OutreachControllerResponseConstant.RESPONSE_OUTREACH_SENT);
        assertThat(response.getBody().data()).isSameAs(detail);
    }
}
