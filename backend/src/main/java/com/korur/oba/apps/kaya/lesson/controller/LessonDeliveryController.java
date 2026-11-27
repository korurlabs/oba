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
import com.korur.oba.apps.kaya.lesson.helper.LessonDeliveryBulkhead;
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
    private final LessonDeliveryBulkhead lessonDeliveryBulkhead;
    private final LoggingService loggingService;

    /**
     * Answers 304 when the CLI's If-None-Match already names the current lessons, so an unchanged
     * session start downloads nothing.
     *
     * Paged, but every page carries the ETag of all applicable lessons: the CLI sends it in
     * If-None-Match with page 1 to skip every page while nothing changed, and with etag on each later
     * page, which answers 409 KAY_VAL_017 once the lessons changed after page 1. The CLI then starts
     * again at page 1, so it never stores pages of two states under one ETag. A request without
     * paging gets page 1 of 100, the newest 100, in the shape of the unpaged list plus pagination,
     * under an ETag of the unpaged form: a CLI that stored only those 100 must not get 304 once it
     * reads every page.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the lessons of a checkout",
            description = "Bearer machine token. The titles of the lessons of the checkout's repository plus the "
                    + "organization-wide ones, newest first; an unknown or unreadable remote gives the organization-wide "
                    + "ones only. Paged, 100 by default and 100 at most; pagination.last marks the last page. The ETag "
                    + "covers every applicable lesson, on every page the same, and ignores since and the page; send it "
                    + "back in If-None-Match with page 1 to get 304 while nothing changed, and as etag with every later "
                    + "page to get 409 KAY_VAL_017 once the lessons changed after page 1. A request without page gets "
                    + "page 1 under an ETag of the unpaged form v2u-, which matches no paged request; a request with "
                    + "page gets the paged form v2-, the only form etag accepts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "One page of the lessons, possibly empty"),
            @ApiResponse(responseCode = "304", description = "If-None-Match matches the current ETag; no body"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token missing, unknown, revoked or expired, "
                    + "or machine retired"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002: the user is not ACTIVE; ORG_SEC_001: not a "
                    + "member, or the membership is not ACTIVE; ORG_SEC_005: the organization is not ACTIVE; ORG_SEC_003: "
                    + "the user is a GUEST; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "409", description = "KAY_VAL_017: etag no longer names the lessons; read them "
                    + "again from page 1"),
            @ApiResponse(responseCode = "422", description = "KAY_VAL_002: repository archived; GEN_VAL_001: remoteUrl "
                    + "missing or too long, since unreadable, page or amount out of range, etag malformed or missing "
                    + "from page 2 on"),
            @ApiResponse(responseCode = "429", description = "KAY_SEC_004: more than 600 lesson reads (pages, 304s "
                    + "and searches) of the token in a minute (errorValues.limit 600, errorValues.policy "
                    + "token-reads-per-minute), more than 1200 of its user, all tokens and the web app together (errorValues.limit "
                    + "1200, errorValues.policy user-reads-per-minute), more than 3000 of its organization, all tokens "
                    + "and the web app together (errorValues.limit 3000, errorValues.policy organization-reads-per-minute), or 2 lesson reads already running on this instance, of its user in "
                    + "all their organizations together (errorValues.limit 2, errorValues.policy user-reads-at-once) "
                    + "or of its organization (errorValues.limit 2, errorValues.policy organization-reads-at-once); "
                    + "Retry-After gives the seconds until the budget's window ends, or 1 for reads that run at once"),
            @ApiResponse(responseCode = "500", description = "Internal server error"),
            @ApiResponse(responseCode = "503", description = "GEN_UPS_004: the read ran past its time limit of 5 "
                    + "seconds, with Retry-After 5; GEN_UPS_005: this instance already runs 4 lesson reads, with "
                    + "Retry-After 1; try again later")
    })
    public ResponseEntity<CustomResponse<List<LessonDeliveryResponse>>> getLessons(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @ModelAttribute @ParameterObject LessonDeliveryQueryRequest request,
            @Parameter(hidden = true) WebRequest webRequest) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_LIST_REQUEST,
                principal.machineId());
        // The request is checked and charged first and the cap on reads at the same time taken next,
        // both outside the transaction of the read: a refused request never takes a pooled connection,
        // and one refused by a check or a budget never takes a place among the reads either.
        lessonQueryService.admitListForCheckout(principal, request);
        LessonDelivery delivery = lessonDeliveryBulkhead.run(principal,
                () -> lessonQueryService.listForCheckout(principal, request));
        // checkNotModified sets the ETag header and, on a match, the 304 status; a null return
        // then sends no body.
        if (webRequest.checkNotModified(delivery.etag())) {
            loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_NOT_MODIFIED,
                    principal.machineId());
            return null;
        }
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_LIST_COMPLETED,
                delivery.lessons().getNumberOfElements(), delivery.lessons().getNumber() + 1,
                delivery.lessons().getTotalPages(), principal.machineId());
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
            @ApiResponse(responseCode = "429", description = "KAY_SEC_004: more than 60 lessons added by the token in a "
                    + "minute (errorValues.limit 60, errorValues.policy token-lessons-per-minute); Retry-After gives the "
                    + "seconds until the window ends"),
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
            @ApiResponse(responseCode = "429", description = "KAY_SEC_004: more than 600 lesson reads (pages, 304s "
                    + "and searches) of the token in a minute (errorValues.limit 600, errorValues.policy "
                    + "token-reads-per-minute), more than 1200 of its user, all tokens and the web app together (errorValues.limit "
                    + "1200, errorValues.policy user-reads-per-minute), more than 3000 of its organization, all tokens "
                    + "and the web app together (errorValues.limit 3000, errorValues.policy organization-reads-per-minute), or 2 lesson reads already running on this instance, of its user in "
                    + "all their organizations together (errorValues.limit 2, errorValues.policy user-reads-at-once) "
                    + "or of its organization (errorValues.limit 2, errorValues.policy organization-reads-at-once); "
                    + "Retry-After gives the seconds until the budget's window ends, or 1 for reads that run at once"),
            @ApiResponse(responseCode = "500", description = "Internal server error"),
            @ApiResponse(responseCode = "503", description = "GEN_UPS_004: the read ran past its time limit of 5 "
                    + "seconds, with Retry-After 5; GEN_UPS_005: this instance already runs 4 lesson reads, with "
                    + "Retry-After 1; try again later")
    })
    public ResponseEntity<CustomResponse<List<LessonDeliverySearchResponse>>> searchLessons(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @ModelAttribute @ParameterObject LessonDeliverySearchRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LessonControllerLogConstant.DELIVERY_SEARCH_REQUEST,
                principal.machineId());
        lessonQueryService.admitSearchForCheckout(principal);
        List<LessonDeliverySearchResponse> response = lessonDeliveryBulkhead.run(principal,
                () -> lessonQueryService.searchForCheckout(principal, request.getRemoteUrl(), request.getSearch()));
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
