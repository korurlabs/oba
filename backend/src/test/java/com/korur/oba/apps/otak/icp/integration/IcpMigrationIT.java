package com.korur.oba.apps.otak.icp.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

// 3. Java SDK
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.apps.otak.icp.entity.Icp;
import com.korur.oba.apps.otak.icp.enums.Industry;
import com.korur.oba.apps.otak.icp.repository.IcpRepository;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The ICP schema refuses rows the application must never write: an inverted size range, a
 * duplicate name inside one organization, and a relation linked to the ICP of another
 * organization; and deleting an ICP keeps the relations that pointed at it. The industry enum
 * of the database lists the values of {@link Industry} in their declaration order.
 */
class IcpMigrationIT extends AbstractTenantIT {

    @Autowired
    private IcpRepository icpRepository;
    @Autowired
    private RelationRepository relationRepository;

    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setUpTenants() {
        tenantA = newTenant();
        tenantB = newTenant();
    }

    private static Icp.IcpBuilder<?, ?> icp(Tenant tenant, String name) {
        return Icp.builder()
                .name(name)
                .industry(Industry.SOFTWARE)
                .organizationId(tenant.organization().getId());
    }

    private static Relation.RelationBuilder<?, ?> lead(Tenant tenant, UUID icpId) {
        return Relation.builder()
                .name("Relation " + uniqueSuffix())
                .type(RelationType.LEAD)
                .stage(PipelineStage.NEW)
                .icpId(icpId)
                .organizationId(tenant.organization().getId());
    }

    @Test
    void saveIcp_minAboveMax_isRejectedByTheCheckConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> icpRepository.saveAndFlush(
                icp(tenantA, "Inverted " + uniqueSuffix())
                        .companySizeMin(RelationSize.SIZE_501_1000)
                        .companySizeMax(RelationSize.SIZE_1_10)
                        .build()));
    }

    @Test
    void saveIcp_equalOrOneSidedSizeRange_isAccepted() {
        icpRepository.saveAndFlush(icp(tenantA, "Equal " + uniqueSuffix())
                .companySizeMin(RelationSize.SIZE_11_50).companySizeMax(RelationSize.SIZE_11_50).build());
        icpRepository.saveAndFlush(icp(tenantA, "MinOnly " + uniqueSuffix())
                .companySizeMin(RelationSize.SIZE_11_50).build());
        icpRepository.saveAndFlush(icp(tenantA, "MaxOnly " + uniqueSuffix())
                .companySizeMax(RelationSize.SIZE_11_50).build());

        assertEquals(3L, count("SELECT count(*) FROM otak.icps WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void saveIcp_sameNameDifferentCaseInOneOrganization_isRejected() {
        String name = "Textile " + uniqueSuffix();
        icpRepository.saveAndFlush(icp(tenantA, name).build());

        assertThrows(DataIntegrityViolationException.class,
                () -> icpRepository.saveAndFlush(icp(tenantA, name.toUpperCase()).build()));
        assertEquals(1L, count("SELECT count(*) FROM otak.icps WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void saveIcp_sameNameInTwoOrganizations_isAccepted() {
        String name = "Textile " + uniqueSuffix();

        icpRepository.saveAndFlush(icp(tenantA, name).build());
        icpRepository.saveAndFlush(icp(tenantB, name).build());

        assertEquals(1L, count("SELECT count(*) FROM otak.icps WHERE organization_id = ?",
                tenantB.organization().getId()));
    }

    @Test
    void deleteIcp_withLinkedRelation_clearsTheLinkAndKeepsTheRelation() {
        Icp icp = icpRepository.saveAndFlush(icp(tenantA, "Linked " + uniqueSuffix()).build());
        Relation relation = relationRepository.saveAndFlush(lead(tenantA, icp.getId()).build());

        jdbcTemplate.update("DELETE FROM otak.icps WHERE id = ?", icp.getId());

        assertEquals(1L, count("SELECT count(*) FROM otak.relations WHERE id = ? AND icp_id IS NULL "
                + "AND organization_id = ?", relation.getId(), tenantA.organization().getId()));
    }

    @Test
    void saveRelation_linkedToIcpOfAnotherOrganization_isRejectedByTheCompositeForeignKey() {
        Icp icpOfA = icpRepository.saveAndFlush(icp(tenantA, "Of A " + uniqueSuffix()).build());

        assertThrows(DataIntegrityViolationException.class,
                () -> relationRepository.saveAndFlush(lead(tenantB, icpOfA.getId()).build()));
        assertEquals(0L, count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId()));
    }

    @Test
    void industryEnum_databaseOrder_matchesJavaDeclarationOrder() {
        List<String> databaseOrder = jdbcTemplate.queryForList(
                "SELECT unnest(enum_range(NULL::otak_industry_enum))::text", String.class);

        List<String> javaOrder = Arrays.stream(Industry.values()).map(Enum::name).toList();

        assertEquals(javaOrder, databaseOrder);
    }
}
