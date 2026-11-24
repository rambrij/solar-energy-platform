CREATE TABLE `catalog_sequences` (
  `name` varchar(30) NOT NULL,
  `next_id` bigint NOT NULL,
  PRIMARY KEY (`name`)
);

CREATE TABLE `identity_sequence` (
  `id` int NOT NULL,
  `next_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `platform_permission_catalogue` (
  `permission` varchar(80) NOT NULL,
  PRIMARY KEY (`permission`)
);

CREATE TABLE `retail_sequences` (
  `name` varchar(30) NOT NULL,
  `next_id` bigint NOT NULL,
  PRIMARY KEY (`name`)
);

CREATE TABLE `subscription_plans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_key` varchar(80) NOT NULL,
  `display_name` varchar(120) NOT NULL,
  `active` BOOLEAN NOT NULL DEFAULT 1,
  `current_version` int NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `plan_key` (`plan_key`)
);

CREATE TABLE `tenant_sequences` (
  `id` int NOT NULL,
  `next_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `tenants` (
  `id` bigint NOT NULL,
  `name` varchar(160) NOT NULL,
  `slug` varchar(100) DEFAULT NULL,
  `online_branch_id` bigint DEFAULT NULL,
  `lifecycle_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `legal_name` varchar(180) NOT NULL DEFAULT '',
  `contact_email` varchar(100) DEFAULT NULL,
  `contact_phone` varchar(30) DEFAULT NULL,
  `tax_identifier` varchar(40) DEFAULT NULL,
  `address` varchar(500) DEFAULT NULL,
  `initial_vendor_admin_username` varchar(100) DEFAULT NULL,
  `terms_accepted_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_slug` (`slug`),
  KEY `fk_vendor_online_branch` (`id`,`online_branch_id`),
  CONSTRAINT `chk_tenant_lifecycle` CHECK ((`lifecycle_status` in ('PENDING','UNDER_REVIEW','ACTIVE','SUSPENDED','CLOSED')))
);

CREATE TABLE `users` (
  `id` bigint NOT NULL,
  `username` varchar(100) NOT NULL,
  `password_hash` varchar(200) NOT NULL,
  `enabled` BOOLEAN NOT NULL,
  `platform_mfa_secret_enc` varchar(512) DEFAULT NULL,
  `platform_mfa_enrolled_at` timestamp NULL DEFAULT NULL,
  `platform_mfa_last_counter` bigint NOT NULL DEFAULT '-1',
  `platform_mfa_failed_attempts` int NOT NULL DEFAULT '0',
  `platform_mfa_locked_until` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
);

CREATE TABLE `vendor_permission_catalogue` (
  `permission` varchar(80) NOT NULL,
  PRIMARY KEY (`permission`)
);

CREATE TABLE `branches` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `name` varchar(160) NOT NULL,
  `invoice_required` BOOLEAN NOT NULL DEFAULT 0,
  `receipt_sequence` bigint NOT NULL DEFAULT '0',
  `pos_return_window_days` int NOT NULL DEFAULT '0',
  `cash_refunds_enabled` BOOLEAN NOT NULL DEFAULT 0,
  `manual_tender_enabled` BOOLEAN NOT NULL DEFAULT 0,
  `upi_merchant_reference` varchar(80) DEFAULT NULL,
  `card_merchant_reference` varchar(80) DEFAULT NULL,
  `split_tender_enabled` BOOLEAN NOT NULL DEFAULT 0,
  `settings_version` bigint NOT NULL DEFAULT '0',
  `currency` varchar(3) NOT NULL DEFAULT 'INR',
  `buyer_details_required` BOOLEAN NOT NULL DEFAULT 0,
  `credit_notes_enabled` BOOLEAN NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`id`),
  CONSTRAINT `branches_ibfk_1` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `branches_chk_1` CHECK (((`pos_return_window_days` >= 0) and (`pos_return_window_days` <= 365)))
);

CREATE TABLE `catalog_categories` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `name` varchar(120) NOT NULL,
  `slug` varchar(100) NOT NULL,
  `active` BOOLEAN NOT NULL DEFAULT 1,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `catalog_categories_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `tenant_id_2` (`tenant_id`,`slug`),
  CONSTRAINT `catalog_categories_ibfk_1` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`)
);

CREATE TABLE `catalog_change_events` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `actor` varchar(100) NOT NULL,
  `action` varchar(40) NOT NULL,
  `resource_id` bigint NOT NULL,
  `reason` varchar(250) NOT NULL,
  `before_state` text NOT NULL,
  `after_state` text NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  KEY `catalog_change_events_tenant_id` (`tenant_id`),
  CONSTRAINT `catalog_change_events_ibfk_1` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`)
);

CREATE TABLE `catalog_discount_policy` (
  `tenant_id` bigint NOT NULL,
  `maximum_percent` decimal(5,2) NOT NULL,
  PRIMARY KEY (`tenant_id`),
  CONSTRAINT `catalog_discount_policy_ibfk_1` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `catalog_discount_policy_chk_1` CHECK (((`maximum_percent` >= 0) and (`maximum_percent` <= 100)))
);

CREATE TABLE `customer_profiles` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  CONSTRAINT `customer_profiles_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
);

CREATE TABLE `identity_invitations` (
  `token_hash` varchar(64) NOT NULL,
  `username` varchar(100) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `created_at` timestamp NOT NULL,
  `consumed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`token_hash`),
  KEY `ix_invitation_user` (`username`,`expires_at`),
  CONSTRAINT `fk_invitation_user` FOREIGN KEY (`username`) REFERENCES `users` (`username`)
);

CREATE TABLE `identity_refresh_tokens` (
  `token_hash` varchar(64) NOT NULL,
  `username` varchar(100) NOT NULL,
  `family` varchar(36) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `used_at` timestamp NULL DEFAULT NULL,
  `revoked` BOOLEAN NOT NULL DEFAULT 0,
  `mfa_verified` BOOLEAN NOT NULL DEFAULT 0,
  `actor_type` varchar(16) NOT NULL DEFAULT 'UNKNOWN',
  `tenant_id` bigint DEFAULT NULL,
  `membership_id` varchar(220) DEFAULT NULL,
  PRIMARY KEY (`token_hash`),
  KEY `ix_refresh_family` (`family`,`revoked`),
  KEY `ix_refresh_actor_scope` (`username`,`actor_type`,`tenant_id`,`membership_id`,`revoked`),
  CONSTRAINT `identity_refresh_tokens_ibfk_1` FOREIGN KEY (`username`) REFERENCES `users` (`username`)
);

CREATE TABLE `platform_audit_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_username` varchar(100) NOT NULL,
  `action` varchar(80) NOT NULL,
  `resource_type` varchar(60) NOT NULL,
  `resource_id` varchar(100) DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `outcome` varchar(20) NOT NULL,
  `correlation_id` varchar(100) DEFAULT NULL,
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_platform_audit_actor` (`actor_username`),
  KEY `ix_platform_audit_time` (`occurred_at` DESC,`id` DESC),
  KEY `ix_platform_audit_tenant_time` (`tenant_id`,`occurred_at` DESC,`id` DESC),
  CONSTRAINT `fk_platform_audit_actor` FOREIGN KEY (`actor_username`) REFERENCES `users` (`username`),
  CONSTRAINT `fk_platform_audit_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `chk_platform_audit_outcome` CHECK ((`outcome` in ('SUCCESS','DENIED','FAILURE')))
);

CREATE TABLE `platform_configuration` (
  `config_key` varchar(100) NOT NULL,
  `category` varchar(60) NOT NULL,
  `plain_value` text,
  `secret_reference` varchar(255) DEFAULT NULL,
  `is_sensitive` BOOLEAN NOT NULL DEFAULT 0,
  `updated_by` varchar(100) NOT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`config_key`),
  KEY `fk_platform_config_actor` (`updated_by`),
  CONSTRAINT `fk_platform_config_actor` FOREIGN KEY (`updated_by`) REFERENCES `users` (`username`),
  CONSTRAINT `chk_platform_config_secret` CHECK ((((`is_sensitive` = true) and (`plain_value` is null) and (`secret_reference` is not null)) or ((`is_sensitive` = false) and (`secret_reference` is null))))
);

