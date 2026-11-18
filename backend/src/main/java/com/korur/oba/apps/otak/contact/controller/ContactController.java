package com.korur.oba.apps.otak.contact.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.constant.controller.ContactControllerLogConstant;
import com.korur.oba.apps.otak.contact.constant.controller.ContactControllerResponseConstant;
import com.korur.oba.apps.otak.contact.dto.request.ContactCreateRequest;
import com.korur.oba.apps.otak.contact.dto.request.ContactUpdateRequest;
import com.korur.oba.apps.otak.contact.dto.response.ContactDetailResponse;
import com.korur.oba.apps.otak.contact.dto.response.ContactListResponse;
import com.korur.oba.apps.otak.contact.service.ContactQueryService;
import com.korur.oba.apps.otak.contact.service.ContactService;
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
 * REST controller for the contacts of a relation, under /otak/relations/{relationId}/contacts:
 * the people to talk to at a company ({@link com.korur.oba.apps.otak.contact.enums.ContactKind#PERSON})
 * and the company's general details such as an info@ inbox
 * ({@link com.korur.oba.apps.otak.contact.enums.ContactKind#BUSINESS}).
 *
 * Access: every endpoint needs {@link OrganizationMemberRole#MEMBER} or higher in the
 * organization named by the X-Organization-ID header, checked by
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} through
 * {@link RequiredUserAccess} before the method runs; every member of the organization reads
 * and changes contacts, a guest does not. The header's organization is passed to the
 * services, which scope every read and write to it, so a relation or contact of another
 * organization answers not found.
 *
 * The controller holds no rule of its own: Bean Validation checks the request bodies
 * (@Valid, 422 GEN_VAL_001), reads go to {@link ContactQueryService} and writes to
 * {@link ContactService}, and the result is wrapped by {@link ResponseUtil} in a
 * {@link CustomResponse}. Each call is traced with {@link LoggingService#observe} before and
 * after the service call.
 *
 * Clients: the Otak web app and the oba CLI (oba otak contact).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/relations/{relationId}/contacts")
@Tag(name = "Otak contacts", description = "Contacts of a relation")
public class ContactController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ContactController.class);

    private final ContactService contactService;
    private final ContactQueryService contactQueryService;
    private final LoggingService loggingService;

    /**
     * GET /otak/relations/{relationId}/contacts: lists every contact of a relation, the main
     * person and main business contact first, then the others by name.
     *
     * Delegates to {@link ContactQueryService#list}; the order is fixed in the service and
     * not taken from the request.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; the access
     *                       check has already run, the method itself does not read it
     * @param organizationId the organization from the X-Organization-ID header
     * @param relationId     the relation identifier
     * @return 200 with the contacts, empty when the relation has none
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001, 404) when the organization has no relation with the id
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List contacts", description = "Every contact of one relation of the organization: the "
            + "main person contact first, then the main business contact, then by name.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<ContactListResponse>>> listContacts(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID relationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ContactControllerLogConstant.CONTACT_LIST_REQUEST);

        List<ContactListResponse> response = contactQueryService.list(organizationId, relationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ContactControllerLogConstant.CONTACT_LIST_COMPLETED);

        return ResponseUtil.ok(ContactControllerResponseConstant.RESPONSE_CONTACT_LIST_RETRIEVED, response);
    }

    /**
     * POST /otak/relations/{relationId}/contacts: adds a person or business contact to a
     * relation. The first contact of its kind becomes main of that kind; a later one becomes
     * main when the flag of its kind is true, and the previous main contact is unmarked.
     *
     * Delegates to {@link ContactService#create} after Bean Validation of
     * {@link ContactCreateRequest}.
     *
     * @param user           the authenticated user, logged by the service as the creator
     * @param organizationId the organization from the X-Organization-ID header
     * @param relationId     the relation identifier
     * @param request        the contact create payload
     * @return 201 with the created contact
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001, 404) when the organization has no relation with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL}
     *         (OTK_VAL_005, 422) or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND}
     *         (OTK_VAL_013, 422)
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create contact", description = "Adds a person or business contact (kind) to one "
            + "relation of the organization. The first contact of a kind becomes the relation's main "
            + "contact of that kind; mainPersonContact true on a PERSON, or mainBusinessContact true on a BUSINESS, "
            + "makes a later one main and unmarks the previous main contact of the kind.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001); an email type "
                    + "without an email address (OTK_VAL_005); a true main flag of the other kind (OTK_VAL_013)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ContactDetailResponse>> createContact(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID relationId,
            @RequestBody @Valid ContactCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ContactControllerLogConstant.CONTACT_CREATE_REQUEST);

        ContactDetailResponse response = contactService.create(organizationId, user, relationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ContactControllerLogConstant.CONTACT_CREATE_COMPLETED);

        return ResponseUtil.created(ContactControllerResponseConstant.RESPONSE_CONTACT_CREATED, response);
    }

    /**
     * PUT /otak/relations/{relationId}/contacts/{contactId}: replaces every field of a
     * contact except its source URL, the kind included. A full replace: null clears an
     * optional field and both main flags are required.
     *
     * Because the request replaces the whole contact, false on the current main contact of
     * an unchanged kind would leave the relation without a main contact of that kind, so it
     * is refused; the main contact changes only by marking another one. A kind change moves
     * the main flag of the old kind to the oldest remaining contact of that kind.
     *
     * Delegates to {@link ContactService#update} after Bean Validation of
     * {@link ContactUpdateRequest}.
     *
     * @param user           the authenticated user; the access check has already run, the
     *                       method itself does not read it
     * @param organizationId the organization from the X-Organization-ID header
     * @param relationId     the relation identifier
     * @param contactId      the contact identifier
     * @param request        the contact update payload
     * @return 200 with the updated contact
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001, 404) or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND}
     *         (OTK_NOT_002, 404)
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL}
     *         (OTK_VAL_005, 422),
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND}
     *         (OTK_VAL_013, 422) or
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionValidationError#VALIDATION}
     *         (GEN_VAL_001, 422) for false on the current main contact of an unchanged kind
     */
    @PutMapping("/{contactId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update contact",
            description = "Replaces every field of a contact except its source URL, the kind included; null "
                    + "clears an optional field. mainPersonContact true on a PERSON, or mainBusinessContact true on a "
                    + "BUSINESS, makes the contact main of its kind and unmarks the previous one; when the kind is "
                    + "unchanged, false in the flag of its kind on the current main contact of that kind is refused. "
                    + "A kind change unmarks the contact as main of its old kind, makes the oldest remaining "
                    + "contact of the old kind main, and makes the contact main of its new kind when it is the first "
                    + "of that kind or its flag is true.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001); contact not found on this relation (OTK_NOT_002)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body, or false on the current main "
                    + "contact of an unchanged kind (GEN_VAL_001); an email type without an email address "
                    + "(OTK_VAL_005); a true main flag of the other kind (OTK_VAL_013)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ContactDetailResponse>> updateContact(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID relationId,
            @PathVariable UUID contactId,
            @RequestBody @Valid ContactUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ContactControllerLogConstant.CONTACT_UPDATE_REQUEST);

        ContactDetailResponse response = contactService.update(organizationId, relationId, contactId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ContactControllerLogConstant.CONTACT_UPDATE_COMPLETED);

        return ResponseUtil.ok(ContactControllerResponseConstant.RESPONSE_CONTACT_UPDATED, response);
    }

    /**
     * DELETE /otak/relations/{relationId}/contacts/{contactId}: removes a contact of a
     * relation. When it was the main contact of its kind, the oldest remaining contact of
     * that kind becomes main.
     *
     * Delegates to {@link ContactService#delete}.
     *
     * @param user           the authenticated user; the access check has already run, the
     *                       method itself does not read it
     * @param organizationId the organization from the X-Organization-ID header
     * @param relationId     the relation identifier
     * @param contactId      the contact identifier
     * @return 200 without data
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001, 404) or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND}
     *         (OTK_NOT_002, 404)
     */
    @DeleteMapping("/{contactId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete contact", description = "Removes a contact of one relation of the organization. "
            + "When it was the main contact of its kind, the oldest remaining contact of that kind becomes main.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001); contact not found on this relation (OTK_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteContact(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID relationId,
            @PathVariable UUID contactId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ContactControllerLogConstant.CONTACT_DELETE_REQUEST);

        contactService.delete(organizationId, relationId, contactId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ContactControllerLogConstant.CONTACT_DELETE_COMPLETED);

        return ResponseUtil.ok(ContactControllerResponseConstant.RESPONSE_CONTACT_DELETED);
    }
}
