package com.korur.oba.shrine.document.controller.document;

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
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.document.constant.controller.document.DocumentControllerResponseConstant;
import com.korur.oba.shrine.document.dto.request.document.DocumentAttachRequest;
import com.korur.oba.shrine.document.dto.request.document.DocumentQueryRequest;
import com.korur.oba.shrine.document.dto.response.document.DocumentListResponse;
import com.korur.oba.shrine.document.service.document.DocumentQueryService;
import com.korur.oba.shrine.document.service.document.DocumentService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

@ExtendWith(MockitoExtension.class)
class DocumentControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentQueryService documentQueryService;

    private DocumentController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new DocumentController(documentService, documentQueryService, new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_everyHandlerRequiresMemberAndTheOwnerDecidesTheRest() {
        // Act
        List<Method> handlers = Arrays.stream(DocumentController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        // Assert
        assertThat(handlers).extracting(Method::getName).containsExactlyInAnyOrder("listDocuments",
                "createDocument", "getDocument", "updateDocument", "replaceDocumentFiles", "deleteDocument",
                "attachDocument");
        assertThat(handlers).allSatisfy(method -> {
            assertThat(method.isAnnotationPresent(PreAuthorize.class)).isFalse();
            RequiredUserAccess access = method.getAnnotation(RequiredUserAccess.class);
            assertThat(access).as(method.getName()).isNotNull();
            assertThat(access.value()).as(method.getName()).isEqualTo(OrganizationMemberRole.MEMBER);
        });
    }

    @Test
    void controller_servesTheShrineDocumentsPath() {
        assertThat(DocumentController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/documents");
    }

    @Test
    void listDocuments_delegatesWithTheCallerAndReturns200() {
        // Arrange
        DocumentQueryRequest request = new DocumentQueryRequest();
        DocumentListResponse item = mock(DocumentListResponse.class);
        Page<DocumentListResponse> page = new PageImpl<>(List.of(item));
        when(documentQueryService.list(user, organizationId, request)).thenReturn(page);

        // Act
        ResponseEntity<CustomResponse<List<DocumentListResponse>>> response =
                controller.listDocuments(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(DocumentControllerResponseConstant.RESPONSE_DOCUMENT_LIST_RETRIEVED);
        assertThat(response.getBody().data()).containsExactly(item);
    }

    @Test
    void attachDocument_passesTheParentAndReturns201() {
        // Arrange
        UUID documentId = UUID.randomUUID();
        UUID mailId = UUID.randomUUID();
        AttachmentResponse attachment = mock(AttachmentResponse.class);
        when(documentService.attachTo(user, organizationId, documentId, StorageEntityType.MAIL, mailId))
                .thenReturn(attachment);

        // Act
        ResponseEntity<CustomResponse<AttachmentResponse>> response = controller.attachDocument(user, organizationId,
                documentId, new DocumentAttachRequest(StorageEntityType.MAIL, mailId));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message())
                .isEqualTo(DocumentControllerResponseConstant.RESPONSE_DOCUMENT_ATTACHED);
        assertThat(response.getBody().data()).isSameAs(attachment);
    }

    @Test
    void deleteDocument_delegatesAndReturns200() {
        // Arrange
        UUID documentId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response = controller.deleteDocument(user, organizationId, documentId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(documentService).delete(organizationId, user, documentId);
    }
}