CREATE TABLE `platform_mfa_enrollment_tokens` (
  `token_hash` varchar(64) NOT NULL,
  `username` varchar(100) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `pending_secret_enc` varchar(512) DEFAULT NULL,
  `pending_expires_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`token_hash`),
  KEY `ix_platform_mfa_token_user` (`username`,`expires_at`),
  CONSTRAINT `fk_platform_mfa_token_user` FOREIGN KEY (`username`) REFERENCES `users` (`username`)
);

CREATE TABLE `platform_support_access` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `actor_username` varchar(100) NOT NULL,
  `purpose` varchar(500) NOT NULL,
  `ticket_reference` varchar(120) NOT NULL,
  `granted_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` timestamp NOT NULL,
  `revoked_at` timestamp NULL DEFAULT NULL,
  `revoked_by` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_support_actor` (`actor_username`),
  KEY `fk_support_revoker` (`revoked_by`),
  KEY `ix_support_active` (`tenant_id`,`actor_username`,`expires_at`,`revoked_at`),
  CONSTRAINT `fk_support_actor` FOREIGN KEY (`actor_username`) REFERENCES `users` (`username`),
  CONSTRAINT `fk_support_revoker` FOREIGN KEY (`revoked_by`) REFERENCES `users` (`username`),
  CONSTRAINT `fk_support_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `chk_support_expiry` CHECK ((`expires_at` > `granted_at`))
);

CREATE TABLE `platform_user_permissions` (
  `username` varchar(100) NOT NULL,
  `permission` varchar(60) NOT NULL,
  PRIMARY KEY (`username`,`permission`),
  KEY `fk_pup_permission_catalog` (`permission`),
  CONSTRAINT `fk_pup_permission_catalog` FOREIGN KEY (`permission`) REFERENCES `platform_permission_catalogue` (`permission`),
  CONSTRAINT `platform_user_permissions_ibfk_1` FOREIGN KEY (`username`) REFERENCES `users` (`username`),
  CONSTRAINT `chk_platform_permissions_v47` CHECK ((`permission` in ('PLATFORM_TENANT_VIEW','PLATFORM_TENANT_CREATE','PLATFORM_TENANT_UPDATE','PLATFORM_TENANT_REACTIVATE','PLATFORM_TENANT_SUSPEND','PLATFORM_TENANT_CLOSE','PLATFORM_PLAN_MANAGE','PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_SUBSCRIPTION_ADJUST','PLATFORM_ANALYTICS_VIEW','PLATFORM_AUDIT_VIEW','PLATFORM_SUPPORT_ACCESS','PLATFORM_CONFIG_MANAGE','PLATFORM_IDENTITY_MANAGE')))
);

CREATE TABLE `pos_audit` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `actor` varchar(100) NOT NULL,
  `action` varchar(40) NOT NULL,
  `resource_id` varchar(36) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `pos_audit_tenant_id` (`tenant_id`),
  CONSTRAINT `pos_audit_ibfk_1` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`)
);

CREATE TABLE `products` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `sku` varchar(80) NOT NULL,
  `barcode` varchar(80) DEFAULT NULL,
  `name` varchar(160) NOT NULL,
  `price` decimal(19,2) NOT NULL,
  `tax_rate` decimal(5,2) NOT NULL,
  `published` BOOLEAN NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `brand` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `products_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `products_tenant_id_2` (`tenant_id`,`sku`),
  UNIQUE KEY `products_tenant_barcode` (`tenant_id`,`barcode`),
  CONSTRAINT `products_ibfk_1` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `products_chk_1` CHECK ((`price` >= 0)),
  CONSTRAINT `products_chk_2` CHECK (((`tax_rate` >= 0) and (`tax_rate` <= 100)))
);

CREATE TABLE `subscription_plan_versions` (
  `plan_id` bigint NOT NULL,
  `version` int NOT NULL,
  `price` decimal(19,4) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `billing_interval` varchar(12) NOT NULL,
  `max_users` int NOT NULL,
  `max_storage_mb` bigint NOT NULL,
  `max_branches` int NOT NULL,
  `max_warehouses` int NOT NULL,
  `entitlements` json NOT NULL,
  `grace_days` int NOT NULL,
  `tax_metadata` json NOT NULL,
  `invoice_description` varchar(500) NOT NULL,
  `created_by` varchar(100) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`plan_id`,`version`),
  KEY `fk_plan_version_actor` (`created_by`),
  CONSTRAINT `fk_plan_version_actor` FOREIGN KEY (`created_by`) REFERENCES `users` (`username`),
  CONSTRAINT `fk_plan_version_plan` FOREIGN KEY (`plan_id`) REFERENCES `subscription_plans` (`id`),
  CONSTRAINT `chk_plan_interval` CHECK ((`billing_interval` in ('MONTHLY','QUARTERLY','YEARLY'))),
  CONSTRAINT `chk_plan_limits` CHECK (((`price` >= 0) and (`max_users` > 0) and (`max_storage_mb` >= 0) and (`max_branches` > 0) and (`max_warehouses` >= 0) and (`grace_days` >= 0)))
);

CREATE TABLE `branch_settings_events` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `actor` varchar(100) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `before_state` text NOT NULL,
  `after_state` text NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  KEY `branch_settings_events_tenant_id` (`tenant_id`,`branch_id`),
  CONSTRAINT `branch_settings_events_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `branches` (`tenant_id`, `id`)
);

CREATE TABLE `carts` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `state` varchar(16) NOT NULL,
  `active_customer_id` bigint GENERATED ALWAYS AS ((case when (`state` = 'ACTIVE') then `customer_id` else NULL end)),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `carts_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `active_customer_id` (`active_customer_id`),
  KEY `customer_id` (`customer_id`),
  CONSTRAINT `carts_ibfk_1` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `carts_ibfk_2` FOREIGN KEY (`customer_id`) REFERENCES `customer_profiles` (`id`),
  CONSTRAINT `carts_chk_1` CHECK ((`state` in ('ACTIVE','ARCHIVED')))
);

CREATE TABLE `catalog_promotion_audit` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `previous_percent` decimal(5,2) NOT NULL,
  `new_percent` decimal(5,2) NOT NULL,
  `actor` varchar(100) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_promotion_history` (`tenant_id`,`product_id`,`created_at`,`id`),
  CONSTRAINT `catalog_promotion_audit_ibfk_1` FOREIGN KEY (`tenant_id`, `product_id`) REFERENCES `products` (`tenant_id`, `id`)
);

CREATE TABLE `catalog_promotions` (
  `tenant_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `percent` decimal(5,2) NOT NULL,
  `updated_by` varchar(100) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `updated_at` timestamp NOT NULL,
  PRIMARY KEY (`tenant_id`,`product_id`),
  CONSTRAINT `catalog_promotions_ibfk_1` FOREIGN KEY (`tenant_id`, `product_id`) REFERENCES `products` (`tenant_id`, `id`),
  CONSTRAINT `catalog_promotions_chk_1` CHECK (((`percent` >= 0) and (`percent` <= 100)))
);

CREATE TABLE `employees` (
  `username` varchar(100) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `enabled` BOOLEAN NOT NULL,
  PRIMARY KEY (`username`),
  UNIQUE KEY `employees_tenant_id` (`tenant_id`,`branch_id`,`username`),
  UNIQUE KEY `uk_employee_tenant_username` (`tenant_id`,`username`),
  CONSTRAINT `employees_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `branches` (`tenant_id`, `id`),
  CONSTRAINT `fk_employee_global_identity` FOREIGN KEY (`username`) REFERENCES `users` (`username`)
);

CREATE TABLE `pos_stock_locations` (
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `stock_location_id` bigint NOT NULL,
  `location_code` varchar(40) NOT NULL,
  `display_name` varchar(160) NOT NULL,
  `active` BOOLEAN NOT NULL DEFAULT 1,
  PRIMARY KEY (`tenant_id`,`branch_id`),
  UNIQUE KEY `stock_location_id` (`stock_location_id`),
  UNIQUE KEY `pos_stock_locations_tenant_id` (`tenant_id`,`branch_id`,`stock_location_id`),
  CONSTRAINT `fk_pos_stock_location_branch` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `branches` (`tenant_id`, `id`),
  CONSTRAINT `chk_pos_stock_location_branch_key` CHECK ((`stock_location_id` = `branch_id`)),
  CONSTRAINT `chk_pos_stock_location_code` CHECK ((`location_code` = 'POS_DEFAULT'))
);

CREATE TABLE `product_categories` (
  `tenant_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  PRIMARY KEY (`tenant_id`,`product_id`),
  KEY `product_categories_tenant_id` (`tenant_id`,`category_id`),
  CONSTRAINT `product_categories_ibfk_1` FOREIGN KEY (`tenant_id`, `product_id`) REFERENCES `products` (`tenant_id`, `id`),
  CONSTRAINT `product_categories_ibfk_2` FOREIGN KEY (`tenant_id`, `category_id`) REFERENCES `catalog_categories` (`tenant_id`, `id`)
);

CREATE TABLE `tenant_customers` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `consent_at` timestamp NOT NULL,
  `provenance` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_customers_tenant_id` (`tenant_id`,`customer_id`),
  UNIQUE KEY `tenant_customers_tenant_id_2` (`tenant_id`,`id`),
  KEY `tenant_customers_customer_id` (`customer_id`),
  CONSTRAINT `tenant_customers_ibfk_1` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `tenant_customers_ibfk_2` FOREIGN KEY (`customer_id`) REFERENCES `customer_profiles` (`id`)
);

