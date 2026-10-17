-- Demo account for local development and the demo: username "demo", password "demo12345".
-- The value is the BCrypt (cost 10) hash of that password; AuthFlowIT logs in with it and
-- BCryptPasswordHasherTest checks the same hash. Remove or change this user before any real deployment.
INSERT INTO app_user (username, password_hash, role)
VALUES ('demo', '$2a$10$g9CyLR1JLlPk7BKr3k5QteRL5cn62q3eGB2DiHBWsKRNKbjjcvbDO', 'USER')
ON CONFLICT (username) DO NOTHING;
