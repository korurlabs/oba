-- =====================================================
-- Kaizen: Add board_sprints join table for ManyToMany relationship
-- =====================================================
-- The Board-Sprint relationship was changed from OneToMany (board_id on sprints)
-- to ManyToMany (join table). This migration creates the join table and migrates
-- existing data from sprints.board_id.

CREATE TABLE kaizen.board_sprints (
    board_id  UUID NOT NULL,
    sprint_id UUID NOT NULL,
    CONSTRAINT board_sprints_pk PRIMARY KEY (board_id, sprint_id),
    CONSTRAINT board_sprints_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE,
    CONSTRAINT board_sprints_sprint_id_fk FOREIGN KEY (sprint_id) REFERENCES kaizen.sprints (id) ON DELETE CASCADE
);

-- Migrate existing board-sprint relationships
INSERT INTO kaizen.board_sprints (board_id, sprint_id)
SELECT board_id, id
FROM kaizen.sprints;

-- Remove board_id from sprints now that the relationship is managed via board_sprints
ALTER TABLE kaizen.sprints DROP CONSTRAINT sprints_board_id_number_uk;
ALTER TABLE kaizen.sprints DROP CONSTRAINT sprints_board_id_fk;
ALTER TABLE kaizen.sprints DROP COLUMN board_id;
