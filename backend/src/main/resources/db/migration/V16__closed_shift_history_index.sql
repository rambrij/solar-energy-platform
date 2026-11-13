CREATE INDEX ix_own_closed_shift ON pos_sessions(tenant_id,branch_id,cashier,state,closed_at,id);
