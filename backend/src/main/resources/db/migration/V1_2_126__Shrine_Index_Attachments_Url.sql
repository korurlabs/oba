-- Database Schema - Index attachments by blob url
-- V1_2_126__Shrine_Index_Attachments_Url.sql
--
-- A blob can be shared by several attachment rows (a migrated document PDF
-- is referenced by a RELATION row and by one MAIL row per mail). Deleting an
-- attachment must keep the blob while another row still points at it, so
-- every delete calls AttachmentRepository.existsByUrl. Without this index
-- that check scans the whole table.

CREATE INDEX shrine_attachments_url_idx
    ON shrine.attachments (url);
