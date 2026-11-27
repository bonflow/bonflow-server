-- Quick "sold out today" switch; the article stays on the menu but cannot be ordered.
ALTER TABLE article ADD COLUMN sold_out BOOLEAN NOT NULL DEFAULT FALSE;
