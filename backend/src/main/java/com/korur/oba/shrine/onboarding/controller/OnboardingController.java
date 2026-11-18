package com.korur.oba.shrine.onboarding.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.onboarding.constant.controller.OnboardingControllerLogConstant;
import com.korur.oba.shrine.onboarding.constant.controller.OnboardingControllerResponseConstant;
import com.korur.oba.shrine.onboarding.dto.request.OnboardingCompleteRequest;
import com.korur.oba.shrine.onboarding.dto.response.OnboardingStatusResponse;
import com.korur.oba.shrine.onboarding.service.OnboardingQueryService;
import com.korur.oba.shrine.onboarding.service.OnboardingService;

/**
 * REST controller for user onboarding operations.
 * <p>
 * Provides endpoints for querying onboarding status and completing the onboarding flow.
 */
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "User onboarding management endpoints")
public class OnboardingController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OnboardingController.class);

    private final OnboardingService onboardingService;
    private final OnboardingQueryService onboardingQueryService;
    private final LoggingService loggingService;

    @GetMapping("/status")
    @Operation(summary = "Get onboarding status", description = "Retrieves the current user's onboarding status")
    public ResponseEntity<CustomResponse<OnboardingStatusResponse>> getOnboardingStatus(
            @Parameter(hidden = true) @GetUser(requireActive = false) User user) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OnboardingControllerLogConstant.ONBOARDING_STATUS_REQUEST);

        OnboardingStatusResponse response = onboardingQueryService.getOnboardingStatus(user);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OnboardingControllerLogConstant.ONBOARDING_STATUS_COMPLETED);
        return ResponseUtil.ok(OnboardingControllerResponseConstant.RESPONSE_ONBOARDING_STATUS_RETRIEVED, response);
    }

    @PostMapping("/complete")
    @Operation(summary = "Complete onboarding", description = "Completes the onboarding process for the current user")
    public ResponseEntity<CustomResponse<OnboardingStatusResponse>> completeOnboarding(
            @Parameter(hidden = true) @GetUser(requireActive = false) User user,
            @Valid @RequestBody OnboardingCompleteRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OnboardingControllerLogConstant.ONBOARDING_COMPLETE_REQUEST);

        OnboardingStatusResponse response = onboardingService.completeOnboarding(user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OnboardingControllerLogConstant.ONBOARDING_COMPLETE_COMPLETED);
        return ResponseUtil.ok(OnboardingControllerResponseConstant.RESPONSE_ONBOARDING_COMPLETED, response);
    }
}
