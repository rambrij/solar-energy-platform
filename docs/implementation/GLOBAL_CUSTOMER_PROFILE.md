# Global customer profile

The global customer profile is keyed by the existing `customer_profiles.id` and `user_id` relationship. The optional `display_name` is a global customer-owned presentation field; it is not copied into vendor CRM records.

## API

- `GET /api/v1/customer/profile` — returns the authenticated customer's profile ID, display name and global sign-in email.
- `PUT /api/v1/customer/profile` — updates only the optional display name (maximum 120 characters; blank clears it).

Both routes require the `CUSTOMER` actor permission. The service resolves the profile from the authenticated principal through `CustomerAccess`, locks the same profile before mutation, and never accepts a customer ID, tenant ID, actor type or role from the request. Vendor and Platform contexts cannot use these endpoints.

The sign-in email is read-only in this slice. Email verification, email change, recovery and mobile verification remain blocked pending the identity lifecycle and verification-policy decisions/infrastructure described by the authoritative requirements.

## Browser verification

The Playwright customer account-security journey signs the same global customer identity in from two isolated browser contexts. It confirms both sessions initially access the same customer-owned profile, revokes other sessions from the first browser after explicit confirmation, verifies the second browser is returned to the sign-in boundary on refresh, and verifies the initiating browser remains authenticated. The test uses the existing session API and seeded demo identity; it creates no customer data and introduces no new policy or persistence.
