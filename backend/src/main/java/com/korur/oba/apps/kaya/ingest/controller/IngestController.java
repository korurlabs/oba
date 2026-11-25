package com.korur.oba.apps.kaya.ingest.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.apps.kaya.ingest.constant.controller.IngestControllerLogConstant;
import com.korur.oba.apps.kaya.ingest.constant.controller.IngestControllerResponseConstant;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestBatchRequest;
import com.korur.oba.apps.kaya.ingest.dto.response.IngestBatchResponse;
import com.korur.oba.apps.kaya.ingest.service.IngestService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * The recorder's batch endpoint, on the machine token chain: the bearer machine token names the
 * organization, the user and the machine, so the request carries no JWT and no X-Organization-ID.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/ingest")
@Tag(name = "Ingest", description = "Recorder batches, authenticated by a machine token")
public class IngestController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IngestController.class);

    private final IngestService ingestService;
    private final LoggingService loggingService;

    /**
     * Applies one batch of a session and acknowledges the highest seq up to which Kaya holds every seq.
     */
    @PostMapping("/batches")
    @Operation(summary = "Send a recorder batch",
            description = "Bearer machine token. Events with a seq Kaya already holds are skipped, so a batch "
                    + "may be sent again after a lost acknowledgement. A batch needs events or usage. A handoff or plan "
                    + "whose content holds U+0000 is a binary file and is not stored; U+0000 in any other text is stored "
                    + "as U+FFFD")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Batch applied; acknowledgedSeq is the highest seq up to "
                    + "which every seq from 1 is held"),
            @ApiResponse(responseCode = "400", description = "KAY_VAL_009: usage out of range; KAY_VAL_013: an event "
                    + "is invalid; KAY_VAL_014: no events and no usage; GEN_API_001: remote URL not accepted"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token missing, unknown, revoked or expired, "
                    + "or machine retired"),
            @ApiResponse(responseCode = "409", description = "KAY_VAL_001: session of another user"),
            @ApiResponse(responseCode = "413", description = "KAY_VAL_012: body over 8 MiB"),
            @ApiResponse(responseCode = "422", description = "KAY_VAL_002: repository archived; GEN_VAL_001: a field "
                    + "is invalid"),
            @ApiResponse(responseCode = "429", description = "KAY_SEC_004: more than 120 batches in a minute"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<IngestBatchResponse>> ingestBatch(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @RequestBody IngestBatchRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IngestControllerLogConstant.BATCH_REQUEST,
                request.sessionId(), principal.machineId());
        IngestBatchResponse response = ingestService.ingestBatch(principal, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IngestControllerLogConstant.BATCH_COMPLETED,
                response.sessionId(), response.acknowledgedSeq());
        return ResponseUtil.ok(IngestControllerResponseConstant.RESPONSE_BATCH_ACKNOWLEDGED, response);
    }
}
