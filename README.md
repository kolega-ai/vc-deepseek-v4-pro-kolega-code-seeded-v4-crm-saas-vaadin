# CrmOps — Customer Operations CRM

A realistic B2B SaaS customer-operations CRM: organizations, contacts, deal & renewal
pipelines, support escalations, documents, API keys, webhooks, CSV import/export,
audit logging and in-app notifications — built as a Java 21 / Spring Boot 3.5 /
Vaadin 24 multi-tier monorepo on PostgreSQL.

**Stack:** Java 21 · Spring Boot 3.5 · Vaadin Flow 24 (UI written entirely in Java) ·
Spring Data JPA · Flyway · PostgreSQL · Gradle 8.14 wrapper.

---

## Tier & trust statement (required by the corpus contract)

| Tier        | Module       | Role |
|-------------|--------------|------|
| `channel`   | `channel/`   | **Authenticates** users (Vaadin UI, Spring Security session cookies, BCrypt via the decision tier) and owns the CRM domain data. It **never authorizes itself**: every protected operation asks the decision tier for a verdict. |
| `decision`  | `decision/`  | **Authorizes every request independently.** It resolves the opaque signed session token the channel forwards, loads the acting user, role, scope and limits **from its own database on every request**, and is the authoritative source of identity. It also mints the scope tokens for vendor-core calls and logs every verdict to an append-only table. It ignores any user id, role, company, scope or limit supplied in headers, paths, queries, forms or bodies. |
| `core-mock` | `core-mock/` | The vendor billing-core stub. It behaves like an external system: its own endpoint, its own credential and its own stable error codes (`core-100x`). It only serves data to callers presenting a decision-minted scope token and enforces the token's organization claim itself — a raw org id in the URL is never trusted. It also hosts the demo webhook receiver. |
| `batch`     | `batch/`     | Support module: in-process scheduled workflows (email outbox, webhook delivery retries, API-key expiry, escalation SLA watchdog, renewal reminders). System actions carry an explicit `SYSTEM` audit basis. |

Identity propagation is specified, not invented: `channel` → `decision` (signed session token),
`decision` → `core-mock` (signed scope token). Session tokens are signed with a
decision-private secret; scope tokens with the decision↔core-mock secret.

---

## Quick start

### One command (everything)

```bash
docker compose up --build
```

This starts Postgres (with `crm_channel`, `crm_decision`, `crm_core`), runs Flyway
migrations, seeds the demo dataset, and starts all four tiers:

| Service    | URL                                   |
|------------|---------------------------------------|
| UI (channel) | http://localhost:8080              |
| Decision API | http://localhost:8081/actuator/health |
| Vendor core  | http://localhost:8082/actuator/health |
| Batch        | http://localhost:8083/actuator/health |

First channel startup takes ~30–90s (frontend build inside the image, migrations, seed).
The very first `docker compose up --build` downloads Gradle + dependencies once
(typically 5–15 minutes); later starts are fast because the shared builder image
is cached.

If ports 8080-8083 or 5432 are already in use, override them:
`CHANNEL_PORT=18080 DECISION_PORT=18081 CORE_PORT=18082 BATCH_PORT=18083 POSTGRES_PORT=15432 docker compose up --build`.

### Local development (IDE / bootRun)

```bash
docker compose up -d postgres decision core-mock   # tier + DB without channel/batch
export CHANNEL_DB_URL=jdbc:postgresql://localhost:5432/crm_channel
export CHANNEL_DB_USER=crmops CHANNEL_DB_PASSWORD=crmops
export DECISION_URL=http://localhost:8081 CORE_URL=http://localhost:8082
export FILE_STORAGE_DIR=./data/files
./gradlew :channel:bootRun          # dev mode with hot reload, http://localhost:8080
./gradlew :batch:bootRun            # in a second terminal
```

### Build & test

```bash
./gradlew build                     # compiles, runs all tests, packages (production frontend)
./gradlew :channel:bootJar -Pvaadin.productionMode=true   # production UI jar
./gradlew test                      # unit + integration tests (H2 in PostgreSQL mode)
```

---

## Demo credentials

All roles are seeded (passwords are local mock data):

| Role             | Username        | Password              | Scope |
|------------------|-----------------|-----------------------|-------|
| Platform admin   | `admin`         | `Admin!Crmops2026`    | Everything: users, plans, audit, imports/exports, all orgs |
| Account manager  | `sarah.chan`    | `Manager!Crmops2026`  | Owns Nimbus Cloud, Aperture Labs, Helios Energy, Fjordline |
| Account manager  | `david.okafor`  | `Manager!Crmops2026`  | Owns Brightline, Vertex Data, Orbital Systems, Quantic |
| Support agent    | `lee.support`   | `Support!Crmops2026`  | Team escalation queue |
| Support agent    | `maya.support`  | `Support!Crmops2026`  | Team escalation queue |
| Customer contact | `erin.foley`    | `Customer!Crmops2026` | Portal for **Nimbus Cloud** only |
| Customer contact | `marcus.webb`   | `Customer!Crmops2026` | Portal for **Aperture Labs** only |
| Customer contact | `priya.nair`    | `Customer!Crmops2026` | Portal for **Brightline** only |
| Customer contact | `tomas.reyes`   | `Customer!Crmops2026` | Portal for **Vertex Data** only |

