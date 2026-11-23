package com.korur.oba.apps.kaya.delivery.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.apps.kaya.delivery.constant.DeliveryConstant;
import com.korur.oba.apps.kaya.delivery.constant.controller.DeliveryControllerLogConstant;
import com.korur.oba.apps.kaya.delivery.constant.controller.DeliveryControllerResponseConstant;
import com.korur.oba.apps.kaya.delivery.dto.derived.DeliveryBlob;
import com.korur.oba.apps.kaya.delivery.dto.derived.DeliveryCurrent;
import com.korur.oba.apps.kaya.delivery.dto.request.DeliveryCurrentRequest;
import com.korur.oba.apps.kaya.delivery.dto.request.DeliveryProposalRequest;
import com.korur.oba.apps.kaya.delivery.dto.request.DeliveryReleaseQueryRequest;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryCurrentResponse;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryProposalResponse;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryReleaseResponse;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryRepositoriesResponse;
import com.korur.oba.apps.kaya.delivery.service.DeliveryProposalService;
import com.korur.oba.apps.kaya.delivery.service.DeliveryQueryService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * Delivery to the CLI, on the machine token chain: the bearer machine token names the
 * organization, so the request carries no JWT and no X-Organization-ID, and every read is limited
 * to that organization. Besides the list of the organization's repositories, only signed releases
 * and the content they name are served. The one write
 * is a proposal, which only enters review and changes no delivered file until a reviewer approves
 * it.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/delivery")
