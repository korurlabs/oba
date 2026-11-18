package com.korur.oba.apps.kaya.ingest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "The acknowledgement of a batch")
public record IngestBatchResponse(
        @Schema(description = "The session of the batch", example = "3f1c2a9e-1b7d-4c55-9f0a-2d7e8c1b4a60")
        String sessionId,

        @Schema(description = "The highest seq up to which Kaya holds every seq from 1 for the "
                + "session; 0 when it lacks seq 1. Events after a gap are stored but not acknowledged. The recorder "
                + "drops its events up to this seq", example = "142")
        long acknowledgedSeq
) {
}
