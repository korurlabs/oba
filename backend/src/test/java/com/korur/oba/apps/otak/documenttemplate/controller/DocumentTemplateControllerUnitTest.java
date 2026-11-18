package com.korur.oba.apps.otak.documenttemplate.controller;

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
import org.springframework.mock.web.MockMultipartFile;
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
import com.korur.oba.apps.otak.documenttemplate.constant.controller.DocumentTemplateControllerResponseConstant;
import com.korur.oba.apps.otak.documenttemplate.dto.request.DocumentTemplateQueryRequest;
import com.korur.oba.apps.otak.documenttemplate.dto.request.DocumentTemplateCreateRequest;
import com.korur.oba.apps.otak.documenttemplate.dto.request.DocumentTemplateUpdateRequest;
import com.korur.oba.apps.otak.documenttemplate.dto.response.DocumentTemplateDetailResponse;
import com.korur.oba.apps.otak.documenttemplate.dto.response.DocumentTemplateListResponse;
import com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateQueryService;
import com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class DocumentTemplateControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private DocumentTemplateService documentTemplateService;

    @Mock
    private DocumentTemplateQueryService documentTemplateQueryService;

    private DocumentTemplateController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new DocumentTemplateController(documentTemplateService, documentTemplateQueryService,
                new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_readsAndAttachmentCopiesRequireMemberAndWritesRequireAdmin() {
        // Arrange
        Map<String, OrganizationMemberRole> expected = Map.of(
                "listDocumentTemplates", OrganizationMemberRole.MEMBER,
                "getDocumentTemplate", OrganizationMemberRole.MEMBER,
                "createDocumentTemplate", OrganizationMemberRole.ADMIN,
                "updateDocumentTemplate", OrganizationMemberRole.ADMIN,
                "replaceDocumentTemplateFiles", OrganizationMemberRole.ADMIN,
                "deleteDocumentTemplate", OrganizationMemberRole.ADMIN,
                "createDocumentTemplateAttachment", OrganizationMemberRole.MEMBER);

        // Act
        List<Method> handlers = Arrays.stream(DocumentTemplateController.class.getDeclaredMethods())
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
    void controller_servesTheOtakDocumentTemplatesPath() {
        assertThat(DocumentTemplateController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/document-templates");
    }

    @Test
    void listDocumentTemplates_delegatesToTheQueryServiceAndReturns200() {
        // Arrange
        DocumentTemplateQueryRequest request = new DocumentTemplateQueryRequest();
        DocumentTemplateListResponse item = mock(DocumentTemplateListResponse.class);
        Page<DocumentTemplateListResponse> page = new PageImpl<>(List.of(item));
        when(documentTemplateQueryService.list(organizationId, request)).thenReturn(page);

        // Act
        ResponseEntity<CustomResponse<List<DocumentTemplateListResponse>>> response =
                controller.listDocumentTemplates(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_LIST_RETRIEVED);
        assertThat(response.getBody().data()).containsExactly(item);
    }

    @Test
    void createDocumentTemplate_delegatesWithHeaderOrganizationAndReturns201() {
        // Arrange
        DocumentTemplateCreateRequest request = mock(DocumentTemplateCreateRequest.class);
        DocumentTemplateDetailResponse detail = mock(DocumentTemplateDetailResponse.class);
        when(documentTemplateService.create(organizationId, user, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<DocumentTemplateDetailResponse>> response =
                controller.createDocumentTemplate(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message())
                .isEqualTo(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_CREATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void getDocumentTemplate_delegatesToTheQueryServiceAndReturns200() {
        // Arrange
        UUID templateId = UUID.randomUUID();
        DocumentTemplateDetailResponse detail = mock(DocumentTemplateDetailResponse.class);
        when(documentTemplateQueryService.get(organizationId, templateId)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<DocumentTemplateDetailResponse>> response =
                controller.getDocumentTemplate(user, organizationId, templateId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void updateDocumentTemplate_delegatesWithPathIdAndReturns200() {
        // Arrange
        UUID templateId = UUID.randomUUID();
        DocumentTemplateUpdateRequest request = mock(DocumentTemplateUpdateRequest.class);
        DocumentTemplateDetailResponse detail = mock(DocumentTemplateDetailResponse.class);
        when(documentTemplateService.update(organizationId, templateId, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<DocumentTemplateDetailResponse>> response =
                controller.updateDocumentTemplate(user, organizationId, templateId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_UPDATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void replaceDocumentTemplateFiles_passesBothPartsToTheServiceAndReturns200() {
        // Arrange
        UUID templateId = UUID.randomUUID();
        MockMultipartFile html = new MockMultipartFile("html", "a.html", "text/html", "<p/>".getBytes());
        MockMultipartFile pdf = new MockMultipartFile("pdf", "a.pdf", "application/pdf", "%PDF-".getBytes());
        DocumentTemplateDetailResponse detail = mock(DocumentTemplateDetailResponse.class);
        when(documentTemplateService.replaceFiles(organizationId, templateId, html, pdf)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<DocumentTemplateDetailResponse>> response =
                controller.replaceDocumentTemplateFiles(user, organizationId, templateId, html, pdf);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_FILES_REPLACED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void deleteDocumentTemplate_delegatesAndReturns200() {
        // Arrange
        UUID templateId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response =
                controller.deleteDocumentTemplate(user, organizationId, templateId);

        // Assert
        verify(documentTemplateService).delete(organizationId, templateId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_DELETED);
    }
}
