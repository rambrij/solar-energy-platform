-- Optional LOCAL demo only. Run after Flyway V6 and the demo store setup.
-- Login: demo-customer / LocalCustomer2026! (separate browser from POS staff).
-- Does not reset existing passwords or create an employee/vendor relationship.
USE solar;
INSERT INTO users(id,username,password_hash,enabled)
SELECT 900001,'demo-customer','$2a$10$eB2Qfts0Ma/69scxE1oJ/OYL4zlbLntw7NKA.VpT0V9HshHDA2the',TRUE
WHERE NOT EXISTS(SELECT 1 FROM users WHERE id=900001 OR username='demo-customer')
  AND NOT EXISTS(SELECT 1 FROM employees WHERE username='demo-customer');
INSERT INTO customer_profiles(id,user_id)
SELECT 900001,900001
WHERE EXISTS(SELECT 1 FROM users WHERE id=900001 AND username='demo-customer')
  AND NOT EXISTS(SELECT 1 FROM customer_profiles WHERE id=900001 OR user_id=900001);
