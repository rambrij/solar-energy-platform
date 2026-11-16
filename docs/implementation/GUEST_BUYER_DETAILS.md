# Transaction-time guest buyer details

The existing cash-sale and POS-draft commands accept an optional `buyer` object:

```json
{
  "buyer": {
    "name": "Buyer supplied name",
    "contact": "Optional contact supplied by buyer",
    "taxId": "Optional tax registration identifier",
    "address": "Buyer supplied address"
  }
}
```

Omitting it, supplying null or leaving every field blank creates no buyer snapshot. A guest needs no email, mobile number, login or registration. These fields never perform identity lookup, create a user/customer/tenant relationship, or attach an existing account. The independent authenticated customer-consent flow remains the only account-association path.

Limits: name/contact 160 characters, tax identifier 80, address 1,000. Unsupported control characters are rejected; address line breaks are retained. Whitespace is trimmed. Validation errors do not echo the entered values.

When the outlet's approved `buyerDetailsRequired` flag is enabled **and** the sale requires or requests a tax invoice, name and address are required. Missing data returns `BUYER_DETAILS_REQUIRED` before any tender is accepted. Contact and tax identifier remain optional under this minimum policy. Jurisdiction-specific mandatory tax fields require approved vendor configuration; no tax-ID format or legal rule is guessed.

Snapshots belong to checkout and are captured once with the draft, then remain unchanged on paid sale retries and receipt copies. Changing buyer fields for an existing command key is rejected. Buyer fingerprints use length-delimited fields and hashing; private values are not stored in command hashes or audit details.

Staff receipt and PDF APIs obtain snapshots through a checkout service that verifies tenant, branch, paid status and in-store origin. Existing receipt-view/reprint permissions still apply. Snapshot reads add a reference-only `BUYER_SNAPSHOT_VIEW` audit. A customer may see the snapshot only through a transaction linked through the authenticated consent workflow and that customer's existing owned-order receipt API.

Cancelled or expired unpaid drafts remove their optional snapshot in the same transaction that releases stock. Confirmed or unresolved tenders prevent that cleanup. Paid snapshots remain part of the issued transaction record and must follow the vendor's approved financial-document retention policy; no arbitrary legal retention duration or automatic financial-record deletion is introduced.

The current billing panel exposes an optional buyer-details section. Browser receipts, customer-owned linked POS receipts and PDF copies show the saved values. Pending command details live only in session storage for safe retry and are cleared after acknowledgement or a definite initial rejection.

## Assumptions for the PR

- The existing invoice buyer-details flag means a minimum of name and address. Per-jurisdiction mandatory tax identifiers and the approved retention duration for paid documents remain policy inputs.
- This increment adds transaction snapshots, not editable customer CRM records or a guest identity directory.
