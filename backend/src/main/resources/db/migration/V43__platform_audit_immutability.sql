-- Platform audit is append-only at the database boundary.
CREATE TRIGGER trg_platform_audit_no_update
BEFORE UPDATE ON platform_audit_events
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Platform audit events are immutable';

CREATE TRIGGER trg_platform_audit_no_delete
BEFORE DELETE ON platform_audit_events
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Platform audit events cannot be deleted';
