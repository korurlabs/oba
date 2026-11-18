package com.korur.oba.apps.otak.icp.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import com.korur.oba.apps.otak.icp.constant.controller.IcpControllerResponseConstant;
import com.korur.oba.apps.otak.icp.dto.request.IcpCreateRequest;
import com.korur.oba.apps.otak.icp.dto.request.IcpUpdateRequest;
import com.korur.oba.apps.otak.icp.dto.response.IcpDetailResponse;
import com.korur.oba.apps.otak.icp.service.IcpQueryService;
import com.korur.oba.apps.otak.icp.service.IcpService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class IcpControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private IcpService icpService;

    @Mock
    private IcpQueryService icpQueryService;

    private IcpController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new IcpController(icpService, icpQueryService, new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_deleteRequiresAdminAndEveryOtherHandlerMember() {
        // Arrange
        Map<String, OrganizationMemberRole> expected = Map.of(
                "listIcps", OrganizationMemberRole.MEMBER,
                "createIcp", OrganizationMemberRole.MEMBER,
                "getIcp", OrganizationMemberRole.MEMBER,
                "updateIcp", OrganizationMemberRole.MEMBER,
                "deleteIcp", OrganizationMemberRole.ADMIN);

        // Act
        List<Method> handlers = Arrays.stream(IcpController.class.getDeclaredMethods())
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
    void controller_servesTheOtakIcpsPath() {
        assertThat(IcpController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/icps");
    }

    @Test
    void createIcp_delegatesWithHeaderOrganizationAndReturns201() {
        // Arrange
        IcpCreateRequest request = mock(IcpCreateRequest.class);
        IcpDetailResponse detail = mock(IcpDetailResponse.class);
        when(icpService.create(organizationId, user, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<IcpDetailResponse>> response = controller.createIcp(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message()).isEqualTo(IcpControllerResponseConstant.RESPONSE_ICP_CREATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void updateIcp_delegatesWithPathIdAndReturns200() {
        // Arrange
        UUID icpId = UUID.randomUUID();
        IcpUpdateRequest request = mock(IcpUpdateRequest.class);
        IcpDetailResponse detail = mock(IcpDetailResponse.class);
        when(icpService.update(organizationId, icpId, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<IcpDetailResponse>> response =
                controller.updateIcp(user, organizationId, icpId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(IcpControllerResponseConstant.RESPONSE_ICP_UPDATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void getIcp_delegatesToTheQueryServiceAndReturns200() {
        // Arrange
        UUID icpId = UUID.randomUUID();
        IcpDetailResponse detail = mock(IcpDetailResponse.class);
        when(icpQueryService.get(organizationId, icpId)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<IcpDetailResponse>> response = controller.getIcp(user, organizationId, icpId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(IcpControllerResponseConstant.RESPONSE_ICP_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void deleteIcp_delegatesAndReturns200() {
        // Arrange
        UUID icpId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response = controller.deleteIcp(user, organizationId, icpId);

        // Assert
        verify(icpService).delete(organizationId, icpId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(IcpControllerResponseConstant.RESPONSE_ICP_DELETED);
    }
}
