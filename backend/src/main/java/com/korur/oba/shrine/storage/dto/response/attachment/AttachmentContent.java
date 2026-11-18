package com.korur.oba.shrine.storage.dto.response.attachment;

/**
 * An attachment's file held in memory: its name, its media type and its bytes.
 *
 * Carries an attachment the server makes from bytes it already holds (a copy of
 * a document template) into storage, and the stored files of a parent out of it
 * when they are sent on, for example as the attachments of a mail. Never part of
 * an API response: clients read files through their URL.
 *
 * @param fileName    the file name shown to users and given to the recipient
 * @param contentType the media type of the content
 * @param content     the file bytes
 */
public record AttachmentContent(
        String fileName,
        String contentType,
        byte[] content) {
}
