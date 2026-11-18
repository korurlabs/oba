package com.korur.oba.connect.azure.service.unit;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 3. Java SDK
import java.util.HashMap;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.azure.constant.service.BlobUrlRewriterLogConstant;
import com.korur.oba.connect.azure.properties.AzureBlobStorageProperties;
import com.korur.oba.connect.azure.service.AzureBlobStorageQueryService;
import com.korur.oba.connect.azure.service.BlobUrlRewriter;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BlobUrlRewriter.
 *
 * Construction is direct (not @InjectMocks) because BlobUrlRewriter
 * normalizes the base URL at constructor time, and tests in
 * BaseUrlNormalization need to vary the raw value passed into the
 * constructor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlobUrlRewriterUnitTest {

    private static final String BASE_URL = "https://examplestorage.blob.core.windows.net";
    private static final String DEFAULT_CONTAINER_KEY = "default";
    private static final String DEFAULT_CONTAINER_NAME = "dev-container";
    private static final String ACCOUNTING_CONTAINER_KEY = "accounting_invoice";
    private static final String ACCOUNTING_CONTAINER_NAME = "accounting-invoice";
    private static final String RAW_URL = BASE_URL + "/" + DEFAULT_CONTAINER_NAME + "/abc123.webp";
    private static final String RAW_URL_INVOICE = BASE_URL + "/" + ACCOUNTING_CONTAINER_NAME + "/invoice-456.pdf";
    private static final String SIGNED_URL = RAW_URL + "?sv=2022-11-02&sig=FAKESIG";
    private static final String SIGNED_URL_INVOICE = RAW_URL_INVOICE + "?sv=2022-11-02&sig=FAKESIG2";
    private static final String OTHER_HOST_URL = "https://other.blob.core.windows.net/container/file.png";

    @Mock
    private AzureBlobStorageQueryService azureBlobStorageQueryService;

    @Mock
    private LoggingService loggingService;

    private BlobUrlRewriter rewriter;

    @BeforeEach
    void setUp() {
        rewriter = new BlobUrlRewriter(buildProperties(BASE_URL), azureBlobStorageQueryService, loggingService);
    }

    private AzureBlobStorageProperties buildProperties(String baseUrl) {
        AzureBlobStorageProperties props = new AzureBlobStorageProperties();
        props.setBlobBaseUrl(baseUrl);
        Map<String, String> containers = new HashMap<>();
        containers.put(DEFAULT_CONTAINER_KEY, DEFAULT_CONTAINER_NAME);
        containers.put(ACCOUNTING_CONTAINER_KEY, ACCOUNTING_CONTAINER_NAME);
        props.setContainers(containers);
        return props;
    }

    @Nested
    class RewriteUrl {

        @Test
        void rewriteUrl_validBlobUrl_returnsSignedUrl() {
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);

            String result = rewriter.rewriteUrl(RAW_URL);

            assertEquals(SIGNED_URL, result);
        }

        @Test
        void rewriteUrl_invoiceContainerUrl_routesToCorrectContainerKey() {
            when(azureBlobStorageQueryService.generateSasUrl(ACCOUNTING_CONTAINER_KEY, "invoice-456.pdf"))
                    .thenReturn(SIGNED_URL_INVOICE);

            String result = rewriter.rewriteUrl(RAW_URL_INVOICE);

            assertEquals(SIGNED_URL_INVOICE, result);
        }

        @Test
        void rewriteUrl_differentHost_returnsUnchanged() {
            String result = rewriter.rewriteUrl(OTHER_HOST_URL);

            assertEquals(OTHER_HOST_URL, result);
            verify(azureBlobStorageQueryService, never()).generateSasUrl(anyString(), anyString());
        }

        @Test
        void rewriteUrl_nullInput_returnsNull() {
            String result = rewriter.rewriteUrl(null);

            assertNull(result);
            verify(azureBlobStorageQueryService, never()).generateSasUrl(anyString(), anyString());
        }

        @Test
        void rewriteUrl_blankInput_returnsBlank() {
            String result = rewriter.rewriteUrl("   ");

            assertEquals("   ", result);
            verify(azureBlobStorageQueryService, never()).generateSasUrl(anyString(), anyString());
        }

        @Test
        void rewriteUrl_alreadyContainsQueryString_stripsAndReSigns() {
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);

            String result = rewriter.rewriteUrl(SIGNED_URL);

            assertEquals(SIGNED_URL, result);
            verify(azureBlobStorageQueryService).generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp");
        }

        @Test
        void rewriteUrl_generateSasThrows_returnsOriginalUrlAndLogsErrorWithException() {
            RuntimeException sasFailure = new RuntimeException("boom");
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenThrow(sasFailure);

            String result = rewriter.rewriteUrl(RAW_URL);

            assertEquals(RAW_URL, result);
            verify(loggingService).error(any(), eq(LogLayer.SERVICE),
                    eq(BlobUrlRewriterLogConstant.REWRITE_FAILED),
                    eq(sasFailure),
                    eq(DEFAULT_CONTAINER_KEY), eq("abc123.webp"));
        }

        @Test
        void rewriteUrl_unknownContainerName_returnsOriginalUrlAndLogsError() {
            String unknownContainerUrl = BASE_URL + "/unknown-container/file.png";

            String result = rewriter.rewriteUrl(unknownContainerUrl);

            assertEquals(unknownContainerUrl, result);
            verify(azureBlobStorageQueryService, never()).generateSasUrl(anyString(), anyString());
            verify(loggingService).error(any(), eq(LogLayer.SERVICE),
                    eq(BlobUrlRewriterLogConstant.REWRITE_UNKNOWN_CONTAINER),
                    eq("unknown-container"));
        }

        @Test
        void hostnamePrefixAttack_doesNotRewriteEvilDomain() {
            String evilHostUrl = "https://examplestorage.blob.core.windows.net.evil.com/"
                    + DEFAULT_CONTAINER_NAME + "/uuid.webp";

            String result = rewriter.rewriteUrl(evilHostUrl);

            assertEquals(evilHostUrl, result);
            verify(azureBlobStorageQueryService, never()).generateSasUrl(anyString(), anyString());
        }

        @Test
        void rewriteUrl_blobNameWithSubdirectory_passesFullPathToService() {
            String subPathUrl = BASE_URL + "/" + DEFAULT_CONTAINER_NAME + "/org-id/abc123.webp";
            String signedSubPath = subPathUrl + "?sv=...";
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "org-id/abc123.webp"))
                    .thenReturn(signedSubPath);

            String result = rewriter.rewriteUrl(subPathUrl);

            assertEquals(signedSubPath, result);
        }

        @Test
        void rewriteUrl_baseUrlOnlyNoContainer_returnsUnchanged() {
            String result = rewriter.rewriteUrl(BASE_URL);

            assertEquals(BASE_URL, result);
            verify(azureBlobStorageQueryService, never()).generateSasUrl(anyString(), anyString());
        }
    }

    @Nested
    class RewriteHtmlBlobUrls {

        @Test
        void rewriteHtmlBlobUrls_singleImgWithBlobUrl_rewritesThatUrl() {
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);
            String html = "<p>look at this</p><img src=\"" + RAW_URL + "\" />";

            String result = rewriter.rewriteHtmlBlobUrls(html);

            assertTrue(result.contains(SIGNED_URL), "expected signed URL in output, got: " + result);
        }

        @Test
        void rewriteHtmlBlobUrls_multipleImgWithBlobUrls_rewritesAll() {
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);
            when(azureBlobStorageQueryService.generateSasUrl(ACCOUNTING_CONTAINER_KEY, "invoice-456.pdf"))
                    .thenReturn(SIGNED_URL_INVOICE);
            String html = "<img src=\"" + RAW_URL + "\"><img src=\"" + RAW_URL_INVOICE + "\">";

            String result = rewriter.rewriteHtmlBlobUrls(html);

            assertTrue(result.contains(SIGNED_URL));
            assertTrue(result.contains(SIGNED_URL_INVOICE));
        }

        @Test
        void rewriteHtmlBlobUrls_noBlobUrls_returnsUnchanged() {
            String html = "<p>just text</p>";

            String result = rewriter.rewriteHtmlBlobUrls(html);

            assertEquals(html, result);
            verify(azureBlobStorageQueryService, never()).generateSasUrl(anyString(), anyString());
        }

        @Test
        void rewriteHtmlBlobUrls_mixedUrls_onlyMatchingPrefixUrlsRewritten() {
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);
            String html = "<img src=\"" + OTHER_HOST_URL + "\"><img src=\"" + RAW_URL + "\">";

            String result = rewriter.rewriteHtmlBlobUrls(html);

            assertTrue(result.contains(OTHER_HOST_URL), "external URL should remain untouched");
            assertTrue(result.contains(SIGNED_URL), "blob URL should be re-signed");
        }

        @Test
        void rewriteHtmlBlobUrls_nullInput_returnsNull() {
            String result = rewriter.rewriteHtmlBlobUrls(null);

            assertNull(result);
        }

        @Test
        void rewriteHtmlBlobUrls_blankInput_returnsBlank() {
            String result = rewriter.rewriteHtmlBlobUrls("   ");

            assertEquals("   ", result);
        }

        @Test
        void rewriteHtmlBlobUrls_hrefAttribute_alsoRewritten() {
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);
            String html = "<a href=\"" + RAW_URL + "\">link</a>";

            String result = rewriter.rewriteHtmlBlobUrls(html);

            assertTrue(result.contains(SIGNED_URL));
        }
    }

    @Nested
    class RewriteMarkdownBlobUrls {

        @BeforeEach
        void stubSigning() {
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);
            when(azureBlobStorageQueryService.generateSasUrl(ACCOUNTING_CONTAINER_KEY, "invoice-456.pdf"))
                    .thenReturn(SIGNED_URL_INVOICE);
        }

        @Test
        void rewriteMarkdownBlobUrls_imageAndLinkDestinations_signsEach() {
            String markdown = "# Plan\n\n![diagram](" + RAW_URL + ")\n\nSee [the invoice](" + RAW_URL_INVOICE
                    + " \"Invoice\").";

            String result = rewriter.rewriteMarkdownBlobUrls(markdown);

            assertEquals("# Plan\n\n![diagram](" + SIGNED_URL + ")\n\nSee [the invoice](" + SIGNED_URL_INVOICE
                    + " \"Invoice\").", result);
        }

        @Test
        void rewriteMarkdownBlobUrls_autolinkReferenceAndHtml_signsEach() {
            String markdown = "<" + RAW_URL + ">\n\n[ref]: " + RAW_URL_INVOICE + "\n\n<img src=\"" + RAW_URL + "\">";

            String result = rewriter.rewriteMarkdownBlobUrls(markdown);

            assertEquals("<" + SIGNED_URL + ">\n\n[ref]: " + SIGNED_URL_INVOICE + "\n\n<img src=\"" + SIGNED_URL
                    + "\">", result);
        }

        @Test
        void rewriteMarkdownBlobUrls_bareUrlEndingSentence_leavesPunctuationOut() {
            String markdown = "Evidence: " + RAW_URL + ".";

            String result = rewriter.rewriteMarkdownBlobUrls(markdown);

            assertEquals("Evidence: " + SIGNED_URL + ".", result);
        }

        @Test
        void rewriteMarkdownBlobUrls_linkTextIsTheUrl_signsTextAndDestination() {
            String markdown = "[" + RAW_URL + "](" + RAW_URL + ")";

            String result = rewriter.rewriteMarkdownBlobUrls(markdown);

            assertEquals("[" + SIGNED_URL + "](" + SIGNED_URL + ")", result);
        }

        @Test
        void rewriteMarkdownBlobUrls_alreadySigned_signsAnew() {
            String markdown = "![x](" + RAW_URL + "?sv=old&sig=EXPIRED)";

            String result = rewriter.rewriteMarkdownBlobUrls(markdown);

            assertEquals("![x](" + SIGNED_URL + ")", result);
        }

        @Test
        void rewriteMarkdownBlobUrls_otherHostAndNestedBlobUrl_returnsUnchanged() {
            String markdown = "![x](" + OTHER_HOST_URL + ") [y](https://example.com/?u=" + RAW_URL + ")";

            String result = rewriter.rewriteMarkdownBlobUrls(markdown);

            assertEquals(markdown, result);
            verify(azureBlobStorageQueryService, never()).generateSasUrl(anyString(), anyString());
        }

        @Test
        void rewriteMarkdownBlobUrls_hostnamePrefixAttack_returnsUnchanged() {
            String markdown = "![x](" + BASE_URL + ".evil.com/" + DEFAULT_CONTAINER_NAME + "/abc123.webp)";

            String result = rewriter.rewriteMarkdownBlobUrls(markdown);

            assertEquals(markdown, result);
        }

        @Test
        void rewriteMarkdownBlobUrls_nullOrBlank_returnsInput() {
            assertNull(rewriter.rewriteMarkdownBlobUrls(null));
            assertEquals("  ", rewriter.rewriteMarkdownBlobUrls("  "));
        }
    }

    @Nested
    class StripMarkdownBlobUrlSignatures {

        @Test
        void stripMarkdownBlobUrlSignatures_signedImageLinkAndBareUrl_removesEachQuery() {
            String markdown = "![diagram](" + SIGNED_URL + ")\n\n[ref]: " + SIGNED_URL_INVOICE + "\n\nSee "
                    + SIGNED_URL + ".";

            String result = rewriter.stripMarkdownBlobUrlSignatures(markdown);

            assertEquals("![diagram](" + RAW_URL + ")\n\n[ref]: " + RAW_URL_INVOICE + "\n\nSee " + RAW_URL + ".",
                    result);
            verify(azureBlobStorageQueryService, never()).generateSasUrl(anyString(), anyString());
        }

        @Test
        void stripMarkdownBlobUrlSignatures_linkTextIsTheUrl_removesBothQueries() {
            String markdown = "[" + SIGNED_URL + "](" + SIGNED_URL + ")";

            String result = rewriter.stripMarkdownBlobUrlSignatures(markdown);

            assertEquals("[" + RAW_URL + "](" + RAW_URL + ")", result);
        }

        @Test
        void stripMarkdownBlobUrlSignatures_unsignedBody_returnsItUnchanged() {
            String markdown = "![diagram](" + RAW_URL + ") and <" + RAW_URL_INVOICE + ">";

            assertEquals(markdown, rewriter.stripMarkdownBlobUrlSignatures(markdown));
        }

        @Test
        void stripMarkdownBlobUrlSignatures_otherHostAndNestedBlobUrl_keepTheirQuery() {
            String markdown = "![x](" + OTHER_HOST_URL + "?sig=keep) [y](https://example.com/?u=" + SIGNED_URL + ")";

            assertEquals(markdown, rewriter.stripMarkdownBlobUrlSignatures(markdown));
        }

        @Test
        void stripMarkdownBlobUrlSignatures_readSignedThenStripped_givesTheStoredBody() {
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);
            String stored = "# Plan\n\n![diagram](" + RAW_URL + ")";

            String roundTrip = rewriter.stripMarkdownBlobUrlSignatures(rewriter.rewriteMarkdownBlobUrls(stored));

            assertEquals(stored, roundTrip);
        }

        @Test
        void stripMarkdownBlobUrlSignatures_nullOrBlank_returnsInput() {
            assertNull(rewriter.stripMarkdownBlobUrlSignatures(null));
            assertEquals("  ", rewriter.stripMarkdownBlobUrlSignatures("  "));
        }
    }

    @Nested
    class BaseUrlNormalization {

        @Test
        void normalize_trailingSlash_strippedAndMatchesUnchangedBehavior() {
            BlobUrlRewriter withSlash = new BlobUrlRewriter(
                    buildProperties(BASE_URL + "/"),
                    azureBlobStorageQueryService,
                    loggingService);
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);

            String result = withSlash.rewriteUrl(RAW_URL);

            assertEquals(SIGNED_URL, result);
        }

        @Test
        void normalize_noScheme_httpsAutoPrependedAndMatches() {
            BlobUrlRewriter noScheme = new BlobUrlRewriter(
                    buildProperties("examplestorage.blob.core.windows.net"),
                    azureBlobStorageQueryService,
                    loggingService);
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "abc123.webp"))
                    .thenReturn(SIGNED_URL);

            String result = noScheme.rewriteUrl(RAW_URL);

            assertEquals(SIGNED_URL, result);
        }

        @Test
        void normalize_explicitAzuriteHttp_noAutoPrependAndMatchesCorrectly() {
            String azuriteBase = "http://127.0.0.1:10000/devstoreaccount1";
            String azuriteRawUrl = azuriteBase + "/" + DEFAULT_CONTAINER_NAME + "/file.png";
            String azuriteSigned = azuriteRawUrl + "?sig=X";

            BlobUrlRewriter azurite = new BlobUrlRewriter(
                    buildProperties(azuriteBase),
                    azureBlobStorageQueryService,
                    loggingService);
            when(azureBlobStorageQueryService.generateSasUrl(DEFAULT_CONTAINER_KEY, "file.png"))
                    .thenReturn(azuriteSigned);

            String result = azurite.rewriteUrl(azuriteRawUrl);

            assertEquals(azuriteSigned, result);
            verify(loggingService, never()).info(any(), any(),
                    eq(BlobUrlRewriterLogConstant.REWRITE_SCHEME_PREPENDED), any());
        }
    }
}
