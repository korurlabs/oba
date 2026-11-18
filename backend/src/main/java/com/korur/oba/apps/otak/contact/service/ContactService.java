package com.korur.oba.apps.otak.contact.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.dto.request.ContactCreateRequest;
import com.korur.oba.apps.otak.contact.dto.request.ContactUpdateRequest;
import com.korur.oba.apps.otak.contact.dto.response.ContactDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write side of the contacts of a relation: create, full replace and delete. The read side
 * is {@link ContactQueryService}; the implementation is
 * {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl}.
 *
 * Organization scoping: the caller's role in the organization is checked before these
 * methods run, by {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor}
 * on the endpoints of {@link com.korur.oba.apps.otak.contact.controller.ContactController}.
 * Every method then works within that organization only, so a relation or a contact of
 * another organization answers the same not found as a missing one and its id reveals
 * nothing to another tenant.
 *
 * Person versus business contacts: a contact is a
 * {@link com.korur.oba.apps.otak.contact.enums.ContactKind#PERSON} (someone to talk to) or a
 * {@link com.korur.oba.apps.otak.contact.enums.ContactKind#BUSINESS} contact (the company's
 * general details, such as an info@ inbox), and each kind has its own main flag. A relation
 * with contacts of a kind always has exactly one main contact of that kind: the first one of
 * the kind becomes main, marking another one moves the flag, and the flag never falls away
 * while contacts of the kind remain. The database backs the rule with partial unique indexes
 * and check constraints on otak.contacts.
 *
 * Called only by {@link com.korur.oba.apps.otak.contact.controller.ContactController}.
 */
public interface ContactService {

    /**
     * Adds a contact to a relation of the organization.
     *
     * The first contact of its kind on the relation becomes main of that kind whatever the
     * request says, so a relation with contacts of a kind always has a main one. A later
     * contact becomes main when the main flag of its own kind is true, and the previous main
     * contact of the kind is unmarked in the same transaction. A true flag of the other kind
     * is refused instead of ignored, so the caller learns its request did not do what it
     * asked.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.controller.ContactController#createContact}.
     *
     * @param organizationId the organization that owns the relation
     * @param user           the authenticated user, recorded in the log as the creator
     * @param relationId     the relation identifier
     * @param request        the contact create payload, already checked by Bean Validation
     * @return the created contact, with its id, main flags and timestamps
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) when the organization has no relation with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL}
     *         (OTK_VAL_005) when an email type comes without an email address, or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND}
     *         (OTK_VAL_013) when a true main flag names the other kind
     */
    ContactDetailResponse create(UUID organizationId, User user, UUID relationId, ContactCreateRequest request);

    /**
     * Replaces every field of a contact, its kind included, except the source URL, which
     * records where the contact was found and is set on create only.
     *
     * The update is a full replace (PUT): the request carries both main flags, and the
     * stored contact takes what they say. A true flag of the contact's kind makes it main of
     * that kind and unmarks the previous main contact in the same transaction. When the kind
     * is unchanged, false on the current main contact of the kind is refused: accepting it
     * would leave the relation without a main contact of that kind, so the main contact
     * changes only by marking another one. A kind change moves the main flag instead: the
     * contact gives up the main flag of its old kind, the oldest remaining contact of the
     * old kind becomes main of it, and the contact becomes main of its new kind when it is
     * the first of that kind or its flag is true.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.controller.ContactController#updateContact}.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @param contactId      the contact identifier
     * @param request        the contact update payload, already checked by Bean Validation
     * @return the updated contact, with its new last-change time
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) when the organization has no relation with the id, or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND}
     *         (OTK_NOT_002) when the relation has no contact with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL}
     *         (OTK_VAL_005) when an email type comes without an email address,
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND}
     *         (OTK_VAL_013) when a true main flag names the other kind, or
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionValidationError#VALIDATION}
     *         (GEN_VAL_001) on the main flag field when the kind is unchanged and the flag is
     *         false on the current main contact of the kind
     */
    ContactDetailResponse update(UUID organizationId, UUID relationId, UUID contactId, ContactUpdateRequest request);

    /**
     * Deletes a contact of a relation of the organization.
     *
     * When the contact was the main contact of its kind, the oldest remaining contact of
     * that kind becomes main, so a relation with contacts of a kind always has one; a
     * relation left without contacts of the kind has no main contact of it. The main contact
     * of the other kind is never touched.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.controller.ContactController#deleteContact}.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @param contactId      the contact identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) when the organization has no relation with the id, or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND}
     *         (OTK_NOT_002) when the relation has no contact with the id
     */
    void delete(UUID organizationId, UUID relationId, UUID contactId);
}
