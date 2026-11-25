package com.korur.oba.apps.otak.outreach.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachCreateRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachUpdateRequest;
import com.korur.oba.apps.otak.outreach.entity.Outreach;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

class OutreachMapperUnitTest {

    private OutreachMapper outreachMapper;

    @BeforeEach
    void setUp() {
        outreachMapper = new OutreachMapperImpl();
    }

    private static Outreach stored() {
        Outreach outreach = Outreach.builder()
                .title("Old title")
                .contactId(UUID.randomUUID())
                .relationId(UUID.randomUUID())
                .organizationId(UUID.randomUUID())
                .mailId(UUID.randomUUID())
                .mailSenderId(UUID.randomUUID())
                .type(OutreachType.MAIL)
                .status(OutreachStatus.SENT)
                .sentTo("jan@fabrikam.example")
                .sentAt(Instant.parse("2026-11-01T10:00:00Z"))
                .build();
        outreach.setId(UUID.randomUUID());
        return outreach;
    }

    @Test
    void toEntity_request_copiesRelationContactAndTitle() {
        // Arrange
        UUID relationId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        // Act
        Outreach outreach = outreachMapper.toEntity(new OutreachCreateRequest(relationId, contactId, "Intro", OutreachType.MAIL, null,
                new MailRequest("S", "B", DocumentLanguage.EN), null, null));

        // Assert
        assertThat(outreach.getRelationId()).isEqualTo(relationId);
        assertThat(outreach.getContactId()).isEqualTo(contactId);
        assertThat(outreach.getTitle()).isEqualTo("Intro");
        assertThat(outreach.getMailId()).isNull();
        assertThat(outreach.getSentAt()).isNull();
    }

    @Test
    void updateEntity_request_replacesContactAndTitleAndKeepsTheRest() {
        // Arrange
        Outreach outreach = stored();
        UUID id = outreach.getId();
        UUID relationId = outreach.getRelationId();
        UUID mailId = outreach.getMailId();
        UUID newContact = UUID.randomUUID();

        // Act
        outreachMapper.updateEntity(outreach, new OutreachUpdateRequest(newContact, "New title",
                new MailRequest("S", "B", DocumentLanguage.NL), null));

        // Assert
        assertThat(outreach.getContactId()).isEqualTo(newContact);
        assertThat(outreach.getTitle()).isEqualTo("New title");
        assertThat(outreach.getId()).isEqualTo(id);
        assertThat(outreach.getRelationId()).isEqualTo(relationId);
        assertThat(outreach.getMailId()).isEqualTo(mailId);
        assertThat(outreach.getStatus()).isEqualTo(OutreachStatus.SENT);
        assertThat(outreach.getSentTo()).isEqualTo("jan@fabrikam.example");
    }
}
