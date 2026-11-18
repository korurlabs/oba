package com.korur.oba.shrine.organization.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.organization.dto.request.OrganizationUpdateRequest;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

class OrganizationMapperUnitTest {

    private OrganizationMapper organizationMapper;

    @BeforeEach
    void setUp() {
        organizationMapper = new OrganizationMapperImpl();
    }

    private Organization stubOrganization() {
        return Organization.builder()
                .name("Acme")
                .description("A description")
                .industry("Finance")
                .companySize("50-100")
                .country("DEU")
                .website("https://acme.io")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    @Nested
    class UpdateEntity {

        @Test
        void allFieldsProvided_writesEveryField() {
            // Arrange
            Organization organization = stubOrganization();
            OrganizationUpdateRequest request = new OrganizationUpdateRequest(
                    "Updated Acme", "New description", "Technology", "11-50", "NLD", "https://updated.io");

            // Act
            organizationMapper.updateEntity(organization, request);

            // Assert
            assertEquals("Updated Acme", organization.getName());
            assertEquals("New description", organization.getDescription());
            assertEquals("Technology", organization.getIndustry());
            assertEquals("11-50", organization.getCompanySize());
            assertEquals("NLD", organization.getCountry());
            assertEquals("https://updated.io", organization.getWebsite());
        }

        @Test
        void nullOptionalFields_clearsFieldsToNull() {
            // Arrange
            Organization organization = stubOrganization();
            OrganizationUpdateRequest request = new OrganizationUpdateRequest("Acme", null, null, null, null, null);

            // Act
            organizationMapper.updateEntity(organization, request);

            // Assert
            assertEquals("Acme", organization.getName());
            assertNull(organization.getDescription());
            assertNull(organization.getIndustry());
            assertNull(organization.getCompanySize());
            assertNull(organization.getCountry());
            assertNull(organization.getWebsite());
        }

        @Test
        void mixedFields_writesProvidedAndClearsNull() {
            // Arrange
            Organization organization = stubOrganization();
            OrganizationUpdateRequest request = new OrganizationUpdateRequest(
                    "Updated Acme", null, "Technology", null, "NLD", null);

            // Act
            organizationMapper.updateEntity(organization, request);

            // Assert
            assertEquals("Updated Acme", organization.getName());
            assertNull(organization.getDescription());
            assertEquals("Technology", organization.getIndustry());
            assertNull(organization.getCompanySize());
            assertEquals("NLD", organization.getCountry());
            assertNull(organization.getWebsite());
        }

        @Test
        void anyRequest_leavesStatusUntouched() {
            // Arrange
            Organization organization = stubOrganization();
            OrganizationUpdateRequest request = new OrganizationUpdateRequest("Acme", null, null, null, null, null);

            // Act
            organizationMapper.updateEntity(organization, request);

            // Assert
            assertEquals(OrganizationStatus.ACTIVE, organization.getStatus());
        }
    }
}
