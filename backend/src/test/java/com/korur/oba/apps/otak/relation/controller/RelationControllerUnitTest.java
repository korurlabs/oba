package com.korur.oba.apps.otak.relation.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import com.korur.oba.apps.otak.relation.constant.controller.RelationControllerResponseConstant;
import com.korur.oba.apps.otak.relation.dto.request.RelationCreateRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationTypeRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationDetailResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationOwnerCandidateResponse;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.dto.request.RelationQueryRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
import com.korur.oba.apps.otak.relation.service.RelationAttachmentQueryService;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.apps.otak.relation.service.RelationService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class RelationControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private RelationService relationService;

    @Mock
    private RelationQueryService relationQueryService;

    @Mock
    private RelationAttachmentQueryService relationAttachmentQueryService;

    private RelationController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new RelationController(relationService, relationQueryService, relationAttachmentQueryService,
                new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_deleteRequiresAdminAndEveryOtherHandlerMember() {
        // Arrange
        Map<String, OrganizationMemberRole> expected = Map.of(
                "listRelations", OrganizationMemberRole.MEMBER,
                "listRelationOwnerCandidates", OrganizationMemberRole.MEMBER,
                "createRelation", OrganizationMemberRole.MEMBER,
                "getRelation", OrganizationMemberRole.MEMBER,
                "listRelationAttachments", OrganizationMemberRole.MEMBER,
                "updateRelation", OrganizationMemberRole.MEMBER,
                "changeRelationStage", OrganizationMemberRole.MEMBER,
                "changeRelationType", OrganizationMemberRole.MEMBER,
                "deleteRelation", OrganizationMemberRole.ADMIN);

        // Act
        List<Method> handlers = Arrays.stream(RelationController.class.getDeclaredMethods())
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
    void controller_servesTheOtakRelationsPath() {
        assertThat(RelationController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/relations");
    }

    @Test
    void listRelationOwnerCandidates_delegatesWithHeaderOrganizationAndReturns200() {
        // Arrange
        List<RelationOwnerCandidateResponse> candidates = List.of(mock(RelationOwnerCandidateResponse.class));
        when(relationQueryService.listOwnerCandidates(organizationId)).thenReturn(candidates);

        // Act
        ResponseEntity<CustomResponse<List<RelationOwnerCandidateResponse>>> response =
                controller.listRelationOwnerCandidates(user, organizationId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(RelationControllerResponseConstant.RESPONSE_RELATION_OWNER_CANDIDATES_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(candidates);
    }

    @Test
    void createRelation_delegatesWithHeaderOrganizationAndReturns201() {
        // Arrange
        RelationCreateRequest request = mock(RelationCreateRequest.class);
        RelationDetailResponse detail = mock(RelationDetailResponse.class);
        when(relationService.create(organizationId, user, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<RelationDetailResponse>> response =
                controller.createRelation(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message()).isEqualTo(RelationControllerResponseConstant.RESPONSE_RELATION_CREATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void changeRelationType_delegatesWithCallerAndReturns200() {
        // Arrange
        UUID relationId = UUID.randomUUID();
        RelationDetailResponse detail = mock(RelationDetailResponse.class);
        when(relationService.changeType(organizationId, user, relationId, RelationType.CUSTOMER)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<RelationDetailResponse>> response = controller.changeRelationType(
                user, organizationId, relationId, new RelationTypeRequest(RelationType.CUSTOMER));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(RelationControllerResponseConstant.RESPONSE_RELATION_TYPE_CHANGED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void deleteRelation_delegatesAndReturns200() {
        // Arrange
        UUID relationId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response = controller.deleteRelation(user, organizationId, relationId);

        // Assert
        verify(relationService).delete(organizationId, relationId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(RelationControllerResponseConstant.RESPONSE_RELATION_DELETED);
    }

    @Test
    void listRelations_withoutTypeAndWithEvent_passesTheRequestOnAndReturns200() {
        // Arrange
        RelationQueryRequest request = new RelationQueryRequest();
        request.setEventId(UUID.randomUUID());
        RelationListResponse row = mock(RelationListResponse.class);
        when(relationQueryService.list(organizationId, request)).thenReturn(new PageImpl<>(List.of(row)));

        // Act
        ResponseEntity<CustomResponse<List<RelationListResponse>>> response =
                controller.listRelations(user, organizationId, request);

        // Assert
        assertThat(request.getType()).isNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(RelationControllerResponseConstant.RESPONSE_RELATION_LIST_RETRIEVED);
        assertThat(response.getBody().data()).containsExactly(row);
    }
}
