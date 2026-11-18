-- Database Schema - Kaizen Add Slugs
-- V1_2_82__Kaizen_Add_Slugs.sql
--
-- URL slugs for boards, board-sprint links and chat channels, so the web app
-- can address them by a readable segment instead of an id. Every existing row
-- gets the slug the application will generate from its name (see
-- kaizen_slugify below), so old and new rows follow one rule.

-- =====================================================
-- 1. Add columns (nullable until the backfill has run)
-- =====================================================

ALTER TABLE kaizen.boards ADD COLUMN slug VARCHAR(80);
ALTER TABLE kaizen.board_sprints ADD COLUMN slug VARCHAR(80);
ALTER TABLE kaizen.chat_channels ADD COLUMN slug VARCHAR(80);

-- =====================================================
-- 2. Slug function (session-local, gone when the migration ends)
-- =====================================================

-- Turkish letters and the common Latin-1 accented letters are mapped before
-- lower-casing, because lower() of the dotted capital I depends on the
-- database locale. Each run of characters outside a-z and 0-9 becomes one
-- hyphen, the ends are trimmed, and the result is cut to 60 characters so a
-- numeric suffix still fits in VARCHAR(80). An empty result becomes fallback.
CREATE FUNCTION pg_temp.kaizen_slugify(name TEXT, fallback TEXT) RETURNS TEXT AS $$
DECLARE
    result TEXT;
BEGIN
    result := translate(
        COALESCE(name, ''),
        'ıİğĞüÜşŞöÖçÇàáâãäåèéêëìíîïñòóôõùúûýÿÀÁÂÃÄÅÈÉÊËÌÍÎÏÑÒÓÔÕÙÚÛÝ',
        'iigguussoocc' || 'aaaaaaeeeeiiiinoooouuuyy' || 'AAAAAAEEEEIIIINOOOOUUUY'
    );
    result := lower(result);
    result := regexp_replace(result, '[^a-z0-9]+', '-', 'g');
    result := btrim(result, '-');
    result := btrim(left(result, 60), '-');
    IF result = '' THEN
        RETURN fallback;
    END IF;
    RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- =====================================================
-- 3. Backfill
-- =====================================================

-- Live rows are walked per scope in creation order and take the first free
-- candidate of base, base-1, base-2, ... that is not UUID-shaped (a path
-- segment must never be mistaken for an id) and, for sprints, is not one of
-- the fixed segments under /board/<slug>/. Soft-deleted rows keep their base
-- slug: the unique indexes below ignore them.
DO $$
DECLARE
    uuid_shape CONSTANT TEXT := '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
    reserved CONSTANT TEXT[] := ARRAY['all', 'backlog', 'roadmap', 'sprint', 'ticket'];
    rec RECORD;
    base TEXT;
    candidate TEXT;
    suffix INTEGER;
BEGIN
    -- Boards: unique among live boards.
    FOR rec IN SELECT id, name, deleted FROM kaizen.boards ORDER BY deleted, created_at, id LOOP
        base := pg_temp.kaizen_slugify(rec.name, 'board');
        candidate := base;
        suffix := 0;
        IF NOT rec.deleted THEN
            WHILE candidate ~ uuid_shape
                  OR EXISTS (SELECT 1 FROM kaizen.boards b WHERE b.slug = candidate AND b.deleted = FALSE) LOOP
                suffix := suffix + 1;
                candidate := base || '-' || suffix;
            END LOOP;
        END IF;
        UPDATE kaizen.boards SET slug = candidate WHERE id = rec.id;
    END LOOP;

    -- Board-sprint links: unique per board among live links.
    FOR rec IN
        SELECT bs.id, bs.board_id, bs.deleted, s.name
        FROM kaizen.board_sprints bs
        JOIN kaizen.sprints s ON s.id = bs.sprint_id
        ORDER BY bs.deleted, bs.created_at, bs.id
    LOOP
        base := pg_temp.kaizen_slugify(rec.name, 'sprint');
        candidate := base;
        suffix := 0;
        IF NOT rec.deleted THEN
            WHILE candidate ~ uuid_shape
                  OR candidate = ANY (reserved)
                  OR EXISTS (SELECT 1 FROM kaizen.board_sprints x
                             WHERE x.board_id = rec.board_id AND x.slug = candidate AND x.deleted = FALSE) LOOP
                suffix := suffix + 1;
                candidate := base || '-' || suffix;
            END LOOP;
        END IF;
        UPDATE kaizen.board_sprints SET slug = candidate WHERE id = rec.id;
    END LOOP;

    -- Chat channels: unique per organization among live channels.
    FOR rec IN SELECT id, organization_id, name, deleted FROM kaizen.chat_channels ORDER BY deleted, created_at, id LOOP
        base := pg_temp.kaizen_slugify(rec.name, 'channel');
        candidate := base;
        suffix := 0;
        IF NOT rec.deleted THEN
            WHILE candidate ~ uuid_shape
                  OR EXISTS (SELECT 1 FROM kaizen.chat_channels x
                             WHERE x.organization_id = rec.organization_id AND x.slug = candidate AND x.deleted = FALSE) LOOP
                suffix := suffix + 1;
                candidate := base || '-' || suffix;
            END LOOP;
        END IF;
        UPDATE kaizen.chat_channels SET slug = candidate WHERE id = rec.id;
    END LOOP;
END
$$;

-- =====================================================
-- 4. Enforce NOT NULL
-- =====================================================

ALTER TABLE kaizen.boards ALTER COLUMN slug SET NOT NULL;
ALTER TABLE kaizen.board_sprints ALTER COLUMN slug SET NOT NULL;
ALTER TABLE kaizen.chat_channels ALTER COLUMN slug SET NOT NULL;

-- =====================================================
-- 5. Create indexes
-- =====================================================

-- Board URLs carry one board segment and boards have no organization column,
-- so the slug is unique across live boards. The index also serves the
-- by-slug lookup.
CREATE UNIQUE INDEX boards_slug_uk ON kaizen.boards (slug) WHERE deleted = FALSE;

-- A sprint can sit on several boards, so its slug lives on the link and is
-- unique per board. board_id leads, so the index also covers the foreign key
-- lookup and the by-slug lookup within a board.
CREATE UNIQUE INDEX board_sprints_board_id_slug_uk ON kaizen.board_sprints (board_id, slug) WHERE deleted = FALSE;

-- Channels are scoped to an organization; organization_id leads for the
-- by-slug lookup in the session's organization.
CREATE UNIQUE INDEX chat_channels_organization_id_slug_uk ON kaizen.chat_channels (organization_id, slug) WHERE deleted = FALSE;
