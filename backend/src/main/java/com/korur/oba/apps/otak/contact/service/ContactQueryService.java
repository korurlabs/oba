package com.korur.oba.apps.otak.contact.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.dto.response.ContactListResponse;

/**
 * Read service for the contacts of a relation, the query half of the contact domain's
 * command and query split; the writes are in
 * {@link com.korur.oba.apps.otak.contact.service.ContactService}.
 *
 * This interface is the contact domain's public read entry point: another domain that
 * needs contacts calls it rather than
 * {@link com.korur.oba.apps.otak.contact.repository.ContactRepository}. Every method takes
 * the organization the request acts in, so a relation or contact of another organization
 * is never read.
 *
 * Implemented by {@link com.korur.oba.apps.otak.contact.service.impl.ContactQueryServiceImpl}.
 */
public interface ContactQueryService {

    /**
     * Lists every contact of a relation of the organization, person and business contacts
     * together, in a fixed order: the main person contact first, then the main business
     * contact, then the others by name, with the id as tie-breaker. The client therefore
     * shows the contact to talk to first and the inbox to write to at the top, and the
     * order is the same on every read.
     *
     * The relation is checked first, so an unknown relation, or a relation of another
     * organization, answers not found instead of an empty list that would hide the mistake.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.controller.ContactController#listContacts}
     * for the contacts card of a relation, and by
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl} to name
     * the contacts of the outreaches it lists.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @return the contacts, main person first, then main business, then in name order;
     *         empty when the relation has none
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) when the organization has no relation with the id
     */
    List<ContactListResponse> list(UUID organizationId, UUID relationId);
}