@Tag(name = "Delivery", description = "Signed releases for the CLI, authenticated by a machine token")
public class DeliveryController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DeliveryController.class);

    private final DeliveryQueryService deliveryQueryService;
    private final DeliveryProposalService deliveryProposalService;
    private final LoggingService loggingService;

    /**
     * Lets the CLI recognise a checkout of the organization by comparing the checkout's remotes with
     * this list on the laptop, instead of sending the remote of every checkout to Kaya.
     */
    @GetMapping("/repositories")
    @Operation(summary = "List the repositories a checkout is recognised by",
            description = "Bearer machine token. The normalised remote URL of every ACTIVE repository of the token's "
                    + "organization, sorted; an ARCHIVED repository is left out")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The organization and its active repositories; an empty "
                    + "list when it has none"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token missing, unknown, revoked or expired, "
                    + "or machine retired"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002 or KAY_SEC_002: the user no longer acts in "
                    + "the organization; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DeliveryRepositoriesResponse>> getRepositories(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.REPOSITORIES_REQUEST,
                principal.machineId());
        DeliveryRepositoriesResponse response = deliveryQueryService.getRepositories(principal);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.REPOSITORIES_COMPLETED,
                response.repositories().size(), principal.machineId());
        return ResponseUtil.ok(DeliveryControllerResponseConstant.RESPONSE_REPOSITORIES_RETRIEVED, response);
    }

    /**
     * Answers 304 when the CLI's If-None-Match already names the current release,
     * so an unchanged launch downloads nothing.
     */
    @GetMapping("/current")
    @Operation(summary = "Get the current release of a checkout",
            description = "Bearer machine token. The newest signed release of the repository the remote names. The ETag "
                    + "covers the manifest hash; send it back in If-None-Match to get 304 while nothing changed")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Current release; releaseId and number are null while the "
                    + "repository has none"),
            @ApiResponse(responseCode = "304", description = "If-None-Match matches the current ETag; no body"),
            @ApiResponse(responseCode = "400", description = "GEN_API_001: remote URL not accepted"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token missing, unknown, revoked or expired, "
                    + "or machine retired"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002 or KAY_SEC_002: the user no longer acts in "
                    + "the organization; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no repository with this remote"),
            @ApiResponse(responseCode = "422", description = "KAY_VAL_002: repository archived; GEN_VAL_001: remoteUrl "
                    + "missing or too long"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DeliveryCurrentResponse>> getCurrent(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @ModelAttribute @ParameterObject DeliveryCurrentRequest request,
            @Parameter(hidden = true) WebRequest webRequest) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.CURRENT_REQUEST,
                principal.machineId());
        DeliveryCurrent current = deliveryQueryService.getCurrent(principal, request.getRemoteUrl());
        // checkNotModified sets the ETag header and, on a match, the 304 status; a null return
        // then sends no body.
        if (webRequest.checkNotModified(current.etag())) {
            loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.CURRENT_NOT_MODIFIED,
                    principal.machineId(), current.etag());
            return null;
        }
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.CURRENT_COMPLETED,
                current.response().number(), principal.machineId(), current.etag());
        return ResponseUtil.ok(DeliveryControllerResponseConstant.RESPONSE_CURRENT_RETRIEVED, current.response());
    }

    @GetMapping("/releases/{releaseId}")
    @Operation(summary = "Get a signed release by id",
            description = "Bearer machine token. The signed manifest text, its SHA-256 and its Ed25519 signature; the CLI "
                    + "verifies the signature over the manifest's UTF-8 bytes before it reads the file list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signed release"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token refused"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002 or KAY_SEC_002: the user no longer acts in "
                    + "the organization; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no release of the organization has this id"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_012: the repository's remote URL changed after "
                    + "the release; a new release is needed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DeliveryReleaseResponse>> getRelease(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @PathVariable UUID releaseId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.RELEASE_REQUEST,
                releaseId, principal.machineId());
        DeliveryReleaseResponse response = deliveryQueryService.getRelease(principal, releaseId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.RELEASE_COMPLETED,
                response.releaseId(), response.number(), principal.machineId());
        return ResponseUtil.ok(DeliveryControllerResponseConstant.RESPONSE_RELEASE_RETRIEVED, response);
    }

    @GetMapping("/releases")
    @Operation(summary = "Get a signed release of a checkout by number",
            description = "Bearer machine token. The same body as GET /delivery/releases/{releaseId}, for the release the "
                    + "CLI recorded by number")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signed release"),
            @ApiResponse(responseCode = "400", description = "GEN_API_001: remote URL not accepted"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token refused"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002 or KAY_SEC_002: the user no longer acts in "
                    + "the organization; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: unknown repository or release number"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_012: the repository's remote URL changed after "
                    + "the release"),
            @ApiResponse(responseCode = "422", description = "KAY_VAL_002: repository archived; GEN_VAL_001: remoteUrl "
                    + "or number missing or invalid"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DeliveryReleaseResponse>> getReleaseByNumber(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @ModelAttribute @ParameterObject DeliveryReleaseQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.RELEASE_BY_NUMBER_REQUEST,
                request.getNumber(), principal.machineId());
        DeliveryReleaseResponse response = deliveryQueryService.getReleaseByNumber(principal, request.getRemoteUrl(),
                request.getNumber());
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.RELEASE_COMPLETED,
                response.releaseId(), response.number(), principal.machineId());
        return ResponseUtil.ok(DeliveryControllerResponseConstant.RESPONSE_RELEASE_RETRIEVED, response);
    }

    /**
     * Answers the raw bytes, not the JSON envelope, because the CLI hashes and writes them as they are.
     */
    @GetMapping(value = "/blobs/{sha256}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Get the content of a delivered file",
            description = "Bearer machine token. The raw bytes stored under the SHA-256, served only when a release of the "
                    + "organization names it. Errors answer the usual JSON envelope")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The content, application/octet-stream"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token refused"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002 or KAY_SEC_002: the user no longer acts in "
                    + "the organization; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no release of the organization names this hash"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: sha256 is not 64 lower-case hex characters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<byte[]> getBlob(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @PathVariable @Pattern(regexp = DeliveryConstant.SHA256_PATTERN, message = DeliveryConstant.SHA256_INVALID_MESSAGE)
            String sha256) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.BLOB_REQUEST,
                sha256, principal.machineId());
        DeliveryBlob blob = deliveryQueryService.getBlob(principal, sha256);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.BLOB_COMPLETED,
                blob.sha256(), blob.size(), principal.machineId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(blob.content().length)
                .eTag(blob.sha256())
                .header(HttpHeaders.CACHE_CONTROL, DeliveryConstant.BLOB_CACHE_CONTROL)
                .body(blob.content());
    }

    @PostMapping("/proposals")
    @Operation(summary = "Propose a change from a checkout",
            description = "Bearer machine token. Creates a draft with source CLI from the files edited on the named release "
                    + "and submits it for review in one step, all or nothing. A path belongs to the "
                    + "pack whose release delivered it; a new path to the mounted pack whose namespace or folder AGENTS.md holds "
                    + "it, else to the deepest pack composed at the repository root. Every path must belong to one pack. "
                    + "The proposer is the user of the token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Proposal in review, with its review page"),
            @ApiResponse(responseCode = "400", description = "GEN_API_001: remote URL not accepted; the body malformed"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token refused"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002 {userStatus} or {organizationId}: the user is not "
                    + "ACTIVE or not a member; KAY_SEC_002: the membership or organization is not ACTIVE; "
                    + "APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: unknown repository or release number"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_002 {currentVersionId, conflicts}: the pack "
                    + "changed a proposed path since the release; KAY_CFG_015: another version of it is in review; "
                    + "KAY_CFG_016: the files change nothing; KAY_CFG_003: a path given by two packs"),
            @ApiResponse(responseCode = "413", description = "KAY_VAL_012: body over 8 MiB"),
            @ApiResponse(responseCode = "422", description = "KAY_CFG_001 {violations}: a validation rule broken; KAY_CFG_019 "
                    + "{owners}: paths of several packs, or {candidates}: a new path with no single deepest root pack; KAY_CFG_028: "
                    + "a new path no file kind accepts; KAY_VAL_002: repository archived; GEN_VAL_001: a field missing or "
                    + "invalid, a path named twice, content not base64"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DeliveryProposalResponse>> propose(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @RequestBody DeliveryProposalRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.PROPOSAL_REQUEST,
                request.files().size(), request.releaseNumber(), principal.machineId());
        DeliveryProposalResponse response = deliveryProposalService.propose(principal, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DeliveryControllerLogConstant.PROPOSAL_COMPLETED,
                response.proposalId(), response.packId(),
                principal.machineId());
        return ResponseUtil.created(DeliveryControllerResponseConstant.RESPONSE_PROPOSAL_CREATED, response);
    }
}
