package com.korur.oba.shrine.storage.service.attachment.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentCreateRequest;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.entity.attachment.Attachment;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.exception.ExceptionDomainStorageError;
import com.korur.oba.shrine.storage.helper.attachment.AttachmentHashHelper;
import com.korur.oba.shrine.storage.mapper.attachment.AttachmentMapper;
import com.korur.oba.shrine.storage.repository.attachment.AttachmentRepository;
import com.korur.oba.shrine.storage.validator.attachment.AttachmentValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.InOrder;

/**
 * Unit tests for AttachmentServiceImpl.uploadAttachments with all
 * collaborators mocked.
 *
 * The load-bearing assertion across these tests is that
 * AzureBlobStorageService.uploadFile is never invoked when Pass 1
 * validation fails for any file in the batch — by either DB-hit or
 * in-batch-hash collision. Without that guarantee the two-pass design
 * gives no protection: orphaned blobs would leak into Azure for files
 * 0..N-1 whenever file N triggers a rollback. The happy-path test
 * additionally asserts that fileHash is propagated onto the persisted
 * entity.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplUnitTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private AttachmentValidator attachmentValidator;

    @Mock
    private AttachmentHashHelper attachmentHashHelper;

    @Mock
    private AzureBlobStorageService azureBlobStorageService;

    @Mock
    private LoggingService loggingService;

    @Mock
    private User user;

    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;

    @InjectMocks
    private AttachmentServiceImpl attachmentServiceImpl;

    private final UUID organizationId = UUID.randomUUID();

    /**
     * Returns a distinct 64-char hex string per multipart file. The service
     * treats this value as opaque, so any 64-char hex digest is sufficient
     * to keep the fileHash field semantics intact; deriving it from the
     * filename plus size gives different files in a batch different
     * hashes, so the in-batch dedup test only trips through the mocked
     * validator and not via accidental hash collisions in the stub.
     */
    private static String fakeHashFor(MultipartFile file) {
        String seed = (file.getOriginalFilename() == null ? "" : file.getOriginalFilename())
                + ":" + file.getSize();
        String hex = String.format("%08x", seed.hashCode());
        return (hex + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef").substring(0, 64);
    }

    @Nested
    class UploadAttachments {

        @Test
        void happyPath_uploadsAndPersistsWithFileHash() {
            // Arrange
            UUID entityId = UUID.randomUUID();
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "doc.pdf", StorageEntityType.TICKET, entityId);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "hello world".getBytes());

            when(attachmentHashHelper.computeHash(any(MultipartFile.class)))
                    .thenAnswer(invocation -> fakeHashFor(invocation.getArgument(0)));
            when(azureBlobStorageService.uploadFile(anyString(), any(byte[].class)))
                    .thenReturn("https://blob/doc.pdf");
            when(attachmentRepository.saveAll(anyList()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            attachmentServiceImpl.uploadAttachments(user, organizationId, List.of(request), List.of(file));

            // Assert
            ArgumentCaptor<List<Attachment>> captor = ArgumentCaptor.captor();
            verify(attachmentRepository).saveAll(captor.capture());
            List<Attachment> saved = captor.getValue();
            assertEquals(1, saved.size());
            assertNotNull(saved.getFirst().getFileHash());
            assertEquals(64, saved.getFirst().getFileHash().length());
            assertEquals("doc.pdf", saved.getFirst().getFileName());
            assertEquals(entityId, saved.getFirst().getEntityId());
            assertEquals(organizationId, saved.getFirst().getOrganizationId());
        }

        @Test
        void duplicateFromDb_throwsBeforeAzureUpload() {
            // Arrange
            UUID entityId = UUID.randomUUID();
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "doc.pdf", StorageEntityType.TICKET, entityId);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "hello world".getBytes());

            when(attachmentHashHelper.computeHash(any(MultipartFile.class)))
                    .thenAnswer(invocation -> fakeHashFor(invocation.getArgument(0)));
            doThrow(new ValidationException(ExceptionDomainStorageError.VAL_FILE_ALREADY_EXISTS))
                    .when(attachmentValidator)
                    .ensureFileNotDuplicate(eq(organizationId), any(MultipartFile.class), any(AttachmentCreateRequest.class),
                            anyString(), any());

            // Act & Assert
            assertThrows(ValidationException.class,
                    () -> attachmentServiceImpl.uploadAttachments(user, organizationId, List.of(request), List.of(file)));

            // Critical: no Azure round-trip when a duplicate is detected.
            verify(azureBlobStorageService, never()).uploadFile(anyString(), any(byte[].class));
            verify(attachmentRepository, never()).saveAll(anyList());
        }

        @Test
        void inBatchDuplicate_throwsBeforeAnyAzureUpload() {
            // Arrange
            UUID entityId = UUID.randomUUID();
            AttachmentCreateRequest first = new AttachmentCreateRequest(
                    "doc.pdf", StorageEntityType.TICKET, entityId);
            AttachmentCreateRequest second = new AttachmentCreateRequest(
                    "doc.pdf", StorageEntityType.TICKET, entityId);
            MultipartFile firstFile = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "same content".getBytes());
            MultipartFile secondFile = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "same content".getBytes());

            when(attachmentHashHelper.computeHash(any(MultipartFile.class)))
                    .thenAnswer(invocation -> fakeHashFor(invocation.getArgument(0)));
            // The validator passes the first file, then rejects the second one as an in-batch duplicate.
            doNothing()
                    .doThrow(new ValidationException(ExceptionDomainStorageError.VAL_FILE_ALREADY_EXISTS))
                    .when(attachmentValidator)
                    .ensureFileNotDuplicate(eq(organizationId), any(MultipartFile.class), any(AttachmentCreateRequest.class),
                            anyString(), any());

            // Act & Assert
            assertThrows(ValidationException.class,
                    () -> attachmentServiceImpl.uploadAttachments(user, organizationId, List.of(first, second), List.of(firstFile, secondFile)));

            // Pass 1 fully completes validation before Pass 2 touches Azure; if any file fails, no upload happens.
            verify(azureBlobStorageService, never()).uploadFile(anyString(), any(byte[].class));
            verify(attachmentRepository, never()).saveAll(anyList());
        }

        @Test
        void multipleFiles_uploadsAllAndPersistsBatchOnce() {
            // Arrange
            UUID entityId = UUID.randomUUID();
            AttachmentCreateRequest first = new AttachmentCreateRequest(
                    "first.pdf", StorageEntityType.TICKET, entityId);
            AttachmentCreateRequest second = new AttachmentCreateRequest(
                    "second.pdf", StorageEntityType.TICKET, entityId);
            MultipartFile firstFile = new MockMultipartFile(
                    "file", "first.pdf", "application/pdf", "first-content".getBytes());
            MultipartFile secondFile = new MockMultipartFile(
                    "file", "second.pdf", "application/pdf", "second-content".getBytes());

            when(attachmentHashHelper.computeHash(any(MultipartFile.class)))
                    .thenAnswer(invocation -> fakeHashFor(invocation.getArgument(0)));
            when(azureBlobStorageService.uploadFile(anyString(), any(byte[].class)))
                    .thenReturn("https://blob/file");
            when(attachmentRepository.saveAll(anyList()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            attachmentServiceImpl.uploadAttachments(user, organizationId, List.of(first, second), List.of(firstFile, secondFile));

            // Assert
            verify(azureBlobStorageService, times(2)).uploadFile(anyString(), any(byte[].class));
            ArgumentCaptor<List<Attachment>> captor = ArgumentCaptor.captor();
            verify(attachmentRepository, times(1)).saveAll(captor.capture());
            assertTrue(captor.getValue().stream()
                    .allMatch(attachment -> organizationId.equals(attachment.getOrganizationId())));
        }
    }

    @Nested
    class DeleteAttachmentsByEntity {

        /**
         * Returns a minimally-populated Attachment with an arbitrary id and url.
         * Only the fields the cascade reads (id, url) are set; the rest of the
         * column surface is irrelevant to this method's behaviour.
         */
        private static Attachment stubAttachment(String url) {
            Attachment attachment = Attachment.builder().url(url).build();
            attachment.setId(UUID.randomUUID());
            return attachment;
        }

        @Test
        void noAttachments_isNoOp() {
            // Arrange — empty result from the repository means the parent has no attachments.
            UUID entityId = UUID.randomUUID();
            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.TICKET, entityId))
                    .thenReturn(List.of());

            // Act
            attachmentServiceImpl.deleteAttachmentsByEntity(StorageEntityType.TICKET, entityId);

            // Assert — no DB write, no Azure round-trip.
            verify(attachmentRepository, never()).deleteAllInBatch(anyList());
            verify(azureBlobStorageService, never()).deleteFile(anyString());
        }

        @Test
        void multipleAttachments_deletesAllRowsAndBlobs() {
            // Arrange
            UUID entityId = UUID.randomUUID();
            Attachment first = stubAttachment("https://blob/first.pdf");
            Attachment second = stubAttachment("https://blob/second.pdf");
            List<Attachment> attachments = List.of(first, second);

            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.TICKET, entityId))
                    .thenReturn(attachments);

            // Act
            attachmentServiceImpl.deleteAttachmentsByEntity(StorageEntityType.TICKET, entityId);

            // Assert — exactly one batch delete for the rows, plus one blob delete per row.
            verify(attachmentRepository, times(1)).deleteAllInBatch(attachments);
            verify(azureBlobStorageService).deleteFile("https://blob/first.pdf");
            verify(azureBlobStorageService).deleteFile("https://blob/second.pdf");
        }

        @Test
        void deletesDbRowsBeforeBlobs() {
            // The DB row must be deleted before the blob so the /exists predicate
            // (existsByEntityTypeAndFileHash) reports false as soon as the
            // surrounding transaction commits. A stuck blob after a successful
            // row delete is acceptable; a successful blob delete with a stuck
            // row is not -- it would leave the exists-check lying about the
            // duplicate.
            UUID entityId = UUID.randomUUID();
            Attachment attachment = stubAttachment("https://blob/doc.pdf");

            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.TICKET, entityId))
                    .thenReturn(List.of(attachment));

            // Act
            attachmentServiceImpl.deleteAttachmentsByEntity(StorageEntityType.TICKET, entityId);

            // Assert
            InOrder inOrder = inOrder(attachmentRepository, azureBlobStorageService);
            inOrder.verify(attachmentRepository).deleteAllInBatch(anyList());
            inOrder.verify(azureBlobStorageService).deleteFile("https://blob/doc.pdf");
        }

        @Test
        void blobHeldByARowOfAnotherParent_keepsThatBlobAndDeletesTheOther() {
            UUID relationId = UUID.randomUUID();
            Attachment shared = stubAttachment("https://blob/shared.pdf");
            Attachment own = stubAttachment("https://blob/own.pdf");
            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.RELATION, relationId))
                    .thenReturn(List.of(shared, own));
            when(attachmentRepository.existsByUrl("https://blob/shared.pdf")).thenReturn(true);

            attachmentServiceImpl.deleteAttachmentsByEntity(StorageEntityType.RELATION, relationId);

            verify(attachmentRepository).deleteAllInBatch(List.of(shared, own));
            verify(azureBlobStorageService, never()).deleteFile("https://blob/shared.pdf");
            verify(azureBlobStorageService).deleteFile("https://blob/own.pdf");
        }

        @Test
        void twoRowsOfOneParentShareABlobNobodyElseHolds_deletesTheBlobOnce() {
            UUID mailId = UUID.randomUUID();
            Attachment first = stubAttachment("https://blob/shared.pdf");
            Attachment second = stubAttachment("https://blob/shared.pdf");
            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.MAIL, mailId))
                    .thenReturn(List.of(first, second));

            attachmentServiceImpl.deleteAttachmentsByEntity(StorageEntityType.MAIL, mailId);

            verify(azureBlobStorageService, times(1)).deleteFile("https://blob/shared.pdf");
        }

        @Test
        void blobDeleteFails_swallowsAndContinues() {
            // The cascade must tolerate a transient Azure failure on any single
            // blob: the row is already gone from the DB so the user-facing fix
            // (/exists returning false) is already in place; surfacing a 5xx
            // would leave the parent-domain delete stuck. The remaining blobs
            // must still be attempted so a single bad blob does not poison the
            // rest of the batch.
            UUID entityId = UUID.randomUUID();
            Attachment first = stubAttachment("https://blob/first.pdf");
            Attachment second = stubAttachment("https://blob/second.pdf");

            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.TICKET, entityId))
                    .thenReturn(List.of(first, second));
            doThrow(new RuntimeException("simulated Azure transient failure"))
                    .when(azureBlobStorageService).deleteFile("https://blob/first.pdf");

            // Act — must not throw despite the blob failure.
            attachmentServiceImpl.deleteAttachmentsByEntity(StorageEntityType.TICKET, entityId);

            // Assert — DB rows were deleted in one batch; the second blob delete still ran.
            verify(attachmentRepository, times(1)).deleteAllInBatch(anyList());
            verify(azureBlobStorageService).deleteFile("https://blob/first.pdf");
            verify(azureBlobStorageService).deleteFile("https://blob/second.pdf");
        }
    }

    @Nested
    class UploadAttachmentsAccess {

        private void ticketAccessible() {
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.TICKET))
                    .thenReturn(ResourceAccessType.TICKET);
        }

        @Test
        void ticketWriteRefused_throwsBeforeHashingDuplicateCheckAndAzure() {
            // Arrange
            UUID ticketId = UUID.randomUUID();
            ticketAccessible();
            doThrow(new ForbiddenException(ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS))
                    .when(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                            ResourceAccessType.TICKET, ticketId, ResourceAccessAction.WRITE);
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "doc.pdf", StorageEntityType.TICKET, ticketId);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "hello".getBytes());

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    attachmentServiceImpl.uploadAttachments(user, organizationId, List.of(request), List.of(file)));
            verifyNoInteractions(attachmentHashHelper, azureBlobStorageService, attachmentRepository);
            verify(attachmentValidator, never()).ensureFileNotDuplicate(any(), any(), any(), anyString(), any());
        }

        @Test
        void ticketWriteAllowed_checksAccessAndUploads() {
            // Arrange
            UUID ticketId = UUID.randomUUID();
            ticketAccessible();
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "doc.pdf", StorageEntityType.TICKET, ticketId);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "hello".getBytes());
            when(attachmentHashHelper.computeHash(any(MultipartFile.class)))
                    .thenAnswer(invocation -> fakeHashFor(invocation.getArgument(0)));
            when(azureBlobStorageService.uploadFile(anyString(), any(byte[].class)))
                    .thenReturn("https://blob/doc.pdf");
            when(attachmentRepository.saveAll(anyList()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            attachmentServiceImpl.uploadAttachments(user, organizationId, List.of(request), List.of(file));

            // Assert
            verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, ticketId, ResourceAccessAction.WRITE);
            verify(attachmentRepository).saveAll(anyList());
        }

        @Test
        void batchNamesTwoTickets_checksEachDistinctTicketOnce() {
            // Arrange
            UUID firstTicket = UUID.randomUUID();
            UUID secondTicket = UUID.randomUUID();
            ticketAccessible();
            doNothing().when(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, firstTicket, ResourceAccessAction.WRITE);
            doThrow(new ForbiddenException(ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS))
                    .when(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                            ResourceAccessType.TICKET, secondTicket, ResourceAccessAction.WRITE);
            List<AttachmentCreateRequest> requests = List.of(
                    new AttachmentCreateRequest("a.pdf", StorageEntityType.TICKET, firstTicket),
                    new AttachmentCreateRequest("b.pdf", StorageEntityType.TICKET, firstTicket),
                    new AttachmentCreateRequest("c.pdf", StorageEntityType.TICKET, secondTicket));
            List<MultipartFile> files = List.of(
                    new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes()),
                    new MockMultipartFile("file", "b.pdf", "application/pdf", "b".getBytes()),
                    new MockMultipartFile("file", "c.pdf", "application/pdf", "c".getBytes()));

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    attachmentServiceImpl.uploadAttachments(user, organizationId, requests, files));
            verify(resourceAccessQueryService, times(1)).verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, firstTicket, ResourceAccessAction.WRITE);
            verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, secondTicket, ResourceAccessAction.WRITE);
            verifyNoInteractions(azureBlobStorageService, attachmentRepository);
        }

        @Test
        void entityTypeRefusedByTheValidator_throwsForbiddenBeforeHashingAndAzure() {
            // Arrange: every current type has access rules, so the refusal is the validator's answer
            UUID ticketId = UUID.randomUUID();
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.TICKET))
                    .thenThrow(new ForbiddenException(ExceptionDomainStorageError.SEC_ENTITY_TYPE_NOT_ATTACHABLE));
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "doc.pdf", StorageEntityType.TICKET, ticketId);
            MultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "hello".getBytes());

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    attachmentServiceImpl.uploadAttachments(user, organizationId, List.of(request), List.of(file)));
            verifyNoInteractions(attachmentHashHelper, azureBlobStorageService, attachmentRepository);
        }

        @Test
        void batchMixesTicketAndARefusedType_throwsForbiddenBeforeAnyUpload() {
            // Arrange
            UUID ticketId = UUID.randomUUID();
            ticketAccessible();
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.CHAT_MESSAGE))
                    .thenThrow(new ForbiddenException(ExceptionDomainStorageError.SEC_ENTITY_TYPE_NOT_ATTACHABLE));
            List<AttachmentCreateRequest> requests = List.of(
                    new AttachmentCreateRequest("a.pdf", StorageEntityType.TICKET, ticketId),
                    new AttachmentCreateRequest("b.pdf", StorageEntityType.CHAT_MESSAGE, UUID.randomUUID()));
            List<MultipartFile> files = List.of(
                    new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes()),
                    new MockMultipartFile("file", "b.pdf", "application/pdf", "b".getBytes()));

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    attachmentServiceImpl.uploadAttachments(user, organizationId, requests, files));
            verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, ticketId, ResourceAccessAction.WRITE);
            verifyNoInteractions(attachmentHashHelper, azureBlobStorageService, attachmentRepository);
        }
    }

    @Nested
    class DeleteAttachment {

        private Attachment storedAttachment(StorageEntityType type, UUID entityId) {
            Attachment attachment = Attachment.builder()
                    .url("https://blob/doc.pdf")
                    .entityType(type)
                    .entityId(entityId)
                    .build();
            attachment.setId(UUID.randomUUID());
            return attachment;
        }

        @Test
        void ticketAttachment_checksDeleteOnStoredTicketThenDeletes() {
            // Arrange
            UUID ticketId = UUID.randomUUID();
            Attachment attachment = storedAttachment(StorageEntityType.TICKET, ticketId);
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.TICKET))
                    .thenReturn(ResourceAccessType.TICKET);
            when(attachmentRepository.findByIdAndOrganizationId(attachment.getId(), organizationId))
                    .thenReturn(Optional.of(attachment));

            // Act
            attachmentServiceImpl.deleteAttachment(user, organizationId, attachment.getId());

            // Assert
            InOrder order = inOrder(resourceAccessQueryService, azureBlobStorageService, attachmentRepository);
            order.verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                    ResourceAccessType.TICKET, ticketId, ResourceAccessAction.DELETE);
            order.verify(attachmentRepository).delete(attachment);
            order.verify(attachmentRepository).flush();
            order.verify(attachmentRepository).existsByUrl("https://blob/doc.pdf");
            order.verify(azureBlobStorageService).deleteFile("https://blob/doc.pdf");
        }

        @Test
        void blobHeldByAnotherRow_deletesTheRowAndKeepsTheBlob() {
            // Arrange: a RELATION row and a MAIL row can hold the same blob after a template copy or a migration
            UUID mailId = UUID.randomUUID();
            Attachment attachment = storedAttachment(StorageEntityType.MAIL, mailId);
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.MAIL))
                    .thenReturn(ResourceAccessType.MAIL);
            when(attachmentRepository.findByIdAndOrganizationId(attachment.getId(), organizationId))
                    .thenReturn(Optional.of(attachment));
            when(attachmentRepository.existsByUrl("https://blob/doc.pdf")).thenReturn(true);

            // Act
            attachmentServiceImpl.deleteAttachment(user, organizationId, attachment.getId());

            // Assert
            verify(attachmentRepository).delete(attachment);
            verify(azureBlobStorageService, never()).deleteFile(anyString());
        }

        @Test
        void ticketWriteRefused_throwsAndKeepsBlobAndRow() {
            // Arrange
            UUID ticketId = UUID.randomUUID();
            Attachment attachment = storedAttachment(StorageEntityType.TICKET, ticketId);
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.TICKET))
                    .thenReturn(ResourceAccessType.TICKET);
            doThrow(new ForbiddenException(ExceptionDomainKazanError.SEC_INSUFFICIENT_BOARD_PERMISSIONS))
                    .when(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                            ResourceAccessType.TICKET, ticketId, ResourceAccessAction.DELETE);
            when(attachmentRepository.findByIdAndOrganizationId(attachment.getId(), organizationId))
                    .thenReturn(Optional.of(attachment));

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    attachmentServiceImpl.deleteAttachment(user, organizationId, attachment.getId()));
            verifyNoInteractions(azureBlobStorageService);
            verify(attachmentRepository, never()).delete(any(Attachment.class));
        }

        @Test
        void attachmentMissing_throwsNotFoundBeforeAnyAccessCheck() {
            // Arrange
            UUID attachmentId = UUID.randomUUID();
            when(attachmentRepository.findByIdAndOrganizationId(attachmentId, organizationId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    attachmentServiceImpl.deleteAttachment(user, organizationId, attachmentId));
            verifyNoInteractions(resourceAccessQueryService, azureBlobStorageService);
            verify(attachmentValidator, never()).ensureEntityTypeAccessible(any());
        }

        @Test
        void storedTypeRefusedByTheValidator_throwsForbiddenAndKeepsBlobAndRow() {
            // Arrange
            Attachment attachment = storedAttachment(StorageEntityType.KALKAN_PAGE, UUID.randomUUID());
            when(attachmentRepository.findByIdAndOrganizationId(attachment.getId(), organizationId))
                    .thenReturn(Optional.of(attachment));
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.KALKAN_PAGE))
                    .thenThrow(new ForbiddenException(ExceptionDomainStorageError.SEC_ENTITY_TYPE_NOT_ATTACHABLE));

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    attachmentServiceImpl.deleteAttachment(user, organizationId, attachment.getId()));
            verifyNoInteractions(azureBlobStorageService);
            verify(attachmentRepository, never()).delete(any(Attachment.class));
        }

        @Test
        void attachmentOfAnotherOrganization_isNotFoundAndKeepsBlobAndRow() {
            // Arrange: the row exists, but only under another organization
            UUID attachmentId = UUID.randomUUID();
            when(attachmentRepository.findByIdAndOrganizationId(attachmentId, organizationId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException thrown = assertThrows(NotFoundException.class, () ->
                    attachmentServiceImpl.deleteAttachment(user, organizationId, attachmentId));
            assertEquals("STG_NOT_001", thrown.getErrorCode());
            verify(attachmentRepository, never()).findById(any());
            verify(attachmentRepository, never()).delete(any(Attachment.class));
            verifyNoInteractions(resourceAccessQueryService, azureBlobStorageService);
        }
    }

    @Nested
    class StoreAttachmentContent {

        private final UUID mailId = UUID.randomUUID();
        private final AttachmentContent content =
                new AttachmentContent("offer.pdf", "application/pdf", "pdf-bytes".getBytes());

        @Test
        void writeRefused_throwsBeforeHashingAndUpload() {
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.MAIL))
                    .thenReturn(ResourceAccessType.MAIL);
            doThrow(new ForbiddenException(ExceptionDomainStorageError.SEC_ENTITY_TYPE_NOT_ATTACHABLE))
                    .when(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                            ResourceAccessType.MAIL, mailId, ResourceAccessAction.WRITE);

            assertThrows(ForbiddenException.class, () -> attachmentServiceImpl.storeAttachmentContent(user,
                    organizationId, StorageEntityType.MAIL, mailId, content));

            verifyNoInteractions(attachmentHashHelper, azureBlobStorageService, attachmentRepository);
            verify(attachmentValidator, never()).ensureContentNotEmpty(any());
        }

        @Test
        void allowed_checksWriteFirstThenStoresFieldsHashAndBlob() {
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.MAIL))
                    .thenReturn(ResourceAccessType.MAIL);
            String hash = "a".repeat(64);
            when(attachmentHashHelper.computeHash(content.content())).thenReturn(hash);
            when(azureBlobStorageService.uploadFile(anyString(), eq(content.content())))
                    .thenReturn("https://blob/new.pdf");
            when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
            AttachmentResponse response = mock(AttachmentResponse.class);
            when(attachmentMapper.toResponse(any(Attachment.class))).thenReturn(response);

            AttachmentResponse result = attachmentServiceImpl.storeAttachmentContent(user, organizationId,
                    StorageEntityType.MAIL, mailId, content);

            assertSame(response, result);
            InOrder order = inOrder(resourceAccessQueryService, attachmentValidator, attachmentRepository);
            order.verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                    ResourceAccessType.MAIL, mailId, ResourceAccessAction.WRITE);
            order.verify(attachmentValidator).ensureContentNotEmpty(content);
            order.verify(attachmentValidator).ensureStoredContentNotDuplicate(organizationId,
                    new AttachmentCreateRequest("offer.pdf", StorageEntityType.MAIL, mailId), hash);
            ArgumentCaptor<Attachment> saved = ArgumentCaptor.forClass(Attachment.class);
            order.verify(attachmentRepository).save(saved.capture());
            Attachment attachment = saved.getValue();
            assertEquals("https://blob/new.pdf", attachment.getUrl());
            assertEquals("offer.pdf", attachment.getFileName());
            assertEquals("application/pdf", attachment.getContentType());
            assertEquals((long) content.content().length, attachment.getFileSize());
            assertEquals(StorageEntityType.MAIL, attachment.getEntityType());
            assertEquals(mailId, attachment.getEntityId());
            assertEquals(organizationId, attachment.getOrganizationId());
            assertEquals(hash, attachment.getFileHash());
            ArgumentCaptor<String> blobName = ArgumentCaptor.forClass(String.class);
            verify(azureBlobStorageService).uploadFile(blobName.capture(), eq(content.content()));
            org.junit.jupiter.api.Assertions.assertTrue(blobName.getValue().endsWith(".pdf"));
        }

        @Test
        void duplicateContent_throwsAndUploadsNothing() {
            when(attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.MAIL))
                    .thenReturn(ResourceAccessType.MAIL);
            when(attachmentHashHelper.computeHash(content.content())).thenReturn("b".repeat(64));
            doThrow(new ValidationException(ExceptionDomainStorageError.VAL_FILE_ALREADY_EXISTS))
                    .when(attachmentValidator).ensureStoredContentNotDuplicate(any(), any(), anyString());

            assertThrows(ValidationException.class, () -> attachmentServiceImpl.storeAttachmentContent(user,
                    organizationId, StorageEntityType.MAIL, mailId, content));

            verifyNoInteractions(azureBlobStorageService);
            verify(attachmentRepository, never()).save(any(Attachment.class));
        }
    }
}
