-- Oba Database Schema - Shrine: Add Attachment Image Dimensions
-- V1_2_102__Shrine_Add_Attachment_Image_Dimensions.sql
--
-- Clients reserve the space of an image before it loads, so an uploaded image
-- keeps its pixel size. width and height are set on upload for PNG, JPEG, GIF
-- and WebP files and stay NULL for every other attachment. Existing rows stay
-- NULL; there is no backfill. No index: the columns are never filtered on.

-- =====================================================
-- 1. Add the columns
-- =====================================================

ALTER TABLE shrine.attachments ADD COLUMN width INTEGER NULL;
ALTER TABLE shrine.attachments ADD COLUMN height INTEGER NULL;

-- =====================================================
-- 2. Constraints
-- =====================================================

-- Both dimensions are set or both are NULL, and a set value is positive.
ALTER TABLE shrine.attachments
    ADD CONSTRAINT attachments_dimensions_ck CHECK (
        (width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)
    );