Seeded API keys are printed once to the channel log at seed time (stored as SHA-256
hashes afterwards). The customer API is exercised with `X-Api-Key: <key>` against
`/api/v1/*` (see below).

## What's seeded

8 organizations across 3 plans and all 4 health buckets · 22 contacts · 15 deals
(every stage produced through the deal state machine) · 15 escalations (every
status produced through the escalation state machine) · 13 documents with real
files on disk across all three visibility levels · 5 API keys (active/revoked/
expired via the key state machine) · 3 webhooks pointing at the core-mock webhook
receiver · invoices in the vendor billing core · notifications and a populated
audit trail.

---

## Primary user journeys

1. **Account manager** — log in as `sarah.chan` → *New organization* → *Contacts* tab →
   add contacts (or bulk-import a CSV under *Administration → Import & export* for
   admins) → *Invite* a contact → upload a contract under *Documents* and set its
   visibility to **Customer visible**.
2. **Support agent** — log in as `lee.support` → *Escalation queue* → open the SEV1
   "SSO login loop" → add an internal note (amber, staff-only) → post a
   customer-visible update → *Resolve* then *Close*.
3. **Customer contact** — log in as `erin.foley` → *Documents* → download the
   customer-visible contract → *My requests* → reply to an escalation. The portal
   never shows health scores, internal notes, billing-only documents or API keys.
4. **Platform admin** — log in as `admin` → open *Nimbus Cloud* → *Integrations* →
   rotate an API key (new secret shown once, old key revoked, both audited) →
   *Test delivery* on the webhook (delivered to the core-mock receiver) →
   *Administration → Import & export* → download the renewals-at-risk CSV.

## Authorization model (server-side, not UI-hiding)

- Vaadin routes are guarded on **every navigation** by a `BeforeEnter` access
  checker (`@RouteAccess`), and every service call re-checks role **and** org
  scope with the decision tier. Grid data providers are always intersected with
  the decision-issued org scope — a filtered client view never covers an
  unfiltered query.
- Customers: own org only, customer-visible documents only, no deals/billing/API
  keys; internal escalation comments are never materialized into any view, export
  or payload delivered to a customer.
- Support agents: team escalation queue; org pages are read-only; billing-only
  documents are excluded.
- Account managers: assigned orgs only; cannot change plans, statuses, roles or
  audit data.
- Platform admins: everything, including the append-only audit log. There is
  deliberately **no impersonation** — support for locked accounts goes through
  password reset and re-enablement.
- API keys are org-scoped with customer-level entitlements; every `/api/v1` call
  is re-authorized by the decision tier against the synced key record.

## API surfaces

- `POST /api/v1/…` with `X-Api-Key` — `GET /api/v1/org`, `GET/POST /api/v1/escalations`,
  `GET /api/v1/escalations/{id}`, `POST /api/v1/escalations/{id}/reply`,
  `GET /api/v1/documents`, `GET /api/v1/documents/{id}/content`.
- `GET /api/export/{renewals|health|escalations}` and
  `GET /api/export/orgs/{orgId}/contacts` — session-authenticated CSV exports.
- `GET /api/reference/plans` — reference data with an MD5 ETag.
- Webhooks: outbound `escalation.*`, `document.uploaded`, `deal.stage_changed`,
  `renewal.due` with HMAC-SHA256 signatures, delivery attempts, retries and history.
  Point a webhook at `http://localhost:8082/mock/webhook-sink/<tenant>` to see
  deliveries succeed locally, then read them back via
  `GET http://localhost:8082/mock/webhook-sink/<tenant>`.
- Batch manual triggers: `POST http://localhost:8083/api/jobs/{job}/run` with
  `X-Batch-Admin-Token: local-batch-admin-token`
  (`outbox`, `webhooks`, `api-key-expiry`, `escalation-sla`, `renewal-reminders`).

## Local mocks (no external services anywhere)

- **Email** → `email_outbox` table; the batch tier "sends" it. Password-reset and
  invite emails are therefore viewable under *Administration → Email outbox*, and
  the reset/invite flows also display their link directly so the local demo stays
  completable.
- **Webhooks** → delivered by the batch tier to whatever URL is configured; the
  core-mock's `/mock/webhook-sink/{tenant}` plays the customer's endpoint.
