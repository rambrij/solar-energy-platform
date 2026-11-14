-- Match the scoped history reads without changing order/payment lifecycle data.
CREATE INDEX ix_customer_order_history ON orders(customer_id,sales_channel,created_at,id);
CREATE INDEX ix_branch_order_history ON orders(tenant_id,branch_id,sales_channel,created_at,id);
