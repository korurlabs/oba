package com.korur.oba.apps.otak.mailsender.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailsender.constant.controller.MailSenderControllerLogConstant;
import com.korur.oba.apps.otak.mailsender.constant.controller.MailSenderControllerResponseConstant;
import com.korur.oba.apps.otak.mailsender.dto.request.MailSenderCreateRequest;
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.apps.otak.mailsender.service.MailSenderQueryService;
import com.korur.oba.apps.otak.mailsender.service.MailSenderService;
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
 * REST controller for the mail senders of an organization: the mailboxes of the Microsoft
 * 365 tenant that the organization may send its outreach mail from. Every member reads
 * them; only an admin adds or deletes one, because a sender speaks for the organization.
 * A sender is never changed: a wrong address is deleted and added again.
 *
 * Every endpoint takes the organization from the X-Organization-ID header; the access
 * interceptor checks the caller's role there against {@link RequiredUserAccess} before the
 * method runs, and the services read only that organization's senders. Responses are
 * wrapped in {@link CustomResponse} through {@link ResponseUtil}.
 *
 * Writes go to {@link MailSenderService}, reads to {@link MailSenderQueryService}. The
 * senders are used by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#sendOutreach}.
 *
 * @see com.korur.oba.apps.otak.mailsender.service.impl.MailSenderServiceImpl
 * @see com.korur.oba.apps.otak.mailsender.service.impl.MailSenderQueryServiceImpl
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/mail-senders")
@Tag(name = "Otak Mail Senders", description = "Sender mailboxes of an organization")
public class MailSenderController {

    /**
     * Log prefix of this controller, derived from the class name.
     */
    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailSenderController.class);

    /**
     * Adds and deletes senders.
     */
    private final MailSenderService mailSenderService;

    /**
     * Lists senders.
     */
    private final MailSenderQueryService mailSenderQueryService;

    /**
     * Writes the request and completion observe lines.
     */
    private final LoggingService loggingService;

    /**
     * Lists every mail sender of the organization by mail address.
     *
     * Open to every member, not only admins, because whoever prepares an outreach picks the
     * sender it goes out from. GUEST is refused. Not paged; calls
     * {@link MailSenderQueryService#list}.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; not used by the
     *                       read, which needs only the organization
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @return the senders of the organization, ordered by mail address
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List mail senders",
            description = "Every mail sender of the organization, ordered by mail address. Not paged.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<MailSenderListResponse>>> listMailSenders(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MailSenderControllerLogConstant.MAIL_SENDER_LIST_REQUEST);

        List<MailSenderListResponse> response = mailSenderQueryService.list(organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailSenderControllerLogConstant.MAIL_SENDER_LIST_COMPLETED);

        return ResponseUtil.ok(MailSenderControllerResponseConstant.RESPONSE_MAIL_SENDER_LIST_RETRIEVED, response);
    }

    /**
     * Adds a mail sender after checking its mailbox in the Microsoft 365 tenant, looked up
     * as the requested type.
     *
     * Admins and owners only, because a sender lets the organization's outreach speak from
     * that mailbox. Calls {@link MailSenderService#create}, which refuses a duplicate
     * address before it asks Microsoft Graph whether the mailbox exists.
     *
     * @param user           the authenticated user, resolved by {@link GetUser} and stored as addedBy
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @param request        the mail address and the kind of mailbox
     * @return the added sender, with HTTP 201
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_MAIL_SENDER_TAKEN} if the address is taken in the organization
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_MAILBOX_NOT_IN_TENANT}, {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ACCOUNT_WITHOUT_MAILBOX}
     *         or {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_SHARED_MAILBOX_NOT_FOUND} if the tenant has no such mailbox
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException
     *         {@link com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION} if Microsoft Graph cannot answer
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Add mail sender",
            description = "Adds a mail sender of the organization. The address must be a mailbox of the Microsoft "
                    + "365 tenant: PERSONAL is looked up by user principal name, SHARED by the mail address of a "
                    + "member account. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "409", description = "Another sender of the organization has this mail "
                    + "address, case ignored (OTK_STA_008)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body, among them a missing type or a "
                    + "blank or malformed mail address (GEN_VAL_001); for PERSONAL, no account with this address in "
                    + "the tenant (OTK_VAL_002) or an account with this address but no mailbox (OTK_VAL_003); for "
                    + "SHARED, no member account with this mail address in the tenant (OTK_VAL_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error"),
            @ApiResponse(responseCode = "502", description = "Microsoft Graph could not be reached (NTF_UPS_001)")
    })
    public ResponseEntity<CustomResponse<MailSenderListResponse>> createMailSender(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid MailSenderCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailSenderControllerLogConstant.MAIL_SENDER_CREATE_REQUEST);

        MailSenderListResponse response = mailSenderService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailSenderControllerLogConstant.MAIL_SENDER_CREATE_COMPLETED);

        return ResponseUtil.created(MailSenderControllerResponseConstant.RESPONSE_MAIL_SENDER_CREATED, response);
    }

    /**
     * Deletes a mail sender.
     *
     * Admins and owners only. Calls {@link MailSenderService#delete}. Outreaches sent from
     * the sender stay SENT and lose only their sender reference.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; not used by the delete
     * @param organizationId the organization the caller acts in, from the X-Organization-ID header
     * @param mailSenderId   the mail sender identifier
     * @return an empty success response
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_SENDER_FOUND} if the organization has no such sender
     */
    @DeleteMapping("/{mailSenderId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete mail sender",
            description = "Removes a mail sender. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Mail sender not found or of another organization "
                    + "(OTK_NOT_006)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteMailSender(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID mailSenderId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailSenderControllerLogConstant.MAIL_SENDER_DELETE_REQUEST);

        mailSenderService.delete(organizationId, mailSenderId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                MailSenderControllerLogConstant.MAIL_SENDER_DELETE_COMPLETED);

        return ResponseUtil.ok(MailSenderControllerResponseConstant.RESPONSE_MAIL_SENDER_DELETED);
    }
}
