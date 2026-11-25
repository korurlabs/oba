package com.korur.oba.apps.kaya.lesson.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.apps.kaya.lesson.constant.controller.LessonControllerLogConstant;
import com.korur.oba.apps.kaya.lesson.constant.controller.LessonControllerResponseConstant;
import com.korur.oba.apps.kaya.lesson.dto.derived.LessonDelivery;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliverySearchRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryCreatedResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliverySearchResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDetailResponse;
import com.korur.oba.apps.kaya.lesson.service.LessonQueryService;
import com.korur.oba.apps.kaya.lesson.service.LessonService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Lessons for the CLI, on the machine token chain: the bearer machine token names the organization and
 * the user, so the request carries no JWT and no X-Organization-ID. A lesson is standing guidance,
 * not a once-only message, so nothing is acknowledged: the CLI fetches conditionally and keeps its
 * own time of last look.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/delivery/lessons")
@Tag(name = "Lesson delivery", description = "Lessons for the CLI, authenticated by a machine token")
public class LessonDeliveryController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LessonDeliveryController.class);

    private final LessonQueryService lessonQueryService;
    private final LessonService lessonService;
    private final LoggingService loggingService;

    /**
     * Answers 304 when the CLI's If-None-Match already names the current lessons, so an unchanged
     * session start downloads nothing.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the lessons of a checkout",
            description = "Bearer machine token. The titles of the lessons of the checkout's repository plus the "
                    + "organization-wide ones, newest first, at most 100; an unknown or unreadable remote gives the "
                    + "organization-wide ones only. The ETag covers every applicable lesson, also past the 100, and "
                    + "ignores since; send it back in If-None-Match to get 304 while nothing changed")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The lessons, possibly none"),
            @ApiResponse(responseCode = "304", description = "If-None-Match matches the current ETag; no body"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token missing, unknown, revoked or expired, "
                    + "or machine retired"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002: the user is not ACTIVE; ORG_SEC_001: not a "
                    + "member, or the membership is not ACTIVE; ORG_SEC_005: the organization is not ACTIVE; ORG_SEC_003: "
                    + "the user is a GUEST; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "422", description = "KAY_VAL_002: repository archived; GEN_VAL_001: remoteUrl "
                    + "missing or too long, since unreadable"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<LessonDeliveryResponse>>> getLessons(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @ModelAttribute @ParameterObject LessonDeliveryQueryRequest request,
            @Parameter(hidden = true) WebRequest webRequest) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_LIST_REQUEST,
                principal.machineId());
        LessonDelivery delivery = lessonQueryService.listForCheckout(principal, request.getRemoteUrl(), request.getSince());
        // checkNotModified sets the ETag header and, on a match, the 304 status; a null return
        // then sends no body.
        if (webRequest.checkNotModified(delivery.etag())) {
            loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_NOT_MODIFIED,
                    principal.machineId());
            return null;
        }
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_LIST_COMPLETED,
                delivery.lessons().size(), principal.machineId());
        return ResponseUtil.ok(LessonControllerResponseConstant.RESPONSE_LESSONS_RETRIEVED, delivery.lessons());
    }

    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Add a lesson from a checkout",
            description = "Bearer machine token. The author is the user of the token. Without repoWide the lesson belongs "
                    + "to the checkout's repository; with it, to the organization, and the remote is not resolved")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Lesson added, with its page"),
            @ApiResponse(responseCode = "400", description = "The body malformed"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token refused"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002, ORG_SEC_001 or ORG_SEC_005: the user no "
                    + "longer acts in the organization; ORG_SEC_003: the user is a GUEST; APP_SEC_001: the user has no Kaya "
                    + "access"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no repository of the organization has this remote"),
            @ApiResponse(responseCode = "422", description = "KAY_VAL_002: repository archived; GEN_VAL_001: a field "
                    + "missing or out of range, claudeSessionId not a session id"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LessonDeliveryCreatedResponse>> addLesson(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @RequestBody LessonDeliveryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_CREATE_REQUEST,
                principal.machineId());
        LessonDeliveryCreatedResponse response = lessonService.create(principal, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_CREATE_COMPLETED,
                response.id(), principal.machineId());
        return ResponseUtil.created(LessonControllerResponseConstant.RESPONSE_LESSON_CREATED, response);
    }

    @GetMapping("/search")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Search the lessons of a checkout",
            description = "Bearer machine token. The lessons of the same scope as the list whose title or text holds the "
                    + "search, case ignored, with their text; newest first, at most 100")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The matching lessons, possibly none"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token refused"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002, ORG_SEC_001 or ORG_SEC_005: the user no "
                    + "longer acts in the organization; ORG_SEC_003: the user is a GUEST; APP_SEC_001: the user has no Kaya "
                    + "access"),
            @ApiResponse(responseCode = "422", description = "KAY_VAL_002: repository archived; GEN_VAL_001: remoteUrl "
                    + "or search missing or too long"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<LessonDeliverySearchResponse>>> searchLessons(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @ModelAttribute @ParameterObject LessonDeliverySearchRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_SEARCH_REQUEST,
                principal.machineId());
        List<LessonDeliverySearchResponse> response =
                lessonQueryService.searchForCheckout(principal, request.getRemoteUrl(), request.getSearch());
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_SEARCH_COMPLETED,
                response.size(), principal.machineId());
        return ResponseUtil.ok(LessonControllerResponseConstant.RESPONSE_LESSONS_RETRIEVED, response);
    }

    @GetMapping("/{lessonId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get a lesson with its text",
            description = "Bearer machine token. The same body as GET /lessons/{lessonId}; canEdit speaks for the user of the token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The lesson"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token refused"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002, ORG_SEC_001 or ORG_SEC_005: the user no "
                    + "longer acts in the organization; ORG_SEC_003: the user is a GUEST; APP_SEC_001: the user has no Kaya "
                    + "access"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no such lesson"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LessonDetailResponse>> getLesson(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @PathVariable UUID lessonId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_GET_REQUEST,
                principal.machineId(), lessonId);
        LessonDetailResponse response = lessonQueryService.getLesson(principal, lessonId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_GET_COMPLETED,
                lessonId, principal.machineId());
        return ResponseUtil.ok(LessonControllerResponseConstant.RESPONSE_LESSON_RETRIEVED, response);
    }

    @DeleteMapping("/{lessonId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete a lesson for good",
            description = "Bearer machine token. Open to the author, or to a user of the token who is ADMIN or OWNER")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token refused"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_003: neither the author nor ADMIN or OWNER; "
                    + "GEN_SEC_002, ORG_SEC_001 or ORG_SEC_005: the user no longer acts in the organization; ORG_SEC_003: "
                    + "the user is a GUEST; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no such lesson"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteLesson(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @PathVariable UUID lessonId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_DELETE_REQUEST,
                principal.machineId(), lessonId);
        lessonService.delete(principal, lessonId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_DELETE_COMPLETED,
                lessonId, principal.machineId());
        return ResponseUtil.ok(LessonControllerResponseConstant.RESPONSE_LESSON_DELETED);
    }
}
