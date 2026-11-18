package com.korur.oba.connect.azure.service;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 3. Java SDK
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.connect.azure.constant.service.BlobUrlRewriterLogConstant;
import com.korur.oba.connect.azure.properties.AzureBlobStorageProperties;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Rewrites raw Azure Blob Storage URLs to SAS-signed URLs at read time.
 *
 * The private blob container has no anonymous access, so every URL the
 * backend exposes to the browser must carry a Shared Access Signature
 * query string. This component is invoked from the mapping layer via the
 * BlobUrlRewriterMapper qualifier methods.
 *
 * Two entry points:
 * - rewriteUrl: rewrites a single blob URL (e.g. the AttachmentResponse.url field).
 * - rewriteHtmlBlobUrls: rewrites every matching URL embedded inside rich text
 *   (e.g. Ticket.description, Comment.content) by scanning for src=... and
 *   href=... attribute values.
 * - rewriteMarkdownBlobUrls: rewrites every blob URL embedded inside a Markdown
 *   body (e.g. a Kalkan page translation), wherever Markdown puts a link
 *   destination.
 * - stripMarkdownBlobUrlSignatures: the reverse for writes, removing the query
 *   string of every blob URL in a Markdown body, so a body read signed and sent
 *   back is stored unsigned.
 *
 * URLs that do not start with the configured normalizedBaseUrl prefix are
 * returned unchanged. Failures to generate SAS are logged and the original
 * URL is returned (graceful degradation).
 */
