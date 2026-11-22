# Product and Operations Guides

This folder contains practical guides derived from the approved Version 2.0 Product Requirements and SRS/HLD.

| Guide | What it explains | Primary readers |
|---|---|---|
| [User Roles, Responsibilities and Journeys](./USER_ROLES_AND_JOURNEYS.md) | Platform Super Admin, Platform Support, Vendor Admin, vendor employees and customer capabilities; end-to-end online and POS journeys | Product, UX, onboarding, engineering, QA, vendors |
| [Feature Lifecycles and State Transitions](./FEATURE_LIFECYCLES.md) | Lifecycle/state progression for tenant, subscription, identity, CRM, quotation, cart, orders, payments, inventory, POS, refunds, documents, service and notifications | Product, backend, frontend, database, QA, operations |
| [Role-to-Feature Permission Matrix](./ROLE_PERMISSION_AND_ACCEPTANCE_MATRIX.md) | Role-to-feature access, separation of duties, sensitive action rules and authorization acceptance criteria | Security, backend, frontend, QA, tenant admins |

## Source of truth

- [Product Requirements — Version 2.0](../saumya-solar-energy-solution-requirements.md): business actors, policy and MVP scope.
- [SRS and HLD — Version 2.0](../saumya-solar-production-ready-srs-hld.md): technical architecture, data ownership, permissions, APIs, controls and acceptance criteria.

Where a practical guide and the baseline conflict, the Product Requirements control business scope and the SRS/HLD controls technical enforcement. Raise the discrepancy and update the source documents together rather than silently changing a guide.

## Implementation caution

These guides describe the approved target behavior. They do not certify that every feature, permission, lifecycle state, endpoint, migration, integration or automated test is already implemented. State labels described as recommended/logical must be confirmed in the detailed domain/API design before they become database enums or public API contracts.
