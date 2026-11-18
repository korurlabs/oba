-- Legacy rows keep file_hash = NULL because backfill requires Azure round-trips and is out of scope;
-- NULL cannot match in existsBy..., so legacy rows never produce a false-positive duplicate.
ALTER TABLE shrine.attachments ADD COLUMN file_hash VARCHAR(64);

CREATE INDEX shrine_attachments_entity_type_entity_id_file_hash_idx
    ON shrine.attachments (entity_type, entity_id, file_hash);
