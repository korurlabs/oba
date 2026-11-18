package com.korur.oba.apps.otak.contact.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.dto.request.ContactCreateRequest;
import com.korur.oba.apps.otak.contact.dto.request.ContactUpdateRequest;
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.enums.ContactEmailType;
import com.korur.oba.apps.otak.contact.enums.ContactKind;

class ContactMapperUnitTest {

    private ContactMapper contactMapper;

    @BeforeEach
    void setUp() {
        contactMapper = new ContactMapperImpl();
    }

    private static Contact stored() {
        Contact contact = Contact.builder()
                .name("Jan de Vries")
                .kind(ContactKind.PERSON)
                .role("Buyer")
                .email("jan@fabrikam.example")
                .emailType(ContactEmailType.COMPANY)
                .phone("+31 6 1234 5678")
                .linkedinUrl("https://linkedin.com/in/jan")
                .sourceUrl("https://fabrikam.example/team")
                .decisionMaker(true)
                .mainPersonContact(true)
                .mainBusinessContact(false)
                .organizationId(UUID.randomUUID())
                .relationId(UUID.randomUUID())
                .build();
        contact.setId(UUID.randomUUID());
        return contact;
    }

    @Test
    void toEntity_request_copiesTheFieldsAndLeavesKindAndMainFlagsToTheService() {
        // Arrange
        ContactCreateRequest request = new ContactCreateRequest("Info inbox", ContactKind.BUSINESS, "Sales",
                "info@fabrikam.example", ContactEmailType.SHARED, "+31 20 123", "https://linkedin.com/in/x", true,
                "https://fabrikam.example/team", true, true);

        // Act
        Contact contact = contactMapper.toEntity(request);

        // Assert
        assertThat(contact.getName()).isEqualTo("Info inbox");
        assertThat(contact.getEmail()).isEqualTo("info@fabrikam.example");
        assertThat(contact.getEmailType()).isEqualTo(ContactEmailType.SHARED);
        assertThat(contact.isDecisionMaker()).isTrue();
        assertThat(contact.getSourceUrl()).isEqualTo("https://fabrikam.example/team");
        assertThat(contact.getKind()).isEqualTo(ContactKind.PERSON);
        assertThat(contact.isMainPersonContact()).isFalse();
        assertThat(contact.isMainBusinessContact()).isFalse();
        assertThat(contact.getRelationId()).isNull();
    }

    @Test
    void updateEntity_nullInEveryNullableField_clearsTheStoredValue() {
        // Arrange
        Contact contact = stored();
        ContactUpdateRequest request = new ContactUpdateRequest("Jan", ContactKind.PERSON, null, null, null, null, null,
                false, true, false);

        // Act
        contactMapper.updateEntity(contact, request);

        // Assert
        assertThat(contact.getName()).isEqualTo("Jan");
        assertThat(contact.getRole()).isNull();
        assertThat(contact.getEmail()).isNull();
        assertThat(contact.getEmailType()).isNull();
        assertThat(contact.getPhone()).isNull();
        assertThat(contact.getLinkedinUrl()).isNull();
        assertThat(contact.isDecisionMaker()).isFalse();
    }

    @Test
    void updateEntity_ignoredTargets_keepTheirStoredValue() {
        // Arrange
        Contact contact = stored();
        UUID id = contact.getId();
        UUID relationId = contact.getRelationId();
        UUID organizationId = contact.getOrganizationId();
        ContactUpdateRequest request = new ContactUpdateRequest("Jan", ContactKind.BUSINESS, null, null, null, null,
                null, false, false, true);

        // Act
        contactMapper.updateEntity(contact, request);

        // Assert
        assertThat(contact.getId()).isEqualTo(id);
        assertThat(contact.getRelationId()).isEqualTo(relationId);
        assertThat(contact.getOrganizationId()).isEqualTo(organizationId);
        assertThat(contact.getSourceUrl()).isEqualTo("https://fabrikam.example/team");
        assertThat(contact.getKind()).isEqualTo(ContactKind.PERSON);
        assertThat(contact.isMainPersonContact()).isTrue();
        assertThat(contact.isMainBusinessContact()).isFalse();
    }
}
