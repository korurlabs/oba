-- Kaizen: Remove Story Points from Tickets
-- V1_1_21__Kaizen_Remove_Story_Points_From_Tickets.sql
--
-- Drops the story_points column from kaizen.tickets.
-- Story points are no longer part of the ticket model.

ALTER TABLE kaizen.tickets DROP COLUMN IF EXISTS story_points;
