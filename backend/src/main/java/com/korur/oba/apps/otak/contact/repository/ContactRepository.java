package com.korur.oba.apps.otak.contact.repository;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.enums.ContactKind;

/**
 * Repository of {@link Contact} rows in otak.contacts.
 *
 * Organization scoping: every query names the organization next to the relation, so a
 * contact of another organization is never found, even when a caller passes a relation
 * or contact id it was not meant to see. The inherited JpaRepository methods (save,
 * saveAndFlush, delete, flush) are used only on entities read through these scoped queries.
 *
 * Main contacts: a relation has at most one main person and at most one main business
 * contact, held by the partial unique indexes contacts_relation_id_main_person_contact_uk
 * and contacts_relation_id_main_business_contact_uk. The queries below exist to keep that
 * rule while contacts are created, change kind or are deleted: find whether a contact is
 * the first of its kind ({@link #existsByRelationIdAndOrganizationIdAndKind}), unmark the
 * previous main contact before marking a new one ({@link #clearMainPersonContact},
 * {@link #clearMainBusinessContact}), and pick the successor when a main contact leaves
 * its kind ({@link #findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc}).
 *
 * Used within the contact domain only: by
 * {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl},
 * {@link com.korur.oba.apps.otak.contact.service.impl.ContactQueryServiceImpl} and
 * {@link com.korur.oba.apps.otak.contact.validator.ContactValidator}. Other domains read
 * contacts through {@link com.korur.oba.apps.otak.contact.service.ContactQueryService}; the
 * relations list reads the main contacts with its own native query,
 * {@link com.korur.oba.apps.otak.relation.repository.RelationRepository#findMainContacts}.
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    /**
     * Reads one contact of one relation within an organization. All three ids must match, so
     * a contact of another relation, or of a relation of another organization, reads as
     * empty, exactly like an id that does not exist; the caller can not tell the two apart
     * and so learns nothing about another tenant's data.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.validator.ContactValidator#ensureContactExists},
     * which turns the empty result into the not-found error of a contact update or delete.
     *
     * @param id             the contact identifier
     * @param relationId     the relation the contact belongs to
     * @param organizationId the organization that owns the relation
     * @return the contact, or empty when the relation has no contact with the id
     */
    Optional<Contact> findByIdAndRelationIdAndOrganizationId(UUID id, UUID relationId, UUID organizationId);

    /**
     * Reads every contact of one relation within an organization, of both kinds, in the
     * given order. A relation has few contacts, so the list is not paged.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactQueryServiceImpl#list},
     * which passes a fixed sort (main person contact, main business contact, then name and
     * id) so the order is the same on every read and never comes from the client.
     *
     * @param relationId     the relation the contacts belong to
     * @param organizationId the organization that owns the relation
     * @param sort           the order of the contacts
     * @return the contacts, empty when the relation has none
     */
    List<Contact> findByRelationIdAndOrganizationId(UUID relationId, UUID organizationId, Sort sort);

    /**
     * Checks whether a relation of the organization has a contact of a kind. The service
     * asks before it writes a contact into a kind: when the answer is false the contact is
     * the first of its kind on the relation and becomes main of that kind whatever the
     * request's flags say, so a relation with contacts of a kind always has a main one.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#create}
     * and, when the kind changes, by
     * {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update} before
     * the new kind is written, while the contact still counts for its old kind.
     *
     * @param relationId     the relation the contacts belong to
     * @param organizationId the organization that owns the relation
     * @param kind           the kind of contact
     * @return true when the relation has at least one contact of the kind
     */
    boolean existsByRelationIdAndOrganizationIdAndKind(UUID relationId, UUID organizationId, ContactKind kind);

    /**
     * Reads the oldest contact of a kind on a relation, the successor that becomes main of
     * the kind when the main contact of the kind is deleted or changes kind. Oldest by
     * creation time, because the longest-known contact is the most likely to still be the
     * right one; the id breaks ties between equal creation times, the same order the
     * migration used when it first chose main contacts, so the choice is deterministic.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update}
     * and {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#delete},
     * after the leaving contact is flushed, so the result is never the contact that just left.
     *
     * @param relationId     the relation the contacts belong to
     * @param organizationId the organization that owns the relation
     * @param kind           the kind of contact
     * @return the oldest contact of the kind, or empty when the relation has none
     */
    Optional<Contact> findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc(UUID relationId,
                                                                                            UUID organizationId,
                                                                                            ContactKind kind);

    /**
     * Unmarks the main person contact of a relation in one bulk update.
     *
     * Why a bulk update before the mark: the partial unique index
     * contacts_relation_id_main_person_contact_uk checks every row write, not the end of the
     * transaction, so the old main contact must be false in the database before the new one
     * is written as true. Hibernate orders the statements of its own flush by its own rules
     * (inserts, then updates, then deletes), not by this one, so the unmark is a separate
     * statement the service runs first. flushAutomatically writes the
     * pending changes before the update, so it works on the current rows. The update
     * bypasses the persistence context: an entity of the old main contact already loaded in
     * the transaction keeps its stale true flag in memory.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#create}
     * and {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update} when a
     * person contact becomes main.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation whose main person contact is unmarked
     * @return the number of rows updated, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contact c SET c.mainPersonContact = false WHERE c.mainPersonContact = true "
            + "AND c.relationId = :relationId AND c.organizationId = :organizationId")
    int clearMainPersonContact(@Param("organizationId") UUID organizationId, @Param("relationId") UUID relationId);

    /**
     * Unmarks the main business contact of a relation in one bulk update, for the same
     * reason and in the same way as {@link #clearMainPersonContact(UUID, UUID)}; here the
     * partial unique index is contacts_relation_id_main_business_contact_uk.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#create}
     * and {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update} when a
     * business contact becomes main.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation whose main business contact is unmarked
     * @return the number of rows updated, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Contact c SET c.mainBusinessContact = false WHERE c.mainBusinessContact = true "
            + "AND c.relationId = :relationId AND c.organizationId = :organizationId")
    int clearMainBusinessContact(@Param("organizationId") UUID organizationId, @Param("relationId") UUID relationId);
}
