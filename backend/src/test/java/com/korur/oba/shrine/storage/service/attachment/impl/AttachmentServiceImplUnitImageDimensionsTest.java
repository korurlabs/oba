package com.korur.oba.shrine.storage.service.attachment.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

// 3. Java SDK
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.shrine.storage.constant.service.attachment.AttachmentServiceLogConstant;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentCreateRequest;
import com.korur.oba.shrine.storage.entity.attachment.Attachment;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.helper.attachment.AttachmentHashHelper;
import com.korur.oba.shrine.storage.mapper.attachment.AttachmentMapper;
import com.korur.oba.shrine.storage.repository.attachment.AttachmentRepository;
import com.korur.oba.shrine.storage.validator.attachment.AttachmentValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the image size AttachmentServiceImpl.uploadAttachments
 * stores with an upload, read from the file header with ImageIO.
 *
 * The JDK has no WebP reader; the size of a WebP upload depends on the
 * imageio-webp plugin being on the classpath, so this test fails when the
 * plugin is missing and WebP images lose their reserved layout box.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplUnitImageDimensionsTest {

    /** A lossless WebP of 3 by 2 pixels, 38 bytes. */
    private static final byte[] WEBP_3_BY_2 =
            Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAkAAAAdQjyLXo/+BiOh/AAA=");

    private static final String FILE_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private AttachmentValidator attachmentValidator;

    @Mock
    private AttachmentHashHelper attachmentHashHelper;

    @Mock
    private AzureBlobStorageService azureBlobStorageService;

    @Mock
    private LoggingService loggingService;

    @Mock
    private User user;

    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;

    @InjectMocks
    private AttachmentServiceImpl attachmentServiceImpl;

    private final UUID organizationId = UUID.randomUUID();

    @Test
    void uploadAttachments_webpImage_storesWidthAndHeight() {
        // Arrange
        AttachmentCreateRequest request = new AttachmentCreateRequest(
                "pixel.webp", StorageEntityType.TICKET, UUID.randomUUID());
        MultipartFile file = new MockMultipartFile("file", "pixel.webp", "image/webp", WEBP_3_BY_2);

        when(attachmentHashHelper.computeHash(any(MultipartFile.class))).thenReturn(FILE_HASH);
        when(azureBlobStorageService.uploadFile(anyString(), any(byte[].class)))
                .thenReturn("https://blob/pixel.webp");
        when(attachmentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        attachmentServiceImpl.uploadAttachments(user, organizationId, List.of(request), List.of(file));

        // Assert
        ArgumentCaptor<List<Attachment>> captor = ArgumentCaptor.captor();
        verify(attachmentRepository).saveAll(captor.capture());
        Attachment saved = captor.getValue().getFirst();
        assertEquals(3, saved.getWidth(), "WebP width should be read from the file header");
        assertEquals(2, saved.getHeight(), "WebP height should be read from the file header");
    }

    @ParameterizedTest
    @CsvSource({"png,image/png", "jpg,image/jpeg", "gif,image/gif"})
    void uploadAttachments_pngJpegOrGifImage_storesWidthAndHeight(String format, String contentType)
            throws IOException {
        // Arrange
        Attachment saved = upload("pixel." + format, contentType, encode(format, 5, 4));

        // Assert
        assertEquals(5, saved.getWidth());
        assertEquals(4, saved.getHeight());
    }

    @Test
    void uploadAttachments_contentTypeWithParametersAndUpperCase_storesWidthAndHeight() throws IOException {
        // Arrange
        Attachment saved = upload("pixel.png", "IMAGE/PNG; charset=binary", encode("png", 6, 3));

        // Assert
        assertEquals(6, saved.getWidth());
        assertEquals(3, saved.getHeight());
    }

    @Test
    void uploadAttachments_pdf_leavesDimensionsNull() {
        // Arrange
        Attachment saved = upload("report.pdf", "application/pdf", "%PDF-1.7 not an image".getBytes());

        // Assert
        assertNull(saved.getWidth());
        assertNull(saved.getHeight());
        verify(loggingService, never()).warn(any(), any(), anyString(), any(Object[].class));
    }

    @Test
    void uploadAttachments_corruptImage_uploadsWithNullDimensionsAndWarns() throws IOException {
        // Arrange: a PNG signature followed by garbage, so a reader is found but the header is unreadable
        byte[] corrupt = new byte[64];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, corrupt, 0, signature.length);

        // Act
        Attachment saved = upload("broken.png", "image/png", corrupt);

        // Assert
        assertNull(saved.getWidth());
        assertNull(saved.getHeight());
        assertEquals("https://blob/broken.png", saved.getUrl());
        verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                eq(AttachmentServiceLogConstant.ATTACHMENT_IMAGE_DIMENSIONS_UNREADABLE), any(Object[].class));
    }

    @Test
    void uploadAttachments_imageWithoutReader_uploadsWithNullDimensionsAndWarns() {
        // Arrange: bytes no image reader recognizes
        Attachment saved = upload("noise.png", "image/png", "plain text".getBytes());

        // Assert
        assertNull(saved.getWidth());
        assertNull(saved.getHeight());
        verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                eq(AttachmentServiceLogConstant.ATTACHMENT_IMAGE_READER_MISSING), any(Object[].class));
    }

    private Attachment upload(String fileName, String contentType, byte[] content) {
        AttachmentCreateRequest request = new AttachmentCreateRequest(
                fileName, StorageEntityType.TICKET, UUID.randomUUID());
        MultipartFile file = new MockMultipartFile("file", fileName, contentType, content);
        when(attachmentHashHelper.computeHash(any(MultipartFile.class))).thenReturn(FILE_HASH);
        when(azureBlobStorageService.uploadFile(anyString(), any(byte[].class)))
                .thenReturn("https://blob/" + fileName);
        when(attachmentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        attachmentServiceImpl.uploadAttachments(user, organizationId, List.of(request), List.of(file));

        ArgumentCaptor<List<Attachment>> captor = ArgumentCaptor.captor();
        verify(attachmentRepository).saveAll(captor.capture());
        return captor.getValue().getFirst();
    }

    private static byte[] encode(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
