# Split tenders and discounts

Use the canonical `/api/v1/vendor/pos/sales/{id}/tenders` API to record cash or an external terminal attempt. Confirmed applied amounts must exactly equal the saved bill before `/finalize`. Pending or unknown terminal attempts block another collection. Independent supervisor evidence is committed before finalization is retried. Production terminal payments are never simulated online payments.

Discounts are passed as authorized line and bill discount instructions with a reason. Branch policy limits apply. Cash change and confirmed terminal references are included in receipt data and PDF copies.

The competing pre-production `order_tenders`, `finalize-split` and percentage-only discount stack was consolidated into the durable payment-attempt model. Only the migration chain from the PR target is supported; no competing V24 migration or duplicate persistence model is retained. No issued financial rows are deleted by this integration.
