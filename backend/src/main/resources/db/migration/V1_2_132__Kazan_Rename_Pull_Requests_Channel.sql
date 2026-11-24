-- Database Schema - Kazan Rename Pull Requests Channel
-- V1_2_132__Kazan_Rename_Pull_Requests_Channel.sql
--
-- Data only. The pull requests channel is where a pull request is shared for
-- review, so it is called "Pull Requests" instead of "PR". Only channels that still
-- carry the original name are renamed: a name an organization chose itself
-- stays. The slug stays "pr", so links to the channel keep working.
UPDATE kazan.chat_channels
   SET name = 'Pull Requests', updated_at = now()
 WHERE purpose = 'PULL_REQUESTS'::kazan_chat_channel_purpose_enum
   AND name = 'PR';
