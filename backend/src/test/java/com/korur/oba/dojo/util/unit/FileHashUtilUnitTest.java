package com.korur.oba.dojo.util.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

// 3. Java SDK
import java.nio.charset.StandardCharsets;

// 4. Internal
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.util.FileHashUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for FileHashUtil.
 *
 * Pins the digest output against published SHA-256 vectors (empty string,
 * "hello") so a future swap of the underlying DigestUtils dependency
 * cannot silently change the hash that callers persist. Also asserts the
 * null/empty input contract: an empty payload must throw rather than
 * return the empty-string digest, since the storage domain depends on a
 * non-empty hash never colliding with a real file's hash.
 */
class FileHashUtilUnitTest {

    private static final String EMPTY_STRING_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String HELLO_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Nested
    class Sha256HexBytes {

        @Test
        void knownVector_hello_returnsExpectedDigest() {
            // Arrange
            byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

            // Act
            String result = FileHashUtil.sha256Hex(content);

            // Assert
            assertEquals(HELLO_SHA256, result);
        }

        @Test
        void deterministic_sameInputProducesSameOutput() {
            // Arrange
            byte[] content = "deterministic".getBytes(StandardCharsets.UTF_8);

            // Act
            String first = FileHashUtil.sha256Hex(content);
            String second = FileHashUtil.sha256Hex(content);

            // Assert
            assertEquals(first, second);
        }

        @Test
        void output_is64HexCharacters() {
            // Arrange
            byte[] content = "any-content".getBytes(StandardCharsets.UTF_8);

            // Act
            String result = FileHashUtil.sha256Hex(content);

            // Assert
            assertEquals(64, result.length());
            assertTrue(result.matches("[0-9a-f]{64}"));
        }

        @Test
        void nullBytes_throwsInternalServerErrorException() {
            // Act & Assert
            assertThrows(InternalServerErrorException.class, () -> FileHashUtil.sha256Hex((byte[]) null));
        }

        @Test
        void emptyBytes_throwsInternalServerErrorException() {
            // Arrange
            byte[] empty = new byte[0];

            // Act & Assert
            assertThrows(InternalServerErrorException.class, () -> FileHashUtil.sha256Hex(empty));
        }

        @Test
        void neverProducesEmptyStringDigest() {
            // Arrange — the digest of an empty input is well-known; we explicitly reject empty input.
            byte[] empty = new byte[0];

            // Act & Assert
            InternalServerErrorException thrown = assertThrows(
                    InternalServerErrorException.class, () -> FileHashUtil.sha256Hex(empty));
            // Sanity: confirm we did not silently return the empty-string digest.
            assertTrue(thrown.getMessage() == null || !thrown.getMessage().equals(EMPTY_STRING_SHA256));
        }
    }

    @Nested
    class Sha256HexMultipartFile {

        @Test
        void multipart_returnsSameDigestAsByteOverload() throws Exception {
            // Arrange
            byte[] content = "multipart-content".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile file = new MockMultipartFile("file", "test.bin", "application/octet-stream", content);

            // Act
            String fromBytes = FileHashUtil.sha256Hex(content);
            String fromMultipart = FileHashUtil.sha256Hex(file);

            // Assert
            assertEquals(fromBytes, fromMultipart);
        }

        @Test
        void emptyMultipart_throwsInternalServerErrorException() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile("file", "empty.bin", "application/octet-stream", new byte[0]);

            // Act & Assert
            assertThrows(InternalServerErrorException.class, () -> FileHashUtil.sha256Hex(file));
        }
    }
}
