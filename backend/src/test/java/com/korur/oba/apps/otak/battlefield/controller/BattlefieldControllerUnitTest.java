package com.korur.oba.apps.otak.battlefield.controller;

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
import com.korur.oba.apps.otak.battlefield.constant.controller.BattlefieldControllerResponseConstant;
import com.korur.oba.apps.otak.battlefield.service.BattlefieldQueryService;
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class BattlefieldControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private BattlefieldQueryService battlefieldQueryService;

    private BattlefieldController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new BattlefieldController(battlefieldQueryService, new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_everyHandlerRequiresMember() {
        // Arrange
        Map<String, OrganizationMemberRole> expected = Map.of(
                "getBattlefield", OrganizationMemberRole.MEMBER);

        // Act
        List<Method> handlers = Arrays.stream(BattlefieldController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        // Assert: every public method is a handler with exactly its expected minimum role
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
    void controller_servesTheOtakBattlefieldPath() {
        assertThat(BattlefieldController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/battlefield");
    }

    @Test
    void getBattlefield_delegatesWithHeaderOrganizationAndReturns200() {
        // Arrange
        List<RelationListResponse> leads = List.of(mock(RelationListResponse.class));
        when(battlefieldQueryService.getBattlefield(organizationId)).thenReturn(leads);

        // Act
        ResponseEntity<CustomResponse<List<RelationListResponse>>> response =
                controller.getBattlefield(user, organizationId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(BattlefieldControllerResponseConstant.RESPONSE_BATTLEFIELD_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(leads);
    }
}
