package com.korur.oba.apps.otak.mailsender.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mailsender.constant.MailSenderConstant;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;
import com.korur.oba.apps.otak.mailsender.repository.MailSenderRepository;
import com.korur.oba.connect.notification.client.MsGraphClient;
import com.korur.oba.connect.notification.dto.GraphMailbox;
import com.korur.oba.connect.notification.dto.GraphMailboxLookup;
import com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class MailSenderValidatorUnitTest {

    private static final String MAIL_ADDRESS = "jane.doe@example.com";

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private MailSenderRepository mailSenderRepository;

    @Mock
    private MsGraphClient msGraphClient;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private MailSenderValidator mailSenderValidator;

    @Nested
    class EnsureMailSenderExists {

        @Test
        void senderOfOrganization_returnsIt() {
            // Arrange
            UUID mailSenderId = UUID.randomUUID();
            MailSender mailSender = MailSender.builder().mailAddress(MAIL_ADDRESS).build();
            when(mailSenderRepository.findByIdAndOrganizationId(mailSenderId, organizationId))
                    .thenReturn(Optional.of(mailSender));

            // Act
            MailSender result = mailSenderValidator.ensureMailSenderExists(organizationId, mailSenderId);

            // Assert
            assertThat(result).isSameAs(mailSender);
        }

        @Test
        void senderMissingOrOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID mailSenderId = UUID.randomUUID();
            when(mailSenderRepository.findByIdAndOrganizationId(mailSenderId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> mailSenderValidator.ensureMailSenderExists(organizationId, mailSenderId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_MAIL_SENDER_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureMailAddressFree {

        @Test
        void addressNotInOrganization_passes() {
            // Arrange
            when(mailSenderRepository.existsByOrganizationIdAndMailAddressIgnoreCase(organizationId, MAIL_ADDRESS))
                    .thenReturn(false);

            // Act and assert
            assertThatCode(() -> mailSenderValidator.ensureMailAddressFree(organizationId, MAIL_ADDRESS))
                    .doesNotThrowAnyException();
        }

        @Test
        void addressTakenCaseIgnored_throwsConflict() {
            // Arrange
            when(mailSenderRepository.existsByOrganizationIdAndMailAddressIgnoreCase(organizationId, MAIL_ADDRESS))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> mailSenderValidator.ensureMailAddressFree(organizationId, MAIL_ADDRESS))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) -> {
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.STA_MAIL_SENDER_TAKEN.getCode());
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    });
        }
    }

    @Nested
    class EnsureMailboxInTenant {

        @Test
        void mailboxFound_returnsIt() {
            // Arrange
            GraphMailbox mailbox = new GraphMailbox(null, MAIL_ADDRESS);
            when(msGraphClient.findMailbox(MAIL_ADDRESS)).thenReturn(new GraphMailboxLookup.Found(mailbox));

            // Act
            GraphMailbox result = mailSenderValidator.ensureMailboxInTenant(organizationId, MAIL_ADDRESS);

            // Assert
            assertThat(result).isSameAs(mailbox);
        }

        @Test
        void accountNotFound_throwsMailboxNotInTenantWith422() {
            // Arrange
            when(msGraphClient.findMailbox(MAIL_ADDRESS)).thenReturn(new GraphMailboxLookup.AccountNotFound());

            // Act and assert
            assertThatThrownBy(() -> mailSenderValidator.ensureMailboxInTenant(organizationId, MAIL_ADDRESS))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) -> {
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.VAL_MAILBOX_NOT_IN_TENANT.getCode());
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                        assertThat(exception.getErrorValues())
                                .containsEntry(MailSenderConstant.FIELD_MAIL_ADDRESS, MAIL_ADDRESS);
                    });
        }

        @Test
        void accountWithoutMailbox_throwsAccountWithoutMailboxWith422() {
            // Arrange
            when(msGraphClient.findMailbox(MAIL_ADDRESS)).thenReturn(new GraphMailboxLookup.AccountWithoutMailbox());

            // Act and assert
            assertThatThrownBy(() -> mailSenderValidator.ensureMailboxInTenant(organizationId, MAIL_ADDRESS))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) -> {
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.VAL_ACCOUNT_WITHOUT_MAILBOX.getCode());
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                        assertThat(exception.getErrorValues())
                                .containsEntry(MailSenderConstant.FIELD_MAIL_ADDRESS, MAIL_ADDRESS);
                    });
        }

        @Test
        void graphFailure_propagatesTheUpstreamError() {
            // Arrange
            when(msGraphClient.findMailbox(MAIL_ADDRESS)).thenThrow(
                    new BadGatewayException(ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION));

            // Act and assert
            assertThatThrownBy(() -> mailSenderValidator.ensureMailboxInTenant(organizationId, MAIL_ADDRESS))
                    .isInstanceOfSatisfying(BadGatewayException.class, (BadGatewayException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION.getCode()));
        }
    }

    @Nested
    class EnsureSharedMailboxInTenant {

        @Test
        void sharedMailboxFound_returnsIt() {
            // Arrange
            GraphMailbox mailbox = new GraphMailbox(null, MAIL_ADDRESS);
            when(msGraphClient.findSharedMailbox(MAIL_ADDRESS)).thenReturn(Optional.of(mailbox));

            // Act
            GraphMailbox result = mailSenderValidator.ensureSharedMailboxInTenant(organizationId, MAIL_ADDRESS);

            // Assert
            assertThat(result).isSameAs(mailbox);
        }

        @Test
        void noSharedMailbox_throwsSharedMailboxNotFoundWith422() {
            // Arrange
            when(msGraphClient.findSharedMailbox(MAIL_ADDRESS)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> mailSenderValidator.ensureSharedMailboxInTenant(organizationId, MAIL_ADDRESS))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) -> {
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.VAL_SHARED_MAILBOX_NOT_FOUND.getCode());
                        assertThat(exception.getErrorCode()).isEqualTo("OTK_VAL_004");
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                        assertThat(exception.getErrorValues())
                                .containsEntry(MailSenderConstant.FIELD_MAIL_ADDRESS, MAIL_ADDRESS);
                    });
        }
    }
}