@Component
public class BlobUrlRewriter {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BlobUrlRewriter.class);
    private static final String SAS_MARKER = "?";
    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";

    /**
     * Matches src=... or href=... attribute values inside HTML / rich text.
     * Group 1: opening attribute prefix (e.g. src=" or href=').
     * Group 2: the URL value itself.
     * Group 3: closing quote.
     */
    private static final Pattern HTML_URL_PATTERN = Pattern.compile(
            "((?:src|href)\\s*=\\s*[\"'])([^\"']+)([\"'])",
            Pattern.CASE_INSENSITIVE);
    private static final int HTML_URL_GROUP = 2;
    private static final int WHOLE_MATCH_GROUP = 0;

    /**
     * Characters that end a URL inside Markdown: whitespace, the parentheses
     * of an inline link, the closing bracket of a link text that is itself a
     * URL, the angle brackets of an autolink and the quotes of a link title or
     * an HTML attribute.
     */
    private static final String MARKDOWN_URL_STOP_CHARS = "\\s()\\]<>\"'";

    private final AzureBlobStorageQueryService azureBlobStorageQueryService;
    private final LoggingService loggingService;
    private final String normalizedBaseUrl;
    private final Map<String, String> containerNameToKey;

    /**
     * Matches a URL of the configured blob account anywhere in a Markdown body:
     * an inline link or image destination, an autolink, a reference definition,
     * a bare URL or an HTML attribute. The URL must start the text or follow
     * whitespace, an opening parenthesis, bracket or angle bracket, or a quote,
     * so a blob URL nested in another URL's query string is left alone. A
     * trailing sentence punctuation mark is not part of the URL, as in a bare
     * URL that ends a sentence.
     */
    private final Pattern markdownBlobUrlPattern;

    public BlobUrlRewriter(
            AzureBlobStorageProperties properties,
            AzureBlobStorageQueryService azureBlobStorageQueryService,
            LoggingService loggingService) {
        this.azureBlobStorageQueryService = azureBlobStorageQueryService;
        this.loggingService = loggingService;
        this.normalizedBaseUrl = normalizeBaseUrl(properties.getBlobBaseUrl());
        this.containerNameToKey = buildContainerNameToKeyMap(properties.getContainers());
        this.markdownBlobUrlPattern = Pattern.compile(
                "(?<![^\\s(\\[<\"'])" + Pattern.quote(normalizedBaseUrl + "/")
                        + "[^" + MARKDOWN_URL_STOP_CHARS + "]*[^" + MARKDOWN_URL_STOP_CHARS + ".,;:!?]");
    }

    /**
     * Rewrites a single raw blob URL into a SAS-signed URL.
     *
     * Returns the input unchanged when:
     * - the input is null or blank;
     * - the URL does not start with the configured base URL prefix followed by '/'
     *   (different host, or hostname-prefix attack like base-url.evil.com/...);
     * - the URL's container path segment does not match any configured container
     *   (logged at error -- this indicates a configuration drift);
     * - SAS generation throws (logged at error with the throwable).
     *
     * If the URL already contains a query string (e.g. previously signed), the
     * existing query string is stripped and a fresh SAS token is appended.
     *
     * @param rawBlobUrl the raw blob URL to rewrite
     * @return the SAS-signed URL or the original input on any non-match path
     */
    public String rewriteUrl(String rawBlobUrl) {
        if (rawBlobUrl == null || rawBlobUrl.isBlank()) {
            return rawBlobUrl;
        }

        String urlWithoutQuery = stripQueryString(rawBlobUrl);

        if (!urlWithoutQuery.startsWith(normalizedBaseUrl + "/")) {
            return rawBlobUrl;
        }

        BlobPathComponents components = extractBlobPathComponents(urlWithoutQuery);
        if (components == null) {
            return rawBlobUrl;
        }

        String containerKey = containerNameToKey.get(components.containerName());
        if (containerKey == null) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    BlobUrlRewriterLogConstant.REWRITE_UNKNOWN_CONTAINER,
                    components.containerName());
            return rawBlobUrl;
        }

        try {
            return azureBlobStorageQueryService.generateSasUrl(containerKey, components.blobName());
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    BlobUrlRewriterLogConstant.REWRITE_FAILED, e,
                    containerKey, components.blobName());
            return rawBlobUrl;
        }
    }

    /**
     * Rewrites every matching blob URL embedded inside src=... or href=...
     * attributes of an HTML / rich-text body.
     *
     * URLs that do not match the configured base URL prefix are left
     * untouched. The method scans the entire string once with a precompiled
     * regex; non-matching attributes (e.g. external image links) pass through
     * unchanged.
     *
     * @param htmlOrText the HTML or rich-text body
     * @return the body with matching blob URLs replaced by SAS-signed URLs
     */
    public String rewriteHtmlBlobUrls(String htmlOrText) {
        if (htmlOrText == null || htmlOrText.isBlank()) {
            return htmlOrText;
        }

        return logRewrites(rewriteEmbeddedUrls(htmlOrText, HTML_URL_PATTERN, HTML_URL_GROUP, this::rewriteUrl));
    }

    /**
     * Rewrites every blob URL embedded in a Markdown body into a SAS-signed URL.
     *
     * Stored Markdown keeps its blob URLs unsigned, so an unchanged source gives
     * an unchanged body, and a signature is minted on every read. A URL that
     * already carries a query string is signed anew. URLs of other hosts pass
     * through unchanged.
     *
     * @param markdown the Markdown body
     * @return the body with every blob URL replaced by a SAS-signed URL
     */
    public String rewriteMarkdownBlobUrls(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        return logRewrites(rewriteEmbeddedUrls(markdown, markdownBlobUrlPattern, WHOLE_MATCH_GROUP,
                this::rewriteUrl));
    }

    /**
     * Removes the query string of every blob URL embedded in a Markdown body,
     * the URLs that rewriteMarkdownBlobUrls finds.
     *
     * A body is read with signed URLs, so an editor sends them back on save.
     * Stripping them on write keeps the stored body free of expiring tokens and
     * lets a body that differs only in its signatures compare equal to the
     * stored one. URLs of other hosts keep their query string.
     *
     * @param markdown the Markdown body
     * @return the body with every blob URL unsigned
     */
    public String stripMarkdownBlobUrlSignatures(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        return rewriteEmbeddedUrls(markdown, markdownBlobUrlPattern, WHOLE_MATCH_GROUP, this::stripQueryString)
                .text();
    }

    /**
     * Replaces the URL captured by urlGroup in every match of pattern with
     * rewrite's result, keeping the rest of the match as it is.
     */
    private RewriteResult rewriteEmbeddedUrls(String text, Pattern pattern, int urlGroup,
                                              UnaryOperator<String> rewrite) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder rewritten = new StringBuilder(text.length());
        int rewriteCount = 0;

        while (matcher.find()) {
            String url = matcher.group(urlGroup);
            String rewrittenUrl = rewrite.apply(url);
            if (!rewrittenUrl.equals(url)) {
                rewriteCount++;
            }

            String match = matcher.group();
            int urlStart = matcher.start(urlGroup) - matcher.start();
            int urlEnd = matcher.end(urlGroup) - matcher.start();
            String replacement = match.substring(0, urlStart) + rewrittenUrl + match.substring(urlEnd);
            // Quote replacement text so any $ or \ in the SAS token is treated literally.
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);

        return new RewriteResult(rewritten.toString(), rewriteCount);
    }

    private String logRewrites(RewriteResult result) {
        if (result.count() > 0) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    BlobUrlRewriterLogConstant.REWRITE_HTML_COUNT, result.count());
        }
        return result.text();
    }

    private String normalizeBaseUrl(String raw) {
        String trimmed = raw.strip();

        boolean hasScheme = trimmed.startsWith(HTTP_SCHEME) || trimmed.startsWith(HTTPS_SCHEME);
        if (!hasScheme) {
            trimmed = HTTPS_SCHEME + trimmed;
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    BlobUrlRewriterLogConstant.REWRITE_SCHEME_PREPENDED, trimmed);
        }

        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Builds a reverse lookup from container name (the URL path segment) to
     * container key (the lookup key on AzureBlobStorageMultiContainerClient).
     *
     * Blob URLs returned by Azure carry the container name, not the
     * application-level key. The configured properties.containers map is
     * {key -> name}; the rewriter needs the inverse to translate a URL
     * segment back into a key.
     */
    private Map<String, String> buildContainerNameToKeyMap(Map<String, String> containers) {
        if (containers == null || containers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> reverse = new HashMap<>(containers.size());
        for (Map.Entry<String, String> entry : containers.entrySet()) {
            reverse.put(entry.getValue(), entry.getKey());
        }
        return Map.copyOf(reverse);
    }

    private String stripQueryString(String url) {
        int markerIndex = url.indexOf(SAS_MARKER);
        if (markerIndex < 0) {
            return url;
        }
        return url.substring(0, markerIndex);
    }

    /**
     * Extracts the container name and blob name from a URL that has already
     * been confirmed to start with normalizedBaseUrl and to have its query
     * string stripped. Returns null if the remaining path does not contain
     * both a container segment and a blob path.
     */
    private BlobPathComponents extractBlobPathComponents(String urlWithoutQuery) {
        String path = urlWithoutQuery.substring(normalizedBaseUrl.length());
        if (path.isEmpty() || "/".equals(path)) {
            return null;
        }
        // path is expected to start with "/" — split into ["", "<container>", "<blobPath>"].
        String[] segments = path.split("/", 3);
        if (segments.length < 3 || segments[1].isEmpty() || segments[2].isEmpty()) {
            return null;
        }
        return new BlobPathComponents(segments[1], segments[2]);
    }

    private record BlobPathComponents(String containerName, String blobName) {
    }

    private record RewriteResult(String text, int count) {
    }
}
