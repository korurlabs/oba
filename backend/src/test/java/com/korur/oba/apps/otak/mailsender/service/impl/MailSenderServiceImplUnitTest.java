package com.korur.oba.apps.otak.mailsender.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mailsender.dto.request.MailSenderCreateRequest;
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;
import com.korur.oba.apps.otak.mailsender.enums.EmailType;
import com.korur.oba.apps.otak.mailsender.mapper.MailSenderMapperImpl;
import com.korur.oba.apps.otak.mailsender.repository.MailSenderRepository;
import com.korur.oba.apps.otak.mailsender.validator.MailSenderValidator;
import com.korur.oba.connect.notification.client.MsGraphClient;
import com.korur.oba.connect.notification.dto.GraphMailbox;
import com.korur.oba.connect.notification.dto.GraphMailboxLookup;
import com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class MailSenderServiceImplUnitTest {

    private static final String MAIL_ADDRESS = "Jane.Doe@example.com";

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private MailSenderRepository mailSenderRepository;

    @Mock
    private MsGraphClient msGraphClient;

    @Mock
    private LoggingService loggingService;

    private MailSenderServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real validator and mapper run over the mocked repository and Graph client, so the
        // error codes and the mapped fields are the production ones.
        service = new MailSenderServiceImpl(mailSenderRepository, new MailSenderMapperImpl(),
                new MailSenderValidator(mailSenderRepository, msGraphClient, loggingService), loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private void savesWhatItIsGiven() {
        when(mailSenderRepository.save(any(MailSender.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class Create {

        @Test
        void mailboxFound_savesTrimmedAddressAndAddedBy() {
            // Arrange
            when(msGraphClient.findMailbox(MAIL_ADDRESS))
                    .thenReturn(new GraphMailboxLookup.Found(new GraphMailbox(null, "jane.doe@example.com")));
            savesWhatItIsGiven();

            // Act
            MailSenderListResponse response = service.create(organizationId, user,
                    new MailSenderCreateRequest(EmailType.PERSONAL, "  " + MAIL_ADDRESS + "  "));

            // Assert
            ArgumentCaptor<MailSender> captor = ArgumentCaptor.forClass(MailSender.class);
            verify(mailSenderRepository).save(captor.capture());
            MailSender saved = captor.getValue();
            assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
            assertThat(saved.getMailAddress()).isEqualTo(MAIL_ADDRESS);
            assertThat(saved.getAddedBy()).isEqualTo(user.getId());
            assertThat(response.mailAddress()).isEqualTo(MAIL_ADDRESS);
            assertThat(response.addedBy()).isEqualTo(user.getId());
        }

        @Test
        void validRequest_checksTheAddressBeforeAskingGraphAndSaving() {
            // Arrange
            when(msGraphClient.findMailbox(MAIL_ADDRESS))
                    .thenReturn(new GraphMailboxLookup.Found(new GraphMailbox(null, MAIL_ADDRESS)));
            savesWhatItIsGiven();

            // Act
            service.create(organizationId, user, new MailSenderCreateRequest(EmailType.PERSONAL, " " + MAIL_ADDRESS + " "));

            // Assert
            InOrder order = inOrder(mailSenderRepository, msGraphClient);
            order.verify(mailSenderRepository).existsByOrganizationIdAndMailAddressIgnoreCase(organizationId, MAIL_ADDRESS);
            order.verify(msGraphClient).findMailbox(MAIL_ADDRESS);
            order.verify(mailSenderRepository).save(any(MailSender.class));
        }

        @Test
        void addressTaken_throwsConflictWithoutAskingGraph() {
            // Arrange
            when(mailSenderRepository.existsByOrganizationIdAndMailAddressIgnoreCase(organizationId, MAIL_ADDRESS))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, new MailSenderCreateRequest(EmailType.PERSONAL, MAIL_ADDRESS)))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_MAIL_SENDER_TAKEN.getCode()));
            verify(msGraphClient, never()).findMailbox(anyString());
            verify(mailSenderRepository, never()).save(any());
        }

        @Test
        void mailboxNotInTenant_throwsValidationAndSavesNothing() {
            // Arrange
            when(msGraphClient.findMailbox(MAIL_ADDRESS)).thenReturn(new GraphMailboxLookup.AccountNotFound());

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, new MailSenderCreateRequest(EmailType.PERSONAL, MAIL_ADDRESS)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_MAILBOX_NOT_IN_TENANT.getCode()));
            verify(mailSenderRepository, never()).save(any());
        }

        @Test
        void accountWithoutMailbox_throwsValidationAndSavesNothing() {
            // Arrange
            when(msGraphClient.findMailbox(MAIL_ADDRESS)).thenReturn(new GraphMailboxLookup.AccountWithoutMailbox());

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, new MailSenderCreateRequest(EmailType.PERSONAL, MAIL_ADDRESS)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_ACCOUNT_WITHOUT_MAILBOX.getCode()));
            verify(mailSenderRepository, never()).save(any());
        }

        @Test
        void graphFailure_throwsTheUpstreamErrorAndSavesNothing() {
            // Arrange
            when(msGraphClient.findMailbox(MAIL_ADDRESS)).thenThrow(
                    new BadGatewayException(ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION));

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, new MailSenderCreateRequest(EmailType.PERSONAL, MAIL_ADDRESS)))
                    .isInstanceOf(BadGatewayException.class);
            verify(mailSenderRepository, never()).save(any());
        }

        @Test
        void personalType_asksOnlyThePersonalLookupAndSavesThePersonalType() {
            // Arrange
            when(msGraphClient.findMailbox(MAIL_ADDRESS))
                    .thenReturn(new GraphMailboxLookup.Found(new GraphMailbox(null, MAIL_ADDRESS)));
            savesWhatItIsGiven();

            // Act
            MailSenderListResponse response = service.create(organizationId, user,
                    new MailSenderCreateRequest(EmailType.PERSONAL, MAIL_ADDRESS));

            // Assert
            verify(msGraphClient).findMailbox(MAIL_ADDRESS);
            verify(msGraphClient, never()).findSharedMailbox(anyString());
            ArgumentCaptor<MailSender> captor = ArgumentCaptor.forClass(MailSender.class);
            verify(mailSenderRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(EmailType.PERSONAL);
            assertThat(response.type()).isEqualTo(EmailType.PERSONAL);
        }

        @Test
        void sharedType_asksOnlyTheSharedLookupAndSavesTheSharedType() {
            // Arrange
            when(msGraphClient.findSharedMailbox(MAIL_ADDRESS)).thenReturn(Optional.of(new GraphMailbox(null, MAIL_ADDRESS)));
            savesWhatItIsGiven();

            // Act
            MailSenderListResponse response = service.create(organizationId, user,
                    new MailSenderCreateRequest(EmailType.SHARED, " " + MAIL_ADDRESS + " "));

            // Assert
            verify(msGraphClient).findSharedMailbox(MAIL_ADDRESS);
            verify(msGraphClient, never()).findMailbox(anyString());
            ArgumentCaptor<MailSender> captor = ArgumentCaptor.forClass(MailSender.class);
            verify(mailSenderRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(EmailType.SHARED);
            assertThat(response.type()).isEqualTo(EmailType.SHARED);
        }

        @Test
        void sharedTypeWithoutSharedMailbox_throwsOtkVal004AndSavesNothing() {
            // Arrange
            when(msGraphClient.findSharedMailbox(MAIL_ADDRESS)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, new MailSenderCreateRequest(EmailType.SHARED, MAIL_ADDRESS)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_SHARED_MAILBOX_NOT_FOUND.getCode()));
            verify(mailSenderRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(EmailType.class)
        void addressTaken_callsNeitherLookup(EmailType type) {
            // Arrange
            when(mailSenderRepository.existsByOrganizationIdAndMailAddressIgnoreCase(organizationId, MAIL_ADDRESS))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, new MailSenderCreateRequest(type, MAIL_ADDRESS)))
                    .isInstanceOf(ConflictException.class);
            verify(msGraphClient, never()).findMailbox(anyString());
            verify(msGraphClient, never()).findSharedMailbox(anyString());
            verify(mailSenderRepository, never()).save(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void senderOfOrganization_isDeleted() {
            // Arrange
            MailSender mailSender = MailSender.builder().mailAddress(MAIL_ADDRESS).organizationId(organizationId).build();
            mailSender.setId(UUID.randomUUID());
            when(mailSenderRepository.findByIdAndOrganizationId(mailSender.getId(), organizationId))
                    .thenReturn(Optional.of(mailSender));

            // Act
            service.delete(organizationId, mailSender.getId());

            // Assert
            verify(mailSenderRepository).delete(mailSender);
        }

        @Test
        void senderOfAnotherOrganization_throwsNotFoundAndDeletesNothing() {
            // Arrange
            UUID mailSenderId = UUID.randomUUID();
            when(mailSenderRepository.findByIdAndOrganizationId(mailSenderId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, mailSenderId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_MAIL_SENDER_FOUND.getCode()));
            verify(mailSenderRepository, never()).delete(any(MailSender.class));
        }
    }
}
