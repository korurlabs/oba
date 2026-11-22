package com.korur.oba.shrine.document.validator.template;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError;
import com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository;

@ExtendWith(MockitoExtension.class)
class DocumentTemplateValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private DocumentTemplateRepository documentTemplateRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private DocumentTemplateValidator validator;

    @Nested
    class EnsureTemplateExists {

        @Test
        void templateOfOrganization_returnsIt() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            DocumentTemplate template = DocumentTemplate.builder().id(templateId).build();
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));

            // Act
            DocumentTemplate result = validator.ensureTemplateExists(organizationId, templateId);

            // Assert
            assertThat(result).isSameAs(template);
        }

        @Test
        void templateMissingOrOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> validator.ensureTemplateExists(organizationId, templateId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainDocumentError.NOT_DOCUMENT_TEMPLATE_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureNameFree {

        @Test
        void createAndNameFree_passes() {
            // Arrange
            when(documentTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguage(
                    organizationId, "First contact", DocumentLanguage.NL)).thenReturn(false);

            // Act and assert
            assertThatCode(() -> validator.ensureNameFree(organizationId, "First contact", DocumentLanguage.NL, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void createAndNameTaken_throwsConflictWithTheNameTakenCode() {
            // Arrange
            when(documentTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguage(
                    organizationId, "First contact", DocumentLanguage.NL)).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> validator.ensureNameFree(organizationId, "First contact",
                    DocumentLanguage.NL, null))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainDocumentError.STA_DOCUMENT_TEMPLATE_NAME_TAKEN.getCode()));
        }

        @Test
        void update_asksWithoutTheTemplateItself() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            when(documentTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                    organizationId, "First contact", DocumentLanguage.EN, templateId)).thenReturn(false);

            // Act
            validator.ensureNameFree(organizationId, "First contact", DocumentLanguage.EN, templateId);

            // Assert
            verify(documentTemplateRepository).existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                    organizationId, "First contact", DocumentLanguage.EN, templateId);
            verify(documentTemplateRepository, never())
                    .existsByOrganizationIdAndNameIgnoreCaseAndLanguage(any(), any(), any());
        }

        @Test
        void updateAndNameTakenByAnotherTemplate_throwsConflict() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            when(documentTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                    organizationId, "NDA", DocumentLanguage.TR, templateId)).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> validator.ensureNameFree(organizationId, "NDA", DocumentLanguage.TR, templateId))
                    .isInstanceOf(ConflictException.class);
        }
    }
}
