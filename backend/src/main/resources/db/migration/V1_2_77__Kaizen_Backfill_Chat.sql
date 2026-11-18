-- Database Schema - Kaizen Backfill Chat
-- V1_2_77__Kaizen_Backfill_Chat.sql
--
-- Data only. Gives every organization its two initial channels, "General" and
-- "Updates", gives every existing daily update the organization it belongs to,
-- creates its chat message in the "Updates" channel, and gives every active
-- member a channel read state for both channels so the backfilled history does
-- not show as unread. Every statement is idempotent: it only touches rows that
-- are still missing, so a rerun inserts and changes nothing.

-- =====================================================
-- 1. Create the initial channels
-- =====================================================

-- "General" is created before "Updates": the channel list is ordered by
-- created_at, so "Updates" gets a time one millisecond later to keep that order
-- stable (now() is the same for every row of the transaction).
INSERT INTO kaizen.chat_channels
    (id, name, type, purpose, visibility, organization_id, created_by_id, deleted, created_at, updated_at)
SELECT gen_random_uuid(), 'General', 'TEXT'::kaizen_chat_channel_type_enum, 'GENERAL'::kaizen_chat_channel_purpose_enum,
       'PUBLIC'::kaizen_chat_channel_visibility_enum, o.id, NULL, FALSE, now(), now()
  FROM shrine.organizations o
 WHERE NOT EXISTS (SELECT 1 FROM kaizen.chat_channels c WHERE c.organization_id = o.id);

INSERT INTO kaizen.chat_channels
    (id, name, type, purpose, visibility, organization_id, created_by_id, deleted, created_at, updated_at)
SELECT gen_random_uuid(), 'Updates', 'TEXT'::kaizen_chat_channel_type_enum, 'DAILY_UPDATES'::kaizen_chat_channel_purpose_enum,
       'PUBLIC'::kaizen_chat_channel_visibility_enum, o.id, NULL, FALSE,
       now() + INTERVAL '1 millisecond', now() + INTERVAL '1 millisecond'
  FROM shrine.organizations o
 WHERE NOT EXISTS (SELECT 1 FROM kaizen.chat_channels c
                    WHERE c.organization_id = o.id AND c.purpose = 'DAILY_UPDATES');

-- =====================================================
-- 2. Resolve the organization of each author
-- =====================================================

-- The default organization of the author when the membership there is ACTIVE
-- and not deleted and the organization is ACTIVE and not deleted. Otherwise the
-- ACTIVE membership of an ACTIVE organization with the earliest membership
-- created_at (ties by organization id). Authors with neither get no
-- organization and so no chat message.
CREATE TEMPORARY TABLE tmp_author_organizations ON COMMIT DROP AS
SELECT u.id AS user_id,
       COALESCE(
           (SELECT o.id
              FROM shrine.organization_members m
              JOIN shrine.organizations o ON o.id = m.organization_id
             WHERE m.user_id = u.id
               AND m.organization_id = u.default_organization_id
               AND m.status = 'ACTIVE' AND m.deleted = FALSE
               AND o.status = 'ACTIVE' AND o.deleted = FALSE),
           (SELECT o.id
              FROM shrine.organization_members m
              JOIN shrine.organizations o ON o.id = m.organization_id
             WHERE m.user_id = u.id
               AND m.status = 'ACTIVE' AND m.deleted = FALSE
               AND o.status = 'ACTIVE' AND o.deleted = FALSE
             ORDER BY m.created_at ASC, o.id ASC
             LIMIT 1)
       ) AS organization_id
  FROM shrine.users u
 WHERE u.id IN (SELECT author_id FROM kaizen.daily_updates);

-- =====================================================
-- 3. Set the organization of every daily update
-- =====================================================

-- Deleted updates too, so the column is complete.
UPDATE kaizen.daily_updates du
   SET organization_id = ao.organization_id
  FROM tmp_author_organizations ao
 WHERE ao.user_id = du.author_id
   AND ao.organization_id IS NOT NULL
   AND du.organization_id IS NULL;

-- =====================================================
-- 4. Create the daily update chat messages
-- =====================================================

-- One message per live update with an organization, in the "Updates" channel of
-- that organization, keeping the update's times.
INSERT INTO kaizen.chat_messages
    (id, content, channel_id, organization_id, author_id, daily_update_id, edited_at, deleted, created_at, updated_at)
SELECT gen_random_uuid(), '', c.id, du.organization_id, du.author_id,
       du.id, du.edited_at, FALSE, du.created_at, du.updated_at
  FROM kaizen.daily_updates du
  JOIN kaizen.chat_channels c
    ON c.organization_id = du.organization_id AND c.purpose = 'DAILY_UPDATES' AND c.deleted = FALSE
 WHERE du.deleted = FALSE
   AND du.organization_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM kaizen.chat_messages cm WHERE cm.daily_update_id = du.id);

-- =====================================================
-- 5. Create the channel read states
-- =====================================================

-- Every ACTIVE membership starts caught up in both channels: the position is now
-- and the newest message of the channel, if any.
INSERT INTO kaizen.chat_read_states
    (id, last_read_at, last_read_message_id, user_id, channel_id, organization_id, thread_id)
SELECT gen_random_uuid(), now(),
       (SELECT cm.id
          FROM kaizen.chat_messages cm
         WHERE cm.channel_id = c.id
           AND cm.thread_id IS NULL AND cm.deleted = FALSE
         ORDER BY cm.created_at DESC, cm.id DESC
         LIMIT 1),
       m.user_id, c.id, m.organization_id, NULL
  FROM shrine.organization_members m
  JOIN kaizen.chat_channels c ON c.organization_id = m.organization_id AND c.deleted = FALSE
 WHERE m.status = 'ACTIVE' AND m.deleted = FALSE
   AND NOT EXISTS (SELECT 1 FROM kaizen.chat_read_states rs
                    WHERE rs.user_id = m.user_id
                      AND rs.channel_id = c.id
                      AND rs.thread_id IS NULL);
