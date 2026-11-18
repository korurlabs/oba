package com.korur.oba.shrine.storage.validator.attachment;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

// 3. Java SDK
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentCreateRequest;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.entity.attachment.Attachment;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.repository.attachment.AttachmentRepository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AttachmentValidator with all collaborators mocked.
 *
 * Centres on ensureFileNotDuplicate, which has the most subtle contract:
 * the in-batch HashSet must catch duplicates that the database query
 * cannot see (because neither row is persisted yet at validation time),
 * the DB query must catch duplicates against committed rows, and a
 * passing call must extend the in-batch set so the next file in the same
 * batch sees the new hash. The other validator methods (empty file,
 * filename mismatch) are covered for completeness.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentValidatorUnitTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private AttachmentValidator attachmentValidator;

    @Nested
    class EnsureFileNotDuplicate {

        @Test
        void inBatchDuplicate_throwsValidationException() {
            // Arrange
            UUID entityId = UUID.randomUUID();
            String hash = "abc123";
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "file.pdf", StorageEntityType.TICKET, entityId);
            MultipartFile file = new MockMultipartFile(
                    "file", "file.pdf", "application/pdf", new byte[]{1, 2, 3});
            Set<String> seen = new HashSet<>();
            seen.add(hash);

            // Act & Assert
            assertThrows(ValidationException.class,
                    () -> attachmentValidator.ensureFileNotDuplicate(file, request, hash, seen));
        }

        @Test
        void duplicateFromDb_throwsValidationException() {
            // Arrange
            UUID entityId = UUID.randomUUID();
            String hash = "def456";
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "file.pdf", StorageEntityType.TICKET, entityId);
            MultipartFile file = new MockMultipartFile(
                    "file", "file.pdf", "application/pdf", new byte[]{1, 2, 3});
            Set<String> seen = new HashSet<>();
            when(attachmentRepository.existsByEntityTypeAndFileHash(
                    StorageEntityType.TICKET, hash))
                    .thenReturn(true);

            // Act & Assert
            assertThrows(ValidationException.class,
                    () -> attachmentValidator.ensureFileNotDuplicate(file, request, hash, seen));
        }

        @Test
        void noDuplicate_addsHashToBatchSet() {
            // Arrange
            UUID entityId = UUID.randomUUID();
            String hash = "novel-hash";
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "file.pdf", StorageEntityType.TICKET, entityId);
            MultipartFile file = new MockMultipartFile(
                    "file", "file.pdf", "application/pdf", new byte[]{1, 2, 3});
            Set<String> seen = new HashSet<>();
            when(attachmentRepository.existsByEntityTypeAndFileHash(
                    StorageEntityType.TICKET, hash))
                    .thenReturn(false);

            // Act
            assertDoesNotThrow(() -> attachmentValidator.ensureFileNotDuplicate(file, request, hash, seen));

            // Assert
            assertTrue(seen.contains(hash));
        }

        @Test
        void chatMessage_fileSentBefore_isAccepted() {
            // Arrange
            String hash = "sent-before";
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "image.png", StorageEntityType.CHAT_MESSAGE, UUID.randomUUID());
            MultipartFile file = new MockMultipartFile(
                    "file", "image.png", "image/png", new byte[]{1, 2, 3});
            Set<String> seen = new HashSet<>();

            // Act
            assertDoesNotThrow(() -> attachmentValidator.ensureFileNotDuplicate(file, request, hash, seen));

            // Assert
            verify(attachmentRepository, never()).existsByEntityTypeAndFileHash(any(), anyString());
            assertTrue(seen.contains(hash));
        }

        @Test
        void kalkanPage_contentAttachedToAnotherPage_isAccepted() {
            // Arrange
            String hash = "attached-elsewhere";
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "diagram.png", StorageEntityType.KALKAN_PAGE, UUID.randomUUID());
            MultipartFile file = new MockMultipartFile(
                    "file", "diagram.png", "image/png", new byte[]{1, 2, 3});
            Set<String> seen = new HashSet<>();

            // Act
            assertDoesNotThrow(() -> attachmentValidator.ensureFileNotDuplicate(file, request, hash, seen));

            // Assert
            verify(attachmentRepository, never()).existsByEntityTypeAndFileHash(any(), anyString());
            assertTrue(seen.contains(hash));
        }

        @Test
        void kalkanPage_sameFileTwiceInOneUpload_throwsValidationException() {
            // Arrange
            String hash = "same-file";
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "diagram.png", StorageEntityType.KALKAN_PAGE, UUID.randomUUID());
            MultipartFile file = new MockMultipartFile(
                    "file", "diagram.png", "image/png", new byte[]{1, 2, 3});
            Set<String> seen = new HashSet<>();
            seen.add(hash);

            // Act & Assert
            assertThrows(ValidationException.class,
                    () -> attachmentValidator.ensureFileNotDuplicate(file, request, hash, seen));
        }

        @Test
        void kalkanPage_fileAlreadyOnThisPage_throwsFileAlreadyExists() {
            // Arrange
            UUID pageId = UUID.randomUUID();
            String hash = "already-here";
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "diagram.png", StorageEntityType.KALKAN_PAGE, pageId);
            MultipartFile file = new MockMultipartFile(
                    "file", "diagram.png", "image/png", new byte[]{1, 2, 3});
            Set<String> seen = new HashSet<>();
            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.KALKAN_PAGE, pageId))
                    .thenReturn(List.of(Attachment.builder().fileHash("other").build(),
                            Attachment.builder().fileHash(hash).build()));

            // Act
            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> attachmentValidator.ensureFileNotDuplicate(file, request, hash, seen));

            // Assert
            assertEquals("STG_VAL_005", thrown.getErrorCode());
            assertFalse(seen.contains(hash));
            verify(attachmentRepository, never()).existsByEntityTypeAndFileHash(any(), anyString());
        }

        @Test
        void chatMessage_sameFileTwiceInOneMessage_throwsValidationException() {
            // Arrange
            String hash = "same-file";
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "image.png", StorageEntityType.CHAT_MESSAGE, UUID.randomUUID());
            MultipartFile file = new MockMultipartFile(
                    "file", "image.png", "image/png", new byte[]{1, 2, 3});
            Set<String> seen = new HashSet<>();
            seen.add(hash);

            // Act & Assert
            assertThrows(ValidationException.class,
                    () -> attachmentValidator.ensureFileNotDuplicate(file, request, hash, seen));
        }
    }

    @Nested
    class EnsureFileNotEmpty {

        @Test
        void emptyFile_throwsValidationException() {
            // Arrange
            MultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

            // Act & Assert
            assertThrows(ValidationException.class, () -> attachmentValidator.ensureFileNotEmpty(file));
        }

        @Test
        void nonEmptyFile_doesNotThrow() {
            // Arrange
            MultipartFile file = new MockMultipartFile("file", "file.pdf", "application/pdf", new byte[]{1});

            // Act & Assert
            assertDoesNotThrow(() -> attachmentValidator.ensureFileNotEmpty(file));
        }
    }

    @Nested
    class EnsureFileNameMatches {

        @Test
        void mismatchedName_throwsValidationException() {
            // Arrange
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "expected.pdf", StorageEntityType.TICKET, UUID.randomUUID());
            MultipartFile file = new MockMultipartFile(
                    "file", "different.pdf", "application/pdf", new byte[]{1});

            // Act & Assert
            assertThrows(ValidationException.class,
                    () -> attachmentValidator.ensureFileNameMatches(file, request));
        }

        @Test
        void matchingName_doesNotThrow() {
            // Arrange
            AttachmentCreateRequest request = new AttachmentCreateRequest(
                    "file.pdf", StorageEntityType.TICKET, UUID.randomUUID());
            MultipartFile file = new MockMultipartFile(
                    "file", "file.pdf", "application/pdf", new byte[]{1});

            // Act & Assert
            assertDoesNotThrow(() -> attachmentValidator.ensureFileNameMatches(file, request));
        }
    }

    @Nested
    class EnsureEntityTypeAccessible {

        @Test
        void attachableType_returnsItsResourceAccessType() {
            assertEquals(ResourceAccessType.TICKET, attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.TICKET));
            assertEquals(ResourceAccessType.CHAT_MESSAGE,
                    attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.CHAT_MESSAGE));
            assertEquals(ResourceAccessType.KALKAN_PAGE,
                    attachmentValidator.ensureEntityTypeAccessible(StorageEntityType.KALKAN_PAGE));
        }

        @Test
        void typeWithoutAccessType_throwsForbiddenStgSec001() {
            for (StorageEntityType type : List.of(StorageEntityType.ORGANIZATION, StorageEntityType.USER,
                    StorageEntityType.INVOICE)) {
                ForbiddenException thrown = assertThrows(ForbiddenException.class,
                        () -> attachmentValidator.ensureEntityTypeAccessible(type), type.name());

                assertEquals("STG_SEC_001", thrown.getErrorCode());
            }
        }
    }

    @Nested
    class EnsureStoredContentNotDuplicate {

        private final String hash = "c".repeat(64);

        private Attachment stored(StorageEntityType type, UUID entityId, String fileHash) {
            return Attachment.builder().entityType(type).entityId(entityId).fileHash(fileHash).build();
        }

        @Test
        void sameContentTwiceOnOneMail_throwsDuplicate() {
            UUID mailId = UUID.randomUUID();
            AttachmentCreateRequest request = new AttachmentCreateRequest("a.pdf", StorageEntityType.MAIL, mailId);
            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.MAIL, mailId))
                    .thenReturn(List.of(stored(StorageEntityType.MAIL, mailId, hash)));

            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> attachmentValidator.ensureStoredContentNotDuplicate(request, hash));

            assertEquals("STG_VAL_005", thrown.getErrorCode());
        }

        @Test
        void sameContentOnTwoMails_isAllowed() {
            UUID mailId = UUID.randomUUID();
            AttachmentCreateRequest request = new AttachmentCreateRequest("a.pdf", StorageEntityType.MAIL, mailId);
            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.MAIL, mailId))
                    .thenReturn(List.of());

            assertDoesNotThrow(() -> attachmentValidator.ensureStoredContentNotDuplicate(request, hash));
            verify(attachmentRepository, never()).existsByEntityTypeAndFileHash(any(), anyString());
        }

        @Test
        void sameContentTwiceOnOneRelation_throwsDuplicate() {
            UUID relationId = UUID.randomUUID();
            AttachmentCreateRequest request =
                    new AttachmentCreateRequest("a.pdf", StorageEntityType.RELATION, relationId);
            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.RELATION, relationId))
                    .thenReturn(List.of(stored(StorageEntityType.RELATION, relationId, hash)));

            assertThrows(ValidationException.class,
                    () -> attachmentValidator.ensureStoredContentNotDuplicate(request, hash));
        }

        @Test
        void otherContentOnTheSameMail_isAllowed() {
            UUID mailId = UUID.randomUUID();
            AttachmentCreateRequest request = new AttachmentCreateRequest("a.pdf", StorageEntityType.MAIL, mailId);
            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.MAIL, mailId))
                    .thenReturn(List.of(stored(StorageEntityType.MAIL, mailId, "d".repeat(64))));

            assertDoesNotThrow(() -> attachmentValidator.ensureStoredContentNotDuplicate(request, hash));
        }

        @Test
        void migratedRowWithoutHash_neverMatches() {
            UUID mailId = UUID.randomUUID();
            AttachmentCreateRequest request = new AttachmentCreateRequest("a.pdf", StorageEntityType.MAIL, mailId);
            when(attachmentRepository.findByEntityTypeAndEntityId(StorageEntityType.MAIL, mailId))
                    .thenReturn(List.of(stored(StorageEntityType.MAIL, mailId, null)));

            assertDoesNotThrow(() -> attachmentValidator.ensureStoredContentNotDuplicate(request, hash));
        }

        @Test
        void typeUniqueAcrossType_checksTheTypeWideHash() {
            AttachmentCreateRequest request =
                    new AttachmentCreateRequest("a.pdf", StorageEntityType.TICKET, UUID.randomUUID());
            when(attachmentRepository.existsByEntityTypeAndFileHash(StorageEntityType.TICKET, hash)).thenReturn(true);

            assertThrows(ValidationException.class,
                    () -> attachmentValidator.ensureStoredContentNotDuplicate(request, hash));
        }
    }

    @Nested
    class EnsureContentNotEmpty {

        @Test
        void noBytes_throwsValidation() {
            assertThrows(ValidationException.class, () -> attachmentValidator.ensureContentNotEmpty(
                    new AttachmentContent("a.pdf", "application/pdf", new byte[0])));
            assertThrows(ValidationException.class, () -> attachmentValidator.ensureContentNotEmpty(
                    new AttachmentContent("a.pdf", "application/pdf", null)));
        }

        @Test
        void withBytes_passes() {
            assertDoesNotThrow(() -> attachmentValidator.ensureContentNotEmpty(
                    new AttachmentContent("a.pdf", "application/pdf", new byte[]{1})));
        }
    }
}
