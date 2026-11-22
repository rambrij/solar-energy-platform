ALTER TABLE vendor_roles ADD COLUMN template_key VARCHAR(80);
ALTER TABLE vendor_roles ADD COLUMN template_version INTEGER;
ALTER TABLE vendor_roles ADD CONSTRAINT chk_role_template_origin CHECK (
    (template_key IS NULL AND template_version IS NULL) OR
    (template_key IS NOT NULL AND template_version IS NOT NULL AND template_version > 0)
);

UPDATE vendor_roles SET template_key=SUBSTRING(role_key,10),template_version=1
WHERE role_key LIKE 'standard:%' AND template_key IS NULL AND template_version IS NULL;
