-- Oba Database Schema - Kaizen: Create Sprint Series
-- V1_2_90__Kaizen_Create_Sprint_Series.sql
--
-- "Repeat automatically" moves from each sprint (kaizen.sprint_recurrences) to a
-- sprint series. A series holds the name, the counter of the next round, the
-- repeat settings and its boards, and is the single source of truth: nothing is
-- copied from sprint to sprint any more. A sprint gets an optional series and a
-- round within it. Existing recurrence chains are converted into series with
-- rounds. The old table and kaizen.sprints.number are dropped by V1_2_91.

-- =====================================================
-- 1. Create tables
-- =====================================================

-- The three enum types are the ones created for sprint_recurrences and stay in
-- use here. A series is never deleted by the application. The bound of
-- create_days_before against length_days is checked in the service.
CREATE TABLE kaizen.sprint_series (
    id UUID NOT NULL,
    name VARCHAR(240) NOT NULL,
    next_round INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    length_days INTEGER NOT NULL,
    create_days_before INTEGER NULL,
    next_creation_at TIMESTAMP WITH TIME ZONE NULL,
    last_run_at TIMESTAMP WITH TIME ZONE NULL,
    last_run_message VARCHAR(500) NULL,
    unfinished_ticket_action kaizen_sprint_recurrence_ticket_action_enum NOT NULL DEFAULT 'MOVE_TO_NEXT_SPRINT',
    creation_mode kaizen_sprint_recurrence_creation_mode_enum NOT NULL DEFAULT 'AT_END',
    last_run_status kaizen_sprint_recurrence_run_status_enum NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sprint_series_pk PRIMARY KEY (id),
    CONSTRAINT sprint_series_next_round_ck CHECK (next_round >= 1),
    CONSTRAINT sprint_series_length_days_ck CHECK (length_days BETWEEN 1 AND 365),
    CONSTRAINT sprint_series_create_days_before_ck CHECK (create_days_before IS NULL OR create_days_before BETWEEN 1 AND 364)
);

-- The boards a series creates its sprints on, in order (position). The rows go
-- with the series (CASCADE on series_id). A board that is deleted for good is
-- not cascaded: the application soft-deletes boards.
CREATE TABLE kaizen.sprint_series_boards (
    id UUID NOT NULL,
    series_id UUID NOT NULL,
    board_id UUID NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sprint_series_boards_pk PRIMARY KEY (id),
    CONSTRAINT sprint_series_boards_series_id_fk FOREIGN KEY (series_id) REFERENCES kaizen.sprint_series (id) ON DELETE CASCADE,
    CONSTRAINT sprint_series_boards_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id),
    CONSTRAINT sprint_series_boards_series_id_board_id_uk UNIQUE (series_id, board_id)
);

-- =====================================================
-- 2. Add the series columns to sprints
-- =====================================================

-- series_id and round are both null or both set, and a round starts at 1.
-- Series names are not unique, so only (series_id, round) is. Soft-deleted
-- sprints count in the unique constraint on purpose: a deleted round is never
-- created again. No ON DELETE rule: a series is never deleted, and a sprint
-- never outlives its series.
ALTER TABLE kaizen.sprints
    ADD COLUMN series_id UUID NULL,
    ADD COLUMN round INTEGER NULL,
    ADD CONSTRAINT sprints_series_id_fk FOREIGN KEY (series_id) REFERENCES kaizen.sprint_series (id),
    ADD CONSTRAINT sprints_series_round_ck CHECK ((series_id IS NULL) = (round IS NULL) AND (round IS NULL OR round >= 1)),
    ADD CONSTRAINT sprints_series_id_round_uk UNIQUE (series_id, round);

-- =====================================================
-- 3. Create indexes
-- =====================================================

-- Foreign key lookup by board (the unique constraint on (series_id, board_id)
-- already serves series_id). sprints.series_id is served by the unique index on
-- (series_id, round).
CREATE INDEX sprint_series_boards_board_id_idx ON kaizen.sprint_series_boards (board_id);

-- The scheduled job reads only the enabled series, which are few next to all.
CREATE INDEX sprint_series_enabled_idx ON kaizen.sprint_series (id) WHERE enabled;

-- =====================================================
-- 4. Convert sprint_recurrences into series
-- =====================================================

-- A chain is the rows linked by next_sprint_id, from the row no other row points
-- to, to the last row. The chain is the history of one repeating sprint, so it
-- becomes one series and each sprint in it one round.
CREATE TEMPORARY TABLE sprint_series_chain_members ON COMMIT DROP AS
WITH RECURSIVE chain AS (
    SELECT r.sprint_id AS head_sprint_id, r.id AS recurrence_id, r.sprint_id, r.next_sprint_id, 0 AS k
    FROM kaizen.sprint_recurrences r
    WHERE NOT EXISTS (SELECT 1 FROM kaizen.sprint_recurrences p WHERE p.next_sprint_id = r.sprint_id)
    UNION ALL
    SELECT c.head_sprint_id, n.id, n.sprint_id, n.next_sprint_id, c.k + 1
    FROM chain c
    JOIN kaizen.sprint_recurrences n ON n.sprint_id = c.next_sprint_id
    WHERE c.k < 100000
)
SELECT head_sprint_id, recurrence_id, sprint_id, k FROM chain;

