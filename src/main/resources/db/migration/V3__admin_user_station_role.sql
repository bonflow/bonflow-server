-- Station display devices get their own login (role STATION), managed like admin users.
ALTER TABLE admin_user DROP CONSTRAINT admin_user_role_check;
ALTER TABLE admin_user ADD CONSTRAINT admin_user_role_check CHECK (role IN ('ADMIN', 'MANAGER', 'STATION'));