CREATE TABLE `tenant_subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL,
  `plan_version` int NOT NULL,
  `status` varchar(30) NOT NULL,
  `billing_period_start` date NOT NULL,
  `billing_period_end` date NOT NULL,
  `grace_until` timestamp NULL DEFAULT NULL,
  `billing_anchor` date DEFAULT NULL,
  `terms_snapshot` json NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `version` bigint NOT NULL DEFAULT '0',
  `active_tenant_id` bigint GENERATED ALWAYS AS ((case when (`status` in ('PENDING','ACTIVE','PAST_DUE','GRACE_PERIOD','SUSPENDED','CANCEL_AT_PERIOD_END')) then `tenant_id` else NULL end)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_subscription_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `uk_tenant_effective_subscription` (`active_tenant_id`),
  KEY `fk_subscription_plan_version` (`plan_id`,`plan_version`),
  KEY `ix_subscription_tenant_history` (`tenant_id`,`created_at` DESC,`id` DESC),
  KEY `ix_subscription_grace_expiry` (`status`,`grace_until`,`id`),
  KEY `ix_subscription_mock_renewal` (`status`,`billing_period_end`,`id`),
  CONSTRAINT `fk_subscription_plan_version` FOREIGN KEY (`plan_id`, `plan_version`) REFERENCES `subscription_plan_versions` (`plan_id`, `version`),
  CONSTRAINT `fk_subscription_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `chk_subscription_status` CHECK ((`status` in ('PENDING','ACTIVE','PAST_DUE','GRACE_PERIOD','SUSPENDED','CANCEL_AT_PERIOD_END','CANCELLED','EXPIRED')))
);

CREATE TABLE `cart_items` (
  `tenant_id` bigint NOT NULL,
  `cart_id` varchar(36) NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  PRIMARY KEY (`tenant_id`,`cart_id`,`product_id`),
  KEY `cart_items_tenant_id` (`tenant_id`,`product_id`),
  CONSTRAINT `cart_items_ibfk_1` FOREIGN KEY (`tenant_id`, `cart_id`) REFERENCES `carts` (`tenant_id`, `id`),
  CONSTRAINT `cart_items_ibfk_2` FOREIGN KEY (`tenant_id`, `product_id`) REFERENCES `products` (`tenant_id`, `id`),
  CONSTRAINT `cart_items_chk_1` CHECK (((`quantity` > 0) and (`quantity` <= 10000)))
);

CREATE TABLE `catalog_stock` (
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `available` int NOT NULL,
  PRIMARY KEY (`tenant_id`,`branch_id`,`product_id`),
  KEY `catalog_stock_tenant_id` (`tenant_id`,`product_id`),
  KEY `ix_catalog_stock_available` (`tenant_id`,`branch_id`,`available`),
  CONSTRAINT `catalog_stock_ibfk_1` FOREIGN KEY (`tenant_id`, `product_id`) REFERENCES `products` (`tenant_id`, `id`),
  CONSTRAINT `catalog_stock_ibfk_2` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `branches` (`tenant_id`, `id`),
  CONSTRAINT `fk_catalog_stock_pos_stock_location` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `catalog_stock_chk_1` CHECK ((`available` >= 0))
);

CREATE TABLE `inventory` (
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `reserved` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`tenant_id`,`branch_id`,`product_id`),
  KEY `inventory_tenant_id` (`tenant_id`,`product_id`),
  CONSTRAINT `fk_inventory_pos_stock_location` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `inventory_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `branches` (`tenant_id`, `id`),
  CONSTRAINT `inventory_ibfk_2` FOREIGN KEY (`tenant_id`, `product_id`) REFERENCES `products` (`tenant_id`, `id`),
  CONSTRAINT `chk_reserved_stock` CHECK (((`reserved` >= 0) and (`reserved` <= `quantity`))),
  CONSTRAINT `inventory_chk_1` CHECK ((`quantity` >= 0))
);

CREATE TABLE `inventory_receipts` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `actor` varchar(100) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `command_key` varchar(80) NOT NULL,
  `available_after` int NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `inventory_receipts_tenant_id` (`tenant_id`,`branch_id`,`command_key`),
  KEY `inventory_receipts_tenant_id_2` (`tenant_id`,`product_id`),
  KEY `inventory_receipts_history` (`tenant_id`,`branch_id`,`created_at`,`id`),
  CONSTRAINT `fk_inventory_receipts_pos_stock_location` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `inventory_receipts_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `branches` (`tenant_id`, `id`),
  CONSTRAINT `inventory_receipts_ibfk_2` FOREIGN KEY (`tenant_id`, `product_id`) REFERENCES `products` (`tenant_id`, `id`),
  CONSTRAINT `inventory_receipts_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `inventory_receipts_chk_2` CHECK ((`available_after` >= 0))
);

CREATE TABLE `registers` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `registers_tenant_id` (`tenant_id`,`branch_id`,`id`),
  CONSTRAINT `fk_registers_pos_stock_location` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `registers_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `branches` (`tenant_id`, `id`)
);

CREATE TABLE `report_export_audit` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `actor` varchar(100) NOT NULL,
  `report_kind` varchar(24) NOT NULL,
  `from_date` date NOT NULL,
  `to_date` date NOT NULL,
  `channel` varchar(16) NOT NULL,
  `row_count` int NOT NULL,
  `content_sha256` varchar(64) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  KEY `report_export_audit_tenant_id` (`tenant_id`,`branch_id`,`actor`),
  KEY `ix_report_export_scope` (`tenant_id`,`branch_id`,`created_at`,`id`),
  CONSTRAINT `report_export_audit_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`, `actor`) REFERENCES `employees` (`tenant_id`, `branch_id`, `username`),
  CONSTRAINT `report_export_audit_chk_1` CHECK ((`report_kind` in ('SALES','CASH_REFUNDS'))),
  CONSTRAINT `report_export_audit_chk_2` CHECK ((`channel` in ('ALL','ONLINE','IN_STORE','CASH_ONLY'))),
  CONSTRAINT `report_export_audit_chk_3` CHECK (((`row_count` > 0) and (`row_count` <= 5000))),
  CONSTRAINT `report_export_audit_chk_4` CHECK ((`from_date` <= `to_date`))
);

CREATE TABLE `subscription_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `subscription_id` bigint NOT NULL,
  `old_status` varchar(30) DEFAULT NULL,
  `new_status` varchar(30) NOT NULL,
  `actor_username` varchar(100) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_sub_event_subscription` (`subscription_id`),
  KEY `fk_sub_event_actor` (`actor_username`),
  KEY `fk_sub_event_subscription_owner` (`tenant_id`,`subscription_id`),
  CONSTRAINT `fk_sub_event_actor` FOREIGN KEY (`actor_username`) REFERENCES `users` (`username`),
  CONSTRAINT `fk_sub_event_subscription` FOREIGN KEY (`subscription_id`) REFERENCES `tenant_subscriptions` (`id`),
  CONSTRAINT `fk_sub_event_subscription_owner` FOREIGN KEY (`tenant_id`, `subscription_id`) REFERENCES `tenant_subscriptions` (`tenant_id`, `id`),
  CONSTRAINT `fk_sub_event_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`)
);

CREATE TABLE `subscription_invoices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `subscription_id` bigint NOT NULL,
  `invoice_number` varchar(100) NOT NULL,
  `billing_period_start` date NOT NULL,
  `billing_period_end` date NOT NULL,
  `currency` varchar(3) NOT NULL,
  `plan_snapshot` json NOT NULL,
  `subtotal` decimal(19,4) NOT NULL,
  `tax_amount` decimal(19,4) NOT NULL,
  `total` decimal(19,4) NOT NULL,
  `status` varchar(20) NOT NULL,
  `due_at` timestamp NOT NULL,
  `issued_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `mock_next_attempt_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `invoice_number` (`invoice_number`),
  UNIQUE KEY `uk_sub_invoice_tenant_id` (`tenant_id`,`id`),
  KEY `fk_sub_invoice_subscription` (`subscription_id`),
  KEY `ix_sub_invoice_tenant_date` (`tenant_id`,`issued_at` DESC,`id` DESC),
  KEY `ix_sub_invoice_mock_retry` (`mock_next_attempt_at`,`subscription_id`),
  KEY `fk_sub_invoice_subscription_owner` (`tenant_id`,`subscription_id`),
  CONSTRAINT `fk_sub_invoice_subscription` FOREIGN KEY (`subscription_id`) REFERENCES `tenant_subscriptions` (`id`),
  CONSTRAINT `fk_sub_invoice_subscription_owner` FOREIGN KEY (`tenant_id`, `subscription_id`) REFERENCES `tenant_subscriptions` (`tenant_id`, `id`),
  CONSTRAINT `fk_sub_invoice_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `chk_sub_invoice_status` CHECK ((`status` in ('OPEN','PAID','VOID','UNCOLLECTIBLE')))
);