-- One row per chain whose last row is enabled. Chains whose last row is
-- disabled get no series. The first sprint's name gives the series name and the
-- first round: a trailing " <digits>" (1 to 999999) is split off, else the whole
-- name and round 1. The length is the whole days between the last sprint's
-- dates; when that is outside 1 to 365 or a date is missing the series gets 14
-- days and is switched off. next_creation_at is kept only while the last sprint
-- is ACTIVE, because only then does the job read it.
CREATE TEMPORARY TABLE sprint_series_chains ON COMMIT DROP AS
SELECT
    gen_random_uuid() AS series_id,
    f.head_sprint_id,
    last_m.sprint_id AS last_sprint_id,
    last_m.k + 1 AS chain_length,
    CASE
        WHEN m.parts IS NOT NULL AND m.parts[2]::INTEGER BETWEEN 1 AND 999999 THEN m.parts[1]
        ELSE m.trimmed
    END AS series_name,
    CASE
        WHEN m.parts IS NOT NULL AND m.parts[2]::INTEGER BETWEEN 1 AND 999999 THEN m.parts[2]::INTEGER
        ELSE 1
    END AS first_round,
    lr.enabled AS last_enabled,
    lr.unfinished_ticket_action,
    lr.creation_mode,
    lr.create_days_before,
    lr.last_run_at,
    lr.last_run_status,
    lr.last_run_message,
    CASE WHEN ls.status = 'ACTIVE' THEN lr.next_creation_at END AS next_creation_at,
    CASE
        WHEN ls.start_date IS NULL OR ls.end_date IS NULL THEN NULL
        ELSE FLOOR(EXTRACT(EPOCH FROM (ls.end_date - ls.start_date)) / 86400)::INTEGER
    END AS length_days_raw
FROM (SELECT DISTINCT head_sprint_id FROM sprint_series_chain_members) h
JOIN sprint_series_chain_members f ON f.head_sprint_id = h.head_sprint_id AND f.k = 0
JOIN LATERAL (
    SELECT cm.sprint_id, cm.recurrence_id, cm.k
    FROM sprint_series_chain_members cm
    WHERE cm.head_sprint_id = h.head_sprint_id
    ORDER BY cm.k DESC
    LIMIT 1
) last_m ON TRUE
JOIN kaizen.sprint_recurrences lr ON lr.id = last_m.recurrence_id
JOIN kaizen.sprints ls ON ls.id = last_m.sprint_id
JOIN kaizen.sprints fs ON fs.id = f.sprint_id
CROSS JOIN LATERAL (
    SELECT
        regexp_replace(fs.name, '^\s+|\s+$', '', 'g') AS trimmed,
        regexp_match(regexp_replace(fs.name, '^\s+|\s+$', '', 'g'), '^(.*\S)\s+(\d{1,6})$') AS parts
) m
WHERE lr.enabled;

INSERT INTO kaizen.sprint_series (
    id, name, next_round, enabled, length_days, create_days_before, next_creation_at,
    last_run_at, last_run_message, unfinished_ticket_action, creation_mode, last_run_status
)
SELECT
    series_id,
    LEFT(series_name, 240),
    first_round + chain_length,
    length_days_raw BETWEEN 1 AND 365,
    CASE WHEN length_days_raw BETWEEN 1 AND 365 THEN length_days_raw ELSE 14 END,
    create_days_before,
    CASE WHEN length_days_raw BETWEEN 1 AND 365 THEN next_creation_at END,
    last_run_at,
    last_run_message,
    unfinished_ticket_action,
    creation_mode,
    last_run_status
FROM sprint_series_chains;

-- Every sprint of a converted chain gets its round: first_round + position in
-- the chain. Names and slugs are not changed here; the next rename or created
-- sprint follows the series naming rule.
UPDATE kaizen.sprints s
SET series_id = c.series_id,
    round = c.first_round + m.k
FROM sprint_series_chain_members m
JOIN sprint_series_chains c ON c.head_sprint_id = m.head_sprint_id
WHERE s.id = m.sprint_id;

-- The boards of a series are the live boards of its last sprint, in link order.
INSERT INTO kaizen.sprint_series_boards (id, series_id, board_id, position)
SELECT
    gen_random_uuid(),
    c.series_id,
    bs.board_id,
    (ROW_NUMBER() OVER (PARTITION BY c.series_id ORDER BY bs.created_at, bs.id) - 1)::INTEGER
FROM sprint_series_chains c
JOIN kaizen.board_sprints bs ON bs.sprint_id = c.last_sprint_id AND bs.deleted = FALSE;
