package com.korur.oba.apps.otak.outreach.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.dto.response.ContactListResponse;
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.service.ContactQueryService;
import com.korur.oba.apps.otak.contact.validator.ContactValidator;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;
import com.korur.oba.apps.otak.mailsender.service.MailSenderQueryService;
import com.korur.oba.apps.otak.mailsender.validator.MailSenderValidator;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachQueryRequest;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachDetailResponse;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachListResponse;
import com.korur.oba.apps.otak.outreach.entity.Outreach;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;
import com.korur.oba.apps.otak.outreach.mapper.OutreachMapperImpl;
import com.korur.oba.apps.otak.outreach.repository.OutreachRepository;
import com.korur.oba.apps.otak.outreach.validator.OutreachValidator;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class OutreachQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID relationId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();
    private final UUID outreachId = UUID.randomUUID();
    private final UUID mailId = UUID.randomUUID();

    @Mock
    private OutreachRepository outreachRepository;

    @Mock
    private RelationQueryService relationQueryService;

    @Mock
    private ContactQueryService contactQueryService;

    @Mock
    private ContactValidator contactValidator;

    @Mock
    private MailSenderQueryService mailSenderQueryService;

    @Mock
    private MailSenderValidator mailSenderValidator;

    @Mock
    private MailQueryService mailQueryService;

    @Mock
    private LoggingService loggingService;

    private OutreachQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OutreachQueryServiceImpl(outreachRepository, new OutreachMapperImpl(),
                new OutreachValidator(outreachRepository, loggingService), relationQueryService,
                contactQueryService, contactValidator, mailSenderQueryService, mailSenderValidator,
                mailQueryService, loggingService);
    }

    private Outreach outreach(OutreachStatus status, UUID mailSenderId, UUID outreachContactId) {
        return Outreach.builder().id(outreachId).title("Title").type(OutreachType.MAIL).status(status)
                .organizationId(organizationId).relationId(relationId).contactId(outreachContactId)
                .mailId(mailId).mailSenderId(mailSenderId).build();
    }

    @Test
    void list_withRelationFilter_checksTheRelationAndPagesNewestFirst() {
        // Arrange
        when(outreachRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        OutreachQueryRequest request = new OutreachQueryRequest();
        request.setRelationId(relationId);

        // Act
        Page<OutreachListResponse> result = service.list(organizationId, request);

        // Assert
        verify(relationQueryService).getEntity(organizationId, relationId);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(outreachRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort().stream().map(order -> order.getProperty() + order.getDirection()))
                .containsExactly("createdAtDESC", "idDESC");
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void list_withoutRelationFilter_doesNotCheckAnyRelation() {
        // Arrange
        when(outreachRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // Act
        service.list(organizationId, new OutreachQueryRequest());

        // Assert
        verify(relationQueryService, never()).getEntity(any(), any());
    }

    @Test
    void list_relationOfAnotherOrganization_throwsNotFound() {
        // Arrange
        NotFoundException notFound = new NotFoundException(ExceptionDomainOtakError.NOT_CONTACT_FOUND, Map.of());
        when(relationQueryService.getEntity(organizationId, relationId)).thenThrow(notFound);
        OutreachQueryRequest request = new OutreachQueryRequest();
        request.setRelationId(relationId);

        // Act and assert
        assertThatThrownBy(() -> service.list(organizationId, request)).isSameAs(notFound);
    }

    @Test
    void list_mapsContactAndSenderAddressAndLeavesMissingOnesNull() {
        // Arrange
        UUID senderId = UUID.randomUUID();
        Outreach sent = outreach(OutreachStatus.SENT, senderId, contactId);
        Outreach orphan = Outreach.builder().id(UUID.randomUUID()).title("Orphan").status(OutreachStatus.DRAFT)
                .type(OutreachType.MAIL).organizationId(organizationId).relationId(relationId).build();
        when(outreachRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sent, orphan)));
        ContactListResponse contact = new ContactListResponse(contactId, relationId, "Jan de Vries", null, null, null, null, null,
                null, false, false, false, null);
        when(contactQueryService.list(organizationId, relationId)).thenReturn(List.of(contact));
        MailSenderListResponse senderResponse = org.mockito.Mockito.mock(MailSenderListResponse.class);
        when(senderResponse.id()).thenReturn(senderId);
        when(senderResponse.mailAddress()).thenReturn("sales@example.com");
        when(mailSenderQueryService.list(organizationId)).thenReturn(List.of(senderResponse));

        // Act
        Page<OutreachListResponse> result = service.list(organizationId, new OutreachQueryRequest());

        // Assert
        OutreachListResponse first = result.getContent().get(0);
        assertThat(first.contact().id()).isEqualTo(contactId);
        assertThat(first.contact().name()).isEqualTo("Jan de Vries");
        assertThat(first.mailSenderAddress()).isEqualTo("sales@example.com");
        OutreachListResponse second = result.getContent().get(1);
        assertThat(second.contact()).isNull();
        assertThat(second.mailSenderAddress()).isNull();
    }

    @Test
    void get_returnsContactSenderAndMail() {
        // Arrange
        UUID senderId = UUID.randomUUID();
        Outreach outreach = outreach(OutreachStatus.SENT, senderId, contactId);
        outreach.setSentTo("jan@fabrikam.example");
        outreach.setSentAt(Instant.parse("2026-11-14T10:00:00Z"));
        when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                .thenReturn(Optional.of(outreach));
        when(contactValidator.ensureContactExists(organizationId, relationId, contactId))
                .thenReturn(Contact.builder().id(contactId).name("Jan de Vries").build());
        when(mailSenderValidator.ensureMailSenderExists(organizationId, senderId))
                .thenReturn(MailSender.builder().id(senderId).mailAddress("sales@example.com").build());
        UUID mailTemplateId = UUID.randomUUID();
        Mail mail = Mail.builder().id(mailId).mailTemplateId(mailTemplateId).subject("Hello").body("Body")
                .language(DocumentLanguage.NL).build();
        when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(mail);

        // Act
        OutreachDetailResponse detail = service.get(organizationId, outreachId);

        // Assert
        assertThat(detail.status()).isEqualTo(OutreachStatus.SENT);
        assertThat(detail.sentTo()).isEqualTo("jan@fabrikam.example");
        assertThat(detail.mailSenderAddress()).isEqualTo("sales@example.com");
        assertThat(detail.contact().name()).isEqualTo("Jan de Vries");
        assertThat(detail.mail().id()).isEqualTo(mailId);
        assertThat(detail.mail().mailTemplateId()).isEqualTo(mailTemplateId);
        assertThat(detail.mail().subject()).isEqualTo("Hello");
        assertThat(detail.mail().body()).isEqualTo("Body");
        assertThat(detail.mail().language()).isEqualTo(DocumentLanguage.NL);
    }

    @Test
    void get_contactAndSenderDeleted_returnsNullsInsteadOfFailing() {
        // Arrange
        Outreach outreach = outreach(OutreachStatus.DRAFT, null, null);
        when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                .thenReturn(Optional.of(outreach));
        when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(Mail.builder().id(mailId).build());

        // Act
        OutreachDetailResponse detail = service.get(organizationId, outreachId);

        // Assert
        assertThat(detail.contact()).isNull();
        assertThat(detail.mailSenderAddress()).isNull();
    }

    @Test
    void get_outreachWithoutMail_returnsNoMail() {
        // Arrange
        Outreach outreach = outreach(OutreachStatus.DRAFT, null, null);
        outreach.setMailId(null);
        when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                .thenReturn(Optional.of(outreach));

        // Act
        OutreachDetailResponse detail = service.get(organizationId, outreachId);

        // Assert
        assertThat(detail.mail()).isNull();
        verify(mailQueryService, never()).getEntity(any(), any());
    }

    @Test
    void get_outreachOfAnotherOrganization_throwsNotFound() {
        // Arrange
        when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId)).thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.get(organizationId, outreachId))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.NOT_OUTREACH_FOUND.getCode()));
    }
}
