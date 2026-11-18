package com.korur.oba.apps.otak.icp.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.icp.dto.request.IcpCreateRequest;
import com.korur.oba.apps.otak.icp.dto.request.IcpUpdateRequest;
import com.korur.oba.apps.otak.icp.entity.Icp;
import com.korur.oba.apps.otak.icp.enums.Industry;
import com.korur.oba.apps.otak.icp.enums.TargetPersonRole;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.RelationSize;

class IcpMapperUnitTest {

    private IcpMapper icpMapper;

    @BeforeEach
    void setUp() {
        icpMapper = new IcpMapperImpl();
    }

    private static Icp stored() {
        Icp icp = Icp.builder()
                .name("Old")
                .description("Old description")
                .industry(Industry.RETAIL)
                .country(Country.NL)
                .companySizeMin(RelationSize.values()[0])
                .companySizeMax(RelationSize.values()[0])
                .targetPersonRole(TargetPersonRole.CEO)
                .organizationId(UUID.randomUUID())
                .build();
        icp.setId(UUID.randomUUID());
        return icp;
    }

    @Test
    void toEntity_request_copiesTheFields() {
        // Act
        Icp icp = icpMapper.toEntity(new IcpCreateRequest("Exporters", Industry.MANUFACTURING, Country.TR,
                RelationSize.values()[0], null, TargetPersonRole.CEO, "Textile exporters"));

        // Assert
        assertThat(icp.getName()).isEqualTo("Exporters");
        assertThat(icp.getIndustry()).isEqualTo(Industry.MANUFACTURING);
        assertThat(icp.getCountry()).isEqualTo(Country.TR);
        assertThat(icp.getCompanySizeMax()).isNull();
        assertThat(icp.getDescription()).isEqualTo("Textile exporters");
        assertThat(icp.getId()).isNull();
        assertThat(icp.getOrganizationId()).isNull();
    }

    @Test
    void updateEntity_nullInEveryNullableField_clearsTheStoredValue() {
        // Arrange
        Icp icp = stored();
        UUID id = icp.getId();
        UUID organizationId = icp.getOrganizationId();

        // Act
        icpMapper.updateEntity(icp, new IcpUpdateRequest("New", Industry.MANUFACTURING, null, null, null, null, null));

        // Assert
        assertThat(icp.getName()).isEqualTo("New");
        assertThat(icp.getIndustry()).isEqualTo(Industry.MANUFACTURING);
        assertThat(icp.getCountry()).isNull();
        assertThat(icp.getCompanySizeMin()).isNull();
        assertThat(icp.getCompanySizeMax()).isNull();
        assertThat(icp.getTargetPersonRole()).isNull();
        assertThat(icp.getDescription()).isNull();
        assertThat(icp.getId()).isEqualTo(id);
        assertThat(icp.getOrganizationId()).isEqualTo(organizationId);
    }
}
