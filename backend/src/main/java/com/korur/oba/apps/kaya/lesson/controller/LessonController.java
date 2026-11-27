package com.korur.oba.apps.kaya.lesson.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import java.util.List;
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
import com.korur.oba.apps.kaya.lesson.constant.controller.LessonControllerLogConstant;
import com.korur.oba.apps.kaya.lesson.constant.controller.LessonControllerResponseConstant;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDetailResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonFiltersResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonListResponse;
import com.korur.oba.apps.kaya.lesson.helper.LessonDeliveryBulkhead;
import com.korur.oba.apps.kaya.lesson.service.LessonQueryService;
import com.korur.oba.apps.kaya.lesson.service.LessonService;

/**
 * Lessons of the organization named by the X-Organization-ID header. Every member reads and
 * creates; editing and deleting are open to "the author, ADMIN or OWNER", so they take MEMBER here
 * and the service checks the rest.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/lessons")
@Tag(name = "Lessons", description = "Standing rules members and machines record for later sessions")
public class LessonController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LessonController.class);

    private final LessonService lessonService;
    private final LessonQueryService lessonQueryService;
    private final LessonDeliveryBulkhead lessonDeliveryBulkhead;
    private final LoggingService loggingService;

    /**
     * Lists the lessons of the organization.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the lessons of the organization",
            description = "Paged, 25 by default and 100 at most; newest first. search matches the title or the text; "
                    + "organizationWide=true lists the lessons of no repository; sessionId lists the lessons a Claude Code "
                    + "session recorded, and a session Kaya has not seen gives an empty page")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a paging or filter value is invalid"),
            @ApiResponse(responseCode = "429", description = "KAY_SEC_004: more than 1200 lesson reads of the user "
                    + "in a minute, in all their organizations and with the CLI together (errorValues.limit 1200, "
                    + "errorValues.policy user-reads-per-minute), more than 3000 of the organization, with the CLI "
                    + "together (errorValues.limit 3000, errorValues.policy organization-reads-per-minute), or 2 "
                    + "lesson reads already running on this instance, of the user in all their organizations together "
                    + "(errorValues.limit 2, errorValues.policy user-reads-at-once) or of the organization "
                    + "(errorValues.limit 2, errorValues.policy organization-reads-at-once); Retry-After gives the "
                    + "seconds until the budget's window ends, or 1 for reads that run at once"),
            @ApiResponse(responseCode = "500", description = "Internal server error"),
            @ApiResponse(responseCode = "503", description = "GEN_UPS_004: the read ran past its time limit of 5 "
                    + "seconds, with Retry-After 5; GEN_UPS_005: this instance already runs 4 lesson reads, with "
                    + "Retry-After 1; try again later")
    })
    public ResponseEntity<CustomResponse<List<LessonListResponse>>> getLessons(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject LessonQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_LIST_REQUEST, organizationId);
        // Charged before the read asks for a place among the reads that run at once, so a member asks
        // for places no more often than the budgets allow, and a refused read never takes one.
        lessonQueryService.admitRead(user, organizationId);
        Page<LessonListResponse> response = lessonDeliveryBulkhead.run(user, organizationId,
                () -> lessonQueryService.listLessons(user, organizationId, request));
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_LIST_COMPLETED,
                response.getNumberOfElements(), organizationId);
        return ResponseUtil.ok(LessonControllerResponseConstant.RESPONSE_LESSONS_RETRIEVED, response);
    }

    /**
     * The options of the list's filters.
     */
    @GetMapping("/filters")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read the lesson filter options",
            description = "The repositories and authors the organization's lessons name, each by name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "429", description = "KAY_SEC_004: more than 1200 lesson reads of the user "
                    + "in a minute, in all their organizations and with the CLI together (errorValues.limit 1200, "
                    + "errorValues.policy user-reads-per-minute), more than 3000 of the organization, with the CLI "
                    + "together (errorValues.limit 3000, errorValues.policy organization-reads-per-minute), or 2 "
                    + "lesson reads already running on this instance, of the user in all their organizations together "
                    + "(errorValues.limit 2, errorValues.policy user-reads-at-once) or of the organization "
                    + "(errorValues.limit 2, errorValues.policy organization-reads-at-once); Retry-After gives the "
                    + "seconds until the budget's window ends, or 1 for reads that run at once"),
            @ApiResponse(responseCode = "500", description = "Internal server error"),
            @ApiResponse(responseCode = "503", description = "GEN_UPS_004: the read ran past its time limit of 5 "
                    + "seconds, with Retry-After 5; GEN_UPS_005: this instance already runs 4 lesson reads, with "
                    + "Retry-After 1; try again later")
    })
    public ResponseEntity<CustomResponse<LessonFiltersResponse>> getFilters(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.FILTERS_REQUEST, organizationId);
        // Charged like a page of the list, before it asks for a place.
        lessonQueryService.admitRead(user, organizationId);
        LessonFiltersResponse response = lessonDeliveryBulkhead.run(user, organizationId,
                () -> lessonQueryService.getFilters(organizationId));
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.FILTERS_COMPLETED, organizationId);
        return ResponseUtil.ok(LessonControllerResponseConstant.RESPONSE_FILTERS_RETRIEVED, response);
    }

    /**
     * Reads one lesson.
     */
    @GetMapping("/{lessonId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a lesson",
            description = "With the session that recorded it, and whether the signed-in user may edit and delete it")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or the lesson id is "
                    + "not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such lesson in the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LessonDetailResponse>> getLesson(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID lessonId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_GET_REQUEST,
                user.getId(), lessonId, organizationId);
        LessonDetailResponse response = lessonQueryService.getLesson(user, organizationId, lessonId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_GET_COMPLETED,
                lessonId, organizationId);
        return ResponseUtil.ok(LessonControllerResponseConstant.RESPONSE_LESSON_RETRIEVED, response);
    }

    /**
     * Creates a lesson.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create a lesson",
            description = "Without a repository the lesson applies to the whole organization")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Lesson created"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such repository in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a field is invalid; KAY_VAL_002: the repository is "
                    + "archived"),
            @ApiResponse(responseCode = "429", description = "KAY_SEC_004: more than 60 lessons added by the user in a "
                    + "minute, in all their organizations together (errorValues.limit 60, errorValues.policy "
                    + "user-lessons-per-minute); Retry-After gives the seconds until the window ends"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LessonDetailResponse>> createLesson(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestBody LessonRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_CREATE_REQUEST,
                user.getId(), organizationId);
        // Charged before the transaction of the lesson, so a refused one takes no connection.
        lessonService.admitCreate(user);
        LessonDetailResponse response = lessonService.create(user, organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_CREATE_COMPLETED,
                response.id(), organizationId);
        return ResponseUtil.created(LessonControllerResponseConstant.RESPONSE_LESSON_CREATED, response);
    }

    /**
     * Replaces the title, text and repository of a lesson.
     */
    @PutMapping("/{lessonId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update a lesson",
            description = "Open to the author, ADMIN and OWNER. Replaces every field: a null repository moves the lesson "
                    + "to the whole organization")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, the lesson id is not "
                    + "a UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization; GEN_SEC_003: "
                    + "neither the author nor ADMIN or OWNER"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such lesson or repository in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a field is invalid; KAY_VAL_002: the lesson moves "
                    + "to an archived repository"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LessonDetailResponse>> updateLesson(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_UPDATE_REQUEST,
                user.getId(), lessonId, organizationId);
        LessonDetailResponse response = lessonService.update(user, organizationId, lessonId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_UPDATE_COMPLETED,
                lessonId, organizationId);
        return ResponseUtil.ok(LessonControllerResponseConstant.RESPONSE_LESSON_UPDATED, response);
    }

    /**
     * Deletes a lesson for good.
     */
    @DeleteMapping("/{lessonId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete a lesson", description = "Open to the author, ADMIN and OWNER. The delete is final")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or the lesson id is "
                    + "not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization; GEN_SEC_003: "
                    + "neither the author nor ADMIN or OWNER"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such lesson in the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteLesson(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID lessonId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_DELETE_REQUEST,
                user.getId(), lessonId, organizationId);
        lessonService.delete(user, organizationId, lessonId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.LESSON_DELETE_COMPLETED,
                lessonId, organizationId);
        return ResponseUtil.ok(LessonControllerResponseConstant.RESPONSE_LESSON_DELETED);
    }
}
