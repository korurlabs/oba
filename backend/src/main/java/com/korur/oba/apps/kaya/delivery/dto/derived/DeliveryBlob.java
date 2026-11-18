package com.korur.oba.apps.kaya.delivery.dto.derived;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The content of a delivered file, served as raw bytes.
 *
 * @param sha256  the hash the content is stored under
 * @param content the bytes
 * @param size    the byte length
 */
public record DeliveryBlob(String sha256, byte[] content, int size) {
}
