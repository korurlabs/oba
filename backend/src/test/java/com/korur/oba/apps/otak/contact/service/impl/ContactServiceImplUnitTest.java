package com.korur.oba.apps.otak.contact.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.dto.request.ContactCreateRequest;
import com.korur.oba.apps.otak.contact.dto.request.ContactUpdateRequest;
import com.korur.oba.apps.otak.contact.dto.response.ContactDetailResponse;
import com.korur.oba.apps.otak.contact.dto.response.ContactListResponse;
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.enums.ContactEmailType;
import com.korur.oba.apps.otak.contact.enums.ContactKind;
import com.korur.oba.apps.otak.contact.mapper.ContactMapper;
import com.korur.oba.apps.otak.contact.mapper.ContactMapperImpl;
import com.korur.oba.apps.otak.contact.repository.ContactRepository;
import com.korur.oba.apps.otak.contact.validator.ContactValidator;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class ContactServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private RelationQueryService relationQueryService;

    @Mock
    private LoggingService loggingService;

    private ContactServiceImpl service;
    private ContactQueryServiceImpl queryService;
    private User user;
    private Relation relation;

    @BeforeEach
    void setUp() {
        // The real validator and mapper run over the mocked repository, so the error codes and
        // the mapped fields are the production ones.
        ContactValidator validator = new ContactValidator(contactRepository, loggingService);
        ContactMapper mapper = new ContactMapperImpl();
        service = new ContactServiceImpl(contactRepository, mapper, validator, relationQueryService, loggingService);
        queryService = new ContactQueryServiceImpl(contactRepository, mapper, relationQueryService, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
        relation = Relation.builder().name("Fabrikam").type(RelationType.LEAD).stage(PipelineStage.NEW)
                .organizationId(organizationId).build();
        relation.setId(UUID.randomUUID());
    }

    private static final String SOURCE_URL = "https://www.fabrikam.example/team";

    private static ContactCreateRequest request(Boolean decisionMaker) {
        return new ContactCreateRequest("Jan de Vries", ContactKind.PERSON, "Operations manager", "jan@fabrikam.example",
                ContactEmailType.COMPANY, "+31 20 123 4567", "https://www.linkedin.com/in/jandevries", decisionMaker,
                SOURCE_URL, false, false);
    }

    private static ContactUpdateRequest updateRequest(Boolean decisionMaker) {
        return new ContactUpdateRequest("Jan de Vries", ContactKind.PERSON, "Operations manager", "jan@fabrikam.example",
                ContactEmailType.COMPANY, "+31 20 123 4567", "https://www.linkedin.com/in/jandevries", decisionMaker,
                false, false);
    }

    private static ContactUpdateRequest updateRequest(String email, ContactEmailType emailType) {
        return new ContactUpdateRequest("Jan de Vries", ContactKind.PERSON, null, email, emailType, null, null, false,
                false, false);
    }

    private void stubRelation() {
        when(relationQueryService.getEntity(organizationId, relation.getId())).thenReturn(relation);
    }

    private void stubRelationMissing(UUID relationId) {
        when(relationQueryService.getEntity(organizationId, relationId)).thenThrow(new NotFoundException(
                ExceptionDomainOtakError.NOT_RELATION_FOUND, Map.of("relationId", relationId)));
    }

    private static ContactCreateRequest emailTypeWithoutEmail() {
        return new ContactCreateRequest("Jan de Vries", ContactKind.PERSON, null, null, ContactEmailType.SHARED, null,
                null, false, null, false, false);
    }

    private Contact storedContact() {
        Contact contact = Contact.builder().name("Old name").role("Buyer").email("old@fabrikam.example")
                .emailType(ContactEmailType.PERSONAL).decisionMaker(true)
                .sourceUrl(SOURCE_URL).organizationId(organizationId).relationId(relation.getId()).build();
        contact.setId(UUID.randomUUID());
        when(contactRepository.findByIdAndRelationIdAndOrganizationId(contact.getId(), relation.getId(),
                organizationId)).thenReturn(Optional.of(contact));
        return contact;
    }

    @Nested
    class Create {

        @Test
        void contact_isStoredOnTheRelationInItsOrganization() {
            // Arrange
            stubRelation();
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(), request(false));

            // Assert
            ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
            verify(contactRepository).save(captor.capture());
            Contact saved = captor.getValue();
            assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
            assertThat(saved.getRelationId()).isEqualTo(relation.getId());
            assertThat(saved.isDecisionMaker()).isFalse();
            assertThat(response.name()).isEqualTo("Jan de Vries");
            assertThat(response.relationId()).isEqualTo(relation.getId());
            assertThat(response.linkedinUrl()).isEqualTo("https://www.linkedin.com/in/jandevries");
        }

        @Test
        void sourceUrl_isStoredAndReturned() {
            // Arrange
            stubRelation();
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(), request(true));

            // Assert
            ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
            verify(contactRepository).save(captor.capture());
            assertThat(captor.getValue().getSourceUrl()).isEqualTo(SOURCE_URL);
            assertThat(response.sourceUrl()).isEqualTo(SOURCE_URL);
        }

        @Test
        void withoutSourceUrl_storesNull() {
            // Arrange
            stubRelation();
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(),
                    new ContactCreateRequest("Jan de Vries", ContactKind.PERSON, null, null, null, null, null, false,
                            null, false, false));

            // Assert
            assertThat(response.sourceUrl()).isNull();
        }

        @Test
        void emailTypeWithEmail_isStoredAndReturned() {
            // Arrange
            stubRelation();
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(), request(true));

            // Assert
            ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
            verify(contactRepository).save(captor.capture());
            assertThat(captor.getValue().getEmailType()).isEqualTo(ContactEmailType.COMPANY);
            assertThat(response.emailType()).isEqualTo(ContactEmailType.COMPANY);
        }

        @Test
        void withoutEmailType_storesNull() {
            // Arrange
            stubRelation();
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(),
                    new ContactCreateRequest("Jan de Vries", ContactKind.PERSON, null, "jan@fabrikam.example", null, null, null,
                            false, null, false, false));

            // Assert
            assertThat(response.email()).isEqualTo("jan@fabrikam.example");
            assertThat(response.emailType()).isNull();
        }

        @Test
        void emailTypeWithoutEmail_throwsEmailTypeWithoutEmailAndSavesNothing() {
            // Arrange
            stubRelation();

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, relation.getId(), emailTypeWithoutEmail()))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_VAL_005"));
            verify(contactRepository, never()).save(any());
        }

        @Test
        void relationOfAnotherOrganization_throwsRelationNotFoundAndSavesNothing() {
            // Arrange
            UUID relationId = UUID.randomUUID();
            stubRelationMissing(relationId);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, relationId, request(true)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_NOT_001"));
            verify(contactRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void contact_getsEveryFieldOfTheRequest() {
            // Arrange
            stubRelation();
            Contact contact = storedContact();
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateRequest(null, null));

            // Assert: a null clears an optional field
            assertThat(contact.getName()).isEqualTo("Jan de Vries");
            assertThat(contact.getRole()).isNull();
            assertThat(contact.isDecisionMaker()).isFalse();
            assertThat(contact.getRelationId()).isEqualTo(relation.getId());
            assertThat(response.name()).isEqualTo("Jan de Vries");
        }

        @Test
        void emailWithNewType_replacesTheStoredType() {
            // Arrange
            stubRelation();
            Contact contact = storedContact();
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateRequest("info@fabrikam.example", ContactEmailType.SHARED));

            // Assert
            assertThat(contact.getEmail()).isEqualTo("info@fabrikam.example");
            assertThat(contact.getEmailType()).isEqualTo(ContactEmailType.SHARED);
            assertThat(response.emailType()).isEqualTo(ContactEmailType.SHARED);
        }

        @Test
        void nullEmailAndNullType_clearsBoth() {
            // Arrange
            stubRelation();
            Contact contact = storedContact();
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            service.update(organizationId, relation.getId(), contact.getId(), updateRequest(null, null));

            // Assert
            assertThat(contact.getEmail()).isNull();
            assertThat(contact.getEmailType()).isNull();
        }

        @Test
        void emailTypeWithoutEmail_throwsEmailTypeWithoutEmailAndKeepsTheContact() {
            // Arrange
            stubRelation();
            Contact contact = storedContact();

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(), contact.getId(),
                    updateRequest(null, ContactEmailType.SHARED)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL.getCode()));
            verify(contactRepository, never()).saveAndFlush(any());
            assertThat(contact.getEmail()).isEqualTo("old@fabrikam.example");
            assertThat(contact.getEmailType()).isEqualTo(ContactEmailType.PERSONAL);
        }

        @Test
        void update_keepsTheStoredSource() {
            // Arrange
            stubRelation();
            Contact contact = storedContact();
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateRequest(null, null));

            // Assert: the update request has no source, and the provenance must survive it
            assertThat(contact.getSourceUrl()).isEqualTo(SOURCE_URL);
            assertThat(response.sourceUrl()).isEqualTo(SOURCE_URL);
        }

        @Test
        void contactMissingOrOfAnotherRelation_throwsContactNotFound() {
            // Arrange
            stubRelation();
            UUID contactId = UUID.randomUUID();
            when(contactRepository.findByIdAndRelationIdAndOrganizationId(contactId, relation.getId(), organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(), contactId, updateRequest(true)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_CONTACT_FOUND.getCode()));
            verify(contactRepository, never()).saveAndFlush(any());
        }

        @Test
        void relationOfAnotherOrganization_throwsRelationNotFoundBeforeContactLookup() {
            // Arrange
            UUID relationId = UUID.randomUUID();
            stubRelationMissing(relationId);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relationId, UUID.randomUUID(), updateRequest(true)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_NOT_001"));
            verify(contactRepository, never()).findByIdAndRelationIdAndOrganizationId(any(), any(), any());
        }
    }

    @Nested
    class Delete {

        @Test
        void contactOfRelation_isDeleted() {
            // Arrange
            stubRelation();
            Contact contact = storedContact();

            // Act
            service.delete(organizationId, relation.getId(), contact.getId());

            // Assert
            verify(contactRepository).delete(contact);
        }

        @Test
        void contactMissing_throwsContactNotFoundAndDeletesNothing() {
            // Arrange
            stubRelation();
            UUID contactId = UUID.randomUUID();
            when(contactRepository.findByIdAndRelationIdAndOrganizationId(contactId, relation.getId(), organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, relation.getId(), contactId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_NOT_002"));
            verify(contactRepository, never()).delete(any(Contact.class));
        }
    }

    @Nested
    class ListContacts {

        @Test
        void contacts_areReadForTheRelationAndOrganizationByName() {
            // Arrange
            stubRelation();
            Contact contact = Contact.builder().name("Anna").organizationId(organizationId)
                    .relationId(relation.getId()).build();
            ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
            when(contactRepository.findByRelationIdAndOrganizationId(eq(relation.getId()), eq(organizationId),
                    sort.capture())).thenReturn(List.of(contact));

            // Act
            List<ContactListResponse> response = queryService.list(organizationId, relation.getId());

            // Assert
            assertThat(response).extracting(ContactListResponse::name).containsExactly("Anna");
            assertThat(sort.getValue().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @Test
        void relationOfAnotherOrganization_throwsRelationNotFound() {
            // Arrange
            UUID relationId = UUID.randomUUID();
            stubRelationMissing(relationId);

            // Act and assert
            assertThatThrownBy(() -> queryService.list(organizationId, relationId))
                    .isInstanceOf(NotFoundException.class);
            verify(contactRepository, never()).findByRelationIdAndOrganizationId(any(), any(), any());
        }
    }

    @Nested
    class MainContact {

        private Contact storedMain() {
            Contact contact = storedContact();
            contact.setMainPersonContact(true);
            return contact;
        }

        private ContactCreateRequest requestWithMain(boolean mainContact) {
            return new ContactCreateRequest("Jan de Vries", ContactKind.PERSON, null, null, null, null, null, false,
                    null, mainContact, false);
        }

        private ContactUpdateRequest updateWithMain(boolean mainContact) {
            return new ContactUpdateRequest("Jan de Vries", ContactKind.PERSON, null, null, null, null, null, false,
                    mainContact, false);
        }

        private Contact savedByCreate() {
            ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
            verify(contactRepository).save(captor.capture());
            return captor.getValue();
        }

        @Test
        void create_firstContactWithFalse_becomesMainWithoutClearing() {
            // Arrange
            stubRelation();
            when(contactRepository.existsByRelationIdAndOrganizationIdAndKind(relation.getId(), organizationId,
                    ContactKind.PERSON))
                    .thenReturn(false);
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(),
                    requestWithMain(false));

            // Assert
            assertThat(savedByCreate().isMainPersonContact()).isTrue();
            assertThat(response.mainPersonContact()).isTrue();
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
        }

        @Test
        void create_laterContactWithFalse_isNotMain() {
            // Arrange
            stubRelation();
            when(contactRepository.existsByRelationIdAndOrganizationIdAndKind(relation.getId(), organizationId,
                    ContactKind.PERSON))
                    .thenReturn(true);
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(),
                    requestWithMain(false));

            // Assert
            assertThat(savedByCreate().isMainPersonContact()).isFalse();
            assertThat(response.mainPersonContact()).isFalse();
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
        }

        @Test
        void create_laterContactWithTrue_clearsTheOldMainBeforeTheInsert() {
            // Arrange
            stubRelation();
            when(contactRepository.existsByRelationIdAndOrganizationIdAndKind(relation.getId(), organizationId,
                    ContactKind.PERSON))
                    .thenReturn(true);
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(),
                    requestWithMain(true));

            // Assert
            InOrder order = inOrder(contactRepository);
            order.verify(contactRepository).clearMainPersonContact(organizationId, relation.getId());
            order.verify(contactRepository).save(any(Contact.class));
            assertThat(response.mainPersonContact()).isTrue();
        }

        @Test
        void update_trueOnAnotherContact_clearsTheOldMainThenMarksIt() {
            // Arrange
            stubRelation();
            Contact contact = storedContact();
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateWithMain(true));

            // Assert
            verify(contactRepository).clearMainPersonContact(organizationId, relation.getId());
            assertThat(contact.isMainPersonContact()).isTrue();
            assertThat(response.mainPersonContact()).isTrue();
        }

        @Test
        void update_trueOnTheMain_isAPlainUpdate() {
            // Arrange
            stubRelation();
            Contact contact = storedMain();
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateWithMain(true));

            // Assert
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
            assertThat(contact.isMainPersonContact()).isTrue();
            assertThat(response.mainPersonContact()).isTrue();
        }

        @Test
        void update_falseOnTheMain_throwsValidationOnTheFlagAndWritesNothing() {
            // Arrange
            stubRelation();
            Contact contact = storedMain();

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(), contact.getId(),
                    updateWithMain(false)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) -> {
                        assertThat(exception.getErrorCode()).isEqualTo("GEN_VAL_001");
                        assertThat(exception.getErrorValues()).containsOnlyKeys("mainPersonContact");
                    });
            verify(contactRepository, never()).saveAndFlush(any());
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
            assertThat(contact.isMainPersonContact()).isTrue();
            assertThat(contact.getName()).isEqualTo("Old name");
        }

        @Test
        void update_falseOnAnotherContact_keepsItNotMain() {
            // Arrange
            stubRelation();
            Contact contact = storedContact();
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            service.update(organizationId, relation.getId(), contact.getId(), updateWithMain(false));

            // Assert
            assertThat(contact.isMainPersonContact()).isFalse();
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
        }

        @Test
        void delete_theMain_promotesTheOldestRemainingAfterTheDeleteIsFlushed() {
            // Arrange
            stubRelation();
            Contact main = storedMain();
            Contact oldest = Contact.builder().name("Anna").organizationId(organizationId)
                    .relationId(relation.getId()).build();
            when(contactRepository.findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc(relation.getId(),
                    organizationId, ContactKind.PERSON)).thenReturn(Optional.of(oldest));

            // Act
            service.delete(organizationId, relation.getId(), main.getId());

            // Assert
            InOrder order = inOrder(contactRepository);
            order.verify(contactRepository).delete(main);
            order.verify(contactRepository).flush();
            order.verify(contactRepository).save(oldest);
            assertThat(oldest.isMainPersonContact()).isTrue();
        }

        @Test
        void delete_theLastContact_promotesNobody() {
            // Arrange
            stubRelation();
            Contact main = storedMain();
            when(contactRepository.findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc(relation.getId(),
                    organizationId, ContactKind.PERSON)).thenReturn(Optional.empty());

            // Act
            service.delete(organizationId, relation.getId(), main.getId());

            // Assert
            verify(contactRepository).delete(main);
            verify(contactRepository, never()).save(any(Contact.class));
        }

        @Test
        void delete_aNonMain_changesNoOtherContact() {
            // Arrange
            stubRelation();
            Contact contact = storedContact();

            // Act
            service.delete(organizationId, relation.getId(), contact.getId());

            // Assert
            verify(contactRepository).delete(contact);
            verify(contactRepository, never())
                    .findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc(any(), any(), any());
            verify(contactRepository, never()).save(any(Contact.class));
        }

        @Test
        void list_ordersMainFirstThenNameThenId() {
            // Arrange
            stubRelation();
            Contact main = Contact.builder().name("Zed").organizationId(organizationId)
                    .relationId(relation.getId()).build();
            main.setMainPersonContact(true);
            ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
            when(contactRepository.findByRelationIdAndOrganizationId(eq(relation.getId()), eq(organizationId),
                    sort.capture())).thenReturn(List.of(main));

            // Act
            List<ContactListResponse> response = queryService.list(organizationId, relation.getId());

            // Assert
            assertThat(sort.getValue()).isEqualTo(Sort.by(Sort.Order.desc("mainPersonContact"),
                    Sort.Order.desc("mainBusinessContact"), Sort.Order.asc("name"), Sort.Order.asc("id")));
            assertThat(response.get(0).mainPersonContact()).isTrue();
        }
    }

    @Nested
    class ContactKinds {

        private ContactCreateRequest requestOf(ContactKind kind, boolean mainPerson, boolean mainBusiness) {
            return new ContactCreateRequest("Info", kind, null, null, null, null, null, false, null, mainPerson,
                    mainBusiness);
        }

        private ContactUpdateRequest updateOf(ContactKind kind, boolean mainPerson, boolean mainBusiness) {
            return new ContactUpdateRequest("Info", kind, null, null, null, null, null, false, mainPerson,
                    mainBusiness);
        }

        private Contact stored(ContactKind kind, boolean mainPerson, boolean mainBusiness) {
            Contact contact = storedContact();
            contact.setKind(kind);
            contact.setMainPersonContact(mainPerson);
            contact.setMainBusinessContact(mainBusiness);
            return contact;
        }

        private void stubExists(ContactKind kind, boolean exists) {
            when(contactRepository.existsByRelationIdAndOrganizationIdAndKind(relation.getId(), organizationId,
                    kind)).thenReturn(exists);
        }

        private void stubOldest(ContactKind kind, Contact oldest) {
            when(contactRepository.findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc(
                    relation.getId(), organizationId, kind)).thenReturn(Optional.ofNullable(oldest));
        }

        private Contact oldestOf(ContactKind kind) {
            return Contact.builder().name("Oldest").kind(kind).organizationId(organizationId)
                    .relationId(relation.getId()).build();
        }

        private Contact createdContact() {
            ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
            verify(contactRepository).save(captor.capture());
            return captor.getValue();
        }

        @Test
        void create_firstPerson_isMainPersonOnly() {
            // Arrange
            stubRelation();
            stubExists(ContactKind.PERSON, false);
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(),
                    requestOf(ContactKind.PERSON, false, false));

            // Assert
            assertThat(response.kind()).isEqualTo(ContactKind.PERSON);
            assertThat(response.mainPersonContact()).isTrue();
            assertThat(response.mainBusinessContact()).isFalse();
        }

        @Test
        void create_firstBusiness_isMainBusinessOnlyAndClearsNothing() {
            // Arrange
            stubRelation();
            stubExists(ContactKind.BUSINESS, false);
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(),
                    requestOf(ContactKind.BUSINESS, false, false));

            // Assert
            assertThat(response.kind()).isEqualTo(ContactKind.BUSINESS);
            assertThat(response.mainBusinessContact()).isTrue();
            assertThat(response.mainPersonContact()).isFalse();
            verify(contactRepository, never()).clearMainBusinessContact(any(), any());
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
        }

        @Test
        void create_laterBusinessWithTrue_clearsOnlyTheBusinessMain() {
            // Arrange
            stubRelation();
            stubExists(ContactKind.BUSINESS, true);
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ContactDetailResponse response = service.create(organizationId, user, relation.getId(),
                    requestOf(ContactKind.BUSINESS, false, true));

            // Assert
            InOrder order = inOrder(contactRepository);
            order.verify(contactRepository).clearMainBusinessContact(organizationId, relation.getId());
            order.verify(contactRepository).save(any(Contact.class));
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
            assertThat(response.mainBusinessContact()).isTrue();
        }

        @Test
        void create_laterPersonWithTrue_clearsOnlyThePersonMain() {
            // Arrange
            stubRelation();
            stubExists(ContactKind.PERSON, true);
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            service.create(organizationId, user, relation.getId(), requestOf(ContactKind.PERSON, true, false));

            // Assert
            verify(contactRepository).clearMainPersonContact(organizationId, relation.getId());
            verify(contactRepository, never()).clearMainBusinessContact(any(), any());
        }

        @Test
        void create_firstBusinessWhenAPersonExists_isMainBusinessEvenWithoutAFlag() {
            // Arrange: the existing person is a different kind, so it does not count
            stubRelation();
            stubExists(ContactKind.BUSINESS, false);
            when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            service.create(organizationId, user, relation.getId(), requestOf(ContactKind.BUSINESS, false, false));

            // Assert
            assertThat(createdContact().isMainBusinessContact()).isTrue();
        }

        @Test
        void create_personWithBusinessFlag_throwsMainFlagNotForKindBeforeAnyWrite() {
            // Arrange
            stubRelation();

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, relation.getId(),
                    requestOf(ContactKind.PERSON, false, true)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_VAL_013"));
            verify(contactRepository, never()).save(any());
            verify(contactRepository, never()).clearMainBusinessContact(any(), any());
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
        }

        @Test
        void create_businessWithPersonFlag_throwsMainFlagNotForKindBeforeAnyWrite() {
            // Arrange
            stubRelation();

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, relation.getId(),
                    requestOf(ContactKind.BUSINESS, true, false)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_VAL_013"));
            verify(contactRepository, never()).save(any());
        }

        @Test
        void update_sameKind_keepsTheKindAndTheMainFlag() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.BUSINESS, false, true);
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.BUSINESS, false, true));

            // Assert
            assertThat(contact.getKind()).isEqualTo(ContactKind.BUSINESS);
            assertThat(response.kind()).isEqualTo(ContactKind.BUSINESS);
            assertThat(contact.isMainBusinessContact()).isTrue();
            verify(contactRepository, never()).existsByRelationIdAndOrganizationIdAndKind(any(), any(), any());
        }

        @Test
        void update_sameKindFalseOnTheMainBusiness_throwsValidationOnTheFlagAndWritesNothing() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.BUSINESS, false, true);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.BUSINESS, false, false)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) -> {
                        assertThat(exception.getErrorCode()).isEqualTo("GEN_VAL_001");
                        assertThat(exception.getErrorValues()).containsOnlyKeys("mainBusinessContact");
                    });
            verify(contactRepository, never()).saveAndFlush(any());
            verify(contactRepository, never()).clearMainBusinessContact(any(), any());
            assertThat(contact.isMainBusinessContact()).isTrue();
        }

        @Test
        void update_otherKindFlagTrueOnTheMainPersonOfTheSameKind_throwsMainFlagNotForKindFirst() {
            // Arrange: the kind and flag fit check runs before the keep-main check
            stubRelation();
            Contact contact = stored(ContactKind.PERSON, true, false);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.PERSON, false, true)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_VAL_013"));
            verify(contactRepository, never()).saveAndFlush(any());
        }

        @Test
        void update_trueFlagOnANonMainBusiness_clearsOnlyTheBusinessMainThenMarksIt() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.BUSINESS, false, false);
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.BUSINESS, false, true));

            // Assert
            verify(contactRepository).clearMainBusinessContact(organizationId, relation.getId());
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
            assertThat(contact.isMainBusinessContact()).isTrue();
        }

        @Test
        void update_otherKindFlag_throwsMainFlagNotForKindAndWritesNothing() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.PERSON, true, false);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.PERSON, true, true)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_VAL_013"));
            verify(contactRepository, never()).saveAndFlush(any());
            verify(contactRepository, never()).clearMainBusinessContact(any(), any());
            assertThat(contact.isMainPersonContact()).isTrue();
        }

        @Test
        void update_flagOfTheNewKindOnAKindChange_isAccepted() {
            // Arrange: PERSON to BUSINESS with the business flag is valid, the flag names the new kind
            stubRelation();
            Contact contact = stored(ContactKind.PERSON, false, false);
            stubExists(ContactKind.BUSINESS, true);
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.BUSINESS, false, true));

            // Assert
            assertThat(contact.getKind()).isEqualTo(ContactKind.BUSINESS);
            assertThat(contact.isMainBusinessContact()).isTrue();
            verify(contactRepository).clearMainBusinessContact(organizationId, relation.getId());
        }

        @Test
        void update_kindChangeOfTheMainPerson_promotesTheOldestPersonAndBecomesFirstMainBusiness() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.PERSON, true, false);
            Contact oldestPerson = oldestOf(ContactKind.PERSON);
            stubExists(ContactKind.BUSINESS, false);
            stubOldest(ContactKind.PERSON, oldestPerson);
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.BUSINESS, false, false));

            // Assert: the old flag is gone and flushed before the promotion, so no unique index sees two
            InOrder order = inOrder(contactRepository);
            order.verify(contactRepository).saveAndFlush(contact);
            order.verify(contactRepository).save(oldestPerson);
            assertThat(oldestPerson.isMainPersonContact()).isTrue();
            assertThat(contact.getKind()).isEqualTo(ContactKind.BUSINESS);
            assertThat(response.mainPersonContact()).isFalse();
            assertThat(response.mainBusinessContact()).isTrue();
        }

        @Test
        void update_kindChangeOfTheOnlyMainPerson_leavesThePersonKindWithoutAMain() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.PERSON, true, false);
            stubExists(ContactKind.BUSINESS, true);
            stubOldest(ContactKind.PERSON, null);
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.BUSINESS, false, false));

            // Assert: a business exists already and nothing asks for main, so the contact is plain
            verify(contactRepository, never()).save(any(Contact.class));
            assertThat(response.mainPersonContact()).isFalse();
            assertThat(response.mainBusinessContact()).isFalse();
        }

        @Test
        void update_kindChangeOfANonMainContact_promotesNobodyAndKeepsTheMains() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.PERSON, false, false);
            stubExists(ContactKind.BUSINESS, true);
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.BUSINESS, false, false));

            // Assert
            assertThat(response.kind()).isEqualTo(ContactKind.BUSINESS);
            assertThat(response.mainBusinessContact()).isFalse();
            verify(contactRepository, never())
                    .findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc(any(), any(), any());
            verify(contactRepository, never()).save(any(Contact.class));
            verify(contactRepository, never()).clearMainBusinessContact(any(), any());
            verify(contactRepository, never()).clearMainPersonContact(any(), any());
        }

        @Test
        void update_kindChangeOfANonMainContactIntoAKindWithoutContacts_becomesItsMain() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.PERSON, false, false);
            stubExists(ContactKind.BUSINESS, false);
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.BUSINESS, false, false));

            // Assert
            assertThat(response.mainBusinessContact()).isTrue();
        }

        @Test
        void update_kindChangeOfTheMainBusiness_promotesTheOldestBusiness() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.BUSINESS, false, true);
            Contact oldestBusiness = oldestOf(ContactKind.BUSINESS);
            stubExists(ContactKind.PERSON, true);
            stubOldest(ContactKind.BUSINESS, oldestBusiness);
            when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

            // Act
            ContactDetailResponse response = service.update(organizationId, relation.getId(), contact.getId(),
                    updateOf(ContactKind.PERSON, false, false));

            // Assert
            assertThat(oldestBusiness.isMainBusinessContact()).isTrue();
            assertThat(response.kind()).isEqualTo(ContactKind.PERSON);
            assertThat(response.mainBusinessContact()).isFalse();
            assertThat(response.mainPersonContact()).isFalse();
        }

        @Test
        void delete_theMainBusiness_promotesTheOldestBusinessOnly() {
            // Arrange
            stubRelation();
            Contact main = stored(ContactKind.BUSINESS, false, true);
            Contact oldestBusiness = oldestOf(ContactKind.BUSINESS);
            stubOldest(ContactKind.BUSINESS, oldestBusiness);

            // Act
            service.delete(organizationId, relation.getId(), main.getId());

            // Assert
            InOrder order = inOrder(contactRepository);
            order.verify(contactRepository).delete(main);
            order.verify(contactRepository).flush();
            order.verify(contactRepository).save(oldestBusiness);
            assertThat(oldestBusiness.isMainBusinessContact()).isTrue();
            assertThat(oldestBusiness.isMainPersonContact()).isFalse();
            verify(contactRepository, never()).findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc(
                    relation.getId(), organizationId, ContactKind.PERSON);
        }

        @Test
        void delete_aNonMainBusiness_promotesNobody() {
            // Arrange
            stubRelation();
            Contact contact = stored(ContactKind.BUSINESS, false, false);

            // Act
            service.delete(organizationId, relation.getId(), contact.getId());

            // Assert
            verify(contactRepository, never())
                    .findFirstByRelationIdAndOrganizationIdAndKindOrderByCreatedAtAscIdAsc(any(), any(), any());
        }
    }
}
