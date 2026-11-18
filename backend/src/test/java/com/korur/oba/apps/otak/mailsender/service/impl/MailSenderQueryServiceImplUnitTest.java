package com.korur.oba.apps.otak.mailsender.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;
import com.korur.oba.apps.otak.mailsender.enums.EmailType;
import com.korur.oba.apps.otak.mailsender.mapper.MailSenderMapperImpl;
import com.korur.oba.apps.otak.mailsender.repository.MailSenderRepository;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class MailSenderQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private MailSenderRepository mailSenderRepository;

    @Mock
    private LoggingService loggingService;

    private MailSenderQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MailSenderQueryServiceImpl(mailSenderRepository, new MailSenderMapperImpl(), loggingService);
    }

    @Test
    void list_mapsEverySenderOfTheOrganizationInRepositoryOrder() {
        // Arrange
        UUID addedBy = UUID.randomUUID();
        MailSender first = MailSender.builder().mailAddress("a@example.com").type(EmailType.PERSONAL)
                .addedBy(addedBy).organizationId(organizationId).build();
        first.setId(UUID.randomUUID());
        MailSender second = MailSender.builder().mailAddress("b@example.com").type(EmailType.SHARED)
                .addedBy(addedBy).organizationId(organizationId).build();
        second.setId(UUID.randomUUID());
        when(mailSenderRepository.findAllByOrganizationIdOrderByMailAddressAscIdAsc(organizationId))
                .thenReturn(List.of(first, second));

        // Act
        List<MailSenderListResponse> result = service.list(organizationId);

        // Assert
        assertThat(result).extracting(MailSenderListResponse::mailAddress).containsExactly("a@example.com", "b@example.com");
        assertThat(result.get(0)).isEqualTo(
                new MailSenderListResponse(first.getId(), "a@example.com", EmailType.PERSONAL, addedBy, null));
    }

    @Test
    void list_organizationWithoutSenders_returnsEmptyList() {
        // Arrange
        when(mailSenderRepository.findAllByOrganizationIdOrderByMailAddressAscIdAsc(organizationId))
                .thenReturn(List.of());

        // Act and assert
        assertThat(service.list(organizationId)).isEmpty();
    }
}
