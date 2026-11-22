package com.korur.oba.apps.otak.outreach.controller;

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
import com.korur.oba.apps.otak.outreach.constant.controller.OutreachControllerLogConstant;
import com.korur.oba.apps.otak.outreach.constant.controller.OutreachControllerResponseConstant;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachCreateRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachQueryRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachSendRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachUpdateRequest;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachDetailResponse;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachListResponse;
import com.korur.oba.apps.otak.outreach.service.OutreachQueryService;
import com.korur.oba.apps.otak.outreach.service.OutreachService;
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
 * REST controller for the outreaches of an organization: reach-outs to one contact of a
 * relation, each with its own mail. The files a mail carries are attachments with the mail
 * as parent, under /storage/attachments, where only an admin writes them and only while the
 * outreach is not sent. Every member reads
 * them; only an admin creates, changes, sends or deletes one, because they speak for the
 * organization. A sent outreach no longer changes.
 *
 * Organization scoping: every endpoint takes the organization from the X-Organization-ID
 * header. The access interceptor checks, before the method runs, that the user is an
 * active member of that organization with at least the role in {@link RequiredUserAccess},
 * so the services trust the organization id and only scope their reads by it.
 *
 * The controller holds no logic: it logs the request, calls {@link OutreachService} for a
 * write or {@link OutreachQueryService} for a read, and wraps the result in a
 * {@link CustomResponse} through {@link ResponseUtil}. Errors are thrown by the services
 * and their validators and turned into the HTTP answer by the global exception handler;
 * the ApiResponses of each method list them with their codes.
 *
 * @see OutreachService
 * @see OutreachQueryService
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/outreaches")
@Tag(name = "Otak Outreaches", description = "Reach-outs of an organization to contacts of its relations")
public class OutreachController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OutreachController.class);

    private final OutreachService outreachService;
    private final OutreachQueryService outreachQueryService;
    private final LoggingService loggingService;

    /**
     * Lists one page of the outreaches, newest first. Any member may read; a GUEST may not,
     * because outreaches name the organization's contacts.
     *
     * Calls {@link OutreachQueryService#list}. The order is fixed by the service, so the
     * sort fields of the request are ignored.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @param request        the one-based page, the page size and the optional relation and status filters
     * @return the page of outreaches with its paging data
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND} if the
     *         relation filter names a relation the organization does not have
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List outreaches",
            description = "One page of the organization's outreaches, newest first, with optional relation and "
                    + "status filters. The order is fixed; sortBy and sortDirection are ignored.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid paging, relation or status (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<OutreachListResponse>>> listOutreaches(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject OutreachQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_LIST_REQUEST);

        Page<OutreachListResponse> response = outreachQueryService.list(organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_LIST_COMPLETED);

        return ResponseUtil.ok(OutreachControllerResponseConstant.RESPONSE_OUTREACH_LIST_RETRIEVED, response);
    }

    /**
     * Creates a DRAFT outreach to a contact of a relation through the requested channel. A
     * MAIL outreach gets its mail copied from a mail template or written by hand, and a copy of
     * each requested document template's PDF as an attachment of that mail; a WHATSAPP or CALL
     * outreach takes neither. Admins and owners only, because
     * an outreach speaks for the organization.
     *
     * Calls {@link OutreachService#create}.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @param request        the relation, the contact, the title, the type, the mail source and the
     *                       document template ids
     * @return 201 with the created outreach
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         or {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionValidationError#VALIDATION} for a missing
     *         type, both or neither mail sources of a MAIL outreach, or any mail field of a WHATSAPP or
     *         CALL outreach, or
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#VAL_DOCUMENT_NOT_USABLE} for
     *         a document template without its PDF
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create outreach",
            description = "Creates a DRAFT outreach to a contact of a relation through the channel in type. "
                    + "A MAIL outreach has a mail that is a copy of a mail template or a subject and body written "
                    + "by hand, stored as a new mail of the relation, and a copy of the PDF of each document "
                    + "template in documentTemplateIds as an attachment of that mail. A WHATSAPP or CALL outreach "
                    + "has no mail and no document templates. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation, contact of the relation, mail template "
                    + "or document template not found (OTK_NOT_001, OTK_NOT_002, OTK_NOT_010, DOC_NOT_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body, among them a missing type, "
                    + "both or neither of mailTemplateId and mail for MAIL, or mailTemplateId, mail or "
                    + "documentTemplateIds for WHATSAPP or CALL (GEN_VAL_001); a document template without its PDF "
                    + "(DOC_VAL_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<OutreachDetailResponse>> createOutreach(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid OutreachCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_CREATE_REQUEST);

        OutreachDetailResponse response = outreachService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_CREATE_COMPLETED);

        return ResponseUtil.created(OutreachControllerResponseConstant.RESPONSE_OUTREACH_CREATED, response);
    }

    /**
     * Reads one outreach with its contact, sender address and mail. Any member may read. The
     * mail's attachments are listed through /storage/attachments with the mail's id.
     *
     * Calls {@link OutreachQueryService#get}.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @param outreachId     the outreach identifier
     * @return the outreach
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no such outreach
     */
    @GetMapping("/{outreachId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get outreach",
            description = "Every field of one outreach of the organization, with its mail. The mail's attachments "
                    + "are listed through GET /storage/attachments with entityType MAIL and the mail's id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Outreach not found or of another organization "
                    + "(OTK_NOT_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<OutreachDetailResponse>> getOutreach(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID outreachId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_GET_REQUEST);

        OutreachDetailResponse response = outreachQueryService.get(organizationId, outreachId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_GET_COMPLETED);

        return ResponseUtil.ok(OutreachControllerResponseConstant.RESPONSE_OUTREACH_RETRIEVED, response);
    }

    /**
     * Changes the contact, the title and, for MAIL, the mail of an outreach that was not sent.
     * Admins and owners only. A full update: every field of the request replaces the stored
     * one. The mail is required for MAIL and must be null for WHATSAPP and CALL.
     *
     * Calls {@link OutreachService#update}, which locks the outreach row so the change
     * cannot interleave with a send.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @param outreachId     the outreach identifier
     * @param request        the new contact, title and, for MAIL, mail
     * @return the updated outreach
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND}
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE} if
     *         the outreach is sent or being sent
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionValidationError#VALIDATION} if the mail
     *         does not fit the outreach's channel
     */
    @PutMapping("/{outreachId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update outreach",
            description = "Replaces the contact, the title and, for a MAIL outreach, the subject and body of its "
                    + "own mail; a WHATSAPP or CALL outreach takes no mail. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Outreach not found or of another organization "
                    + "(OTK_NOT_005), or contact not of the outreach's relation (OTK_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "Outreach sent or being sent (OTK_STA_011)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body, among them a "
                    + "blank subject or body, no mail for MAIL or a mail for WHATSAPP or CALL (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<OutreachDetailResponse>> updateOutreach(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID outreachId,
            @RequestBody @Valid OutreachUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_UPDATE_REQUEST);

        OutreachDetailResponse response = outreachService.update(organizationId, outreachId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_UPDATE_COMPLETED);

        return ResponseUtil.ok(OutreachControllerResponseConstant.RESPONSE_OUTREACH_UPDATED, response);
    }

    /**
     * Sends a DRAFT or FAILED MAIL outreach from a mail sender of the organization through
     * Microsoft Graph. Admins and owners only.
     *
     * Calls {@link OutreachService#send}, which claims the outreach as SENT before it calls
     * {@link com.korur.oba.connect.notification.client.MsGraphClient#sendEmail}, so two clicks
     * on send mail the contact once, and marks it FAILED when the mail cannot go out.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @param outreachId     the outreach identifier
     * @param request        the mail sender to send from
     * @return the outreach with its new status
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_SENDER_FOUND} or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND}
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE} if
     *         the outreach is sent or another send claimed it first
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ACTION_NOT_FOR_TYPE},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_WITHOUT_ADDRESS},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ATTACHMENTS_TOO_LARGE} or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_SHARED_MAILBOX_NOT_FOUND}
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException
     *         {@link com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION}
     *         if Microsoft Graph fails
     */
    @PostMapping("/{outreachId}/send")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Send outreach",
            description = "Sends a DRAFT or FAILED MAIL outreach through Microsoft Graph from a mail sender of the "
                    + "organization to the contact's current address: the mail as the body and every attachment "
                    + "of the mail in upload order, with its own file name and type. A SHARED sender sends from the shared mailbox, never "
                    + "from the caller. Success sets SENT; a Graph failure sets FAILED, which can be sent again. "
                    + "Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Outreach, mail sender or contact not found or of "
                    + "another organization (OTK_NOT_005, OTK_NOT_006, OTK_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "Outreach sent or being sent (OTK_STA_011)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001); not a MAIL "
                    + "outreach (OTK_VAL_014); contact without a mail address (OTK_VAL_007); attachments over 3 MB "
                    + "together (OTK_VAL_008); shared mailbox no longer in the tenant (OTK_VAL_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error, among them an attachment that "
                    + "could not be read (AZR_UPS_002); the attachments are read after the claim, so the outreach is "
                    + "FAILED"),
            @ApiResponse(responseCode = "502", description = "Microsoft Graph failed (NTF_UPS_001): the outreach is "
                    + "FAILED when sending the mail failed, unchanged when the shared mailbox lookup failed")
    })
    public ResponseEntity<CustomResponse<OutreachDetailResponse>> sendOutreach(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID outreachId,
            @RequestBody @Valid OutreachSendRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_SEND_REQUEST);

        OutreachDetailResponse response = outreachService.send(organizationId, outreachId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_SEND_COMPLETED);

        return ResponseUtil.ok(OutreachControllerResponseConstant.RESPONSE_OUTREACH_SENT, response);
    }

    /**
     * Records a DRAFT WHATSAPP or CALL outreach as done: it becomes SENT, with the moment of
     * this request and the contact's phone. Admins and owners only. Such an outreach happened
     * outside Oba, so nothing is sent; a MAIL outreach is sent through its own endpoint.
     *
     * Calls {@link OutreachService#complete}, which locks the outreach row.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @param outreachId     the outreach identifier
     * @return the outreach, now SENT
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND}
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE} if
     *         the outreach is SENT already
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ACTION_NOT_FOR_TYPE} for
     *         a MAIL outreach, or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_WITHOUT_PHONE} if
     *         the contact has no phone number
     */
    @PostMapping("/{outreachId}/complete")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Record outreach as done",
            description = "Records a DRAFT WHATSAPP or CALL outreach as done: status SENT, sentAt now and sentTo "
                    + "the contact's phone, no mail sender. Nothing is sent. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Outreach not found or of another organization "
                    + "(OTK_NOT_005), or the outreach has no contact or its contact is gone (OTK_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "Outreach already sent or done (OTK_STA_011)"),
            @ApiResponse(responseCode = "422", description = "A MAIL outreach (OTK_VAL_014), or a contact without "
                    + "a phone number (OTK_VAL_015)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<OutreachDetailResponse>> completeOutreach(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID outreachId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_COMPLETE_REQUEST);

        OutreachDetailResponse response = outreachService.complete(organizationId, outreachId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                OutreachControllerLogConstant.OUTREACH_COMPLETE_COMPLETED);

        return ResponseUtil.ok(OutreachControllerResponseConstant.RESPONSE_OUTREACH_COMPLETED, response);
    }

    /**
     * Deletes an outreach that was not sent, with its mail and the mail's attachments. Admins
     * and owners only. A sent outreach is the record of what the contact
     * received and can not be deleted.
     *
     * Calls {@link OutreachService#delete}.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @param outreachId     the outreach identifier
     * @return a response without data
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no such outreach
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE} if
     *         the outreach is sent or being sent
     */
    @DeleteMapping("/{outreachId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete outreach",
            description = "Removes an outreach that was not sent, together with its mail and the mail's "
                    + "attachments. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Outreach not found or of another organization "
                    + "(OTK_NOT_005)"),
            @ApiResponse(responseCode = "409", description = "Outreach sent or being sent (OTK_STA_011)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteOutreach(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID outreachId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_DELETE_REQUEST);

        outreachService.delete(organizationId, outreachId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OutreachControllerLogConstant.OUTREACH_DELETE_COMPLETED);

        return ResponseUtil.ok(OutreachControllerResponseConstant.RESPONSE_OUTREACH_DELETED);
    }
}
