package com.korur.oba.apps.otak.relation.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.relation.dto.response.RelationAttachmentResponse;
import com.korur.oba.apps.otak.relation.enums.RelationAttachmentSource;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

@ExtendWith(MockitoExtension.class)
class RelationAttachmentQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID relationId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-22T10:00:00Z");

    @Mock
    private AttachmentQueryService attachmentQueryService;

    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;

    @Mock
    private RelationQueryService relationQueryService;

    @Mock
    private LoggingService loggingService;

    private RelationAttachmentQueryServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RelationAttachmentQueryServiceImpl(attachmentQueryService, resourceAccessQueryService,
                relationQueryService, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private AttachmentResponse file(StorageEntityType type, UUID entityId, Instant createdAt, UUID documentId) {
        return new AttachmentResponse(UUID.randomUUID(), "signed", "file.pdf", "application/pdf", 10L, type, entityId,
                createdAt, null, null, documentId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void relationWithMails_mergesOwnAndMailFilesNewestFirstWithSourceAndOutreachTitle() {
        // Arrange
        UUID mailId = UUID.randomUUID();
        UUID mailWithoutOutreachId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AttachmentResponse own = file(StorageEntityType.RELATION, relationId, now.minusSeconds(60), null);
        AttachmentResponse snapshot = file(StorageEntityType.MAIL, mailId, now, documentId);
        AttachmentResponse loose = file(StorageEntityType.MAIL, mailWithoutOutreachId, now.minusSeconds(120), null);
        when(attachmentQueryService.listAttachmentsByEntity(user, organizationId, StorageEntityType.RELATION,
                relationId)).thenReturn(List.of(own));
        Map<UUID, String> titles = new HashMap<>();
        titles.put(mailId, "First contact");
        titles.put(mailWithoutOutreachId, null);
        when(relationQueryService.getMailOutreachTitles(organizationId, relationId)).thenReturn(titles);
        Map<UUID, List<AttachmentResponse>> mailFiles = new LinkedHashMap<>();
        mailFiles.put(mailId, List.of(snapshot));
        mailFiles.put(mailWithoutOutreachId, List.of(loose));
        when(resourceAccessQueryService.withReadGrant(eq(user), eq(ResourceAccessType.MAIL), any(Collection.class),
                any(Supplier.class))).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());
        when(attachmentQueryService.listAttachmentsByEntities(eq(user), eq(organizationId), eq(StorageEntityType.MAIL),
                any())).thenReturn(mailFiles);

        // Act
        List<RelationAttachmentResponse> files = service.list(user, organizationId, relationId);

        // Assert
        assertThat(files).extracting(RelationAttachmentResponse::id)
                .containsExactly(snapshot.id(), own.id(), loose.id());
        RelationAttachmentResponse first = files.get(0);
        assertThat(first.source()).isEqualTo(RelationAttachmentSource.MAIL);
        assertThat(first.mailId()).isEqualTo(mailId);
        assertThat(first.outreachTitle()).isEqualTo("First contact");
        assertThat(first.documentId()).isEqualTo(documentId);
        RelationAttachmentResponse second = files.get(1);
        assertThat(second.source()).isEqualTo(RelationAttachmentSource.RELATION);
        assertThat(second.mailId()).isNull();
        assertThat(second.outreachTitle()).isNull();
        assertThat(files.get(2).outreachTitle()).isNull();
    }

    @Test
    void relationWithoutMails_readsNoMailFiles() {
        // Arrange
        when(attachmentQueryService.listAttachmentsByEntity(user, organizationId, StorageEntityType.RELATION,
                relationId)).thenReturn(List.of());
        when(relationQueryService.getMailOutreachTitles(organizationId, relationId)).thenReturn(Map.of());

        // Act
        List<RelationAttachmentResponse> files = service.list(user, organizationId, relationId);

        // Assert
        assertThat(files).isEmpty();
        verifyNoInteractions(resourceAccessQueryService);
    }

    @Test
    void relationOfAnotherOrganization_throwsNotFoundBeforeReadingMails() {
        // Arrange
        when(relationQueryService.getEntity(organizationId, relationId))
                .thenThrow(new NotFoundException(ExceptionDomainOtakError.NOT_RELATION_FOUND));

        // Act and assert
        assertThatThrownBy(() -> service.list(user, organizationId, relationId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ExceptionDomainOtakError.NOT_RELATION_FOUND.getCode());
        verifyNoInteractions(attachmentQueryService, resourceAccessQueryService);
    }
}
