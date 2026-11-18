package com.korur.oba.connect.outbox.dto.payload;

/**
 * Payload for Azure Blob Storage container-scoped file deletion via outbox.
 */
public record AzureBlobContainerDeletePayload(String containerKey, String fileUrl) {
}
