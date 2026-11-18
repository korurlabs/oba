package com.korur.oba.connect.notification.dto;


/**
 *
 * @param filename - Not Nullable - Name of the file
 * @param content - Not Nullable - byte array content of the attachment
 * @param contentType - Not nullable - Content type
 */
public record AttachmentEmail(
        String filename,
        byte[] content,
        String contentType
) {}
