-- scripts/db-migration/001_add_sm3_digest.sql (DM8 / MySQL 通用)
ALTER TABLE publish_artifacts ADD COLUMN sm3_digest VARCHAR(64);