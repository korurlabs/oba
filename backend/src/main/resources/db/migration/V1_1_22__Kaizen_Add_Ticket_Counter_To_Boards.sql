-- Kaizen: Add ticket_counter to boards
-- V1_1_22__Kaizen_Add_Ticket_Counter_To_Boards.sql
--
-- Stores the last-used ticket number for each board.
-- Initialised to the current maximum ticket number per board so that
-- existing tickets are not invalidated.

ALTER TABLE kaizen.boards
    ADD COLUMN ticket_counter BIGINT NOT NULL DEFAULT 0;

-- Back-fill: set counter to the highest ticket number already in use
-- for each board. Key format is '{PREFIX}-{NUMBER}'.
UPDATE kaizen.boards b
SET    ticket_counter = COALESCE((
           SELECT MAX(
               CAST(
                   SUBSTRING(t.key FROM '^[^-]+-(\d+)$')
                   AS BIGINT
               )
           )
           FROM kaizen.tickets t
           WHERE t.board_id = b.id
             AND t.key ~ '^[^-]+-[0-9]+$'
       ), 0);
