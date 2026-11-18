package com.korur.oba.apps.otak.relation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;

// 3. Java SDK
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.repository.ContactRepository;
import com.korur.oba.apps.otak.relation.dto.request.RelationQueryRequest;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;
import com.korur.oba.apps.otak.relation.specification.RelationSpecification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The CRM schema refuses rows the application must never write, whatever the application does:
 * only a lead has a stage and every lead has one, and a contact always carries the organization of
 * its relation, so no contact can hang under another tenant's relation.
 */
class RelationMigrationIT extends AbstractTenantIT {

    @Autowired
    private RelationRepository relationRepository;
    @Autowired
    private ContactRepository contactRepository;

    private Tenant tenantA;
    private Tenant tenantB;
    private Relation leadOfA;

    @BeforeEach
    void setUpTenantsAndLeadOfA() {
        tenantA = newTenant();
        tenantB = newTenant();
        leadOfA = relationRepository.saveAndFlush(relation(tenantA, RelationType.LEAD, PipelineStage.NEW).build());
    }

    private static Relation.RelationBuilder<?, ?> relation(Tenant tenant, RelationType type, PipelineStage stage) {
        return Relation.builder()
                .name("Relation " + uniqueSuffix())
                .type(type)
                .stage(stage)
                .organizationId(tenant.organization().getId());
    }

    private static Contact contact(UUID organizationId, UUID relationId) {
        return Contact.builder()
                .name("Contact " + uniqueSuffix())
                .organizationId(organizationId)
                .relationId(relationId)
                .build();
    }

    private long relationCount(Tenant tenant) {
        return count("SELECT count(*) FROM otak.relations WHERE organization_id = ?", tenant.organization().getId());
    }

    @Test
    void saveRelation_leadWithStage_isAccepted() {
        Relation saved = relationRepository.saveAndFlush(relation(tenantA, RelationType.LEAD, PipelineStage.WARM).build());

        assertEquals(PipelineStage.WARM, saved.getStage());
        assertEquals(2L, relationCount(tenantA));
    }

    @Test
    void saveRelation_customerAtClosedOrDirectAndSupplierWithoutStage_areAccepted() {
        relationRepository.saveAndFlush(relation(tenantA, RelationType.CUSTOMER, PipelineStage.CLOSED).build());
        relationRepository.saveAndFlush(relation(tenantA, RelationType.CUSTOMER, PipelineStage.DIRECT).build());
        relationRepository.saveAndFlush(relation(tenantA, RelationType.SUPPLIER, null).build());

        assertEquals(4L, relationCount(tenantA));
    }

