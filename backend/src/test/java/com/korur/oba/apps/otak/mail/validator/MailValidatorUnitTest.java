package com.korur.oba.apps.otak.mail.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.repository.MailRepository;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class MailValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private MailRepository mailRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private MailValidator validator;

    @Nested
    class EnsureMailExists {

        @Test
        void mailOfOrganization_returnsIt() {
            Mail mail = Mail.builder().id(UUID.randomUUID()).organizationId(organizationId).build();
            when(mailRepository.findByIdAndOrganizationId(mail.getId(), organizationId)).thenReturn(Optional.of(mail));

            assertThat(validator.ensureMailExists(organizationId, mail.getId())).isSameAs(mail);
        }

        @Test
        void mailMissingOrOfAnotherOrganization_throwsGenericNotFound() {
            UUID mailId = UUID.randomUUID();
            when(mailRepository.findByIdAndOrganizationId(mailId, organizationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureMailExists(organizationId, mailId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionNotFoundError.RESOURCE_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureMailExistsInAnyOrganization {

        @Test
        void mailExists_returnsIt() {
            Mail mail = Mail.builder().id(UUID.randomUUID()).organizationId(organizationId).build();
            when(mailRepository.findById(mail.getId())).thenReturn(Optional.of(mail));

            assertThat(validator.ensureMailExists(mail.getId())).isSameAs(mail);
        }

        @Test
        void mailMissing_throwsOtakNotMailFoundWithTheMailId() {
            UUID mailId = UUID.randomUUID();
            when(mailRepository.findById(mailId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureMailExists(mailId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) -> {
                        assertThat(exception.getErrorCode()).isEqualTo("OTK_NOT_011");
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.NOT_MAIL_FOUND.getCode());
                    });
            verify(loggingService).warn(any(), any(), any(), eq(mailId));
        }
    }

    @Nested
    class EnsureMailUnsent {

        private final Mail mail = Mail.builder().id(UUID.randomUUID()).organizationId(organizationId).build();

        @Test
        void outreachSent_throwsConflictOtkSta011AndLogs() {
            when(mailRepository.findOutreachStatusForUpdate(mail.getId())).thenReturn(Optional.of(OutreachStatus.SENT));

            assertThatThrownBy(() -> validator.ensureMailUnsent(mail))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_STA_011"));
            verify(loggingService).warn(any(), any(), any(), eq(mail.getId()), eq(organizationId));
        }

        @Test
        void outreachDraft_passes() {
            when(mailRepository.findOutreachStatusForUpdate(mail.getId())).thenReturn(Optional.of(OutreachStatus.DRAFT));

            assertThatCode(() -> validator.ensureMailUnsent(mail)).doesNotThrowAnyException();
            verify(loggingService, never()).warn(any(), any(), any(), any(), any());
        }

        @Test
        void outreachFailed_passes() {
            when(mailRepository.findOutreachStatusForUpdate(mail.getId())).thenReturn(Optional.of(OutreachStatus.FAILED));

            assertThatCode(() -> validator.ensureMailUnsent(mail)).doesNotThrowAnyException();
        }

        @Test
        void noOutreach_passes() {
            when(mailRepository.findOutreachStatusForUpdate(mail.getId())).thenReturn(Optional.empty());

            assertThatCode(() -> validator.ensureMailUnsent(mail)).doesNotThrowAnyException();
        }
    }
}
