package com.korur.oba.connect.push.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.constant.controller.PushControllerLogConstant;
import com.korur.oba.connect.push.constant.controller.PushControllerResponseConstant;
import com.korur.oba.connect.push.dto.request.PushDeviceRegisterRequest;
import com.korur.oba.connect.push.dto.request.PushPreferenceUpdateRequest;
import com.korur.oba.connect.push.dto.response.PushDeviceResponse;
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.service.PushDeviceService;
import com.korur.oba.connect.push.service.PushPreferenceQueryService;
import com.korur.oba.connect.push.service.PushPreferenceService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;

/**
 * REST controller for the caller's push devices and push switches.
 *
 * Devices and switches belong to the user, not to an organization: one phone receives the pushes of
 * every organization the user is a member of. The paths are therefore listed in
 * {@link com.korur.oba.dojo.security.constant.SecurityConstant#ORGANIZATION_EXEMPT_PATHS} and the
 * methods carry no RequiredUserAccess; GetUser requires an ACTIVE user, and every service call is
 * scoped by that user, so a caller only ever reads or changes their own rows.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(PushConstant.PUSH_PATH)
@Tag(name = "Push", description = "Push notification devices and preferences of the signed-in user")
public class PushController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PushController.class);

    private final PushDeviceService pushDeviceService;
    private final PushPreferenceService pushPreferenceService;
    private final PushPreferenceQueryService pushPreferenceQueryService;
    private final LoggingService loggingService;

    /**
     * Registers the caller's phone for push notifications, or refreshes the registration of its token.
     */
    @PutMapping(PushConstant.DEVICES_PATH)
    @Operation(summary = "Register push device",
            description = "Registers an Expo push token for the signed-in user, or refreshes it; a token registered "
                    + "under another user moves to the caller. Idempotent; the app calls it on every launch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "User not provisioned or not ACTIVE"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PushDeviceResponse>> registerDevice(
            @Parameter(hidden = true) @GetUser User user,
            @RequestBody @Valid PushDeviceRegisterRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PushControllerLogConstant.DEVICE_REGISTER_REQUEST,
                request.platform());

        PushDeviceResponse response = pushDeviceService.registerDevice(user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PushControllerLogConstant.DEVICE_REGISTER_COMPLETED,
                response.id());

        return ResponseUtil.ok(PushControllerResponseConstant.RESPONSE_DEVICE_REGISTERED, response);
    }

    /**
     * Removes one of the caller's devices, as the app does on sign-out.
     */
    @DeleteMapping(PushConstant.DEVICE_PATH)
    @Operation(summary = "Unregister push device",
            description = "Removes one of the signed-in user's devices; another user's device reads as not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "User not provisioned or not ACTIVE"),
            @ApiResponse(responseCode = "404", description = "Device not found or another user's (PSH_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> unregisterDevice(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID deviceId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PushControllerLogConstant.DEVICE_UNREGISTER_REQUEST,
                deviceId);

        pushDeviceService.unregisterDevice(user, deviceId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PushControllerLogConstant.DEVICE_UNREGISTER_COMPLETED,
                deviceId);

        return ResponseUtil.ok(PushControllerResponseConstant.RESPONSE_DEVICE_UNREGISTERED);
    }

    /**
     * Returns the caller's push switches.
     */
    @GetMapping(PushConstant.PREFERENCES_PATH)
    @Operation(summary = "Get push preferences",
            description = "The signed-in user's push switches; every switch is on when the user never saved them")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "User not provisioned or not ACTIVE"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PushPreferenceResponse>> getPreferences(
            @Parameter(hidden = true) @GetUser User user) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PushControllerLogConstant.PREFERENCES_GET_REQUEST);

        PushPreferenceResponse response = pushPreferenceQueryService.getPreferences(user);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PushControllerLogConstant.PREFERENCES_GET_COMPLETED);

        return ResponseUtil.ok(PushControllerResponseConstant.RESPONSE_PREFERENCES_RETRIEVED, response);
    }

    /**
     * Replaces all of the caller's push switches.
     */
    @PutMapping(PushConstant.PREFERENCES_PATH)
    @Operation(summary = "Update push preferences",
            description = "Replaces every push switch of the signed-in user; they apply to all of the user's devices")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "User not provisioned or not ACTIVE"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PushPreferenceResponse>> updatePreferences(
            @Parameter(hidden = true) @GetUser User user,
            @RequestBody @Valid PushPreferenceUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PushControllerLogConstant.PREFERENCES_UPDATE_REQUEST);

        PushPreferenceResponse response = pushPreferenceService.updatePreferences(user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PushControllerLogConstant.PREFERENCES_UPDATE_COMPLETED);

        return ResponseUtil.ok(PushControllerResponseConstant.RESPONSE_PREFERENCES_UPDATED, response);
    }
}
