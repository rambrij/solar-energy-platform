-- Tenant-scoped installation commissioning checklist and immutable evidence history.
CREATE TABLE installation_checklist_items (
  tenant_id BIGINT NOT NULL,
  installation_id BIGINT NOT NULL,
  item_key VARCHAR(64) NOT NULL,
  label VARCHAR(255) NOT NULL,
  sort_order SMALLINT NOT NULL,
  required BOOLEAN NOT NULL DEFAULT TRUE,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  completed_by VARCHAR(100) NULL,
  completed_at TIMESTAMP(6) NULL,
  evidence_note VARCHAR(1000) NULL,
  PRIMARY KEY (tenant_id,installation_id,item_key),
  KEY ix_installation_checklist_order (tenant_id,installation_id,sort_order,item_key),
  CONSTRAINT fk_installation_checklist_job FOREIGN KEY (tenant_id,installation_id)
    REFERENCES installation_jobs(tenant_id,id),
  CONSTRAINT fk_installation_checklist_actor FOREIGN KEY (tenant_id,completed_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_installation_checklist_state CHECK (
    (completed=TRUE AND completed_by IS NOT NULL AND completed_at IS NOT NULL AND evidence_note IS NOT NULL)
    OR (completed=FALSE AND completed_by IS NULL AND completed_at IS NULL AND evidence_note IS NULL)
  )
);

CREATE TABLE installation_checklist_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  installation_id BIGINT NOT NULL,
  item_key VARCHAR(64) NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  evidence_note VARCHAR(1000) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_installation_checklist_event_timeline (tenant_id,installation_id,occurred_at,id),
  CONSTRAINT fk_installation_checklist_event_item
    FOREIGN KEY (tenant_id,installation_id,item_key)
    REFERENCES installation_checklist_items(tenant_id,installation_id,item_key),
  CONSTRAINT fk_installation_checklist_event_actor
    FOREIGN KEY (tenant_id,actor_username)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_installation_checklist_event_type CHECK (
    event_type IN ('ITEM_COMPLETED','CHECKLIST_SUBMITTED')
  )
);

-- Existing open jobs receive the required checklist. Historical terminal jobs are not
-- represented as having evidence that was never captured.
INSERT INTO installation_checklist_items(
  tenant_id,installation_id,item_key,label,sort_order,required)
SELECT j.tenant_id,j.id,c.item_key,c.label,c.sort_order,TRUE
FROM installation_jobs j
CROSS JOIN (
  SELECT 'SITE_READINESS' item_key,'Site readiness and customer access are confirmed against the approved survey and design' label,10 sort_order
  UNION ALL SELECT 'ORDER_EQUIPMENT_MATCH','Equipment and serialized products match the approved order and records',20
  UNION ALL SELECT 'MOUNTING_MODULES_VERIFIED','Mounting system and modules are verified against the approved design by a qualified installer',30
  UNION ALL SELECT 'ELECTRICAL_WORK_VERIFIED','Electrical connections and protection are verified by a qualified professional against the approved design',40
  UNION ALL SELECT 'EARTHING_SAFETY_VERIFIED','Earthing and applicable safety checks are verified by a qualified professional',50
  UNION ALL SELECT 'INVERTER_COMMISSIONING_VERIFIED','Inverter configuration and commissioning results are verified against the approved plan',60
  UNION ALL SELECT 'MONITORING_CONNECTED','Monitoring and telemetry connectivity are confirmed',70
  UNION ALL SELECT 'CUSTOMER_HANDOVER_COMPLETED','Customer handover and required documentation are completed',80
) c
WHERE j.status IN ('ASSIGNED','IN_PROGRESS');

/*!80000 CREATE TRIGGER trg_installation_checklist_events_no_update BEFORE UPDATE ON installation_checklist_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Installation checklist events are immutable' */;
/*!80000 CREATE TRIGGER trg_installation_checklist_events_no_delete BEFORE DELETE ON installation_checklist_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Installation checklist events cannot be deleted' */;
/*!80000 CREATE TRIGGER trg_installation_checklist_items_completed_no_update BEFORE UPDATE ON installation_checklist_items FOR EACH ROW IF OLD.completed=TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Completed installation checklist evidence is immutable'; END IF */;
/*!80000 CREATE TRIGGER trg_installation_checklist_items_no_delete BEFORE DELETE ON installation_checklist_items FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Installation checklist items cannot be deleted' */;
