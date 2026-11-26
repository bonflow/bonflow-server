-- When a station bumped the ticket. Recall clears it again. Orders "recently done" correctly.
ALTER TABLE station_ticket ADD COLUMN done_at TIMESTAMPTZ;
UPDATE station_ticket SET done_at = created_at WHERE status = 'DONE';
