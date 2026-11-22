package com.korur.oba.shrine.document.helper.common;

// 1. Framework-level
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.service.AzureBlobStorageQueryService;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.constant.common.DocumentFileConstant;
import com.korur.oba.shrine.document.constant.common.DocumentFileLogConstant;
import com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError;

/**
 * Stores the HTML and PDF file of a document template in the default private blob
 * container of Azure Blob Storage, reads the PDF back, and removes them again. Every
 * template has exactly these two files: the HTML source and the PDF rendered from it,
 * which is what a mail or a relation gets as an attachment.
 *
 * Blob names. Every upload gets a fresh flat blob name (a random UUID with the
 * extension), because a blob delete keeps only the last segment of the URL and because
 * two rows must never share a blob: replacing or deleting the files of one row would
 * otherwise break the other. The extension lets the container answer the right content
 * type on download. For the same reason an attachment made from a template gets its own
 * blob: {@link #readPdf} hands the bytes to storage, which uploads them under a new name,
 * so the attachment is a copy of the template's PDF, not a reference to it.
 *
 * Signed and unsigned URLs. The methods take and return the unsigned blob URLs that the
 * rows store. The container is private, so an unsigned URL can not be opened by a
 * browser; the mappers sign it with a short-lived Shared Access Signature at read time
 * (see {@link com.korur.oba.connect.azure.service.BlobUrlRewriter}). Storing the unsigned
 * URL means a stored row never holds an expired signature.
 *
 * Transactions. Blob Storage takes no part in the database transaction, so a blob that
 * was uploaded stays even when the transaction rolls back, and a blob that was deleted
 * does not come back. The callers therefore follow a fixed order: upload the new blobs,
 * write the row, delete the new blobs again if that write fails, and delete the old blobs
 * only after the row no longer points at them. This class cleans up its own partial
 * uploads; the callers clean up after a failed row write.
 *
 * Called by {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}:
 * {@link #store} and {@link #deleteQuietly} when a template's files are replaced,
 * {@link #deleteQuietly} when a template is deleted, and {@link #readPdf} when a template's
 * PDF is copied onto a mail or a relation as an attachment.
 *
 * @see StoredDocumentFiles
 * @see AzureBlobStorageService
 * @see AzureBlobStorageQueryService
 */
