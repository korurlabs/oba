package com.korur.oba.shrine.document.constant.common;

/**
 * Multipart part names, file extensions, the PDF header check and the file validation
 * messages shared by every file kept in the document domain: the HTML and PDF of a template
 * and, later, of a document. Both hold the same pair of files and go through the same
 * storage helper, so the rules live here once.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentFileConstant {

    private DocumentFileConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Files-----------//

    public static final String PART_HTML = "html";
    public static final String PART_PDF = "pdf";
    public static final String HTML_EXTENSION = ".html";
    public static final String PDF_EXTENSION = ".pdf";

    /**
     * First bytes of every PDF file, checked so a file merely labelled as a PDF is refused.
     */
    public static final String PDF_MAGIC = "%PDF-";

    // -------------Validation messages-----------//

    public static final String HTML_FILE_MESSAGE = "The html part must be a non-empty text/html file";
    public static final String PDF_FILE_MESSAGE = "The pdf part must be a non-empty application/pdf file";
}
