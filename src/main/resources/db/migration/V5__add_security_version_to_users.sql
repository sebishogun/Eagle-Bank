-- Add security version to users table for token invalidation on password change
ALTER TABLE users ADD COLUMN security_version INTEGER DEFAULT 0 NOT NULL;

-- Add index for better performance
CREATE INDEX idx_users_security_version ON users(security_version);