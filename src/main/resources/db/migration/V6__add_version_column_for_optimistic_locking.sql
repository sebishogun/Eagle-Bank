-- Add version column for optimistic locking (required by BaseEntity)
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;