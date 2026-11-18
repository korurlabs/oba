package com.korur.oba.connect.outbox.dto.payload;

/**
 * Payload for Azure Blob Storage file deletion via outbox.
 */
public record AzureBlobDeletePayload(String fileUrl) {
}