@Component
@RequiredArgsConstructor
public class DocumentFileStorage {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DocumentFileStorage.class);

    /**
     * The bytes every PDF file starts with ("%PDF-"). Checked on upload, because the
     * declared content type of a multipart part is chosen by the client and proves nothing.
     */
    private static final byte[] PDF_MAGIC = DocumentFileConstant.PDF_MAGIC.getBytes(StandardCharsets.US_ASCII);

    private final AzureBlobStorageService azureBlobStorageService;
    private final AzureBlobStorageQueryService azureBlobStorageQueryService;
    private final LoggingService loggingService;

    /**
     * Checks both files and uploads them as new blobs. Nothing is uploaded when a check
     * fails, and an uploaded HTML blob is deleted again when the PDF upload fails, so a
     * failed call leaves no blob behind.
     *
     * The checks: each part is present and not empty, its declared media type is text/html
     * or application/pdf (a charset or other parameter is allowed), and the PDF starts with
     * the PDF header bytes. Both files are read and checked before the first upload, so a
     * bad PDF never leaves an HTML blob behind. The HTML is not parsed or sanitised here; it
     * is stored as given and only ever served from the private container.
     *
     * The old blobs of the row are not touched: the caller writes the new URLs to the row
     * first and then calls {@link #deleteQuietly} with the old ones.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#replaceFiles}.
     * A template is always created without files; they arrive through this replacement.
     *
     * @param html the HTML part, which must be a non-empty text/html file
     * @param pdf  the PDF part, which must be a non-empty application/pdf file starting with the PDF header
     * @return the unsigned URLs of the new blobs
     * @throws ValidationException {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#VAL_FILE_NOT_HTML_OR_PDF} (DOC_VAL_001)
     *         when a part is missing, empty or of the wrong type
     * @throws IntegrationErrorException {@link ExceptionDomainAzureError#UPS_AZURE_BLOB_UPLOAD} (AZR_UPS_001)
     *         when a file can not be read or uploaded
     */
    public StoredDocumentFiles store(MultipartFile html, MultipartFile pdf) {
        // Read and check both parts before any upload, so a refused request stores nothing.
        byte[] htmlContent = readChecked(html, DocumentFileConstant.PART_HTML, MediaType.TEXT_HTML,
                DocumentFileConstant.HTML_FILE_MESSAGE);
        byte[] pdfContent = readChecked(pdf, DocumentFileConstant.PART_PDF, MediaType.APPLICATION_PDF,
                DocumentFileConstant.PDF_FILE_MESSAGE);
        // The declared type is the client's claim; the header bytes show the file really is a PDF.
        if (!startsWithPdfMagic(pdfContent)) {
            throw refuse(DocumentFileConstant.PART_PDF, pdf, DocumentFileConstant.PDF_FILE_MESSAGE);
        }

        // HTML first. If the PDF upload then fails, the HTML blob is the only thing to undo.
        String htmlUrl = upload(DocumentFileConstant.HTML_EXTENSION, htmlContent);
        try {
            String pdfUrl = upload(DocumentFileConstant.PDF_EXTENSION, pdfContent);
            return new StoredDocumentFiles(htmlUrl, pdfUrl);
        } catch (RuntimeException e) {
            // The upload already logged the failure; only the HTML blob it leaves behind is cleaned up here.
            deleteQuietly(htmlUrl);
            throw e;
        }
    }

    /**
     * Copies a stored HTML and PDF blob to two new blobs, so a document made from a template
     * owns files of its own and a later change to the template never reaches it. Both blobs
     * are read before the first upload, and an uploaded HTML copy is deleted again when the PDF
     * upload fails, so a failed call leaves no blob behind.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.service.document.impl.DocumentServiceImpl#create}.
     *
     * @param htmlUrl the unsigned URL of the HTML blob to copy
     * @param pdfUrl  the unsigned URL of the PDF blob to copy
     * @return the unsigned URLs of the new blobs
     * @throws IntegrationErrorException {@link ExceptionDomainAzureError#UPS_AZURE_BLOB_DOWNLOAD} (AZR_UPS_002)
     *         when a blob can not be read, or {@link ExceptionDomainAzureError#UPS_AZURE_BLOB_UPLOAD}
     *         (AZR_UPS_001) when a copy can not be uploaded
     */
    public StoredDocumentFiles copy(String htmlUrl, String pdfUrl) {
        byte[] htmlContent = azureBlobStorageQueryService.downloadPrivateBlob(blobName(htmlUrl));
        byte[] pdfContent = azureBlobStorageQueryService.downloadPrivateBlob(blobName(pdfUrl));

        String newHtmlUrl = upload(DocumentFileConstant.HTML_EXTENSION, htmlContent);
        try {
            String newPdfUrl = upload(DocumentFileConstant.PDF_EXTENSION, pdfContent);
            return new StoredDocumentFiles(newHtmlUrl, newPdfUrl);
        } catch (RuntimeException e) {
            // The upload already logged the failure; only the HTML copy it leaves behind is cleaned up here.
            deleteQuietly(newHtmlUrl);
            throw e;
        }
    }

    /**
     * Reads the bytes of a stored PDF blob. No content check runs, because the file passed
     * {@link #store} when it was uploaded. The blob is only read; the caller stores the bytes
     * under a new name, so the copy and the template never share a blob.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#copyToAttachment}
     * with the PDF URL of a template read within the organization.
     *
     * @param url the unsigned URL of the PDF blob
     * @return the bytes of the PDF
     * @throws IntegrationErrorException {@link ExceptionDomainAzureError#UPS_AZURE_BLOB_DOWNLOAD} (AZR_UPS_002)
     *         when the blob can not be read
     */
    public byte[] readPdf(String url) {
        return azureBlobStorageQueryService.downloadPrivateBlob(blobName(url));
    }

    /**
     * Deletes each given blob, skipping null URLs. Best effort: the row that pointed at
     * the blob is already changed or gone, so a failed delete leaves an orphaned blob,
     * which is logged at error level with its URL and cause for cleanup, and never fails
     * the user's request.
     *
     * Failing the request would be worse than an orphan: the database change is already
     * done, and an error would tell the user it was not. Null entries are skipped because a
     * template without files has null URLs, and the callers pass the old URLs
     * as they are.
     *
     * Called by {@link #store} to undo a half-finished upload, and by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}
     * after a file replacement (old blobs), after a delete (the row's blobs, once the row is flushed
     * away) and after a failed row write (the new blobs no row will point at).
     *
     * @param urls the unsigned blob URLs to delete; null entries are skipped
     */
    public void deleteQuietly(String... urls) {
        Arrays.stream(urls)
                .filter(url -> url != null)
                .forEach(this::deleteOne);
    }

    /**
     * Deletes one blob through {@link AzureBlobStorageService#deleteFile(String)} and logs
     * a failure instead of throwing it, see {@link #deleteQuietly}.
     *
     * @param url the unsigned blob URL
     */
    private void deleteOne(String url) {
        try {
            azureBlobStorageService.deleteFile(url);
        } catch (RuntimeException e) {
            // Logged at error with the URL, so the orphaned blob can be found and removed later;
            // not re-thrown, because the row change it belongs to has already happened.
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    DocumentFileLogConstant.FILE_BLOB_DELETE_FAILED, e, url);
        }
    }

    /**
     * The private download addresses a blob by its name in the default container, not by
     * its URL. Blob names here are flat, so the name is the last segment of the URL.
     *
     * @param url the unsigned blob URL
     * @return the blob name, a UUID with the file extension
     */
    private static String blobName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * Uploads bytes as a new blob under a fresh random name through
     * {@link AzureBlobStorageService#uploadFile(String, byte[])}, which logs and throws
     * {@link ExceptionDomainAzureError#UPS_AZURE_BLOB_UPLOAD} on failure.
     *
     * @param extension the file extension, with its dot, so the container serves the right content type
     * @param content   the file bytes
     * @return the unsigned URL of the new blob
     */
    private String upload(String extension, byte[] content) {
        // A random name per upload: no two rows, and no two versions of one row, share a blob.
        String blobName = UUID.randomUUID() + extension;
        String url = azureBlobStorageService.uploadFile(blobName, content);
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DocumentFileLogConstant.FILE_UPLOADED,
                blobName, content.length);
        return url;
    }

    /**
     * Reads a part after checking that it is present, not empty and declared with the
     * expected media type (parameters such as a charset are allowed).
     *
     * @param file     the multipart part, may be null when the client left it out
     * @param part     the part name, used as the field of the error details
     * @param expected the media type the part must declare
     * @param message  the message shown for the part when it is refused
     * @return the bytes of the part
     * @throws ValidationException {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#VAL_FILE_NOT_HTML_OR_PDF} when a check fails
     * @throws IntegrationErrorException {@link ExceptionDomainAzureError#UPS_AZURE_BLOB_UPLOAD} when the
     *         bytes can not be read
     */
    private byte[] readChecked(MultipartFile file, String part, MediaType expected, String message) {
        if (file == null || file.isEmpty() || !hasMediaType(file, expected)) {
            throw refuse(part, file, message);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            // A part that can not be read is a server-side fault, not a client error; it is
            // reported as a failed upload, the step the user sees fail.
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    DocumentFileLogConstant.FILE_READ_FAILED, e, part);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_UPLOAD);
        }
    }

    /**
     * Tells whether the part declares the expected media type. Only type and subtype are
     * compared, so text/html;charset=UTF-8 counts as text/html.
     *
     * @param file     the multipart part
     * @param expected the media type it must declare
     * @return true when the declared type matches; false when it differs, is missing or is malformed
     */
    private boolean hasMediaType(MultipartFile file, MediaType expected) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }
        try {
            return expected.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException e) {
            // rule-10-no-op -- reason: a malformed content type is a wrong file type; the caller
            // refuses the part with a logged validation error that names the content type.
            return false;
        }
    }

    /**
     * Tells whether the bytes start with the PDF header, see PDF_MAGIC.
     *
     * @param content the file bytes
     * @return true when the content starts with the PDF header
     */
    private boolean startsWithPdfMagic(byte[] content) {
        return content.length >= PDF_MAGIC.length
                && Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC);
    }

    /**
     * Logs a refused part with its declared content type and size, and builds the error.
     * Returned, not thrown, so each caller's throw statement stays visible.
     *
     * @param part    the part name, used as the field of the error details
     * @param file    the refused part, may be null when the client left it out
     * @param message the message shown for the part
     * @return a {@link ValidationException} with {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#VAL_FILE_NOT_HTML_OR_PDF}
     */
    private ValidationException refuse(String part, MultipartFile file, String message) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, DocumentFileLogConstant.FILE_REFUSED, part,
                file == null ? null : file.getContentType(), file == null ? 0L : file.getSize());
        return new ValidationException(ExceptionDomainDocumentError.VAL_FILE_NOT_HTML_OR_PDF, Map.of(part, message));
    }
}
