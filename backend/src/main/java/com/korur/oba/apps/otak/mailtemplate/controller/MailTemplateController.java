package com.korur.oba.apps.otak.mailtemplate.controller;

// 1. Framework-level
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
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailtemplate.constant.controller.MailTemplateControllerLogConstant;
import com.korur.oba.apps.otak.mailtemplate.constant.controller.MailTemplateControllerResponseConstant;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateCreateRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateQueryRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateUpdateRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateDetailResponse;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateListResponse;
import com.korur.oba.apps.otak.mailtemplate.service.MailTemplateQueryService;
import com.korur.oba.apps.otak.mailtemplate.service.MailTemplateService;
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

/**
 * REST controller under /otak/mail-templates for the mail templates of an organization:
 * reusable mails (name, language, subject, body) that the mail of an outreach is copied
 * from.
 *
 * Access. Every endpoint carries {@link RequiredUserAccess} with its minimum
 * {@link OrganizationMemberRole}, and
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} checks it
 * against the caller's membership in the organization of the X-Organization-ID header
 * before the method runs. Every member reads mail templates
 * ({@link OrganizationMemberRole#MEMBER}); only an admin or owner creates, changes or
 * deletes one ({@link OrganizationMemberRole#ADMIN}), because mail templates speak for the
 * organization. Every lookup is scoped to the checked organization, so a mail template of
 * another organization answers 404 like a missing one.
 *
 * Each method only logs, delegates to {@link MailTemplateService} for writes or
 * {@link MailTemplateQueryService} for reads, and wraps the result in a
 * {@link CustomResponse} through {@link ResponseUtil}. Errors are thrown by the services and
 * turned into the error response by
 * {@link com.korur.oba.dojo.exception.handler.mvc_layer.GlobalExceptionHandler}.
 *
 * Clients: the template pages of the Otak web app and the oba CLI.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/mail-templates")
@Tag(name = "Otak Mail Templates", description = "Reusable mail templates of an organization")
public class MailTemplateController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailTemplateController.class);

    private final MailTemplateService mailTemplateService;
    private final MailTemplateQueryService mailTemplateQueryService;
    private final LoggingService loggingService;

    /**
     * Lists one page of the organization's mail templates, sorted by name, then language.
     *
     * Delegates to {@link MailTemplateQueryService#list}. The order is fixed, so sortBy and
     * sortDirection are ignored; the name search and the language are optional filters. The
     * items carry no body; the detail does.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; unused here, the
     *                       access check already ran
     * @param organizationId the organization from the X-Organization-ID header
     * @param request        the one-based page, the page size and the optional search and language
     * @return 200 with the page of {@link MailTemplateListResponse} items and the paging data
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List mail templates",
            description = "One page of the organization's mail templates by name, then language, with an optional "
                    + "search on name and an optional language filter. The order is fixed; sortBy and "
                    + "sortDirection are ignored.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "422", description = "Invalid paging, search or language (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<MailTemplateListResponse>>> listMailTemplates(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject MailTemplateQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_LIST_REQUEST);

        Page<MailTemplateListResponse> response = mailTemplateQueryService.list(organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_LIST_COMPLETED);

        return ResponseUtil.ok(MailTemplateControllerResponseConstant.RESPONSE_MAIL_TEMPLATE_LIST_RETRIEVED, response);
    }

    /**
     * Creates a mail template of the organization, complete and usable at once.
     *
     * Delegates to {@link MailTemplateService#create}. The name must be free in the language
     * within the organization, case ignored.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}, recorded in the log
     * @param organizationId the organization from the X-Organization-ID header, which owns the new mail template
     * @param request        the name, language, subject and body
     * @return 201 with the created {@link MailTemplateDetailResponse}
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_MAIL_TEMPLATE_NAME_TAKEN}
     *         (OTK_STA_014) when another mail template of the organization has the name in the language
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create mail template",
            description = "Creates a mail template of the organization; name, language, subject and body are all "
                    + "required. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "409", description = "Another mail template of the organization has this "
                    + "name in this language, case ignored (OTK_STA_014)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body, among them a missing or blank "
                    + "subject or body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MailTemplateDetailResponse>> createMailTemplate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid MailTemplateCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_CREATE_REQUEST);

        MailTemplateDetailResponse response = mailTemplateService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_CREATE_COMPLETED);

        return ResponseUtil.created(MailTemplateControllerResponseConstant.RESPONSE_MAIL_TEMPLATE_CREATED, response);
    }

    /**
     * Reads every field of one mail template of the organization.
     *
     * Delegates to {@link MailTemplateQueryService#get}.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; unused here, the
     *                       access check already ran
     * @param organizationId the organization from the X-Organization-ID header
     * @param mailTemplateId the mail template to read
     * @return 200 with the {@link MailTemplateDetailResponse}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         (OTK_NOT_010) when the organization has no mail template with the id
     */
    @GetMapping("/{mailTemplateId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get mail template", description = "Every field of one mail template of the organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Mail template not found or of another organization "
                    + "(OTK_NOT_010)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MailTemplateDetailResponse>> getMailTemplate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID mailTemplateId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_GET_REQUEST);

        MailTemplateDetailResponse response = mailTemplateQueryService.get(organizationId, mailTemplateId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_GET_COMPLETED);

        return ResponseUtil.ok(MailTemplateControllerResponseConstant.RESPONSE_MAIL_TEMPLATE_RETRIEVED, response);
    }

    /**
     * Replaces the name, language, subject and body of a mail template.
     *
     * Delegates to {@link MailTemplateService#update}. A full replace, as every PUT of this
     * API. Mails already copied from the mail template keep their own content.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; unused here, the
     *                       access check already ran
     * @param organizationId the organization from the X-Organization-ID header
     * @param mailTemplateId the mail template to update
     * @param request        the new name, language, subject and body
     * @return 200 with the updated {@link MailTemplateDetailResponse}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         (OTK_NOT_010) when the organization has no mail template with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_MAIL_TEMPLATE_NAME_TAKEN}
     *         (OTK_STA_014) when another mail template of the organization has the name in the language
     */
    @PutMapping("/{mailTemplateId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update mail template",
            description = "Replaces the name, language, subject and body of a mail template; all are required. "
                    + "Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Mail template not found or of another organization "
                    + "(OTK_NOT_010)"),
            @ApiResponse(responseCode = "409", description = "Another mail template of the organization has this "
                    + "name in this language, case ignored (OTK_STA_014)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body, among them a missing or blank "
                    + "subject or body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MailTemplateDetailResponse>> updateMailTemplate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID mailTemplateId,
            @RequestBody @Valid MailTemplateUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_UPDATE_REQUEST);

        MailTemplateDetailResponse response = mailTemplateService.update(organizationId, mailTemplateId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_UPDATE_COMPLETED);

        return ResponseUtil.ok(MailTemplateControllerResponseConstant.RESPONSE_MAIL_TEMPLATE_UPDATED, response);
    }

    /**
     * Deletes a mail template.
     *
     * Delegates to {@link MailTemplateService#delete}. Mails copied from the mail template
     * stay with their own content; only their link to it is cleared.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; unused here, the
     *                       access check already ran
     * @param organizationId the organization from the X-Organization-ID header
     * @param mailTemplateId the mail template to delete
     * @return 200 with no data
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         (OTK_NOT_010) when the organization has no mail template with the id
     */
    @DeleteMapping("/{mailTemplateId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete mail template",
            description = "Removes a mail template. Mails copied from it stay, without the link. Organization "
                    + "admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Mail template not found or of another organization "
                    + "(OTK_NOT_010)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteMailTemplate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID mailTemplateId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_DELETE_REQUEST);

        mailTemplateService.delete(organizationId, mailTemplateId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailTemplateControllerLogConstant.MAIL_TEMPLATE_DELETE_COMPLETED);

        return ResponseUtil.ok(MailTemplateControllerResponseConstant.RESPONSE_MAIL_TEMPLATE_DELETED);
    }
}
