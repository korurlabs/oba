package com.korur.oba.apps.otak.contact.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.otak.contact.constant.controller.ContactControllerResponseConstant;
import com.korur.oba.apps.otak.contact.dto.request.ContactCreateRequest;
import com.korur.oba.apps.otak.contact.dto.request.ContactUpdateRequest;
import com.korur.oba.apps.otak.contact.dto.response.ContactDetailResponse;
import com.korur.oba.apps.otak.contact.dto.response.ContactListResponse;
import com.korur.oba.apps.otak.contact.service.ContactQueryService;
import com.korur.oba.apps.otak.contact.service.ContactService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class ContactControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID relationId = UUID.randomUUID();

    @Mock
    private ContactService contactService;

    @Mock
    private ContactQueryService contactQueryService;

    private ContactController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new ContactController(contactService, contactQueryService, new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_everyHandlerRequiresMember() {
        // Arrange
        Set<String> expected = Set.of("listContacts", "createContact", "updateContact", "deleteContact");

        // Act
        List<Method> handlers = Arrays.stream(ContactController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        // Assert: every public method is a handler with MEMBER as its minimum role
        assertThat(handlers.stream().map(Method::getName).collect(Collectors.toSet())).isEqualTo(expected);
        assertThat(handlers).allSatisfy(method -> {
            assertThat(method.isAnnotationPresent(PreAuthorize.class)).isFalse();
            RequiredUserAccess access = method.getAnnotation(RequiredUserAccess.class);
            assertThat(access).as(method.getName()).isNotNull();
            assertThat(access.value()).as(method.getName()).isEqualTo(OrganizationMemberRole.MEMBER);
        });
    }

    @Test
    void controller_servesTheContactsPathOfARelation() {
        assertThat(ContactController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/relations/{relationId}/contacts");
    }

    @Test
    void createContact_delegatesWithHeaderOrganizationAndReturns201() {
        // Arrange
        ContactCreateRequest request = mock(ContactCreateRequest.class);
        ContactDetailResponse contact = mock(ContactDetailResponse.class);
        when(contactService.create(organizationId, user, relationId, request)).thenReturn(contact);

        // Act
        ResponseEntity<CustomResponse<ContactDetailResponse>> response =
                controller.createContact(user, organizationId, relationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message()).isEqualTo(ContactControllerResponseConstant.RESPONSE_CONTACT_CREATED);
        assertThat(response.getBody().data()).isSameAs(contact);
    }

    @Test
    void deleteContact_delegatesAndReturns200WithoutData() {
        // Arrange
        UUID contactId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response =
                controller.deleteContact(user, organizationId, relationId, contactId);

        // Assert
        verify(contactService).delete(organizationId, relationId, contactId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(ContactControllerResponseConstant.RESPONSE_CONTACT_DELETED);
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void updateContact_delegatesWithPathIdsAndReturnsDetail() {
        // Arrange
        UUID contactId = UUID.randomUUID();
        ContactUpdateRequest request = mock(ContactUpdateRequest.class);
        ContactDetailResponse contact = mock(ContactDetailResponse.class);
        when(contactService.update(organizationId, relationId, contactId, request)).thenReturn(contact);

        // Act
        ResponseEntity<CustomResponse<ContactDetailResponse>> response =
                controller.updateContact(user, organizationId, relationId, contactId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(ContactControllerResponseConstant.RESPONSE_CONTACT_UPDATED);
        assertThat(response.getBody().data()).isSameAs(contact);
    }

    @Test
    void listContacts_delegatesToTheQueryServiceAndReturnsTheListItems() {
        // Arrange
        ContactListResponse item = mock(ContactListResponse.class);
        when(contactQueryService.list(organizationId, relationId)).thenReturn(List.of(item));

        // Act
        ResponseEntity<CustomResponse<List<ContactListResponse>>> response =
                controller.listContacts(user, organizationId, relationId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(ContactControllerResponseConstant.RESPONSE_CONTACT_LIST_RETRIEVED);
        assertThat(response.getBody().data()).containsExactly(item);
    }
}
