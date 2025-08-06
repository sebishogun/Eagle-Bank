-- Create ShedLock table for distributed scheduling
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- Add index for performance
CREATE INDEX IF NOT EXISTS idx_shedlock_lock_until ON shedlock(lock_until);

-- Add comment for documentation
COMMENT ON TABLE shedlock IS 'Table used by ShedLock to coordinate scheduled tasks across multiple application instances';
COMMENT ON COLUMN shedlock.name IS 'Unique name of the scheduled task';
COMMENT ON COLUMN shedlock.lock_until IS 'Timestamp until which the lock is held';
COMMENT ON COLUMN shedlock.locked_at IS 'Timestamp when the lock was acquired';
COMMENT ON COLUMN shedlock.locked_by IS 'Identifier of the instance holding the lock';