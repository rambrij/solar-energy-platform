# Platform Super Admin demo walkthrough

## Open the workspace

1. Set `SPRING_PROFILES_ACTIVE=local` in the repository root `.env`.
2. Run `docker compose up --build -d`. Open [Platform Overview](http://localhost:8080/platform) or http://localhost:5173/platform when using Vite directly.
3. Sign in with the local-only Platform Super Admin listed in the [demo account matrix](README.md). The active actor should be `PLATFORM`.
4. If the tenant directory or Configuration is missing, inspect `GET /api/v1/auth/me` for `PLATFORM_TENANT_VIEW`, `PLATFORM_CONFIG_MANAGE`, and `PLATFORM_IDENTITY_MANAGE`. Older seed data can lack these grants. Follow the [clean local database guide](../implementation/CLEAN_DEVELOPMENT_DATABASE.md) only if your volume is disposable.

## Quick reference

| What you want to do | Where to find it |
| --- | --- |
| Create a tenant and its first Vendor Admin | **Tenants → Onboard a vendor tenant**. Enter the initial Vendor Admin email. A new identity receives a one-time invitation token. |
| Review, activate, suspend, reactivate, or close a tenant | **Tenants → Tenant directory**. Enter a lifecycle reason and select an action on the tenant row. A new tenant moves from **Pending** to **Under review**, then to **Active**. Other actions depend on its current status. |
| Manage plans, prices, billing periods, and entitlements | **Subscription plans → Create subscription plan** for a new plan, or **Revise plan** on an existing plan. Saving a revision creates a new version and preserves the previous terms. |
| Manage a tenant's subscription | **Tenant subscriptions → Billing and events**. Review billing history and choose an available administrative transition. Activation requires a verified platform payment event. |
| View metrics, health, and audit | **Overview → Subscriptions by status** and **Tenant lifecycle**. Health appears in the top navigation. Administrative audit records are under **Platform audit**. |
| Grant or revoke support access | **Support access**. Record a tenant ID, ticket, purpose, and expiry of up to four hours. Select **Revoke** on an active grant and provide a reason. |
| Manage global settings and payment-provider references | **Configuration → Global technical settings** or **Payment providers**. Save non-sensitive values and external secret references. Live payment execution is outside the demo. |
| Edit the MFA notification templates | **Configuration → Email templates**. Save a subject and plain-text body, or choose **Edit template** beside a saved template. |
| Switch the local MFA mail adapter | **Configuration → Integration adapter settings → Configure MFA mail adapter**. Choose **Local Mailpit SMTP** or **Disabled**. The next committed MFA activity uses the saved setting. |
| Issue or reset platform MFA and view activity notices | **Platform identities → Issue / reset MFA** on a different provisioned platform identity. Enter a reason and share the one-time token through the approved secure channel. Activity notices appear in [Mailpit](http://localhost:8025). |
| Simulate a vendor payment locally | Set `PAYMENTS_MOCK_ENABLED=true` with the `local` profile, then use test UPI or card payment in vendor checkout as a demo customer. |

## Vendor tenant onboarding and lifecycle

Open **Tenants → Onboard a vendor tenant**. Enter a unique storefront slug, display name, legal name, and the initial Vendor Admin's email. Submission creates a pending tenant, initial branch, and Vendor Admin membership. Share a newly issued invitation token once through the approved secure channel so the nominated admin can accept the invitation. If the nominated identity is already active, use its existing credentials.

In **Tenant directory**, enter a reason for each lifecycle change and select the action shown on the tenant row.

| Current state | Available transitions |
| --- | --- |
| Pending | Under review or closed |
| Under review | Active or closed |
| Active | Suspended or closed |
| Suspended | Active or closed |
| Closed | Terminal state |

Use **Edit profile** to update a tenant's legal name, contact details, tax identifier, and address. When the vendor's acceptance has been confirmed, select **Record confirmed vendor terms acceptance** during onboarding or profile editing. The server records the first confirmation time and the platform audit attributes the action to the operator. Profiles without confirmation show **Unrecorded**.

Tenant activation and subscription activation are separate. The Platform actor has no commercial catalogue, vendor POS, or customer-order authority.

## Subscription plans and tenant subscriptions

1. In **Subscription plans**, enter the price, currency, billing period, limits, and feature entitlements. Save the first version.
2. Use **Revise plan** to edit the current terms and save a new version. Older terms remain in the catalogue and existing subscription records keep their version. Use **Enable plan** or **Disable plan** to manage availability for new subscriptions.
3. Use the top **Tenant subscriptions** link. Choose a tenant, plan, billing dates, and reason to create a pending subscription. Open **Billing and events** to see history and permitted administrative transitions. Activation requires a verified platform payment event. The vendor checkout mock does not activate platform subscriptions.

**Overview** shows platform health, tenant lifecycle totals, and subscription counts by status. Use **Platform audit** for administrative events. The dashboard counts do not include tenant-private transaction details.

## Global configuration and supported adapters

- **Global technical settings** and **Integration adapter settings** are under **Configuration**. Use an external secret-manager reference for sensitive values.
- To switch local MFA activity notifications at runtime, choose **Integration adapter settings → Configure MFA mail adapter**, select **Local Mailpit SMTP** or **Disabled**, and save. The next committed MFA activity uses that setting without a restart.
- **Payment providers** keeps vendor and subscription provider settings separate. These records do not turn on a live gateway.

## Platform MFA and Mailpit

1. In **Configuration → Email templates**, save a subject and plain-text body for `PLATFORM_MFA_ENROLLMENT_ISSUED` and `PLATFORM_MFA_ENROLLMENT_COMPLETED`. Use **Edit template** on a saved row to change it. Default notices are used if a template is absent.
2. In **Platform identities**, select a different provisioned platform identity, enter the reason, and choose **Issue / reset MFA**. The local seed contains one Platform Super Admin, so this flow requires an independently provisioned second identity. Self-reset remains blocked. See [Platform MFA operations](../security/platform-mfa-operations.md).
3. Share the one-time token through the approved secure channel. The target opens **Set up Platform MFA**, enters the token, scans or enters the TOTP setup in an authenticator, and submits a current code.
4. Open [Mailpit](http://localhost:8025) for the issued/reset and completed notices addressed to the target. The app sends notices only after commit. The notices carry no enrollment token, TOTP code, secret, or provisioning URI.

## Controlled support access

In **Support access**, enter the tenant ID, ticket, purpose, and an expiry up to four hours away. Choose **Record support grant**. An active grant can be revoked with **Revoke** and a reason. The grant and revocation are audited. The grant alone does not give unrestricted tenant access.

## Simulate a vendor payment

Use a disposable local environment. Set `PAYMENTS_MOCK_ENABLED=true` with `SPRING_PROFILES_ACTIVE=local`, then run `docker compose up --build -d`.

1. Sign out of the Platform account. Sign in as a demo customer with an available vendor cart and proceed to vendor checkout.
2. Choose **Start test UPI payment** or **Start test card payment**. The checkout records a pending attempt scoped to the vendor and order.
3. Choose **Simulate successful payment** for the selected method. The application verifies the attempt and completes its normal order, stock, and receipt flow with a mock reference. Reload or query status before retrying an uncertain outcome.

This simulates successful vendor payment confirmation. No network provider is contacted and no funds move. Terminal tenders and platform subscription payments use separate flows. Disable the flag when the demo is finished.