CREATE TABLE `vendor_access_audit_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `actor_username` varchar(100) NOT NULL,
  `subject_username` varchar(100) DEFAULT NULL,
  `resource_type` varchar(40) NOT NULL,
  `resource_id` varchar(120) DEFAULT NULL,
  `action` varchar(60) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `correlation_id` varchar(100) DEFAULT NULL,
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_vendor_access_audit_actor` (`tenant_id`,`actor_username`),
  KEY `fk_vendor_access_audit_subject` (`tenant_id`,`subject_username`),
  KEY `ix_vendor_access_audit_tenant_time` (`tenant_id`,`occurred_at` DESC,`id` DESC),
  CONSTRAINT `fk_vendor_access_audit_actor` FOREIGN KEY (`tenant_id`, `actor_username`) REFERENCES `employees` (`tenant_id`, `username`),
  CONSTRAINT `fk_vendor_access_audit_subject` FOREIGN KEY (`tenant_id`, `subject_username`) REFERENCES `employees` (`tenant_id`, `username`),
  CONSTRAINT `fk_vendor_access_audit_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`)
);

CREATE TABLE `vendor_roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `role_key` varchar(120) NOT NULL,
  `display_name` varchar(120) NOT NULL,
  `active` BOOLEAN NOT NULL DEFAULT 1,
  `created_by` varchar(100) NOT NULL,
  `updated_by` varchar(100) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `version` bigint NOT NULL DEFAULT '0',
  `template_key` varchar(80) DEFAULT NULL,
  `template_version` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vendor_role_tenant_key` (`tenant_id`,`role_key`),
  UNIQUE KEY `uk_vendor_role_tenant_id` (`tenant_id`,`id`),
  KEY `fk_vendor_role_created_by` (`tenant_id`,`created_by`),
  KEY `fk_vendor_role_updated_by` (`tenant_id`,`updated_by`),
  CONSTRAINT `fk_vendor_role_created_by` FOREIGN KEY (`tenant_id`, `created_by`) REFERENCES `employees` (`tenant_id`, `username`),
  CONSTRAINT `fk_vendor_role_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `fk_vendor_role_updated_by` FOREIGN KEY (`tenant_id`, `updated_by`) REFERENCES `employees` (`tenant_id`, `username`),
  CONSTRAINT `chk_role_template_origin` CHECK ((((`template_key` is null) and (`template_version` is null)) or ((`template_key` is not null) and (`template_version` is not null) and (`template_version` > 0))))
);

CREATE TABLE `employee_register_assignments` (
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `username` varchar(100) NOT NULL,
  `register_id` bigint NOT NULL,
  `active` BOOLEAN NOT NULL DEFAULT 1,
  `assigned_by` varchar(100) NOT NULL,
  `assigned_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_by` varchar(100) DEFAULT NULL,
  `revoked_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`tenant_id`,`username`,`register_id`),
  KEY `fk_employee_register_register` (`tenant_id`,`branch_id`,`register_id`),
  KEY `fk_employee_register_assigned_by` (`tenant_id`,`assigned_by`),
  KEY `fk_employee_register_revoked_by` (`tenant_id`,`revoked_by`),
  KEY `ix_employee_register_active` (`tenant_id`,`branch_id`,`username`,`active`),
  CONSTRAINT `fk_employee_register_assigned_by` FOREIGN KEY (`tenant_id`, `assigned_by`) REFERENCES `employees` (`tenant_id`, `username`),
  CONSTRAINT `fk_employee_register_employee` FOREIGN KEY (`tenant_id`, `branch_id`, `username`) REFERENCES `employees` (`tenant_id`, `branch_id`, `username`),
  CONSTRAINT `fk_employee_register_register` FOREIGN KEY (`tenant_id`, `branch_id`, `register_id`) REFERENCES `registers` (`tenant_id`, `branch_id`, `id`),
  CONSTRAINT `fk_employee_register_revoked_by` FOREIGN KEY (`tenant_id`, `revoked_by`) REFERENCES `employees` (`tenant_id`, `username`)
);

CREATE TABLE `employee_role_assignments` (
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `username` varchar(100) NOT NULL,
  `role_id` bigint NOT NULL,
  `active` BOOLEAN NOT NULL DEFAULT 1,
  `assigned_by` varchar(100) NOT NULL,
  `assigned_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_by` varchar(100) DEFAULT NULL,
  `revoked_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`tenant_id`,`username`,`role_id`),
  KEY `fk_employee_role_employee` (`tenant_id`,`branch_id`,`username`),
  KEY `fk_employee_role_role` (`tenant_id`,`role_id`),
  KEY `fk_employee_role_assigned_by` (`tenant_id`,`assigned_by`),
  KEY `fk_employee_role_revoked_by` (`tenant_id`,`revoked_by`),
  KEY `ix_employee_role_active` (`tenant_id`,`username`,`active`),
  CONSTRAINT `fk_employee_role_assigned_by` FOREIGN KEY (`tenant_id`, `assigned_by`) REFERENCES `employees` (`tenant_id`, `username`),
  CONSTRAINT `fk_employee_role_employee` FOREIGN KEY (`tenant_id`, `branch_id`, `username`) REFERENCES `employees` (`tenant_id`, `branch_id`, `username`),
  CONSTRAINT `fk_employee_role_revoked_by` FOREIGN KEY (`tenant_id`, `revoked_by`) REFERENCES `employees` (`tenant_id`, `username`),
  CONSTRAINT `fk_employee_role_role` FOREIGN KEY (`tenant_id`, `role_id`) REFERENCES `vendor_roles` (`tenant_id`, `id`)
);

CREATE TABLE `pos_sessions` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `cashier` varchar(100) NOT NULL,
  `register_id` bigint NOT NULL,
  `state` varchar(16) NOT NULL,
  `opening_float` decimal(19,2) NOT NULL,
  `active_register` bigint GENERATED ALWAYS AS ((case when (`state` = 'OPEN') then `register_id` else NULL end)),
  `expected_cash` decimal(19,2) DEFAULT NULL,
  `counted_cash` decimal(19,2) DEFAULT NULL,
  `closed_at` timestamp NULL DEFAULT NULL,
  `cash_variance` decimal(19,2) DEFAULT NULL,
  `variance_reason` varchar(250) DEFAULT NULL,
  `close_request_id` varchar(36) DEFAULT NULL,
  `close_requested_at` timestamp NULL DEFAULT NULL,
  `reviewed_by` varchar(100) DEFAULT NULL,
  `review_reason` varchar(250) DEFAULT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  `active_register_guard` bigint GENERATED ALWAYS AS ((case when (`state` in ('OPEN','CLOSING')) then `register_id` else NULL end)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `pos_sessions_tenant_id_2` (`tenant_id`,`branch_id`,`cashier`,`id`),
  UNIQUE KEY `uk_pos_session_tenant_branch_id` (`tenant_id`,`branch_id`,`id`),
  UNIQUE KEY `pos_sessions_tenant_id` (`tenant_id`,`active_register`),
  UNIQUE KEY `uk_session_open_closing_register` (`tenant_id`,`active_register_guard`),
  KEY `fk_session_reviewer` (`tenant_id`,`branch_id`,`reviewed_by`),
  KEY `ix_own_closed_shift` (`tenant_id`,`branch_id`,`cashier`,`state`,`closed_at`,`id`),
  KEY `ix_register_availability` (`tenant_id`,`branch_id`,`register_id`,`state`),
  CONSTRAINT `fk_pos_sessions_stock_location` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `fk_session_reviewer` FOREIGN KEY (`tenant_id`, `branch_id`, `reviewed_by`) REFERENCES `employees` (`tenant_id`, `branch_id`, `username`),
  CONSTRAINT `pos_sessions_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`, `cashier`) REFERENCES `employees` (`tenant_id`, `branch_id`, `username`),
  CONSTRAINT `pos_sessions_ibfk_2` FOREIGN KEY (`tenant_id`, `branch_id`, `register_id`) REFERENCES `registers` (`tenant_id`, `branch_id`, `id`),
  CONSTRAINT `chk_session_independent_review` CHECK (((`reviewed_by` is null) or (`reviewed_by` <> `cashier`))),
  CONSTRAINT `pos_sessions_chk_1` CHECK ((`opening_float` >= 0))
);

CREATE TABLE `subscription_payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `subscription_invoice_id` bigint NOT NULL,
  `provider` varchar(60) NOT NULL,
  `platform_merchant_reference` varchar(160) NOT NULL,
  `provider_payment_reference` varchar(160) DEFAULT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `status` varchar(24) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `confirmed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `provider` (`provider`,`idempotency_key`),
  KEY `fk_sub_payment_invoice` (`subscription_invoice_id`),
  KEY `ix_sub_payment_tenant_date` (`tenant_id`,`created_at` DESC,`id` DESC),
  KEY `fk_sub_payment_invoice_owner` (`tenant_id`,`subscription_invoice_id`),
  CONSTRAINT `fk_sub_payment_invoice` FOREIGN KEY (`subscription_invoice_id`) REFERENCES `subscription_invoices` (`id`),
  CONSTRAINT `fk_sub_payment_invoice_owner` FOREIGN KEY (`tenant_id`, `subscription_invoice_id`) REFERENCES `subscription_invoices` (`tenant_id`, `id`),
  CONSTRAINT `fk_sub_payment_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `chk_sub_payment_status` CHECK ((`status` in ('CREATED','PENDING','SUCCEEDED','FAILED','CANCELLED','REFUNDED')))
);

