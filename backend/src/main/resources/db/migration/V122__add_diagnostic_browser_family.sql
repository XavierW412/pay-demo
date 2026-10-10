ALTER TABLE diagnostic_incident
    ADD COLUMN browser_family VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD CONSTRAINT ck_diagnostic_incident_browser_family
        CHECK (browser_family IN ('CHROMIUM', 'FIREFOX', 'SAFARI', 'OTHER', 'UNKNOWN'));
