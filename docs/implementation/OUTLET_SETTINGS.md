# Outlet configuration

GET `/api/v1/vendor/pos/settings` requires POS_VIEW. POST requires POS_SETTINGS_MANAGE and an expectedVersion plus reason. Tenant and branch come from membership. The settings service locks the branch, rejects stale versions, validates opaque merchant references/currency/return windows and appends before/after audit history. Payment/return modules consume immutable retail policy DTOs and no longer map retail entities.

Settings cover outlet name/currency, manual vendor-terminal verification, split tender, return window/cash refunds and approved invoice/buyer/credit-note flags. Statutory flag activation does not replace qualified approval of the vendor's tax policy. POST `/api/v1/vendor/pos/registers` creates a till for the assigned branch and audits its reason; reload the register list after an uncertain response before creating another.

The UI is part of the existing POS shell, grouped under Manage, with Outlet, Payments, and Returns/tax tabs. The default pre-existing money currency is INR; changing a setting must not rewrite issued sale currency once snapshots are integrated.
