package com.korur.oba.apps.otak.mailsender.controller;

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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.otak.mailsender.constant.controller.MailSenderControllerResponseConstant;
import com.korur.oba.apps.otak.mailsender.dto.request.MailSenderCreateRequest;
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.apps.otak.mailsender.enums.EmailType;
import com.korur.oba.apps.otak.mailsender.service.MailSenderQueryService;
import com.korur.oba.apps.otak.mailsender.service.MailSenderService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class MailSenderControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private MailSenderService mailSenderService;

    @Mock
    private MailSenderQueryService mailSenderQueryService;

    private MailSenderController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new MailSenderController(mailSenderService, mailSenderQueryService, new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_listRequiresMemberAndWritesRequireAdmin() {
        // Arrange
        Map<String, OrganizationMemberRole> expected = Map.of(
                "listMailSenders", OrganizationMemberRole.MEMBER,
                "createMailSender", OrganizationMemberRole.ADMIN,
                "deleteMailSender", OrganizationMemberRole.ADMIN);

        // Act
        List<Method> handlers = Arrays.stream(MailSenderController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        // Assert
        assertThat(handlers.stream().map(Method::getName).collect(Collectors.toSet()))
                .isEqualTo(expected.keySet());
        assertThat(handlers).allSatisfy(method -> {
            assertThat(method.isAnnotationPresent(PreAuthorize.class)).isFalse();
            RequiredUserAccess access = method.getAnnotation(RequiredUserAccess.class);
            assertThat(access).as(method.getName()).isNotNull();
            assertThat(access.value()).as(method.getName()).isEqualTo(expected.get(method.getName()));
        });
    }

    @Test
    void controller_servesTheOtakMailSendersPath() {
        assertThat(MailSenderController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/mail-senders");
    }

    @Test
    void listMailSenders_delegatesToTheQueryServiceAndReturns200() {
        // Arrange
        MailSenderListResponse item = mock(MailSenderListResponse.class);
        when(mailSenderQueryService.list(organizationId)).thenReturn(List.of(item));

        // Act
        ResponseEntity<CustomResponse<List<MailSenderListResponse>>> response =
                controller.listMailSenders(user, organizationId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(MailSenderControllerResponseConstant.RESPONSE_MAIL_SENDER_LIST_RETRIEVED);
        assertThat(response.getBody().data()).containsExactly(item);
    }

    @Test
    void createMailSender_delegatesWithHeaderOrganizationAndReturns201() {
        // Arrange
        MailSenderCreateRequest request = new MailSenderCreateRequest(EmailType.PERSONAL, "jane.doe@example.com");
        MailSenderListResponse created = mock(MailSenderListResponse.class);
        when(mailSenderService.create(organizationId, user, request)).thenReturn(created);

        // Act
        ResponseEntity<CustomResponse<MailSenderListResponse>> response =
                controller.createMailSender(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message())
                .isEqualTo(MailSenderControllerResponseConstant.RESPONSE_MAIL_SENDER_CREATED);
        assertThat(response.getBody().data()).isSameAs(created);
    }

    @Test
    void deleteMailSender_delegatesAndReturns200() {
        // Arrange
        UUID mailSenderId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response = controller.deleteMailSender(user, organizationId, mailSenderId);

        // Assert
        verify(mailSenderService).delete(organizationId, mailSenderId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(MailSenderControllerResponseConstant.RESPONSE_MAIL_SENDER_DELETED);
    }
}
