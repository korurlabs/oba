package com.korur.oba.shrine.storage.service.attachment.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.connect.azure.service.AzureBlobStorageQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentExistsCheckRequest;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentMetadata;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentExistsResponse;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.entity.attachment.Attachment;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.exception.ExceptionDomainStorageError;
import com.korur.oba.shrine.storage.helper.attachment.AttachmentHashHelper;
import com.korur.oba.shrine.storage.mapper.attachment.AttachmentMapper;
import com.korur.oba.shrine.storage.repository.attachment.AttachmentRepository;
import com.korur.oba.shrine.storage.validator.attachment.AttachmentValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AttachmentQueryServiceImpl.checkAttachmentExists with
 * all collaborators mocked.
 *
 * Asserts the boolean-only contract: when the repository predicate
 * matches, the response carries exists=true; when it does not, exists
 * is false. Also pins the validation prefix (empty file, filename
 * mismatch) so a future refactor cannot accidentally bypass either
 * guard, and verifies that the validators are invoked exactly once per
 * call, and that an entity type without a resource access type is refused
 * before any file is read.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentQueryServiceImplUnitTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private AttachmentValidator attachmentValidator;

    @Mock
    private AttachmentHashHelper attachmentHashHelper;

    @Mock
    private LoggingService loggingService;

    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;

    @Mock
    private AzureBlobStorageQueryService azureBlobStorageQueryService;

    @InjectMocks
    private AttachmentQueryServiceImpl attachmentQueryServiceImpl;

    private final UUID organizationId = UUID.randomUUID();

    private static final String FAKE_HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Nested
    class CheckAttachmentExists {

        @Test
        void repositoryReturnsTrue_responseIsTrue() {
            // Arrange
            AttachmentExistsCheckRequest request = new AttachmentExistsCheckRequest(
                    "doc.pdf", StorageEntityType.TICKET);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "content".getBytes());

            when(attachmentHashHelper.computeHash(file)).thenReturn(FAKE_HASH);
            when(attachmentRepository.existsByEntityTypeAndFileHashAndOrganizationId(
                    eq(StorageEntityType.TICKET), anyString(), eq(organizationId)))
                    .thenReturn(true);
            when(attachmentMapper.toExistsResponse(true)).thenReturn(new AttachmentExistsResponse(true));

            // Act
            AttachmentExistsResponse response = attachmentQueryServiceImpl.checkAttachmentExists(organizationId, request, file);

            // Assert
            assertTrue(response.exists());
            verify(attachmentValidator).ensureFileNotEmpty(file);
            verify(attachmentValidator).ensureFileNameMatches(eq(file), any(AttachmentMetadata.class));
        }

        @Test
        void repositoryReturnsFalse_responseIsFalse() {
            // Arrange
            AttachmentExistsCheckRequest request = new AttachmentExistsCheckRequest(
                    "doc.pdf", StorageEntityType.TICKET);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "content".getBytes());

            when(attachmentHashHelper.computeHash(file)).thenReturn(FAKE_HASH);
            when(attachmentRepository.existsByEntityTypeAndFileHashAndOrganizationId(
                    eq(StorageEntityType.TICKET), anyString(), eq(organizationId)))
                    .thenReturn(false);
            when(attachmentMapper.toExistsResponse(false)).thenReturn(new AttachmentExistsResponse(false));

            // Act
            AttachmentExistsResponse response = attachmentQueryServiceImpl.checkAttachmentExists(organizationId, request, file);

            // Assert
            assertFalse(response.exists());
        }

        @Test
        void chatMessageFileSentBefore_responseIsFalseWithoutLookup() {
            // Arrange
            AttachmentExistsCheckRequest request = new AttachmentExistsCheckRequest(
                    "doc.pdf", StorageEntityType.CHAT_MESSAGE);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "content".getBytes());

            when(attachmentMapper.toExistsResponse(false)).thenReturn(new AttachmentExistsResponse(false));

            // Act
            AttachmentExistsResponse response = attachmentQueryServiceImpl.checkAttachmentExists(organizationId, request, file);

            // Assert
            assertFalse(response.exists());
            verify(attachmentValidator).ensureFileNotEmpty(file);
            verify(attachmentValidator).ensureFileNameMatches(eq(file), any(AttachmentMetadata.class));
            verifyNoInteractions(attachmentRepository, attachmentHashHelper);
        }

        @Test
        void kalkanPageContentAttachedElsewhere_responseIsFalseWithoutLookup() {
            // Arrange
            AttachmentExistsCheckRequest request = new AttachmentExistsCheckRequest(
                    "diagram.png", StorageEntityType.KALKAN_PAGE);
            MultipartFile file = new MockMultipartFile(
                    "file", "diagram.png", "image/png", "content".getBytes());

            when(attachmentMapper.toExistsResponse(false)).thenReturn(new AttachmentExistsResponse(false));

            // Act
            AttachmentExistsResponse response = attachmentQueryServiceImpl.checkAttachmentExists(organizationId, request, file);

            // Assert
            assertFalse(response.exists());
            verifyNoInteractions(attachmentRepository, attachmentHashHelper);
        }

        @Test
        void entityTypeRefusedByTheValidator_throwsForbiddenBeforeReadingFile() {
            // Arrange: every current type has access rules, so the refusal is the validator's answer
            AttachmentExistsCheckRequest request = new AttachmentExistsCheckRequest(
                    "doc.pdf", StorageEntityType.TICKET);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "content".getBytes());
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.TICKET))
                    .thenThrow(new ForbiddenException(ExceptionDomainStorageError.SEC_ENTITY_TYPE_NOT_ATTACHABLE));

            // Act & Assert
            assertThrows(ForbiddenException.class,
                    () -> attachmentQueryServiceImpl.checkAttachmentExists(organizationId, request, file));
            verify(attachmentValidator, never()).ensureFileNotEmpty(any());
            verifyNoInteractions(attachmentHashHelper, attachmentRepository, attachmentMapper,
                    resourceAccessQueryService);
        }

        @Test
        void emptyFile_validatorThrows_repositoryNotCalled() {
            // Arrange
            AttachmentExistsCheckRequest request = new AttachmentExistsCheckRequest(
                    "doc.pdf", StorageEntityType.TICKET);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", new byte[0]);

            doThrow(new ValidationException(ExceptionDomainStorageError.VAL_FILE_EMPTY))
                    .when(attachmentValidator).ensureFileNotEmpty(file);

            // Act & Assert
            assertThrows(ValidationException.class,
                    () -> attachmentQueryServiceImpl.checkAttachmentExists(organizationId, request, file));
            verify(attachmentRepository, never()).existsByEntityTypeAndFileHashAndOrganizationId(any(), anyString(), any());
        }

        @Test
        void nameMismatch_validatorThrows_repositoryNotCalled() {
            // Arrange
            AttachmentExistsCheckRequest request = new AttachmentExistsCheckRequest(
                    "expected.pdf", StorageEntityType.TICKET);
            MultipartFile file = new MockMultipartFile(
                    "file", "different.pdf", "application/pdf", "content".getBytes());

            doThrow(new ValidationException(ExceptionDomainStorageError.VAL_FILE_NAME_MISSING))
                    .when(attachmentValidator)
                    .ensureFileNameMatches(eq(file), any(AttachmentMetadata.class));

            // Act & Assert
            assertThrows(ValidationException.class,
                    () -> attachmentQueryServiceImpl.checkAttachmentExists(organizationId, request, file));
            verify(attachmentRepository, never()).existsByEntityTypeAndFileHashAndOrganizationId(any(), anyString(), any());
        }

        @Test
        void invokesValidatorsExactlyOnce() {
            // Arrange
            AttachmentExistsCheckRequest request = new AttachmentExistsCheckRequest(
                    "doc.pdf", StorageEntityType.TICKET);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "content".getBytes());

            when(attachmentHashHelper.computeHash(file)).thenReturn(FAKE_HASH);
            when(attachmentRepository.existsByEntityTypeAndFileHashAndOrganizationId(
                    eq(StorageEntityType.TICKET), anyString(), eq(organizationId)))
                    .thenReturn(false);
            when(attachmentMapper.toExistsResponse(false)).thenReturn(new AttachmentExistsResponse(false));

            // Act
            attachmentQueryServiceImpl.checkAttachmentExists(organizationId, request, file);

            // Assert
            verify(attachmentValidator, times(1)).ensureFileNotEmpty(file);
            verify(attachmentValidator, times(1)).ensureFileNameMatches(eq(file), any(AttachmentMetadata.class));
        }

        @Test
        void asksOnlyWithinTheRequestOrganization() {
            // Arrange
            AttachmentExistsCheckRequest request = new AttachmentExistsCheckRequest(
                    "doc.pdf", StorageEntityType.TICKET);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "content".getBytes());
            when(attachmentHashHelper.computeHash(file)).thenReturn(FAKE_HASH);
            when(attachmentMapper.toExistsResponse(false)).thenReturn(new AttachmentExistsResponse(false));

            // Act
            attachmentQueryServiceImpl.checkAttachmentExists(organizationId, request, file);

            // Assert
            verify(attachmentRepository).existsByEntityTypeAndFileHashAndOrganizationId(StorageEntityType.TICKET,
                    FAKE_HASH, organizationId);
        }
    }

    @Nested
    class ListAttachmentsByEntity {

        private User user() {
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            return user;
        }

        private void ticketAccessible() {
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.TICKET))
                    .thenReturn(ResourceAccessType.TICKET);
        }

        @Test
        void ticketReadAllowed_checksAccessBeforeRepositoryRead() {
            // Arrange
            User user = user();
            UUID ticketId = UUID.randomUUID();
            ticketAccessible();
            Attachment attachment = Attachment.builder().build();
            AttachmentResponse response = mock(AttachmentResponse.class);
            when(attachmentRepository.findByEntityTypeAndEntityIdAndOrganizationIdOrderByCreatedAtAscIdAsc(StorageEntityType.TICKET,
                    ticketId, organizationId))
                    .thenReturn(List.of(attachment));
            when(attachmentMapper.toResponse(attachment)).thenReturn(response);

            // Act
            List<AttachmentResponse> result =
                    attachmentQueryServiceImpl.listAttachmentsByEntity(user, organizationId, StorageEntityType.TICKET, ticketId);

            // Assert
            assertEquals(List.of(response), result);
            InOrder order = inOrder(resourceAccessQueryService, attachmentRepository);
            order.verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, ticketId,
                    ResourceAccessAction.READ);
            order.verify(attachmentRepository).findByEntityTypeAndEntityIdAndOrganizationIdOrderByCreatedAtAscIdAsc(StorageEntityType.TICKET,
                    ticketId, organizationId);
        }

        @Test
        void ticketReadRefused_throwsAndSkipsRepository() {
            // Arrange
            User user = user();
            UUID ticketId = UUID.randomUUID();
            ticketAccessible();
            doThrow(new NotFoundException(ExceptionDomainKazanError.NOT_BOARD_MEMBER_FOUND))
                    .when(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                            ResourceAccessType.TICKET, ticketId, ResourceAccessAction.READ);

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    attachmentQueryServiceImpl.listAttachmentsByEntity(user, organizationId, StorageEntityType.TICKET, ticketId));
            verifyNoInteractions(attachmentRepository, attachmentMapper);
        }

        @Test
        void entityTypeRefusedByTheValidator_throwsForbiddenAndSkipsRepository() {
            // Arrange
            User user = user();
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.KALKAN_PAGE))
                    .thenThrow(new ForbiddenException(ExceptionDomainStorageError.SEC_ENTITY_TYPE_NOT_ATTACHABLE));

            // Act & Assert
            assertThrows(ForbiddenException.class, () -> attachmentQueryServiceImpl.listAttachmentsByEntity(
                    user, organizationId, StorageEntityType.KALKAN_PAGE, UUID.randomUUID()));
            verifyNoInteractions(attachmentRepository, attachmentMapper);
        }
    }

    @Nested
    class ListAttachmentsByEntities {

        private User user() {
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            return user;
        }

        private void ticketAccessible() {
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.TICKET))
                    .thenReturn(ResourceAccessType.TICKET);
        }

        @Test
        void readAllowed_checksEveryParentBeforeOneQueryAndGroupsInRequestOrder() {
            // Arrange
            User user = user();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            UUID withoutFiles = UUID.randomUUID();
            ticketAccessible();
            Attachment secondFile = Attachment.builder().entityId(second).build();
            Attachment firstFileA = Attachment.builder().entityId(first).build();
            Attachment firstFileB = Attachment.builder().entityId(first).build();
            AttachmentResponse secondResponse = mock(AttachmentResponse.class);
            AttachmentResponse firstResponseA = mock(AttachmentResponse.class);
            AttachmentResponse firstResponseB = mock(AttachmentResponse.class);
            Set<UUID> requested = Set.of(first, second, withoutFiles);
            when(attachmentRepository.findByEntityTypeAndEntityIdInAndOrganizationIdOrderByCreatedAtAscIdAsc(
                    eq(StorageEntityType.TICKET), any(), eq(organizationId)))
                    .thenReturn(List.of(secondFile, firstFileA, firstFileB));
            when(attachmentMapper.toResponse(secondFile)).thenReturn(secondResponse);
            when(attachmentMapper.toResponse(firstFileA)).thenReturn(firstResponseA);
            when(attachmentMapper.toResponse(firstFileB)).thenReturn(firstResponseB);

            // Act
            Map<UUID, List<AttachmentResponse>> result = attachmentQueryServiceImpl.listAttachmentsByEntities(
                    user, organizationId, StorageEntityType.TICKET, List.of(first, second, withoutFiles, first));

            // Assert
            assertEquals(List.of(first, second, withoutFiles), new ArrayList<>(result.keySet()));
            assertEquals(List.of(firstResponseA, firstResponseB), result.get(first));
            assertEquals(List.of(secondResponse), result.get(second));
            assertTrue(result.get(withoutFiles).isEmpty());
            InOrder order = inOrder(resourceAccessQueryService, attachmentRepository);
            order.verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, first,
                    ResourceAccessAction.READ);
            order.verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, second,
                    ResourceAccessAction.READ);
            order.verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, withoutFiles,
                    ResourceAccessAction.READ);
            order.verify(attachmentRepository).findByEntityTypeAndEntityIdInAndOrganizationIdOrderByCreatedAtAscIdAsc(
                    eq(StorageEntityType.TICKET), eq(requested), eq(organizationId));
            verify(resourceAccessQueryService, times(1)).verifyResourceAccess(user, organizationId, ResourceAccessType.TICKET, first,
                    ResourceAccessAction.READ);
        }

        @Test
        void oneParentRefused_throwsAndSkipsRepository() {
            // Arrange
            User user = user();
            UUID allowed = UUID.randomUUID();
            UUID refused = UUID.randomUUID();
            ticketAccessible();
            doThrow(new NotFoundException(ExceptionDomainKazanError.NOT_BOARD_MEMBER_FOUND))
                    .when(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                            ResourceAccessType.TICKET, refused, ResourceAccessAction.READ);

            // Act & Assert
            assertThrows(NotFoundException.class, () -> attachmentQueryServiceImpl.listAttachmentsByEntities(
                    user, organizationId, StorageEntityType.TICKET, List.of(refused, allowed)));
            verifyNoInteractions(attachmentRepository, attachmentMapper);
        }

        @Test
        void entityTypeRefusedByTheValidator_throwsForbiddenAndSkipsRepository() {
            // Arrange
            User user = user();
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.CHAT_MESSAGE))
                    .thenThrow(new ForbiddenException(ExceptionDomainStorageError.SEC_ENTITY_TYPE_NOT_ATTACHABLE));

            // Act & Assert
            assertThrows(ForbiddenException.class, () -> attachmentQueryServiceImpl.listAttachmentsByEntities(
                    user, organizationId, StorageEntityType.CHAT_MESSAGE, List.of(UUID.randomUUID())));
            verifyNoInteractions(attachmentRepository, attachmentMapper);
        }

        @Test
        void nullOrEmptyIds_returnsEmptyMapWithoutQuerying() {
            // Arrange
            User user = user();

            // Act
            Map<UUID, List<AttachmentResponse>> fromNull =
                    attachmentQueryServiceImpl.listAttachmentsByEntities(user, organizationId, StorageEntityType.TICKET, null);
            Map<UUID, List<AttachmentResponse>> fromEmpty =
                    attachmentQueryServiceImpl.listAttachmentsByEntities(user, organizationId, StorageEntityType.TICKET, List.of());

            // Assert
            assertTrue(fromNull.isEmpty());
            assertTrue(fromEmpty.isEmpty());
            verifyNoInteractions(resourceAccessQueryService, attachmentValidator, attachmentRepository, attachmentMapper);
        }
    }

    @Nested
    class GetEntityAttachmentContents {

        private Attachment row(String url, String fileName, String contentType) {
            return Attachment.builder().url(url).fileName(fileName).contentType(contentType).build();
        }

        @Test
        void rowsInUploadOrder_returnsContentsInThatOrderWithOwnNameAndType() {
            UUID mailId = UUID.randomUUID();
            Attachment first = row("https://host/account/container/first-blob.pdf", "voorstel.pdf",
                    "application/pdf");
            Attachment second = row("https://host/account/container/second-blob.docx", "scope.docx",
                    "application/vnd.openxmlformats");
            when(attachmentRepository.findByEntityTypeAndEntityIdAndOrganizationIdOrderByCreatedAtAscIdAsc(
                    StorageEntityType.MAIL, mailId, organizationId)).thenReturn(List.of(first, second));
            when(azureBlobStorageQueryService.downloadPrivateBlob("first-blob.pdf")).thenReturn(new byte[]{1});
            when(azureBlobStorageQueryService.downloadPrivateBlob("second-blob.docx")).thenReturn(new byte[]{2, 2});

            List<AttachmentContent> result = attachmentQueryServiceImpl
                    .getEntityAttachmentContents(organizationId, StorageEntityType.MAIL, mailId);

            assertEquals(2, result.size());
            assertEquals("voorstel.pdf", result.get(0).fileName());
            assertEquals("application/pdf", result.get(0).contentType());
            assertEquals(1, result.get(0).content().length);
            assertEquals("scope.docx", result.get(1).fileName());
            assertEquals("application/vnd.openxmlformats", result.get(1).contentType());
            assertEquals(2, result.get(1).content().length);
            InOrder order = inOrder(azureBlobStorageQueryService);
            order.verify(azureBlobStorageQueryService).downloadPrivateBlob("first-blob.pdf");
            order.verify(azureBlobStorageQueryService).downloadPrivateBlob("second-blob.docx");
        }

        @Test
        void noRows_returnsEmptyAndDownloadsNothing() {
            UUID mailId = UUID.randomUUID();
            when(attachmentRepository.findByEntityTypeAndEntityIdAndOrganizationIdOrderByCreatedAtAscIdAsc(
                    StorageEntityType.MAIL, mailId, organizationId)).thenReturn(List.of());

            assertTrue(attachmentQueryServiceImpl.getEntityAttachmentContents(organizationId, StorageEntityType.MAIL, mailId)
                    .isEmpty());

            verifyNoInteractions(azureBlobStorageQueryService);
            verifyNoInteractions(resourceAccessQueryService);
        }
    }
}
