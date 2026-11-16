-- Platform grants have no tenant; commercial access continues to require employee/customer context.
CREATE TABLE platform_user_permissions (
 username VARCHAR(100) NOT NULL,
 permission VARCHAR(60) NOT NULL,
 PRIMARY KEY(username,permission),
 FOREIGN KEY(username) REFERENCES users(username),
 CHECK(permission IN ('PLATFORM_TENANT_CREATE','PLATFORM_TENANT_UPDATE','PLATFORM_TENANT_SUSPEND','PLATFORM_TENANT_CLOSE','PLATFORM_PLAN_MANAGE','PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_SUBSCRIPTION_ADJUST','PLATFORM_ANALYTICS_VIEW','PLATFORM_AUDIT_VIEW','PLATFORM_SUPPORT_ACCESS'))
);