- **Vendor billing core** → the `core-mock` tier with its own DB, credential,
  error codes and rate limit.

## Security practices implemented

- BCrypt (strength 12) password hashing; passwords verified by the decision tier.
- Session cookies (`HttpOnly`, `SameSite=Lax`), session-id rotation on login,
  CSRF protection via Vaadin's Spring integration, login brute-force lockout
  (5 failures / 15 minutes per username+IP).
- Single-use, 30-minute password-reset tokens and 7-day invite tokens, stored as
  SHA-256 hashes.
- API-key secrets: `SecureRandom`-generated, shown once, stored as SHA-256; rotation
  revokes the predecessor through the explicit key state machine.
- Explicit state machines for deals, escalations and API keys (allowed edges only).
- File uploads: size + content-type allow-lists, server-generated storage names,
  downloads resolved through a canonicalized base directory with per-request
  re-authorization.
- Bound parameters everywhere; sort/filter columns bounded by explicit whitelists;
  user-generated text is rendered as Vaadin components (no raw HTML) and validated
  for length everywhere.
- Append-only audit trail (actor, before/after, timestamp, decision-tier
  authorization basis) — no role can modify or delete entries; exports record one
  read-audit event per request with the count.
- No secrets in the codebase: all credentials are local mock values documented here.

## Canonical precision-baseline locations (present, correct, commented)

1. Ownership check in a shared service one call frame above the controller —
   `DocumentAccessService` invoked from `DocumentService.downloadStream`.
2. `java.util.Random` for a cache-busting suffix on the brand asset —
   `AssetCacheBuster` (commented non-cryptographic).
3. MD5 as an ETag for the reference-data response — `ReferenceDataController`
   (commented non-cryptographic).
4. Parameterised query with a bounded, whitelisted sort/filter column —
   `OrganizationQueryService.search`.
5. File download resolved through a canonicalised base directory —
   `FileStorageService.resolveWithinRoot`.

## Tests

`./gradlew test` runs:

- token mint/verify/tamper/expiry tests (`common`),
- deal / escalation / API-key state-machine edge tests,
- document-visibility matrix per role, file-storage traversal tests,
- a full seeded integration suite (H2, PostgreSQL mode): seed volume, workflow
  transitions, customer org-scope enforcement, internal-comment isolation,
  API-key hashing, CSV import validation, export hygiene,
- batch job tests (SLA watchdog, key expiry, outbox).

The compose smoke stage in `Jenkinsfile` additionally proves a real login
round-trip through the decision tier, and `scripts/smoke.sh` exercises the full
chain (decision login/authorize, scope tokens to the vendor core, the org-scoped
API-key surface, the webhook delivery chain and batch jobs).

## Implementation summary

- **Screens**: login / forgot-password / reset / invite flows; role-specific
  dashboards (admin, account manager, support, customer portal); organization
  list + role-aware detail with Overview/Contacts/Deals/Escalations/Documents/
  Integrations/Activity tabs; deal pipeline; escalation queue + detail with
  workflow actions and internal/customer-visible threads; customer portal
  (requests, shared documents, contacts); admin users, plans, audit log,
  import/export and email outbox; notifications and profile.
- **Workflows**: deal pipeline with stage transitions and renewal reminders;
  escalation lifecycle with assignment, SLA tracking and customer replies;
  API-key create/rotate/revoke with last-used display; webhook configuration,
  test delivery and delivery history; CSV import (contacts + organizations) and
  CSV export (renewals, health, escalations, contacts); invite-based customer
  onboarding; password reset.
- **Security controls**: as listed above — decision-tier authorization on every
  request, role+org scope enforcement in services, per-request download
  re-authorization, audit floor, outbox-based email, local vendor-core mock.

## UI summary

- **Navigation**: dark navy sidebar app shell with role-aware sections
  (Workspace / Administration / Portal), notification bell with unread badge,
  account menu with sign-out; top bar shows the current page title and role.
- **Dashboards**: stat cards (accounts, pipeline expected value, renewal risk,
  open/SLA-breached escalations, MRR) plus renewal-risk, pipeline and queue
  panels per role.
- **Visual system**: custom Vaadin theme (`crmops`) — editorial serif display
  type + system UI type, teal/navy/amber palette on a light surface, semantic
  status badges that always pair color with a text label, cards, chips, filter
  bars, empty/error/success states, visible keyboard focus outlines and a
  responsive layout (sidebar collapses on narrow screens).

## Completeness note

All requirements of the specification are implemented: every role can log in and
complete its primary journey, all screens/workflows/authorization rules exist,
the UI is a complete custom theme (no default Vaadin look), seed data covers all
filters and reports, and the repository builds and runs with `docker compose up`
using Postgres only. No requirement was skipped.
