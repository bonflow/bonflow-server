-- Optional display colour (#RRGGBB) for the buttons of areas, tables and article categories.
ALTER TABLE area ADD COLUMN color VARCHAR(7) CHECK (color ~ '^#[0-9A-F]{6}$');
ALTER TABLE dining_table ADD COLUMN color VARCHAR(7) CHECK (color ~ '^#[0-9A-F]{6}$');
ALTER TABLE article_category ADD COLUMN color VARCHAR(7) CHECK (color ~ '^#[0-9A-F]{6}$');
