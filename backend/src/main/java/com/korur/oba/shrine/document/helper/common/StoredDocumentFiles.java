package com.korur.oba.shrine.document.helper.common;

/**
 * Unsigned blob URLs of a freshly stored HTML file and the PDF rendered from it, as
 * returned by {@link DocumentFileStorage#store}.
 *
 * The URLs are unsigned on purpose: they are what a
 * {@link com.korur.oba.shrine.document.entity.template.DocumentTemplate} stores in its
 * html_blob_url and pdf_blob_url columns, and a mapper signs them only when a response is built. The two
 * blobs are new and belong to no row yet, so the caller that fails to write them to a row
 * must remove them again with {@link DocumentFileStorage#deleteQuietly}.
 *
 * Read by
 * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#replaceFiles}.
 *
 * @param htmlUrl the URL of the HTML blob
 * @param pdfUrl  the URL of the PDF blob
 * @see DocumentFileStorage
 */
public record StoredDocumentFiles(String htmlUrl, String pdfUrl) {
}
