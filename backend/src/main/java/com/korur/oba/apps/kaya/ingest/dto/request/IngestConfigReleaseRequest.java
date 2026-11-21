package com.korur.oba.apps.kaya.ingest.dto.request;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.session.enums.SessionDeliveredBy;
import com.korur.oba.apps.kaya.session.enums.SessionReleaseState;

/**
 * The config release the session started on, as `oba kaya hook session-start` wrote it into the
 * recorder's session record. No field is refused: a number and hash that match no release of the
 * repository, or a missing state, are stored as UNVERIFIED, because a refused batch would stop
 * the session from recording. For the same reason a state or delivery the server does not know,
 * as a newer CLI may send, reads as null instead of failing the batch: the state is then stored as
 * UNVERIFIED and the delivery left unset.
 */
@Schema(description = "The config release the session started on")
public record IngestConfigReleaseRequest(
        @Schema(description = "Release number of the session's repository; null with state NONE", nullable = true, example = "7")
        Integer number,

        @Schema(description = "SHA-256 of the signed manifest the CLI verified, 64 lower-case hex characters; null with state NONE",
                nullable = true, example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String manifestSha256,

        @Schema(description = "FRESH, CACHED, STALE or NONE; a missing or unknown state is stored as UNVERIFIED",
                nullable = true, example = "FRESH")
        @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        SessionReleaseState state,

        @Schema(description = "LAUNCHER or HOOK; an unknown value is stored as unset", nullable = true, example = "LAUNCHER")
        @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        SessionDeliveredBy deliveredBy
) {
}
