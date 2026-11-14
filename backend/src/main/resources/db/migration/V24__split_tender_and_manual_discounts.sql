-- Split tender (SRS POS-013) and supervisor-governed manual discounts (POS-024).

-- Per-tender rows. CASH: tendered = cash handed over, applied = what it settles, change = difference.
-- UPI/CARD: tendered = applied, change = 0. Sum(applied) = orders.total is enforced by the service.
CREATE TABLE order_tenders (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 order_id VARCHAR(36) NOT NULL,
 tender_no INT NOT NULL CHECK(tender_no>=1 AND tender_no<=4),
 method VARCHAR(8) NOT NULL CHECK(method IN ('CASH','UPI','CARD')),
 applied_amount DECIMAL(19,2) NOT NULL CHECK(applied_amount>0),
 tendered_amount DECIMAL(19,2) NOT NULL CHECK(tendered_amount>=applied_amount),
 change_given DECIMAL(19,2) NOT NULL CHECK(change_given>=0),
 merchant_account VARCHAR(80) NULL,
 provider_reference VARCHAR(80) NULL,
 created_at TIMESTAMP NOT NULL,
 UNIQUE(tenant_id,order_id,tender_no),
 UNIQUE(tenant_id,order_id,method),
 CHECK(change_given=tendered_amount-applied_amount),
 CHECK(method='CASH' OR change_given=0),
 FOREIGN KEY(tenant_id,order_id) REFERENCES orders(tenant_id,id)
);

-- A sale may now be paid by several methods. For SPLIT, cash_received-change_given is the cash portion
-- (so the drawer formula is unchanged) and must be strictly below the total because another tender pays the rest.
ALTER TABLE orders DROP CONSTRAINT chk_sale_payment_method;
ALTER TABLE orders DROP CONSTRAINT chk_sale_cash_amount;
ALTER TABLE orders ADD CONSTRAINT chk_sale_payment_method CHECK(payment_method IN ('CASH','MOCK_UPI','MOCK_CARD','SPLIT'));
ALTER TABLE orders ADD CONSTRAINT chk_sale_cash_amount CHECK(status<>'PAID' OR sales_channel='ONLINE'
 OR (payment_method='CASH' AND cash_received-change_given=total)
 OR (payment_method IN ('MOCK_UPI','MOCK_CARD') AND cash_received=0 AND change_given=0)
 OR (payment_method='SPLIT' AND cash_received>=0 AND change_given>=0 AND change_given<=cash_received AND cash_received-change_given<total));

-- Manual discounts. order_items.price stays the effective unit price actually charged (so tax, receipts and refund
-- allocation remain correct); price+manual_discount_unit equals the current catalogue price and is re-validated on payment.
ALTER TABLE order_items ADD manual_discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0 CHECK(manual_discount_percent>=0 AND manual_discount_percent<100);
ALTER TABLE order_items ADD manual_discount_unit DECIMAL(19,2) NOT NULL DEFAULT 0 CHECK(manual_discount_unit>=0);
ALTER TABLE orders ADD discount_approved_by VARCHAR(100) NULL;
-- Cashier discount ceiling per branch. 0 (default) disables cashier-initiated discounts; approvers are not limited.
ALTER TABLE branches ADD manual_discount_limit_percent DECIMAL(5,2) NOT NULL DEFAULT 0 CHECK(manual_discount_limit_percent>=0 AND manual_discount_limit_percent<100);