CREATE TABLE `vendor_role_permissions` (
  `tenant_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `permission` varchar(80) NOT NULL,
  `created_by` varchar(100) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tenant_id`,`role_id`,`permission`),
  KEY `fk_vendor_role_permission_actor` (`tenant_id`,`created_by`),
  KEY `fk_vrp_permission_catalog` (`permission`),
  CONSTRAINT `fk_vendor_role_permission_actor` FOREIGN KEY (`tenant_id`, `created_by`) REFERENCES `employees` (`tenant_id`, `username`),
  CONSTRAINT `fk_vendor_role_permission_role` FOREIGN KEY (`tenant_id`, `role_id`) REFERENCES `vendor_roles` (`tenant_id`, `id`),
  CONSTRAINT `fk_vrp_permission_catalog` FOREIGN KEY (`permission`) REFERENCES `vendor_permission_catalogue` (`permission`),
  CONSTRAINT `chk_vendor_role_permission_no_platform` CHECK ((not((`permission` like 'PLATFORM_%'))))
);

CREATE TABLE `orders` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint DEFAULT NULL,
  `cashier` varchar(100) DEFAULT NULL,
  `session_id` varchar(36) DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `sales_channel` varchar(16) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `total` decimal(19,2) NOT NULL,
  `cash_received` decimal(19,2) DEFAULT NULL,
  `change_given` decimal(19,2) DEFAULT NULL,
  `receipt_number` bigint DEFAULT NULL,
  `tax_invoice` BOOLEAN NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status` varchar(24) NOT NULL DEFAULT 'PAID',
  `expires_at` timestamp NULL DEFAULT NULL,
  `stock_branch_id` bigint DEFAULT NULL,
  `draft_owner` varchar(80) DEFAULT NULL,
  `invoice_requested` BOOLEAN NOT NULL DEFAULT 0,
  `cancellation_reason` varchar(250) DEFAULT NULL,
  `status_changed_at` timestamp(6) NULL DEFAULT NULL,
  `receipt_seller_name` varchar(160) DEFAULT NULL,
  `receipt_outlet_name` varchar(160) DEFAULT NULL,
  `payment_method` varchar(16) NOT NULL DEFAULT 'CASH',
  `payment_reference` varchar(80) DEFAULT NULL,
  `discount_reason` varchar(250) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `orders_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `orders_tenant_id_2` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `tenant_id_3` (`tenant_id`,`branch_id`,`receipt_number`),
  UNIQUE KEY `uk_active_draft_owner` (`tenant_id`,`draft_owner`),
  UNIQUE KEY `uk_customer_order_key` (`customer_id`,`idempotency_key`),
  UNIQUE KEY `uk_order_return_branch` (`tenant_id`,`branch_id`,`id`),
  UNIQUE KEY `uk_orders_tenant_branch_session_id` (`tenant_id`,`branch_id`,`session_id`,`id`),
  UNIQUE KEY `uk_orders_tenant_branch_cashier_session_id` (`tenant_id`,`branch_id`,`cashier`,`session_id`,`id`),
  UNIQUE KEY `uk_orders_stock_branch_id` (`tenant_id`,`stock_branch_id`,`id`),
  KEY `ix_draft_expiry` (`status`,`expires_at`),
  KEY `ix_customer_order_history` (`customer_id`,`sales_channel`,`created_at`,`id`),
  KEY `ix_branch_order_history` (`tenant_id`,`branch_id`,`sales_channel`,`created_at`,`id`),
  CONSTRAINT `fk_online_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer_profiles` (`id`),
  CONSTRAINT `fk_order_stock_branch` FOREIGN KEY (`tenant_id`, `stock_branch_id`) REFERENCES `branches` (`tenant_id`, `id`),
  CONSTRAINT `fk_orders_inventory_stock_location` FOREIGN KEY (`tenant_id`, `stock_branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `fk_orders_pos_branch_stock_location` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `orders_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`, `cashier`, `session_id`) REFERENCES `pos_sessions` (`tenant_id`, `branch_id`, `cashier`, `id`),
  CONSTRAINT `chk_order_channel_context` CHECK ((((`sales_channel` = 'ONLINE') and (`customer_id` is not null) and (`branch_id` is null) and (`cashier` is null) and (`session_id` is null) and (`cash_received` is null) and (`change_given` is null) and (`receipt_number` is null)) or ((`sales_channel` = 'IN_STORE') and (`branch_id` is not null) and (`cashier` is not null) and (`session_id` is not null) and ((`status` <> 'PAID') or ((`cash_received` is not null) and (`change_given` is not null) and (`receipt_number` is not null)))))),
  CONSTRAINT `chk_order_draft_state` CHECK ((`status` in ('DRAFT','PAID','CANCELLED','EXPIRED'))),
  CONSTRAINT `chk_pos_order_uses_its_branch_stock` CHECK (((`sales_channel` <> 'IN_STORE') or ((`stock_branch_id` is not null) and (`branch_id` = `stock_branch_id`)))),
  CONSTRAINT `chk_sale_cash_amount` CHECK (((`status` <> 'PAID') or (`sales_channel` = 'ONLINE') or ((`payment_method` = 'CASH') and ((`cash_received` - `change_given`) = `total`)) or ((`payment_method` in ('UPI','CARD')) and (`cash_received` = 0) and (`change_given` = 0)) or ((`payment_method` = 'SPLIT') and (`cash_received` >= `change_given`) and ((`cash_received` - `change_given`) <= `total`)))),
  CONSTRAINT `chk_sale_payment_method` CHECK ((`payment_method` in ('CASH','MOCK_UPI','MOCK_CARD','UPI','CARD','SPLIT'))),
  CONSTRAINT `chk_terminal_receipt_reference` CHECK (((`payment_method` not in ('UPI','CARD')) or ((`sales_channel` = 'IN_STORE') and (`payment_reference` is not null))))
);

CREATE TABLE `pos_cash_movements` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `cashier` varchar(100) NOT NULL,
  `session_id` varchar(36) NOT NULL,
  `movement_type` varchar(16) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `pos_cash_movements_tenant_id` (`tenant_id`,`session_id`,`idempotency_key`),
  KEY `fk_cash_movement_session` (`tenant_id`,`branch_id`,`cashier`,`session_id`),
  CONSTRAINT `fk_cash_movement_session` FOREIGN KEY (`tenant_id`, `branch_id`, `cashier`, `session_id`) REFERENCES `pos_sessions` (`tenant_id`, `branch_id`, `cashier`, `id`),
  CONSTRAINT `chk_cash_movement_amount` CHECK ((`amount` > 0)),
  CONSTRAINT `chk_cash_movement_reason` CHECK ((char_length(trim(`reason`)) > 0)),
  CONSTRAINT `chk_cash_movement_type` CHECK ((`movement_type` in ('CASH_IN','CASH_OUT','SAFE_DROP')))
);

CREATE TABLE `pos_shift_reviews` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `cashier` varchar(100) NOT NULL,
  `session_id` varchar(36) NOT NULL,
  `close_request_id` varchar(36) NOT NULL,
  `reviewer` varchar(100) NOT NULL,
  `decision` varchar(24) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `variance_reason` varchar(250) NOT NULL,
  `expected_cash` decimal(19,2) NOT NULL,
  `counted_cash` decimal(19,2) NOT NULL,
  `cash_variance` decimal(19,2) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `pos_shift_reviews_tenant_id` (`tenant_id`,`session_id`,`close_request_id`),
  KEY `pos_shift_reviews_tenant_id_2` (`tenant_id`,`branch_id`,`cashier`,`session_id`),
  KEY `pos_shift_reviews_tenant_id_3` (`tenant_id`,`branch_id`,`reviewer`),
  CONSTRAINT `pos_shift_reviews_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`, `cashier`, `session_id`) REFERENCES `pos_sessions` (`tenant_id`, `branch_id`, `cashier`, `id`),
  CONSTRAINT `pos_shift_reviews_ibfk_2` FOREIGN KEY (`tenant_id`, `branch_id`, `reviewer`) REFERENCES `employees` (`tenant_id`, `branch_id`, `username`),
  CONSTRAINT `pos_shift_reviews_chk_1` CHECK ((`decision` in ('APPROVE','RETURN_FOR_CORRECTION'))),
  CONSTRAINT `pos_shift_reviews_chk_2` CHECK ((char_length(trim(`reason`)) > 0)),
  CONSTRAINT `pos_shift_reviews_chk_3` CHECK ((`reviewer` <> `cashier`))
);

CREATE TABLE `inventory_ledger` (
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `quantity_delta` int NOT NULL,
  PRIMARY KEY (`tenant_id`,`order_id`,`product_id`),
  KEY `ix_inventory_ledger_location_owner` (`tenant_id`,`branch_id`,`product_id`),
  KEY `ix_inventory_ledger_location_order` (`tenant_id`,`branch_id`,`order_id`),
  CONSTRAINT `fk_inventory_ledger_order_stock_branch` FOREIGN KEY (`tenant_id`, `branch_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `stock_branch_id`, `id`),
  CONSTRAINT `fk_inventory_ledger_pos_stock_location` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `inventory_ledger_ibfk_1` FOREIGN KEY (`tenant_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `id`),
  CONSTRAINT `inventory_ledger_ibfk_2` FOREIGN KEY (`tenant_id`, `branch_id`, `product_id`) REFERENCES `inventory` (`tenant_id`, `branch_id`, `product_id`)
);

CREATE TABLE `order_buyer_snapshots` (
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `name` varchar(160) DEFAULT NULL,
  `contact` varchar(160) DEFAULT NULL,
  `tax_id` varchar(80) DEFAULT NULL,
  `address` varchar(1000) DEFAULT NULL,
  `captured_at` timestamp NOT NULL,
  PRIMARY KEY (`tenant_id`,`order_id`),
  CONSTRAINT `order_buyer_snapshots_ibfk_1` FOREIGN KEY (`tenant_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `id`)
);

