package com.korur.oba.dojo.security.service;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class AESEncryptionServiceUnitTest {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;

    private final AESEncryptionService service = new AESEncryptionService("a-test-secret-of-thirty-two-byte");

    @Test
    void encrypt_writesIvCiphertextAndTagWithoutGaps() {
        // Arrange
        String plaintext = "çok gizli değer";
        int plaintextLength = plaintext.getBytes(StandardCharsets.UTF_8).length;

        // Act
        byte[] encrypted = Base64.getDecoder().decode(service.encrypt(plaintext));

        // Assert
        assertThat(encrypted).hasSize(IV_LENGTH + plaintextLength + TAG_LENGTH);
        assertThat(service.decrypt(service.encrypt(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void encrypt_oneByteText_roundTrips() {
        // Act
        String encrypted = service.encrypt("x");

        // Assert
        assertThat(Base64.getDecoder().decode(encrypted)).hasSize(IV_LENGTH + 1 + TAG_LENGTH);
        assertThat(service.decrypt(encrypted)).isEqualTo("x");
    }

    @Test
    void encrypt_sameTextTwice_usesAFreshIv() {
        // Act
        String first = service.encrypt("value");
        String second = service.encrypt("value");

        // Assert
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void encrypt_emptyOrNull_isReturnedUnchanged() {
        // Act and Assert
        assertThat(service.encrypt("")).isEmpty();
        assertThat(service.encrypt(null)).isNull();
    }

    @Test
    void decrypt_tamperedCiphertext_isRefused() {
        // Arrange
        byte[] encrypted = Base64.getDecoder().decode(service.encrypt("value"));
        encrypted[IV_LENGTH] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(encrypted);

        // Act and Assert
        assertThatThrownBy(() -> service.decrypt(tampered)).isInstanceOf(RuntimeException.class);
    }
}
