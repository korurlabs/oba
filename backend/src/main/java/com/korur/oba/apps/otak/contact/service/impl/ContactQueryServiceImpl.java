package com.korur.oba.apps.otak.contact.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.contact.constant.service.ContactServiceLogConstant.*;

import com.korur.oba.apps.otak.contact.constant.ContactConstant;
import com.korur.oba.apps.otak.contact.dto.response.ContactListResponse;
import com.korur.oba.apps.otak.contact.mapper.ContactMapper;
import com.korur.oba.apps.otak.contact.repository.ContactRepository;
import com.korur.oba.apps.otak.contact.service.ContactQueryService;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implementation of {@link ContactQueryService}, the read side of the contacts of a
 * relation.
 *
 * All methods run within a read-only transaction, so Hibernate skips dirty checking and
 * the entities read here are never written back.
 *
 * Organization scoping: the relation is read first through
 * {@link RelationQueryService#getEntity}, which answers not found for a relation of another
 * organization, and the contacts are then read with
 * {@link ContactRepository#findByRelationIdAndOrganizationId}, which names the organization
 * again. The relation is reached through its query service and not its repository because
 * the relation is another domain.
 *
 * Entities are mapped to responses with {@link ContactMapper#toListResponse}, so no entity
 * leaves the service.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactQueryServiceImpl implements ContactQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ContactQueryServiceImpl.class);

    // The main person contact leads the list, then the main business contact (true sorts
    // after false, hence descending); person and business contacts then mix by name. The id
    // breaks ties between equal names, so the order is the same on every read. The order is
    // fixed here and never taken from the request, so no client value reaches the query.
    private static final Sort MAIN_FIRST_BY_NAME = Sort
            .by(Sort.Direction.DESC, ContactConstant.FIELD_MAIN_PERSON_CONTACT)
            .and(Sort.by(Sort.Direction.DESC, ContactConstant.FIELD_MAIN_BUSINESS_CONTACT))
            .and(Sort.by(Sort.Direction.ASC, ContactConstant.FIELD_NAME))
            .and(Sort.by(Sort.Direction.ASC, ContactConstant.FIELD_ID));

    private final ContactRepository contactRepository;
    private final ContactMapper contactMapper;
    private final RelationQueryService relationQueryService;
    private final LoggingService loggingService;

    /**
     * Lists every contact of a relation of the organization in the order of
     * {@link #MAIN_FIRST_BY_NAME}: main person contact, main business contact, then the
     * others by name and id.
     *
     * Steps: check the relation through {@link RelationQueryService#getEntity}, read the
     * contacts with {@link ContactRepository#findByRelationIdAndOrganizationId}, map each with
     * {@link ContactMapper#toListResponse}. Two queries, whatever the number of contacts.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.controller.ContactController#listContacts}
     * and by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl}.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @return the contacts, main person first, then main business, then in name order;
     *         empty when the relation has none
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) when the organization has no relation with the id
     */
    @Override
    public List<ContactListResponse> list(UUID organizationId, UUID relationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_CONTACTS, relationId, organizationId);

        // An unknown relation, or one of another organization, answers not found instead of
        // an empty list; the returned entity is not needed, only the check.
        relationQueryService.getEntity(organizationId, relationId);

        // The repository query names the organization again, so the read stays scoped even
        // without the check above; the fixed sort puts the main contacts first.
        return contactRepository.findByRelationIdAndOrganizationId(relationId, organizationId, MAIN_FIRST_BY_NAME).stream()
                .map(contactMapper::toListResponse)
                .toList();
    }
}
