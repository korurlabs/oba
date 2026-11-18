package com.korur.oba.apps.otak.documenttemplate.file;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.nio.charset.StandardCharsets;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.connect.azure.service.AzureBlobStorageQueryService;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class DocumentFileStorageUnitTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7 content".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HTML_BYTES = "<html></html>".getBytes(StandardCharsets.UTF_8);

    @Mock
    private AzureBlobStorageService azureBlobStorageService;

    @Mock
    private AzureBlobStorageQueryService azureBlobStorageQueryService;

    @Mock
    private LoggingService loggingService;

    private DocumentFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new DocumentFileStorage(azureBlobStorageService, azureBlobStorageQueryService, loggingService);
    }

    private static MockMultipartFile html() {
        return new MockMultipartFile("html", "a.html", "text/html", HTML_BYTES);
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("pdf", "a.pdf", "application/pdf", PDF_BYTES);
    }

    private static void assertNotHtmlOrPdf(Throwable thrown) {
        assertThat(thrown).isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainOtakError.VAL_FILE_NOT_HTML_OR_PDF.getCode()));
    }

    @Nested
    class Store {

        @Test
        void validFiles_uploadsBothUnderNewFlatNamesAndReturnsTheUrls() {
            // Arrange
            when(azureBlobStorageService.uploadFile(anyString(), any(byte[].class)))
                    .thenAnswer(invocation -> "https://blob/" + invocation.getArgument(0));

            // Act
            StoredDocumentFiles stored = storage.store(html(), pdf());

            // Assert
            ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
            verify(azureBlobStorageService, org.mockito.Mockito.times(2)).uploadFile(names.capture(), any(byte[].class));
            assertThat(names.getAllValues().get(0)).matches("[0-9a-f-]{36}\\.html");
            assertThat(names.getAllValues().get(1)).matches("[0-9a-f-]{36}\\.pdf");
            assertThat(stored.htmlUrl()).isEqualTo("https://blob/" + names.getAllValues().get(0));
            assertThat(stored.pdfUrl()).isEqualTo("https://blob/" + names.getAllValues().get(1));
        }

        @Test
        void pdfUploadFails_deletesTheHtmlBlobAndRethrows() {
            // Arrange
            RuntimeException failure = new IllegalStateException("storage down");
            when(azureBlobStorageService.uploadFile(anyString(), any(byte[].class)))
                    .thenReturn("https://blob/new.html")
                    .thenThrow(failure);

            // Act and assert
            assertThatThrownBy(() -> storage.store(html(), pdf())).isSameAs(failure);
            verify(azureBlobStorageService).deleteFile("https://blob/new.html");
        }

        @Test
        void htmlWithWrongContentType_isRefusedBeforeAnyUpload() {
            MockMultipartFile wrong = new MockMultipartFile("html", "a.html", "text/plain", HTML_BYTES);

            assertThatThrownBy(() -> storage.store(wrong, pdf())).satisfies(DocumentFileStorageUnitTest::assertNotHtmlOrPdf);
            verifyNoInteractions(azureBlobStorageService);
        }

        @Test
        void htmlWithoutContentType_isRefused() {
            MockMultipartFile wrong = new MockMultipartFile("html", "a.html", null, HTML_BYTES);

            assertThatThrownBy(() -> storage.store(wrong, pdf())).satisfies(DocumentFileStorageUnitTest::assertNotHtmlOrPdf);
        }

        @Test
        void htmlWithInvalidContentType_isRefused() {
            MockMultipartFile wrong = new MockMultipartFile("html", "a.html", "not a media type", HTML_BYTES);

            assertThatThrownBy(() -> storage.store(wrong, pdf())).satisfies(DocumentFileStorageUnitTest::assertNotHtmlOrPdf);
        }

        @Test
        void emptyPdf_isRefused() {
            MockMultipartFile empty = new MockMultipartFile("pdf", "a.pdf", "application/pdf", new byte[0]);

            assertThatThrownBy(() -> storage.store(html(), empty)).satisfies(DocumentFileStorageUnitTest::assertNotHtmlOrPdf);
            verifyNoInteractions(azureBlobStorageService);
        }

        @Test
        void pdfWithoutPdfMagic_isRefused() {
            MockMultipartFile fake = new MockMultipartFile("pdf", "a.pdf", "application/pdf",
                    "plain text".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> storage.store(html(), fake)).satisfies(DocumentFileStorageUnitTest::assertNotHtmlOrPdf);
            verifyNoInteractions(azureBlobStorageService);
        }

        @Test
        void missingFile_isRefused() {
            assertThatThrownBy(() -> storage.store(null, pdf())).satisfies(DocumentFileStorageUnitTest::assertNotHtmlOrPdf);
        }
    }

    @Nested
    class ReadPdf {

        @Test
        void storedPdf_isDownloadedByItsLastUrlSegment() {
            // Arrange
            when(azureBlobStorageQueryService.downloadPrivateBlob("old.pdf")).thenReturn(PDF_BYTES);

            // Act
            byte[] pdf = storage.readPdf("https://blob/container/old.pdf");

            // Assert
            assertThat(pdf).isSameAs(PDF_BYTES);
        }
    }

    @Nested
    class DeleteQuietly {

        @Test
        void nullUrls_areSkipped() {
            storage.deleteQuietly(null, "https://blob/a.pdf");

            verify(azureBlobStorageService).deleteFile("https://blob/a.pdf");
            verify(azureBlobStorageService, never()).deleteFile(null);
        }

        @Test
        void deleteFails_isLoggedAndTheNextBlobIsStillDeleted() {
            // Arrange
            org.mockito.Mockito.doThrow(new IllegalStateException("gone")).when(azureBlobStorageService)
                    .deleteFile("https://blob/a.html");

            // Act
            storage.deleteQuietly("https://blob/a.html", "https://blob/a.pdf");

            // Assert
            verify(azureBlobStorageService).deleteFile("https://blob/a.pdf");
            verify(loggingService).error(any(), any(), anyString(), any(Throwable.class), any());
        }
    }
}
