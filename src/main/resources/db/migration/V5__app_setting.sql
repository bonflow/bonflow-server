-- Venue wide settings (key/value), edited in the admin UI.
CREATE TABLE app_setting (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL
);
