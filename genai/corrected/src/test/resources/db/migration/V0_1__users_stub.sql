-- Test-only stand-in for the platform's users table (the AI-generated V1 migration has a foreign key to it).
CREATE TABLE users (
    username VARCHAR(255) PRIMARY KEY
);
