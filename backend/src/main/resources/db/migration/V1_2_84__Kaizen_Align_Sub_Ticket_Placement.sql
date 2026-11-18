-- Database Schema - Kaizen Align Sub Ticket Placement
-- V1_2_84__Kaizen_Align_Sub_Ticket_Placement.sql
--
-- Data only. Sprint and backlog lists used to place a sub-ticket where its
-- top-level ancestor was placed, because a sub-ticket's own sprint_id and
-- backlog_id could be stale in rows written before sub-tickets moved with their
-- parent. The lists now read a sub-ticket's own columns, so every live
-- sub-ticket gets the sprint_id and backlog_id of its top-level live ancestor
-- here, and each ticket stays listed exactly where users saw it before.
--
-- The walk follows parent_ticket_id through non-deleted parents only and ends on
-- a parent cycle, at the last ticket before the cycle closes. A ticket whose
-- ancestor has neither a sprint nor a backlog keeps its own columns, so that it
-- is never left in no list. An ancestor on another board is skipped, since a
-- backlog belongs to one board. No parent_ticket_id, status, board_id or
-- updated_at changes. Only rows that differ are updated, so a rerun updates
-- nothing, except inside a parent cycle: there each ticket takes the placement
-- of another ticket of the cycle, so a rerun moves them again.

-- =====================================================
-- 1. Align sub-tickets with their top-level ancestor
-- =====================================================

WITH RECURSIVE ancestry (ticket_id, ancestor_id, depth, path) AS (
    SELECT t.id, p.id, 1, ARRAY[t.id, p.id]
      FROM kaizen.tickets t
      JOIN kaizen.tickets p ON p.id = t.parent_ticket_id AND p.deleted = FALSE
     WHERE t.deleted = FALSE
    UNION ALL
    SELECT a.ticket_id, p.id, a.depth + 1, a.path || p.id
      FROM ancestry a
      JOIN kaizen.tickets c ON c.id = a.ancestor_id
      JOIN kaizen.tickets p ON p.id = c.parent_ticket_id AND p.deleted = FALSE
     WHERE p.id <> ALL (a.path)
),
top_ancestor AS (
    SELECT DISTINCT ON (ticket_id) ticket_id, ancestor_id
      FROM ancestry
     ORDER BY ticket_id, depth DESC
)
UPDATE kaizen.tickets t
   SET sprint_id = anc.sprint_id,
       backlog_id = anc.backlog_id
  FROM top_ancestor ta
  JOIN kaizen.tickets anc ON anc.id = ta.ancestor_id
 WHERE t.id = ta.ticket_id
   AND anc.board_id = t.board_id
   AND (anc.sprint_id IS NOT NULL OR anc.backlog_id IS NOT NULL)
   AND (t.sprint_id IS DISTINCT FROM anc.sprint_id OR t.backlog_id IS DISTINCT FROM anc.backlog_id);
