package com.korur.oba.apps.otak.contact.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.contact.constant.service.ContactServiceLogConstant.*;

import com.korur.oba.apps.otak.contact.dto.request.ContactCreateRequest;
import com.korur.oba.apps.otak.contact.dto.request.ContactUpdateRequest;
import com.korur.oba.apps.otak.contact.dto.response.ContactDetailResponse;
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.enums.ContactKind;
import com.korur.oba.apps.otak.contact.mapper.ContactMapper;
import com.korur.oba.apps.otak.contact.repository.ContactRepository;
import com.korur.oba.apps.otak.contact.service.ContactService;
import com.korur.oba.apps.otak.contact.validator.ContactValidator;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of {@link ContactService}, the write side of the contacts of a relation.
 * The read side is {@link ContactQueryServiceImpl}.
 *
 * Every public method runs in one read-write transaction, so the unmark of the previous
 * main contact, the write of the contact and the promotion of a successor commit or roll
 * back together; a refused request writes nothing.
 *
 * Organization scoping: the parent relation is read first through
 * {@link RelationQueryService#getEntity}, which answers not found for a relation of another
 * organization; the relation is reached through its query service and not its repository
 * because it is another domain. The contact is then read by its id, the relation and the
 * organization together with {@link ContactValidator#ensureContactExists}, and every
 * repository write or bulk update names the organization again.
 *
 * Person versus business contacts and the main contact rule: a contact is a
 * {@link ContactKind#PERSON} or a {@link ContactKind#BUSINESS} contact, and each kind has its
 * own main flag, {@link Contact#mainPersonContact} and {@link Contact#mainBusinessContact}. A
 * relation with contacts of a kind has exactly one main contact of that kind. The partial
 * unique indexes contacts_relation_id_main_person_contact_uk and
 * contacts_relation_id_main_business_contact_uk on otak.contacts refuse a second main
 * contact of a kind, and check constraints refuse a flag on a contact of the other kind.
 * The unique indexes check every row write, not the end of the transaction, so the order of
 * the writes matters:
 *
 * - before a contact is marked main, the previous main contact of the kind is unmarked in
 *   the database by a bulk update ({@link ContactRepository#clearMainPersonContact} or
 *   {@link ContactRepository#clearMainBusinessContact}), so no flush ever writes two;
 * - when the main contact of a kind leaves the kind, by delete or by a kind change, its
 *   change is flushed first and only then is the oldest remaining contact of the kind
 *   promoted with {@link ContactRepository#findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc}.
 *
 * The request rules (email type needs an address, a main flag only for the own kind, the
 * main contact kept on an update) are checked by {@link ContactValidator} before any write.
 * Entities are mapped with {@link ContactMapper}, so no entity leaves the service.
 *
 * Called by {@link com.korur.oba.apps.otak.contact.controller.ContactController}.
 */
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ContactServiceImpl.class);

    private final ContactRepository contactRepository;
    private final ContactMapper contactMapper;
    private final ContactValidator contactValidator;
    private final RelationQueryService relationQueryService;
    private final LoggingService loggingService;

    /**
     * Adds a contact to a relation of the organization and decides whether it is main of
     * its kind.
     *
     * Steps:
     * - read the relation with {@link RelationQueryService#getEntity}, which scopes the call
     *   to the organization;
     * - check the email type with {@link ContactValidator#ensureEmailTypeHasEmail} and the
     *   main flags with {@link ContactValidator#ensureMainFlagFitsKind};
     * - ask {@link ContactRepository#existsByRelationIdAndOrganizationIdAndKind} whether the
     *   contact is the first of its kind: the first one is main whatever the request says;
     * - when a later contact is marked main, unmark the previous main contact of the kind
     *   with {@link #clearMain} before the insert;
     * - map the request with {@link ContactMapper#toEntity}, set the organization, relation,
     *   kind and main flag, save, and map the result with
     *   {@link ContactMapper#toDetailResponse}.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.controller.ContactController#createContact}.
     *
     * @param organizationId the organization that owns the relation
     * @param user           the authenticated user, logged as the creator
     * @param relationId     the relation identifier
     * @param request        the contact create payload
     * @return the created contact
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) when the organization has no relation with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL}
     *         (OTK_VAL_005) or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND}
     *         (OTK_VAL_013), both before any write
     */
    @Override
    @Transactional
    public ContactDetailResponse create(UUID organizationId, User user, UUID relationId,
                                        ContactCreateRequest request) {
        // Not found for a relation of another organization; the entity supplies the
        // organization and relation ids the new contact is written with.
        Relation relation = relationQueryService.getEntity(organizationId, relationId);
        // Both request rules are checked before anything is written, so a refused request
        // leaves the relation's contacts as they were.
        contactValidator.ensureEmailTypeHasEmail(request.email(), request.emailType());
        ContactKind kind = request.kind();
        contactValidator.ensureMainFlagFitsKind(kind, request.mainPersonContact(), request.mainBusinessContact());

        // The first contact of a kind is main of it whatever the flag says, so a relation
        // with contacts of a kind always has a main one.
        boolean firstOfKind = !contactRepository.existsByRelationIdAndOrganizationIdAndKind(relationId,
                organizationId, kind);
        boolean markedMain = isMarkedMain(kind, request.mainPersonContact(), request.mainBusinessContact());
        // A later contact that takes over the main flag: the previous main contact of the
        // kind is unmarked in the database before the insert, because the unique index
        // checks the insert row by row. The first of its kind has no predecessor to unmark.
        if (markedMain && !firstOfKind) {
            clearMain(organizationId, relationId, kind);
        }

        // The mapper leaves the organization, relation, kind and main flags to the service,
        // so they come from the checked relation and the rule above, never from raw input.
        Contact contact = contactMapper.toEntity(request);
        contact.setOrganizationId(relation.getOrganizationId());
        contact.setRelationId(relation.getId());
        contact.setKind(kind);
        setMain(contact, kind, firstOfKind || markedMain);
        // A plain save is enough: the previous main contact of the kind is already unmarked
        // in the database, so the insert can not meet a second main one.
        contact = contactRepository.save(contact);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CONTACT_CREATED,
                contact.getId(), relationId, organizationId, user.getId());

        return contactMapper.toDetailResponse(contact);
    }

    /**
     * Replaces every field of a contact except its source URL, and moves the main flags as
     * the full-replace request and a possible kind change require.
     *
     * Steps:
     * - read the relation with {@link RelationQueryService#getEntity} and the contact with
     *   {@link ContactValidator#ensureContactExists}, both scoped to the organization;
     * - check the email type with {@link ContactValidator#ensureEmailTypeHasEmail} and the
     *   main flags with {@link ContactValidator#ensureMainFlagFitsKind};
     * - when the kind is unchanged, refuse false on the current main contact of the kind with
     *   {@link ContactValidator#ensureMainContactKept}: the request replaces the whole
     *   contact, so false would ask for a relation without a main contact of the kind;
     * - when the kind changes, unmark the contact as main of its old kind; when it was main,
     *   flush that change and promote the oldest remaining contact of the old kind with
     *   {@link #promoteOldest}. The contact becomes main of its new kind when it is the first
     *   of that kind or its flag is true;
     * - when the contact becomes main, unmark the previous main contact of the kind with
     *   {@link #clearMain} and mark this one;
     * - write the other fields with {@link ContactMapper#updateEntity}, flush, and map the
     *   result with {@link ContactMapper#toDetailResponse}.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.controller.ContactController#updateContact}.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @param contactId      the contact identifier
     * @param request        the contact update payload
     * @return the updated contact, with its new last-change time
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND}
     *         (OTK_NOT_002)
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL}
     *         (OTK_VAL_005),
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND}
     *         (OTK_VAL_013) or
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionValidationError#VALIDATION}
     *         (GEN_VAL_001) for false on the current main contact of an unchanged kind; all
     *         before any write
     */
    @Override
    @Transactional
    public ContactDetailResponse update(UUID organizationId, UUID relationId, UUID contactId,
                                        ContactUpdateRequest request) {
        // Only the check is needed: a relation of another organization answers not found
        // before the contact is looked at.
        relationQueryService.getEntity(organizationId, relationId);
        // A contact of another relation or organization answers the same not found as a
        // missing one.
        Contact contact = contactValidator.ensureContactExists(organizationId, relationId, contactId);
        contactValidator.ensureEmailTypeHasEmail(request.email(), request.emailType());
        // The stored kind is kept aside: the main flag rules below compare it with the
        // requested kind, and the contact's kind is overwritten on a kind change.
        ContactKind oldKind = contact.getKind();
        ContactKind kind = request.kind();
        contactValidator.ensureMainFlagFitsKind(kind, request.mainPersonContact(), request.mainBusinessContact());
        if (kind == oldKind) {
            // Refused before any write: false on the main contact of the kind would leave the
            // relation without one, so the main contact changes only by marking another one.
            contactValidator.ensureMainContactKept(contact, request.mainPersonContact(),
                    request.mainBusinessContact());
        }

        boolean markedMain = isMarkedMain(kind, request.mainPersonContact(), request.mainBusinessContact());
        boolean becomesMain;
        if (kind != oldKind) {
            // Read before the kind is written, while the contact still counts for its old kind.
            boolean firstOfKind = !contactRepository.existsByRelationIdAndOrganizationIdAndKind(relationId,
                    organizationId, kind);
            boolean wasMain = isMain(contact, oldKind);
            // The contact leaves its old kind, and with it the old kind's main flag; the check
            // constraints refuse a flag of one kind on a contact of the other.
            contact.setKind(kind);
            setMain(contact, oldKind, false);
            if (wasMain) {
                // Flushed before the promotion, so the oldest of the old kind is another
                // contact and the unique index never sees two main contacts of the old kind.
                contactRepository.saveAndFlush(contact);
                promoteOldest(organizationId, relationId, oldKind);
            }
            // In its new kind the contact follows the create rule: main when it is the first
            // of the kind or when its flag asks for it.
            becomesMain = firstOfKind || markedMain;
        } else {
            // Same kind: only a contact that is not yet main can become main; true on the
            // current main contact changes nothing.
            becomesMain = markedMain && !isMain(contact, kind);
        }
        if (becomesMain) {
            // The previous main contact of the kind is unmarked in the database first, so the
            // flush below never writes two main contacts of the kind.
            clearMain(organizationId, relationId, kind);
            setMain(contact, kind, true);
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIN_CONTACT_CHANGED,
                    kind, contactId, relationId, organizationId);
        }

        // The mapper writes every other field, null clearing an optional one; it leaves the
        // kind, the main flags and the source URL alone, so the rules above stand.
        contactMapper.updateEntity(contact, request);
        // Flushed here so the response carries the new last-change time.
        contact = contactRepository.saveAndFlush(contact);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CONTACT_UPDATED, contactId, relationId, organizationId);

        return contactMapper.toDetailResponse(contact);
    }

    /**
     * Deletes a contact of a relation and, when it was the main contact of its kind, makes
     * the oldest remaining contact of that kind main.
     *
     * Steps: check the relation with {@link RelationQueryService#getEntity}, read the contact
     * with {@link ContactValidator#ensureContactExists}, note whether it was main of its kind,
     * delete and flush it, then call {@link #promoteOldest} when it was main.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.controller.ContactController#deleteContact}.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @param contactId      the contact identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND}
     *         (OTK_NOT_002)
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID relationId, UUID contactId) {
        // Both reads are scoped to the organization, so a foreign id answers not found.
        relationQueryService.getEntity(organizationId, relationId);
        Contact contact = contactValidator.ensureContactExists(organizationId, relationId, contactId);

        // Read before the delete: after it the entity no longer says which kind lost its
        // main contact.
        ContactKind kind = contact.getKind();
        boolean wasMain = isMain(contact, kind);
        contactRepository.delete(contact);
        // Hibernate writes deletes after updates, so the delete is flushed before another
        // contact is marked; otherwise the partial unique index would see two main contacts.
        contactRepository.flush();

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CONTACT_DELETED, contactId, relationId, organizationId);

        // Only the main contact's departure needs a successor; deleting another contact
        // leaves the main flags as they are.
        if (wasMain) {
            promoteOldest(organizationId, relationId, kind);
        }
    }

    /**
     * Marks the oldest contact of a kind main of that kind, after the main one of the kind
     * left it by delete or kind change; a relation without contacts of the kind keeps none.
     *
     * Oldest by creation time, id as tie-breaker, read with
     * {@link ContactRepository#findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc}:
     * the longest-known contact is the most likely to still be the right one, and the order
     * is deterministic. The caller has flushed the leaving contact first, so the result is
     * never that contact and no other contact of the kind is still main.
     *
     * Called by {@link #update} on a kind change of the main contact and by {@link #delete}.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation whose main contact of the kind is replaced
     * @param kind           the kind that lost its main contact
     */
    private void promoteOldest(UUID organizationId, UUID relationId, ContactKind kind) {
        contactRepository.findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc(relationId,
                        organizationId, kind)
                .ifPresent(oldest -> {
                    setMain(oldest, kind, true);
                    contactRepository.save(oldest);
                    loggingService.info(LOG_PREFIX, LogLayer.SERVICE, MAIN_CONTACT_PROMOTED,
                            kind, oldest.getId(), relationId, organizationId);
                });
    }

    /**
     * Unmarks the main contact of a kind on a relation in the database, before another
     * contact of the kind is marked.
     *
     * Delegates to {@link ContactRepository#clearMainPersonContact} for
     * {@link ContactKind#PERSON} and to {@link ContactRepository#clearMainBusinessContact} for
     * {@link ContactKind#BUSINESS}. Both are bulk updates that run as their own statement,
     * because the unique index checks each row write and Hibernate would otherwise order the
     * unmark after the mark. They bypass the persistence context, so an already loaded
     * entity of the old main contact keeps its stale flag in memory; no caller reads it
     * afterwards.
     *
     * Called by {@link #create} and {@link #update}.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation whose main contact of the kind is unmarked
     * @param kind           the kind whose main flag is cleared
     */
    private void clearMain(UUID organizationId, UUID relationId, ContactKind kind) {
        if (kind == ContactKind.PERSON) {
            contactRepository.clearMainPersonContact(organizationId, relationId);
        } else {
            contactRepository.clearMainBusinessContact(organizationId, relationId);
        }
    }

    /**
     * Tells whether a request asks to make the contact main of its kind: the flag of the
     * kind is read, the flag of the other kind is ignored here because
     * {@link ContactValidator#ensureMainFlagFitsKind} has already refused a true one. Takes
     * the two flags instead of a request so {@link ContactCreateRequest} and
     * {@link ContactUpdateRequest} share it. Boolean.TRUE.equals keeps a null flag false,
     * although Bean Validation already refuses a null one.
     *
     * Called by {@link #create} and {@link #update}.
     *
     * @param kind                the kind the contact has after the request
     * @param mainPersonContact   the main person flag of the request
     * @param mainBusinessContact the main business flag of the request
     * @return true when the flag of the kind is true
     */
    private static boolean isMarkedMain(ContactKind kind, Boolean mainPersonContact, Boolean mainBusinessContact) {
        Boolean flag = kind == ContactKind.PERSON ? mainPersonContact : mainBusinessContact;
        return Boolean.TRUE.equals(flag);
    }

    /**
     * Tells whether a stored contact is main of a kind, reading
     * {@link Contact#mainPersonContact} for {@link ContactKind#PERSON} and
     * {@link Contact#mainBusinessContact} for {@link ContactKind#BUSINESS}. The kind is a
     * parameter rather than the contact's own, so {@link #update} can ask about the old kind
     * before the kind change is written.
     *
     * Called by {@link #update} and {@link #delete}.
     *
     * @param contact the stored contact
     * @param kind    the kind whose main flag is read
     * @return true when the contact is main of the kind
     */
    private static boolean isMain(Contact contact, ContactKind kind) {
        return kind == ContactKind.PERSON ? contact.isMainPersonContact() : contact.isMainBusinessContact();
    }

    /**
     * Sets the main flag of one kind on a contact in memory; the flag of the other kind is
     * left alone. The write reaches the database with the next save or flush, so the caller
     * orders it after {@link #clearMain} when it sets true.
     *
     * Called by {@link #create}, {@link #update} and {@link #promoteOldest}.
     *
     * @param contact the contact to change
     * @param kind    the kind whose main flag is set
     * @param main    the new value of the flag
     */
    private static void setMain(Contact contact, ContactKind kind, boolean main) {
        if (kind == ContactKind.PERSON) {
            contact.setMainPersonContact(main);
        } else {
            contact.setMainBusinessContact(main);
        }
    }
}