    @Test
    void saveRelation_customerWithLeadStage_isRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> relationRepository
                .saveAndFlush(relation(tenantB, RelationType.CUSTOMER, PipelineStage.NEW).build()));
        assertEquals(0L, relationCount(tenantB));
    }

    @Test
    void saveRelation_customerWithoutStage_isRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> relationRepository
                .saveAndFlush(relation(tenantB, RelationType.CUSTOMER, null).build()));
        assertEquals(0L, relationCount(tenantB));
    }

    @Test
    void saveRelation_leadAtClosedOrDirect_isRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> relationRepository
                .saveAndFlush(relation(tenantB, RelationType.LEAD, PipelineStage.CLOSED).build()));
        assertThrows(DataIntegrityViolationException.class, () -> relationRepository
                .saveAndFlush(relation(tenantB, RelationType.LEAD, PipelineStage.DIRECT).build()));
        assertEquals(0L, relationCount(tenantB));
    }

    @Test
    void migratedSchema_hasNoCustomerWithoutStage() {
        // The migration turned every customer that had no stage into a DIRECT customer.
        assertEquals(0L, count("SELECT count(*) FROM otak.relations WHERE type = 'CUSTOMER'::otak_relation_type_enum "
                + "AND stage IS NULL"));
        assertEquals(0L, count("SELECT count(*) FROM pg_type WHERE typname = 'otak_lead_stage_enum'"));
    }

    @Test
    void updateRelation_leadToCustomerWithStageClosed_isAcceptedInOneStatement() {
        jdbcTemplate.update("UPDATE otak.relations SET type = 'CUSTOMER'::otak_relation_type_enum, "
                + "stage = 'CLOSED'::otak_pipeline_stage_enum WHERE id = ?", leadOfA.getId());

        assertEquals(1L, count("SELECT count(*) FROM otak.relations WHERE id = ? "
                + "AND type = 'CUSTOMER'::otak_relation_type_enum AND stage = 'CLOSED'::otak_pipeline_stage_enum",
                leadOfA.getId()));
    }

    @Test
    void listFilter_customersByStage_andPipelineHoldLeadsAndCustomersOnly() {
        Relation closed = relationRepository.saveAndFlush(
                relation(tenantA, RelationType.CUSTOMER, PipelineStage.CLOSED).build());
        Relation direct = relationRepository.saveAndFlush(
                relation(tenantA, RelationType.CUSTOMER, PipelineStage.DIRECT).build());
        Relation parked = relationRepository.saveAndFlush(
                relation(tenantA, RelationType.LEAD, PipelineStage.PARKED).build());
        Relation supplier = relationRepository.saveAndFlush(relation(tenantA, RelationType.SUPPLIER, null).build());
        UUID organizationId = tenantA.organization().getId();

        RelationQueryRequest onlyClosed = new RelationQueryRequest();
        onlyClosed.setType(RelationType.CUSTOMER);
        onlyClosed.setStage(List.of(PipelineStage.CLOSED));
        RelationQueryRequest allCustomers = new RelationQueryRequest();
        allCustomers.setType(RelationType.CUSTOMER);
        RelationQueryRequest customersAtLeadStage = new RelationQueryRequest();
        customersAtLeadStage.setType(RelationType.CUSTOMER);
        customersAtLeadStage.setStage(List.of(PipelineStage.WARM));

        assertEquals(List.of(closed.getId()), ids(RelationSpecification.filterBy(organizationId, onlyClosed)));
        assertEquals(Set.of(closed.getId(), direct.getId()),
                Set.copyOf(ids(RelationSpecification.filterBy(organizationId, allCustomers))));
        assertEquals(List.of(), ids(RelationSpecification.filterBy(organizationId, customersAtLeadStage)));

        List<UUID> pipeline = ids(RelationSpecification.pipelineOf(organizationId));
        assertEquals(Set.of(leadOfA.getId(), closed.getId(), direct.getId(), parked.getId()), Set.copyOf(pipeline));
        assertFalse(pipeline.contains(supplier.getId()));
    }

    private List<UUID> ids(Specification<Relation> specification) {
        return relationRepository.findAll(specification).stream().map(Relation::getId).toList();
    }

    @Test
    void saveRelation_supplierWithStage_isRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> relationRepository
                .saveAndFlush(relation(tenantB, RelationType.SUPPLIER, PipelineStage.PROPOSAL).build()));
        assertEquals(0L, relationCount(tenantB));
    }

    @Test
    void saveRelation_leadWithoutStage_isRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> relationRepository
                .saveAndFlush(relation(tenantB, RelationType.LEAD, null).build()));
        assertEquals(0L, relationCount(tenantB));
    }

    @Test
    void updateRelation_typeChangedWithoutStage_isRejected() {
        // A type change that forgets the stage would leave a customer with a lead stage.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE otak.relations SET type = 'CUSTOMER'::otak_relation_type_enum WHERE id = ?", leadOfA.getId()));
        assertEquals(1L, count("SELECT count(*) FROM otak.relations WHERE id = ? "
                + "AND type = 'LEAD'::otak_relation_type_enum AND stage = 'NEW'::otak_pipeline_stage_enum", leadOfA.getId()));
    }

    @Test
    void saveContact_onRelationOfTheSameOrganization_isAccepted() {
        contactRepository.saveAndFlush(contact(tenantA.organization().getId(), leadOfA.getId()));

        assertEquals(1L, count("SELECT count(*) FROM otak.contacts WHERE relation_id = ?", leadOfA.getId()));
    }

    @Test
    void saveContact_onRelationOfAnotherOrganization_isRejectedByTheCompositeForeignKey() {
        assertThrows(DataIntegrityViolationException.class, () -> contactRepository
                .saveAndFlush(contact(tenantB.organization().getId(), leadOfA.getId())));
        assertEquals(0L, count("SELECT count(*) FROM otak.contacts WHERE relation_id = ?", leadOfA.getId()));
    }

    @Test
    void deleteRelation_withContacts_cascadesToItsContactsOnly() {
        Relation otherLead = relationRepository.saveAndFlush(relation(tenantA, RelationType.LEAD, PipelineStage.NEW).build());
        contactRepository.saveAndFlush(contact(tenantA.organization().getId(), leadOfA.getId()));
        contactRepository.saveAndFlush(contact(tenantA.organization().getId(), otherLead.getId()));

        jdbcTemplate.update("DELETE FROM otak.relations WHERE id = ?", leadOfA.getId());

        assertEquals(0L, count("SELECT count(*) FROM otak.contacts WHERE relation_id = ?", leadOfA.getId()));
        assertEquals(1L, count("SELECT count(*) FROM otak.contacts WHERE relation_id = ?", otherLead.getId()));
    }

    @Test
    void updateRelation_unknownCountryCode_isRejectedByTheEnumType() {
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE otak.relations SET country = 'XX'::otak_country_enum WHERE id = ?", leadOfA.getId()));
        assertEquals(0L, count("SELECT count(*) FROM otak.relations WHERE id = ? AND country IS NOT NULL",
                leadOfA.getId()));
    }
}
