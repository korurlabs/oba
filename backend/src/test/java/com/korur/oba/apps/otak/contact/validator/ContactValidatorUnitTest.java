package com.korur.oba.apps.otak.contact.validator;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

// 3. Java SDK
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.enums.ContactEmailType;
import com.korur.oba.apps.otak.contact.enums.ContactKind;
import com.korur.oba.apps.otak.contact.repository.ContactRepository;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class ContactValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID relationId = UUID.randomUUID();

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private ContactValidator contactValidator;

    @Test
    void ensureContactExists_contactOfRelation_returnsContact() {
        // Arrange
        Contact contact = Contact.builder().name("Jan").build();
        contact.setId(UUID.randomUUID());
        when(contactRepository.findByIdAndRelationIdAndOrganizationId(contact.getId(), relationId, organizationId))
                .thenReturn(Optional.of(contact));

        // Act
        Contact result = contactValidator.ensureContactExists(organizationId, relationId, contact.getId());

        // Assert
        assertSame(contact, result);
    }

    @Test
    void ensureContactExists_missingOrOfAnotherRelationOrOrganization_throwsContactNotFound() {
        // Arrange: the lookup is scoped by relation and organization, so a foreign id is not found
        UUID contactId = UUID.randomUUID();
        when(contactRepository.findByIdAndRelationIdAndOrganizationId(contactId, relationId, organizationId))
                .thenReturn(Optional.empty());

        // Act
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> contactValidator.ensureContactExists(organizationId, relationId, contactId));

        // Assert
        assertEquals(ExceptionDomainOtakError.NOT_CONTACT_FOUND.getCode(), exception.getErrorCode());
        assertEquals("OTK_NOT_002", exception.getErrorCode());
    }

    @Test
    void ensureEmailTypeHasEmail_typeWithEmail_passes() {
        // Act and assert
        assertDoesNotThrow(() -> contactValidator.ensureEmailTypeHasEmail("jan@fabrikam.example", ContactEmailType.COMPANY));
    }

    @Test
    void ensureEmailTypeHasEmail_noType_passesWithOrWithoutEmail() {
        // Act and assert
        assertDoesNotThrow(() -> contactValidator.ensureEmailTypeHasEmail(null, null));
        assertDoesNotThrow(() -> contactValidator.ensureEmailTypeHasEmail("jan@fabrikam.example", null));
    }

    @Test
    void ensureEmailTypeHasEmail_typeWithoutEmail_throwsEmailTypeWithoutEmail() {
        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> contactValidator.ensureEmailTypeHasEmail(null, ContactEmailType.SHARED));

        // Assert
        assertEquals(ExceptionDomainOtakError.VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL.getCode(), exception.getErrorCode());
        assertEquals("OTK_VAL_005", exception.getErrorCode());
    }

    @Test
    void ensureEmailTypeHasEmail_typeWithBlankEmail_throwsEmailTypeWithoutEmail() {
        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> contactValidator.ensureEmailTypeHasEmail("  ", ContactEmailType.PERSONAL));

        // Assert
        assertEquals("OTK_VAL_005", exception.getErrorCode());
    }

    @ParameterizedTest
    @CsvSource({"PERSON, true, ", "PERSON, true, false", "PERSON, true, ", "PERSON, , ", "PERSON, false, false",
            "BUSINESS, , true", "BUSINESS, false, true", "BUSINESS, , ", "BUSINESS, false, false"})
    void ensureMainFlagFitsKind_ownKindFlagOrNoTrueFlag_passes(ContactKind kind, Boolean person, Boolean business) {
        // Act and assert
        assertDoesNotThrow(() -> contactValidator.ensureMainFlagFitsKind(kind, person, business));
        verifyNoInteractions(loggingService);
    }

    @ParameterizedTest
    @CsvSource({"PERSON, , true", "PERSON, false, true", "PERSON, true, true",
            "BUSINESS, true, ", "BUSINESS, true, false", "BUSINESS, true, true"})
    void ensureMainFlagFitsKind_trueFlagOfTheOtherKind_throwsMainFlagNotForKind(ContactKind kind, Boolean person,
                                                                             Boolean business) {
        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> contactValidator.ensureMainFlagFitsKind(kind, person, business));

        // Assert
        assertEquals(ExceptionDomainOtakError.VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND.getCode(), exception.getErrorCode());
        assertEquals("OTK_VAL_013", exception.getErrorCode());
    }

    private static Contact contactOf(ContactKind kind, boolean mainPerson, boolean mainBusiness) {
        Contact contact = Contact.builder().name("Jan").kind(kind).mainPersonContact(mainPerson)
                .mainBusinessContact(mainBusiness).build();
        contact.setId(UUID.randomUUID());
        return contact;
    }

    @ParameterizedTest
    @CsvSource({"PERSON, true, false, false, false", "PERSON, true, false, false, true",
            "BUSINESS, false, true, false, false", "BUSINESS, false, true, true, false"})
    void ensureMainContactKept_falseOnTheMainOfTheKind_throwsWithThatFlagsField(ContactKind kind, boolean mainPerson,
            boolean mainBusiness, Boolean personFlag, Boolean businessFlag) {
        // Arrange
        Contact contact = contactOf(kind, mainPerson, mainBusiness);
        String field = kind == ContactKind.PERSON ? "mainPersonContact" : "mainBusinessContact";

        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> contactValidator.ensureMainContactKept(contact, personFlag, businessFlag));

        // Assert
        assertEquals("GEN_VAL_001", exception.getErrorCode());
        assertEquals(Map.of(field, "Mark another contact as main instead"), exception.getErrorValues());
    }

    @ParameterizedTest
    @CsvSource({"PERSON, true, false, , false", "BUSINESS, false, true, false, "})
    void ensureMainContactKept_nullFlagOnTheMainOfTheKind_throws(ContactKind kind, boolean mainPerson,
            boolean mainBusiness, Boolean personFlag, Boolean businessFlag) {
        Contact contact = contactOf(kind, mainPerson, mainBusiness);

        assertThrows(ValidationException.class,
                () -> contactValidator.ensureMainContactKept(contact, personFlag, businessFlag));
    }

    @ParameterizedTest
    @CsvSource({"PERSON, true, false, true, false", "BUSINESS, false, true, false, true"})
    void ensureMainContactKept_trueOnTheMainOfTheKind_passes(ContactKind kind, boolean mainPerson, boolean mainBusiness,
            Boolean personFlag, Boolean businessFlag) {
        Contact contact = contactOf(kind, mainPerson, mainBusiness);

        assertDoesNotThrow(() -> contactValidator.ensureMainContactKept(contact, personFlag, businessFlag));
        verifyNoInteractions(loggingService);
    }

    @ParameterizedTest
    @CsvSource({"PERSON, false, false", "PERSON, false, true", "BUSINESS, false, false", "BUSINESS, true, false"})
    void ensureMainContactKept_falseOrTrueOnANonMain_passes(ContactKind kind, Boolean personFlag, Boolean businessFlag) {
        Contact contact = contactOf(kind, false, false);

        assertDoesNotThrow(() -> contactValidator.ensureMainContactKept(contact, personFlag, businessFlag));
        verifyNoInteractions(loggingService);
    }
}
