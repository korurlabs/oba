package com.korur.oba.connect.outbox.dto;

import com.korur.oba.connect.outbox.enums.ConnectOutboxSystem;
import com.korur.oba.connect.outbox.enums.OutboxEventType;

// 1. Framework-level
import jakarta.validation.constraints.NotNull;

public record OutboxCreateDTO(
        @NotNull Object payload,
        @NotNull OutboxEventType outboxEventType,
        @NotNull ConnectOutboxSystem connectOutboxSystem) {
}