CREATE TABLE `order_items` (
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `product_id` bigint NOT NULL,
  `name` varchar(160) NOT NULL,
  `quantity` int NOT NULL,
  `price` decimal(19,2) NOT NULL,
  `tax_rate` decimal(5,2) NOT NULL,
  `tax` decimal(19,2) NOT NULL,
  `total` decimal(19,2) NOT NULL,
  `list_price` decimal(19,2) NOT NULL DEFAULT '0.00',
  `discount` decimal(19,2) NOT NULL DEFAULT '0.00',
  `discount_percent` decimal(5,2) NOT NULL DEFAULT '0.00',
  `manual_discount` decimal(19,2) NOT NULL DEFAULT '0.00',
  PRIMARY KEY (`tenant_id`,`order_id`,`product_id`),
  KEY `order_items_tenant_id` (`tenant_id`,`product_id`),
  CONSTRAINT `order_items_ibfk_1` FOREIGN KEY (`tenant_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `id`),
  CONSTRAINT `order_items_ibfk_2` FOREIGN KEY (`tenant_id`, `product_id`) REFERENCES `products` (`tenant_id`, `id`),
  CONSTRAINT `chk_order_discount` CHECK (((`list_price` >= `price`) and (`discount` >= 0) and (`discount` = (((`list_price` - `price`) * `quantity`) + `manual_discount`)) and (`manual_discount` <= (`price` * `quantity`)) and (`discount_percent` >= 0) and (`discount_percent` <= 100))),
  CONSTRAINT `order_items_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `order_items_chk_2` CHECK ((`manual_discount` >= 0))
);

CREATE TABLE `pos_active_drafts` (
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `cashier` varchar(100) NOT NULL,
  `session_id` varchar(36) NOT NULL,
  `order_reference` varchar(36) NOT NULL,
  PRIMARY KEY (`tenant_id`,`order_reference`),
  KEY `pos_active_drafts_session` (`tenant_id`,`branch_id`,`cashier`,`session_id`),
  KEY `fk_pos_active_draft_order` (`tenant_id`,`branch_id`,`cashier`,`session_id`,`order_reference`),
  CONSTRAINT `fk_pos_active_draft_order` FOREIGN KEY (`tenant_id`, `branch_id`, `cashier`, `session_id`, `order_reference`) REFERENCES `orders` (`tenant_id`, `branch_id`, `cashier`, `session_id`, `id`),
  CONSTRAINT `pos_active_drafts_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`, `cashier`, `session_id`) REFERENCES `pos_sessions` (`tenant_id`, `branch_id`, `cashier`, `id`)
);

CREATE TABLE `pos_customer_challenges` (
  `token_hash` varchar(64) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `approved_customer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`token_hash`),
  KEY `approved_customer_id` (`approved_customer_id`),
  KEY `ix_customer_challenge_order` (`tenant_id`,`order_id`),
  CONSTRAINT `pos_customer_challenges_ibfk_1` FOREIGN KEY (`tenant_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `id`),
  CONSTRAINT `pos_customer_challenges_ibfk_2` FOREIGN KEY (`approved_customer_id`) REFERENCES `customer_profiles` (`id`)
);

CREATE TABLE `pos_drawer_tenders` (
  `id` varchar(80) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `session_id` varchar(36) NOT NULL,
  `order_reference` varchar(36) NOT NULL,
  `cash_received` decimal(19,2) NOT NULL,
  `change_given` decimal(19,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `pos_drawer_tenders_session` (`tenant_id`,`branch_id`,`session_id`),
  KEY `fk_pos_drawer_tender_order` (`tenant_id`,`branch_id`,`session_id`,`order_reference`),
  CONSTRAINT `fk_pos_drawer_tender_branch` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `branches` (`tenant_id`, `id`),
  CONSTRAINT `fk_pos_drawer_tender_order` FOREIGN KEY (`tenant_id`, `branch_id`, `session_id`, `order_reference`) REFERENCES `orders` (`tenant_id`, `branch_id`, `session_id`, `id`),
  CONSTRAINT `fk_pos_drawer_tender_session` FOREIGN KEY (`tenant_id`, `branch_id`, `session_id`) REFERENCES `pos_sessions` (`tenant_id`, `branch_id`, `id`),
  CONSTRAINT `pos_drawer_tenders_ibfk_1` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `pos_drawer_tenders_chk_1` CHECK (((`cash_received` >= 0) and (`change_given` >= 0) and (`change_given` <= `cash_received`)))
);

CREATE TABLE `pos_returns` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `requester` varchar(100) NOT NULL,
  `session_id` varchar(36) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `proof` varchar(32) NOT NULL,
  `proof_note` varchar(250) NOT NULL,
  `state` varchar(16) NOT NULL,
  `created_at` timestamp NOT NULL,
  `reviewed_by` varchar(100) DEFAULT NULL,
  `review_reason` varchar(250) DEFAULT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  `inspected_by` varchar(100) DEFAULT NULL,
  `inspection_hash` varchar(64) DEFAULT NULL,
  `inspected_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `pos_returns_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `pos_returns_tenant_id_2` (`tenant_id`,`branch_id`,`id`),
  UNIQUE KEY `pos_returns_tenant_id_3` (`tenant_id`,`id`,`order_id`),
  UNIQUE KEY `tenant_id_4` (`tenant_id`,`idempotency_key`),
  KEY `tenant_id_5` (`tenant_id`,`branch_id`,`order_id`),
  KEY `tenant_id_6` (`tenant_id`,`branch_id`,`requester`,`session_id`),
  KEY `ix_branch_return_queue` (`tenant_id`,`branch_id`,`state`,`created_at`,`id`),
  KEY `ix_return_queue` (`tenant_id`,`branch_id`,`created_at`,`id`),
  CONSTRAINT `pos_returns_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `branch_id`, `id`),
  CONSTRAINT `pos_returns_ibfk_2` FOREIGN KEY (`tenant_id`, `branch_id`, `requester`, `session_id`) REFERENCES `pos_sessions` (`tenant_id`, `branch_id`, `cashier`, `id`),
  CONSTRAINT `pos_returns_chk_1` CHECK ((`proof` in ('ORIGINAL_RECEIPT','ORDER_AND_ITEM_VERIFIED'))),
  CONSTRAINT `pos_returns_chk_2` CHECK ((`state` in ('REQUESTED','APPROVED','REJECTED','INSPECTED'))),
  CONSTRAINT `pos_returns_chk_3` CHECK (((`reviewed_by` is null) or (`reviewed_by` <> `requester`))),
  CONSTRAINT `pos_returns_chk_4` CHECK (((`inspected_by` is null) or (`inspected_by` <> `requester`))),
  CONSTRAINT `pos_returns_chk_5` CHECK ((((`state` = 'REQUESTED') and (`reviewed_by` is null) and (`inspected_by` is null)) or ((`state` in ('APPROVED','REJECTED')) and (`reviewed_by` is not null) and (`review_reason` is not null) and (`reviewed_at` is not null) and (`inspected_by` is null)) or ((`state` = 'INSPECTED') and (`reviewed_by` is not null) and (`inspected_by` is not null) and (`inspection_hash` is not null) and (`inspected_at` is not null))))
);

CREATE TABLE `receipt_document_audit` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `actor` varchar(100) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `content_sha256` varchar(64) NOT NULL,
  `content_length` int NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `receipt_document_audit_tenant_id_2` (`tenant_id`,`branch_id`,`actor`),
  KEY `ix_receipt_document_scope` (`tenant_id`,`branch_id`,`order_id`,`created_at`,`id`),
  CONSTRAINT `receipt_document_audit_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `branch_id`, `id`),
  CONSTRAINT `receipt_document_audit_ibfk_2` FOREIGN KEY (`tenant_id`, `branch_id`, `actor`) REFERENCES `employees` (`tenant_id`, `branch_id`, `username`),
  CONSTRAINT `receipt_document_audit_chk_1` CHECK (((`content_length` > 0) and (`content_length` <= 2000000)))
);

