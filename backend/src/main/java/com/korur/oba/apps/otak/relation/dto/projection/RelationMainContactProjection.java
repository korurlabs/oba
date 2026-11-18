package com.korur.oba.apps.otak.relation.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One main contact of a relation, person or business, as one row of the native query
 * {@link com.korur.oba.apps.otak.relation.repository.RelationRepository#findMainContacts}.
 *
 * A Spring Data interface projection: Spring backs each getter with the result column of
 * the same alias (relationId, name, email, mainPersonContact), so the getter names must
 * match the aliases of that query. It carries only what the relations list shows, read
 * straight from otak.contacts for a whole page of relations, without loading
 * {@link com.korur.oba.apps.otak.contact.entity.Contact} entities and without calling the
 * contact domain.
 *
 * {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl} splits the
 * rows by {@link #getMainPersonContact()} into the main person and the main business
 * contact of each relation, and
 * {@link com.korur.oba.apps.otak.relation.mapper.RelationMapper#toListResponse} copies them
 * into the list item: the person's name and e-mail become the contact and contact mail
 * columns, the business contact's e-mail the company mail column.
 */
public interface RelationMainContactProjection {

    /**
     * The relation the contact belongs to, the key under which the service files the row.
     *
     * @return the relation identifier, never null
     */
    UUID getRelationId();

    /**
     * The contact's name; the relations list shows it for the main person contact only.
     *
     * @return the name, never null
     */
    String getName();

    /**
     * The contact's e-mail address: the contact mail for the main person contact, the
     * company mail for the main business contact.
     *
     * @return the address, or null when the contact has none
     */
    String getEmail();

    /**
     * True for the main person contact, false for the main business contact. The query only
     * returns rows that carry one of the two flags, and a check constraint per flag allows
     * each flag only on a contact of its own kind, so a row is never both and false always
     * means the main business contact.
     *
     * @return the kind of main contact the row is, never null
     */
    Boolean getMainPersonContact();
}
