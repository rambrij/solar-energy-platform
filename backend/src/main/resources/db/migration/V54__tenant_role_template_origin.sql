ALTER TABLE vendor_roles ADD COLUMN template_key VARCHAR(80);
ALTER TABLE vendor_roles ADD COLUMN template_version INTEGER;
ALTER TABLE vendor_roles ADD CONSTRAINT chk_role_template_origin CHECK (
    (template_key IS NULL AND template_version IS NULL) OR
    (template_key IS NOT NULL AND template_version IS NOT NULL AND template_version > 0)
);
