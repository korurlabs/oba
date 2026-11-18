package com.korur.oba.apps.otak.relation.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.projection.RelationMainContactProjection;
import com.korur.oba.apps.otak.relation.entity.Relation;

/**
 * Repository interface for {@link Relation} entity database operations.
 */
@Repository
public interface RelationRepository extends JpaRepository<Relation, UUID>, JpaSpecificationExecutor<Relation> {

    /**
     * Reads one relation within an organization, so a relation of another
     * organization is not found.
     *
     * @param id             the relation identifier
     * @param organizationId the organization that owns the relation
     * @return the relation, or empty when the organization has no relation with the id
     */
    Optional<Relation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Reads the main person and the main business contact of each given relation in one
     * statement, so a list page shows its Contact, Contact mail and Company mail columns
     * without a query per row.
     *
     * Why one query for the page: reading the main contacts relation by relation would cost
     * one extra query per listed relation (the N+1 problem). The caller collects the ids of
     * the relations it lists (one page, or the whole pipeline of the battlefield) and asks
     * once, so the list costs the same number of queries whatever its length.
     *
     * Why native and here, not in the contact domain: the contact domain already depends on
     * the relation one (it checks the relation through
     * {@link com.korur.oba.apps.otak.relation.service.RelationQueryService#getEntity}), so
     * reading contacts through
     * {@link com.korur.oba.apps.otak.contact.service.ContactQueryService} from the relation
     * service would close a bean cycle. The relation domain has no
     * {@link com.korur.oba.apps.otak.contact.entity.Contact} mapping, so the query reads the
     * table otak.contacts directly into {@link RelationMainContactProjection}, whose getters
     * match the column aliases.
     *
     * Why at most two rows per relation: a partial unique index per flag
     * (contacts_relation_id_main_person_contact_uk and
     * contacts_relation_id_main_business_contact_uk, both on relation_id where the flag is
     * true) allows one main contact of each kind per relation, and those indexes also serve
     * this lookup. A relation without contacts, or without a main contact of a kind, has no
     * row for that kind. Organization scoping: the query names the organization, so an id of
     * another organization's relation finds nothing.
     *
     * Called by the private mainContacts helper of
     * {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl}, for
     * {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl#list} and
     * {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl#listPipeline};
     * the rows end in
     * {@link com.korur.oba.apps.otak.relation.mapper.RelationMapper#toListResponse}.
     *
     * @param organizationId the organization that owns the relations
     * @param relationIds    the relations of the page; must not be empty, because an empty
     *                       IN list is not valid SQL
     * @return one row per main contact, at most two per relation, its mainPersonContact
     *         telling the kind apart; empty when no given relation has a main contact
     */
    @Query(value = """
            SELECT c.relation_id AS relationId,
                   c.name AS name,
                   c.email AS email,
                   c.main_person_contact AS mainPersonContact
            FROM otak.contacts c
            WHERE c.organization_id = :organizationId
              AND c.relation_id IN (:relationIds)
              AND (c.main_person_contact OR c.main_business_contact)
            """, nativeQuery = true)
    List<RelationMainContactProjection> findMainContacts(@Param("organizationId") UUID organizationId,
                                                         @Param("relationIds") Collection<UUID> relationIds);
}
