package com.korur.oba.shrine.storage.dto.request.attachment;

// 1. Framework-level
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

public record AttachmentCreateRequest(
        @NotNull String fileName,
        @NotNull StorageEntityType entityType,
        @NotNull UUID entityId) implements AttachmentMetadata {
}
