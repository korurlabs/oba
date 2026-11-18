package com.korur.oba.apps.otak.mail.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.repository.MailRepository;
import com.korur.oba.apps.otak.mail.validator.MailValidator;

@ExtendWith(MockitoExtension.class)
class MailQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private MailRepository mailRepository;

    @Mock
    private MailValidator mailValidator;

    @InjectMocks
    private MailQueryServiceImpl service;

    @Nested
    class GetEntityById {

        @Test
        void mailExists_returnsTheValidatorsMailWithoutOrganizationScope() {
            Mail mail = Mail.builder().id(UUID.randomUUID()).organizationId(organizationId).build();
            when(mailValidator.ensureMailExists(mail.getId())).thenReturn(mail);

            assertThat(service.getEntityById(mail.getId())).isSameAs(mail);
        }
    }

    @Nested
    class GetIdsByRelation {

        @Test
        void relationWithMails_returnsTheIdsOfTheOrganization() {
            UUID relationId = UUID.randomUUID();
            List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
            when(mailRepository.findIdsByOrganizationIdAndRelationId(organizationId, relationId)).thenReturn(ids);

            assertThat(service.getIdsByRelation(organizationId, relationId)).isEqualTo(ids);
            verify(mailRepository).findIdsByOrganizationIdAndRelationId(organizationId, relationId);
        }

        @Test
        void relationWithoutMails_returnsEmpty() {
            UUID relationId = UUID.randomUUID();
            when(mailRepository.findIdsByOrganizationIdAndRelationId(organizationId, relationId)).thenReturn(List.of());

            assertThat(service.getIdsByRelation(organizationId, relationId)).isEmpty();
        }
    }
}