CREATE TABLE `stock_reservations` (
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `product_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `state` varchar(16) NOT NULL,
  `expires_at` timestamp NOT NULL,
  PRIMARY KEY (`tenant_id`,`order_id`,`product_id`),
  KEY `ix_stock_reservations_location_owner` (`tenant_id`,`branch_id`,`product_id`),
  KEY `ix_stock_reservations_location_order` (`tenant_id`,`branch_id`,`order_id`),
  CONSTRAINT `fk_stock_reservations_order_stock_branch` FOREIGN KEY (`tenant_id`, `branch_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `stock_branch_id`, `id`),
  CONSTRAINT `fk_stock_reservations_pos_stock_location` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `stock_reservations_ibfk_1` FOREIGN KEY (`tenant_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `id`),
  CONSTRAINT `stock_reservations_ibfk_2` FOREIGN KEY (`tenant_id`, `branch_id`, `product_id`) REFERENCES `inventory` (`tenant_id`, `branch_id`, `product_id`),
  CONSTRAINT `stock_reservations_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `stock_reservations_chk_2` CHECK ((`state` in ('HELD','RELEASED','CONSUMED')))
);

CREATE TABLE `vendor_invoices` (
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `invoice_number` varchar(100) NOT NULL,
  `seller_name` varchar(160) NOT NULL,
  `outlet_name` varchar(160) NOT NULL,
  `total` decimal(19,2) NOT NULL,
  `issued_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tenant_id`,`order_id`),
  UNIQUE KEY `vendor_invoices_tenant_id` (`tenant_id`,`invoice_number`),
  CONSTRAINT `vendor_invoices_ibfk_1` FOREIGN KEY (`tenant_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `id`)
);

CREATE TABLE `vendor_payment_attempts` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `method` varchar(8) NOT NULL,
  `merchant_account` varchar(80) NOT NULL,
  `state` varchar(48) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `created_at` timestamp NOT NULL,
  `confirmed_at` timestamp NULL DEFAULT NULL,
  `provider_reference` varchar(80) DEFAULT NULL,
  `origin` varchar(24) NOT NULL DEFAULT 'ONLINE_MOCK',
  `evidence_reference` varchar(100) DEFAULT NULL,
  `reviewed_by` varchar(100) DEFAULT NULL,
  `review_reason` varchar(250) DEFAULT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `cash_received` decimal(19,2) DEFAULT NULL,
  `change_given` decimal(19,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tender_key` (`tenant_id`,`order_id`,`idempotency_key`),
  KEY `ix_terminal_review` (`tenant_id`,`origin`,`state`,`created_at`),
  KEY `ix_tenders_order` (`tenant_id`,`order_id`),
  CONSTRAINT `vendor_payment_attempts_ibfk_1` FOREIGN KEY (`tenant_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `id`),
  CONSTRAINT `chk_cash_tender` CHECK (((`origin` <> 'POS_CASH') or ((`method` = 'CASH') and (`state` = 'CONFIRMED') and (`cash_received` >= `amount`) and (`change_given` >= 0) and ((`cash_received` - `change_given`) = `amount`)))),
  CONSTRAINT `chk_tender_method` CHECK ((`method` in ('UPI','CARD','CASH'))),
  CONSTRAINT `chk_tender_origin` CHECK ((`origin` in ('ONLINE_MOCK','POS_TERMINAL','POS_CASH'))),
  CONSTRAINT `chk_tender_state` CHECK ((`state` in ('PENDING','PENDING_VERIFICATION','CONFIRMED','FAILED','UNKNOWN_RECONCILIATION_REQUIRED'))),
  CONSTRAINT `chk_terminal_evidence` CHECK (((`origin` <> 'POS_TERMINAL') or (`state` not in ('CONFIRMED','FAILED')) or ((`reviewed_by` is not null) and (`reviewed_at` is not null) and (`provider_reference` is not null) and (`evidence_reference` is not null) and (`review_reason` is not null)))),
  CONSTRAINT `vendor_payment_attempts_chk_3` CHECK ((`amount` >= 0))
);

CREATE TABLE `pos_cash_refunds` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `return_id` varchar(36) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `original_receipt` bigint NOT NULL,
  `cashier` varchar(100) NOT NULL,
  `return_requester` varchar(100) NOT NULL,
  `session_id` varchar(36) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `quote_hash` varchar(64) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `paid_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `pos_cash_refunds_tenant_id` (`tenant_id`,`id`),
  UNIQUE KEY `pos_cash_refunds_tenant_id_2` (`tenant_id`,`id`,`order_id`),
  UNIQUE KEY `pos_cash_refunds_tenant_id_3` (`tenant_id`,`return_id`),
  UNIQUE KEY `pos_cash_refunds_tenant_id_4` (`tenant_id`,`idempotency_key`),
  KEY `pos_cash_refunds_tenant_id_5` (`tenant_id`,`branch_id`,`return_id`),
  KEY `pos_cash_refunds_tenant_id_6` (`tenant_id`,`branch_id`,`order_id`),
  KEY `tenant_id_7` (`tenant_id`,`branch_id`,`cashier`,`session_id`),
  KEY `ix_refund_drawer` (`tenant_id`,`session_id`),
  KEY `ix_refund_branch_paid` (`tenant_id`,`branch_id`,`paid_at`,`id`),
  CONSTRAINT `pos_cash_refunds_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`, `return_id`) REFERENCES `pos_returns` (`tenant_id`, `branch_id`, `id`),
  CONSTRAINT `pos_cash_refunds_ibfk_2` FOREIGN KEY (`tenant_id`, `branch_id`, `order_id`) REFERENCES `orders` (`tenant_id`, `branch_id`, `id`),
  CONSTRAINT `pos_cash_refunds_ibfk_3` FOREIGN KEY (`tenant_id`, `branch_id`, `cashier`, `session_id`) REFERENCES `pos_sessions` (`tenant_id`, `branch_id`, `cashier`, `id`),
  CONSTRAINT `pos_cash_refunds_chk_1` CHECK ((`amount` > 0)),
  CONSTRAINT `pos_cash_refunds_chk_2` CHECK ((`cashier` <> `return_requester`))
);

CREATE TABLE `pos_return_events` (
  `id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `return_id` varchar(36) NOT NULL,
  `actor` varchar(100) NOT NULL,
  `action` varchar(16) NOT NULL,
  `note` varchar(250) NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_return_event_timeline` (`tenant_id`,`return_id`,`created_at`,`id`),
  CONSTRAINT `pos_return_events_ibfk_1` FOREIGN KEY (`tenant_id`, `return_id`) REFERENCES `pos_returns` (`tenant_id`, `id`)
);

