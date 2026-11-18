-- Database Schema - Kaizen Backfill Pull Requests Channel
-- V1_2_79__Kaizen_Backfill_Pull_Requests_Channel.sql
--
-- Data only. Gives every organization without a live pull requests channel a
-- public "PR" text channel with the PULL_REQUESTS purpose. No read states are
-- created: a channel without one has no unread messages. The statement only
-- touches organizations that still lack the channel, so a rerun inserts nothing.

-- The channel list is ordered by created_at: "PR" gets a time two milliseconds
-- after "General", one after "Updates", to keep it last (now() is the same for
-- every row of the transaction).
INSERT INTO kaizen.chat_channels
    (id, name, type, purpose, visibility, organization_id, created_by_id, deleted, created_at, updated_at)
SELECT gen_random_uuid(), 'PR', 'TEXT'::kaizen_chat_channel_type_enum, 'PULL_REQUESTS'::kaizen_chat_channel_purpose_enum,
       'PUBLIC'::kaizen_chat_channel_visibility_enum, o.id, NULL, FALSE,
       now() + INTERVAL '2 milliseconds', now() + INTERVAL '2 milliseconds'
  FROM shrine.organizations o
 WHERE NOT EXISTS (SELECT 1 FROM kaizen.chat_channels c
                    WHERE c.organization_id = o.id AND c.purpose = 'PULL_REQUESTS' AND c.deleted = FALSE);
