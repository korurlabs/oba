package com.korur.oba.apps.kaya.seed.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.seed.constant.controller.SeedControllerLogConstant;
import com.korur.oba.apps.kaya.seed.constant.controller.SeedControllerResponseConstant;
import com.korur.oba.apps.kaya.seed.dto.request.SeedRequest;
import com.korur.oba.apps.kaya.seed.dto.response.SeedRunResponse;
import com.korur.oba.apps.kaya.seed.service.SeedService;

/**
 * Seed runs: the import of today's repository agent config into the organization's packs.
 * Only an OWNER seeds, because a seed approves its own versions, executable changes
 * included.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/seed-runs")
@Tag(name = "Seed runs", description = "Import of repository agent config into packs")
public class SeedController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SeedController.class);

    private final SeedService seedService;
    private final LoggingService loggingService;

    /**
     * Runs a seed and returns the stored run with its report.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.OWNER)
    @Operation(summary = "Run a seed",
            description = "Every named blob must be uploaded first. Finds or creates each pack under its parent, "
                    + "replaces the pack list of each named repository with its packs and mount folders, creates an approved "
                    + "SEED version for each pack whose files changed and signs one release per repository whose chains hold a "
                    + "changed pack or whose pack list changed. A file that breaks a file rule, or that Kaya cannot "
                    + "store, is reported under report.refused, each link that leads to it under report.notImported, and the "
                    + "rest is imported; a size limit is only a warning, under report.warnings; a refused repository "
                    + "composition discards every write and is reported under report.violations with report.applied false. "
                    + "report.entities counts the imported entities per kind. Either way the run is stored and answered 200; "
                    + "status tells PASSED from FAILED. A second run with unchanged input creates no version")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The run was stored, PASSED or FAILED"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, the body malformed, or a "
                    + "remote Kaya does not accept (GEN_API_001)"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not an OWNER of the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_033: a stored pack has another parent than the set "
                    + "names (errorValues name, parent, storedParentPackId)"),
            @ApiResponse(responseCode = "413", description = "KAY_CFG_011: the sets name more than 20000 files in all "
                    + "(errorValues limitFiles, fileCount) or more than 64 MiB of distinct content (limitBytes, contentBytes)"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: the request breaks a limit, lists a set, path or "
                    + "repository twice, or names a parent or repository pack that is unknown, the pack "
                    + "itself or part of a cycle; KAY_CFG_009: blobs not uploaded, listed under errorValues.missing; "
                    + "KAY_CFG_026: an invalid mount folder; KAY_CFG_001: a pack a repository lists twice; KAY_CFG_034: a "
                    + "rule of a mounted pack not scoped to each of its folders (errorValues rule, folder, pack)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SeedRunResponse>> seed(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestBody SeedRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SeedControllerLogConstant.SEED_REQUEST,
                organizationId, request.sets().size());
        SeedRunResponse response = seedService.seed(user, organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SeedControllerLogConstant.SEED_COMPLETED,
                response.id(), organizationId, response.status());
        return ResponseUtil.ok(SeedControllerResponseConstant.RESPONSE_SEED_RUN_COMPLETED, response);
    }
}
