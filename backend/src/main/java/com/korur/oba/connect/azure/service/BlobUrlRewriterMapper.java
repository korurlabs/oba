package com.korur.oba.connect.azure.service;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;

/**
 * Thin adapter that exposes BlobUrlRewriter through MapStruct &#64;Named
 * qualifier methods so MapStruct-generated mappers can invoke the rewriter
 * for specific fields via qualifiedByName.
 *
 * Kept as a separate Spring bean (rather than annotating BlobUrlRewriter
 * itself) so the rewriter stays free of MapStruct-specific annotations and
 * can be used directly from non-mapper callers if needed.
 */
@Component
@RequiredArgsConstructor
public class BlobUrlRewriterMapper {

    private final BlobUrlRewriter blobUrlRewriter;

    @Named("rewriteBlobUrl")
    public String rewriteBlobUrl(String rawUrl) {
        return blobUrlRewriter.rewriteUrl(rawUrl);
    }

    @Named("rewriteHtmlBlobUrls")
    public String rewriteHtmlBlobUrls(String html) {
        return blobUrlRewriter.rewriteHtmlBlobUrls(html);
    }

    @Named("rewriteMarkdownBlobUrls")
    public String rewriteMarkdownBlobUrls(String markdown) {
        return blobUrlRewriter.rewriteMarkdownBlobUrls(markdown);
    }
}
