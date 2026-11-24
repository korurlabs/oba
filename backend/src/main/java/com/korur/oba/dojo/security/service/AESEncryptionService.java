package com.korur.oba.dojo.security.service;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 2. Third-party

// 3. Java SDK
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

// 4. Internal

/**
 * AES/GCM Encryption Service
 * Uses a secret key loaded from application.properties
 */
@Service
public class AESEncryptionService {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    public AESEncryptionService(@Value("${encryption.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "Encryption secret cannot be null or empty. Please set 'encryption.secret' in application.properties");
        }
        // Use first 32 bytes (256 bits) of the secret (or pad/truncate as needed)
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        byte[] normalizedKey = new byte[32];
        System.arraycopy(keyBytes, 0, normalizedKey, 0, Math.min(keyBytes.length, 32));
        this.secretKey = new SecretKeySpec(normalizedKey, "AES");
    }

    /**
     * Encrypts the given plaintext
     * 
     * @param plaintext the text to encrypt
     * @return Base64 encoded string: IV + ciphertext + tag
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);

            // Generate random 12-byte IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);

            // Layout: IV (12 bytes) + ciphertext + tag. The cipher writes straight
            // behind the IV; addExact rejects a plaintext whose output would not
            // fit one array instead of wrapping to a negative size.
            byte[] result = new byte[Math.addExact(GCM_IV_LENGTH, cipher.getOutputSize(plaintextBytes.length))];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            cipher.doFinal(plaintextBytes, 0, plaintextBytes.length, result, GCM_IV_LENGTH);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypts the given encrypted string
     * 
     * @param encryptedBase64 Base64 string containing IV + ciphertext + tag
     * @return decrypted plaintext
     */
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) {
            return encryptedBase64;
        }

        try {
            byte[] encryptedData = Base64.getDecoder().decode(encryptedBase64);

            if (encryptedData.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted data: too short");
            }

            // Extract IV from first 12 bytes
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);

            // Extract ciphertext + tag
            byte[] ciphertext = new byte[encryptedData.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedData, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decrypted = cipher.doFinal(ciphertext);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to decrypt data. Check if the encrypted value is valid and the secret key is correct.", e);
        }
    }
}