CREATE TABLE `pos_return_items` (
  `tenant_id` bigint NOT NULL,
  `return_id` varchar(36) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `product_id` bigint NOT NULL,
  `name` varchar(160) NOT NULL,
  `quantity` int NOT NULL,
  `received_condition` varchar(16) DEFAULT NULL,
  `disposition` varchar(16) DEFAULT NULL,
  `inspection_note` varchar(250) DEFAULT NULL,
  PRIMARY KEY (`tenant_id`,`return_id`,`product_id`),
  KEY `pos_return_items_tenant_id` (`tenant_id`,`return_id`,`order_id`),
  KEY `ix_original_return_items` (`tenant_id`,`order_id`,`product_id`),
  CONSTRAINT `pos_return_items_ibfk_1` FOREIGN KEY (`tenant_id`, `return_id`, `order_id`) REFERENCES `pos_returns` (`tenant_id`, `id`, `order_id`),
  CONSTRAINT `pos_return_items_ibfk_2` FOREIGN KEY (`tenant_id`, `order_id`, `product_id`) REFERENCES `order_items` (`tenant_id`, `order_id`, `product_id`),
  CONSTRAINT `pos_return_items_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `pos_return_items_chk_2` CHECK ((((`received_condition` is null) and (`disposition` is null)) or ((`received_condition` in ('SELLABLE','DAMAGED','INCOMPLETE')) and (`disposition` in ('RESTOCK','QUARANTINE')) and (`inspection_note` is not null) and ((`disposition` <> 'RESTOCK') or (`received_condition` = 'SELLABLE')))))
);

CREATE TABLE `inventory_return_dispositions` (
  `tenant_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  `return_id` varchar(36) NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `disposition` varchar(16) NOT NULL,
  `received_condition` varchar(16) NOT NULL,
  `actor` varchar(100) NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`tenant_id`,`return_id`,`product_id`),
  KEY `inventory_return_dispositions_tenant_id` (`tenant_id`,`branch_id`,`return_id`),
  KEY `ix_return_dispositions_location_owner` (`tenant_id`,`branch_id`,`product_id`),
  CONSTRAINT `fk_return_dispositions_pos_stock_location` FOREIGN KEY (`tenant_id`, `branch_id`) REFERENCES `pos_stock_locations` (`tenant_id`, `branch_id`),
  CONSTRAINT `inventory_return_dispositions_ibfk_1` FOREIGN KEY (`tenant_id`, `branch_id`, `return_id`) REFERENCES `pos_returns` (`tenant_id`, `branch_id`, `id`),
  CONSTRAINT `inventory_return_dispositions_ibfk_2` FOREIGN KEY (`tenant_id`, `return_id`, `product_id`) REFERENCES `pos_return_items` (`tenant_id`, `return_id`, `product_id`),
  CONSTRAINT `inventory_return_dispositions_ibfk_3` FOREIGN KEY (`tenant_id`, `branch_id`, `product_id`) REFERENCES `inventory` (`tenant_id`, `branch_id`, `product_id`),
  CONSTRAINT `inventory_return_dispositions_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `inventory_return_dispositions_chk_2` CHECK ((`disposition` in ('RESTOCK','QUARANTINE'))),
  CONSTRAINT `inventory_return_dispositions_chk_3` CHECK ((`received_condition` in ('SELLABLE','DAMAGED','INCOMPLETE'))),
  CONSTRAINT `inventory_return_dispositions_chk_4` CHECK (((`disposition` <> 'RESTOCK') or (`received_condition` = 'SELLABLE')))
);

CREATE TABLE `pos_cash_refund_items` (
  `tenant_id` bigint NOT NULL,
  `refund_id` varchar(36) NOT NULL,
  `return_id` varchar(36) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `product_id` bigint NOT NULL,
  `name` varchar(160) NOT NULL,
  `quantity` int NOT NULL,
  `net` decimal(19,2) NOT NULL,
  `tax` decimal(19,2) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  PRIMARY KEY (`tenant_id`,`refund_id`,`product_id`),
  KEY `pos_cash_refund_items_tenant_id` (`tenant_id`,`refund_id`,`order_id`),
  KEY `pos_cash_refund_items_tenant_id_2` (`tenant_id`,`return_id`,`product_id`),
  KEY `ix_refund_original_items` (`tenant_id`,`order_id`,`product_id`),
  CONSTRAINT `pos_cash_refund_items_ibfk_1` FOREIGN KEY (`tenant_id`, `refund_id`, `order_id`) REFERENCES `pos_cash_refunds` (`tenant_id`, `id`, `order_id`),
  CONSTRAINT `pos_cash_refund_items_ibfk_2` FOREIGN KEY (`tenant_id`, `return_id`, `product_id`) REFERENCES `pos_return_items` (`tenant_id`, `return_id`, `product_id`),
  CONSTRAINT `pos_cash_refund_items_ibfk_3` FOREIGN KEY (`tenant_id`, `order_id`, `product_id`) REFERENCES `order_items` (`tenant_id`, `order_id`, `product_id`),
  CONSTRAINT `pos_cash_refund_items_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `pos_cash_refund_items_chk_2` CHECK ((`net` >= 0)),
  CONSTRAINT `pos_cash_refund_items_chk_3` CHECK ((`tax` >= 0)),
  CONSTRAINT `pos_cash_refund_items_chk_4` CHECK ((`amount` = (`net` + `tax`)))
);

ALTER TABLE tenants ADD CONSTRAINT `fk_vendor_online_branch` FOREIGN KEY (`id`, `online_branch_id`) REFERENCES `branches` (`tenant_id`, `id`);

INSERT INTO platform_permission_catalogue (permission) VALUES
  ('PLATFORM_ANALYTICS_VIEW'),
  ('PLATFORM_AUDIT_VIEW'),
  ('PLATFORM_CONFIG_MANAGE'),
  ('PLATFORM_IDENTITY_MANAGE'),
  ('PLATFORM_PLAN_MANAGE'),
  ('PLATFORM_SUBSCRIPTION_ADJUST'),
  ('PLATFORM_SUBSCRIPTION_VIEW'),
  ('PLATFORM_SUPPORT_ACCESS'),
  ('PLATFORM_TENANT_CLOSE'),
  ('PLATFORM_TENANT_CREATE'),
  ('PLATFORM_TENANT_REACTIVATE'),
  ('PLATFORM_TENANT_SUSPEND'),
  ('PLATFORM_TENANT_UPDATE'),
  ('PLATFORM_TENANT_VIEW');

INSERT INTO vendor_permission_catalogue (permission) VALUES
  ('CATALOG_PROMOTION_MANAGE'),
  ('INVENTORY_RECEIVE'),
  ('INVENTORY_VIEW'),
  ('POS_CASHIER_REPORT_VIEW'),
  ('POS_CASH_IN'),
  ('POS_CASH_OUT'),
  ('POS_DISCOUNT_APPLY'),
  ('POS_DISCOUNT_APPROVE'),
  ('POS_DISCOUNT_OVERRIDE'),
  ('POS_RECEIPT_REPRINT'),
  ('POS_RECEIPT_VIEW'),
  ('POS_REFUND_APPROVE'),
  ('POS_REFUND_CASH_RECORD'),
  ('POS_REFUND_PRINT'),
  ('POS_REFUND_VIEW'),
  ('POS_REPORT_EXPORT'),
  ('POS_REPORT_VIEW'),
  ('POS_RETURN_APPROVE'),
  ('POS_RETURN_AUDIT_VIEW'),
  ('POS_RETURN_CREATE'),
  ('POS_RETURN_INSPECT'),
  ('POS_RETURN_VIEW'),
  ('POS_SAFE_DROP'),
  ('POS_SALE_CANCEL'),
  ('POS_SALE_CREATE'),
  ('POS_SESSION_CLOSE'),
  ('POS_SESSION_OPEN'),
  ('POS_SESSION_REOPEN'),
  ('POS_SETTINGS_MANAGE'),
  ('POS_SHIFT_VARIANCE_APPROVE'),
  ('POS_TENDER_CASH_RECORD'),
  ('POS_TENDER_EXTERNAL_RECORD'),
  ('POS_TENDER_MANUAL_VERIFY'),
  ('POS_VIEW'),
  ('PRICE_UPDATE'),
  ('PRICE_VIEW'),
  ('PRODUCT_ARCHIVE'),
  ('PRODUCT_CREATE'),
  ('PRODUCT_UPDATE'),
  ('PRODUCT_VIEW'),
  ('ROLE_ASSIGN'),
  ('ROLE_CREATE'),
  ('ROLE_UPDATE'),
  ('ROLE_VIEW'),
  ('USER_CREATE'),
  ('USER_DISABLE'),
  ('USER_UPDATE'),
  ('USER_VIEW');

INSERT INTO retail_sequences (name, next_id) VALUES ('REGISTER', 1), ('BRANCH', 1);

INSERT INTO catalog_sequences (name, next_id) VALUES ('CATEGORY', 1), ('PRODUCT', 1);

INSERT INTO tenant_sequences (id, next_id) VALUES (1, 1);

INSERT INTO identity_sequence (id, next_id) VALUES (1, 1);

-- Non-login system identity preserves the existing subscription audit actor foreign key.
INSERT INTO users (id, username, password_hash, enabled)
VALUES (0, 'system:subscription-grace-expiry', '!', FALSE),
       (-1, 'system:subscription-mock-billing', '!', FALSE);

/*!80000 CREATE TRIGGER `trg_platform_audit_no_update` BEFORE UPDATE ON `platform_audit_events` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Platform audit events are immutable' */;

/*!80000 CREATE TRIGGER `trg_platform_audit_no_delete` BEFORE DELETE ON `platform_audit_events` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Platform audit events cannot be deleted' */;
