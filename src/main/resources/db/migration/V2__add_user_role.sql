-- Add role column to users table
ALTER TABLE users 
ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Add check constraint to ensure valid roles
ALTER TABLE users
ADD CONSTRAINT check_user_role CHECK (role IN ('USER', 'ADMIN'));

-- Create index on role for performance
CREATE INDEX idx_users_role ON users(role);