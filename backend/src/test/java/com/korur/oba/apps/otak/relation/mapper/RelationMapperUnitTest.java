package com.korur.oba.apps.otak.relation.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.math.BigDecimal;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.request.RelationCreateRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationUpdateRequest;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.apps.otak.relation.enums.RelationSource;
import com.korur.oba.apps.otak.relation.enums.RelationType;

class RelationMapperUnitTest {

    private RelationMapper relationMapper;

    @BeforeEach
    void setUp() {
        relationMapper = new RelationMapperImpl();
    }

    private static Relation stored() {
        Relation relation = Relation.builder()
                .name("Old name")
                .website("https://old.example.com")
                .sector("Textile")
                .registrationNumber("KVK-1")
                .vatNumber("NL-1")
                .street("Old street 1")
                .postalCode("1000 AA")
                .city("Amsterdam")
                .notes("Old notes")
                .expectedValue(new BigDecimal("500.00"))
                .size(RelationSize.values()[0])
                .country(Country.NL)
                .source(RelationSource.NETWORK)
                .ownerUserId(UUID.randomUUID())
                .icpId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .campaignId(UUID.randomUUID())
                .type(RelationType.CUSTOMER)
                .stage(PipelineStage.CLOSED)
                .organizationId(UUID.randomUUID())
                .build();
        relation.setId(UUID.randomUUID());
        return relation;
    }

    @Test
    void toEntity_request_copiesTheFieldsAndLeavesStageToTheService() {
        // Arrange
        UUID owner = UUID.randomUUID();
        RelationCreateRequest request = new RelationCreateRequest("Fabrikam", RelationType.LEAD, PipelineStage.QUALIFIED,
                "https://fabrikam.example", "Textile", RelationSize.values()[0], "KVK-9", "NL-9", "Street 2", "2000 BB",
                "Haarlem", Country.NL, RelationSource.LINKEDIN, new BigDecimal("1200.50"), owner, null, null, null,
                "Warm lead");

        // Act
        Relation relation = relationMapper.toEntity(request);

        // Assert
        assertThat(relation.getName()).isEqualTo("Fabrikam");
        assertThat(relation.getType()).isEqualTo(RelationType.LEAD);
        assertThat(relation.getWebsite()).isEqualTo("https://fabrikam.example");
        assertThat(relation.getOwnerUserId()).isEqualTo(owner);
        assertThat(relation.getNotes()).isEqualTo("Warm lead");
        assertThat(relation.getStage()).isNull();
        assertThat(relation.getId()).isNull();
        assertThat(relation.getOrganizationId()).isNull();
    }

    @Test
    void updateEntity_nullInEveryNullableField_clearsTheStoredValue() {
        // Arrange
        Relation relation = stored();
        RelationUpdateRequest request = new RelationUpdateRequest("New name", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        // Act
        relationMapper.updateEntity(relation, request);

        // Assert
        assertThat(relation.getName()).isEqualTo("New name");
        assertThat(relation.getWebsite()).isNull();
        assertThat(relation.getSector()).isNull();
        assertThat(relation.getSize()).isNull();
        assertThat(relation.getRegistrationNumber()).isNull();
        assertThat(relation.getVatNumber()).isNull();
        assertThat(relation.getStreet()).isNull();
        assertThat(relation.getPostalCode()).isNull();
        assertThat(relation.getCity()).isNull();
        assertThat(relation.getCountry()).isNull();
        assertThat(relation.getSource()).isNull();
        assertThat(relation.getExpectedValue()).isNull();
        assertThat(relation.getOwnerUserId()).isNull();
        assertThat(relation.getIcpId()).isNull();
        assertThat(relation.getEventId()).isNull();
        assertThat(relation.getCampaignId()).isNull();
        assertThat(relation.getNotes()).isNull();
    }

    @Test
    void updateEntity_ignoredTargets_keepTheirStoredValue() {
        // Arrange
        Relation relation = stored();
        UUID id = relation.getId();
        UUID organizationId = relation.getOrganizationId();

        // Act
        relationMapper.updateEntity(relation, new RelationUpdateRequest("New name", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null));

        // Assert
        assertThat(relation.getId()).isEqualTo(id);
        assertThat(relation.getOrganizationId()).isEqualTo(organizationId);
        assertThat(relation.getType()).isEqualTo(RelationType.CUSTOMER);
        assertThat(relation.getStage()).isEqualTo(PipelineStage.CLOSED);
    }
}
