# BonFlow — Architecture Proposal for a Restaurant & Venue Ordering System

> **Note (Nov 2026):** the stack was updated from the original proposal (Java 21, Spring Boot 3.x, Vaadin 24.x) to
> Java 25 LTS, Spring Boot 4.1, Vaadin 25 and Spring Modulith 2. Spring Boot 3.5 left OSS support on 2026-06-30.
> The design below is unchanged.

## Context
Servers on the floor take orders on a phone (Flutter app). Once an order is completed (submitted) it must be
split by station (drinks, kitchen, …), printed on receipt printers at those stations, and shown live on
station displays. Payment is handled outside this system. An admin UI manages master data (tables,
articles, categories, app users). Stack: Java/Spring backend, Vaadin for admin + station displays,
REST API for Flutter. Two repos: `bonflow-server` (Spring + Vaadin) and `bonflow-app` (Flutter).

This is a greenfield proposal; nothing in the current workspace (`java/` PROJ checkout) is reused.

---

## 1. System overview

```
 ┌──────────────┐  HTTPS/REST (JSON, JWT)   ┌──────────────────────────────────────────────┐
 │ Flutter app  │ ────────────────────────► │ bonflow-server (Spring Boot, one deployable) │
 │ (servers'    │ ◄──── SSE / polling ───── │                                              │
 │  phones)     │                           │  ┌──────────┐  ┌───────────┐  ┌───────────┐ │
 └──────────────┘                           │  │ REST API │  │ Vaadin    │  │ Vaadin    │ │
                                            │  │ /api/v1  │  │ Admin UI  │  │ Station   │ │
 ┌──────────────┐  Browser (Vaadin Push)    │  └────┬─────┘  └─────┬─────┘  │ Display   │ │
 │ Admin PC     │ ────────────────────────► │       │              │        └─────┬─────┘ │
 └──────────────┘                           │  ┌────▼──────────────▼──────────────▼─────┐ │
 ┌──────────────┐  Browser (kiosk, Push)    │  │ Application services (domain logic)    │ │
 │ Station      │ ────────────────────────► │  └────┬────────────────────┬──────────────┘ │
 │ screens      │                           │       │ domain events      │ JPA            │
 └──────────────┘                           │  ┌────▼─────────┐     ┌────▼─────┐          │
                                            │  │ Print service│     │PostgreSQL│          │
 ┌──────────────┐  TCP 9100 (ESC/POS)       │  │ (outbox +    │     └──────────┘          │
 │ Receipt      │ ◄──────────────────────── │  │  worker)     │                           │
 │ printers     │                           │  └──────────────┘                           │
 └──────────────┘                           └──────────────────────────────────────────────┘
```

**Recommendation: a modular monolith.** One Spring Boot application hosts REST API, Vaadin admin and
Vaadin station displays. A venue's load (tens of phones, a handful of stations) does not justify
microservices; one deployable is simpler to run on-premise (a small server/NUC in the venue — printers
are on the local network anyway). Internal module boundaries keep the option to split later.

## 2. Repositories

### `bonflow-server` (Java 25, Spring Boot 4.1, Vaadin 25, Maven)
Package-by-feature, enforced with **Spring Modulith** (module boundaries + event publication registry):

```
ch.famfrehner.bonflow
├── masterdata/      Table, Area, Article, ArticleCategory, Station, Printer, ModifierGroup
├── users/           AppUser (waiters), AdminUser, roles, PIN/password handling
├── ordering/        Order, OrderItem, OrderService, state machine, domain events
├── routing/         Category/Article → Station mapping, splits an order into StationTickets
├── printing/        Printer adapters (ESC/POS over TCP), print job outbox, retry worker
├── display/         Live ticket state for station screens, broadcaster
├── api/             REST controllers + DTOs (/api/v1/...), OpenAPI spec
├── ui/admin/        Vaadin admin views (CRUD)
├── ui/station/      Vaadin station display views
└── security/        Spring Security config: JWT for /api, session login for Vaadin
```

### `bonflow-app` (Flutter)
- Feature-first structure (`login/`, `tables/`, `order/`, `menu/`), state via Riverpod or Bloc.
- **API client generated from the server's OpenAPI spec** (`openapi-generator` dart-dio) — the spec is the
  contract between the two repos; the server publishes it as a versioned artifact / in CI.
- Local cache of master data (articles, categories, tables) with a version/ETag check, so the menu works
  fast and survives brief Wi-Fi drops.

## 3. Domain model (core)

| Entity | Key fields |
|---|---|
| `Area` | name (e.g. Terrace, Hall) |
| `Table` | number/label, area, active |
| `ArticleCategory` | name, sort order, **default station**, parent (optional) |
| `Article` | name, short name (for printing), category, price (informational, payment is external), active, **station override (optional)**, modifier groups |
| `ModifierGroup` / `Modifier` | e.g. "Cooking: rare/medium", "extra cheese" |
| `Station` | name (Kitchen, Bar, …), printer(s), has display |
| `Printer` | name, host, port, protocol (ESC/POS), paper width, enabled |
| `AppUser` | display name, login/PIN, active, roles (WAITER) |
| `AdminUser` | username, password hash, role (ADMIN/MANAGER) |
| `Order` | id (UUID, client-generated → idempotent), table, waiter, status, createdAt, submittedAt, note |
| `OrderItem` | article, qty, modifiers, free-text note, course, status |
| `StationTicket` | order, station, items, status (NEW → IN_PROGRESS → DONE), printedAt |
| `PrintJob` | ticket, printer, payload, status, attempts, lastError |

**Routing rule:** item's station = `article.stationOverride ?? category.defaultStation`. On submit,
an order is split into one `StationTicket` per station involved.

**Order lifecycle:** `DRAFT` (only on phone, optional server-side) → `SUBMITTED` → (tickets processed) →
`COMPLETED` / `CANCELLED`. Additional items for the same table are a **new submission** (new tickets
labelled "Nachbestellung/addition"), never edits of already-printed tickets. Cancellations of
printed items produce a "STORNO" ticket at the station.

## 4. REST API (Flutter ↔ server)

Base `/api/v1`, JSON, JWT bearer tokens.

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/login` | user + PIN/password → access + refresh token |
| POST | `/auth/refresh` | renew token |
| GET | `/masterdata` | tables, categories, articles, modifiers in one payload, with `ETag` |
| GET | `/tables/{id}/orders` | open orders of a table |
| POST | `/orders` | submit order (body contains client UUID → **idempotent**; retries don't duplicate prints) |
| POST | `/orders/{id}/items` | add items (follow-up submission) |
| POST | `/orders/{id}/cancellations` | cancel items → storno ticket |
| GET | `/orders/{id}` | order + per-station status (for "ready to serve" info) |
| GET | `/events` (SSE, optional phase 2) | notifications to waiter e.g. "Table 12 food ready" |

Errors as RFC 9457 Problem Details. API documented with springdoc-openapi; spec exported in CI.

## 5. Order processing flow

1. Phone `POST /orders` (with client UUID).
2. `OrderService` validates (active articles/table), persists `Order`, uses `routing` to create
   `StationTicket`s, and publishes `OrderSubmitted` — **all in one transaction**.
3. Spring Modulith's event publication registry (transactional outbox) guarantees listeners run after
   commit and are retried if they fail:
   - `printing` → creates `PrintJob`s per station printer; an async worker sends ESC/POS via TCP
     socket (library: `escpos-coffee`). Failures are retried with backoff; persistent failures are
     flagged on the station display and in admin ("printer offline") — **an order is never lost
     because a printer is down**; display acts as fallback.
   - `display` → pushes the new ticket to all open station views.
4. Phone receives `201` with order id + ticket summary.

## 6. Vaadin UIs

### Admin (`/admin`, role ADMIN/MANAGER)
- CRUD views (Vaadin Grid + Binder/Form, or Vaadin CRUD component) for: Areas & Tables,
  Categories, Articles (incl. station override, modifiers, active toggle / "sold out"),
  Stations & Printers (with **"test print"** button), App users (reset PIN, deactivate).
- Order overview / history (read-only, filter by date/table/waiter), reprint ticket.
- Spring Security form login, `@RolesAllowed` on views.

### Station display (`/station/{stationId}`)
- Full-screen kiosk view (browser on a TV/tablet), card layout: one card per `StationTicket`
  showing table, waiter, time since submission (colour turns amber/red), items + modifiers + notes.
- Tap card / items → `IN_PROGRESS` → `DONE` (bump). "Recall" last done tickets.
- **Live updates with Vaadin `@Push`** (WebSocket): a `StationBroadcaster` (in `display`) holds
  registered UIs per station; on events it calls `ui.access(...)` to update. On reconnect the view
  reloads the current open tickets from the DB, so it never depends on the push stream alone.
- Auth: a per-station device login (long-lived session, role STATION) so screens need no daily login.

## 7. Cross-cutting decisions

| Topic | Decision |
|---|---|
| DB | PostgreSQL; schema migrations with Flyway |
| Persistence | Spring Data JPA; optimistic locking (`@Version`) on Order/Ticket |
| Security | `/api/**` stateless JWT (Spring Security resource server, own signing key); Vaadin session-based; HTTPS on LAN (reverse proxy e.g. Caddy/nginx) |
| Idempotency | client-generated order UUID + unique constraint |
| Printing | ESC/POS over raw TCP 9100 (Epson TM / compatible); adapter interface so other printer types can be added |
| Real-time | Vaadin Push internally; SSE for phones in phase 2 |
| Deployment | Docker Compose (app + Postgres + reverse proxy) on an on-premise box in the venue LAN |
| Observability | Spring Boot Actuator health (incl. custom printer health indicator), structured logs |
| i18n | Vaadin I18NProvider, Flutter `intl` (DE/EN) |
| Testing | JUnit 5 + Testcontainers (Postgres), Spring Modulith `ApplicationModuleTest`, REST contract tests against OpenAPI, fake TCP printer for print tests; Flutter widget + integration tests against a mock server |

## 8. Phasing
1. **MVP:** master data admin, waiter login, submit orders, routing, printing, station display.
2. Follow-up orders, storno, reprint, printer health, order history.
3. Waiter notifications (SSE/push), courses ("fire main course"), sold-out sync, reporting export.

## 9. Open points to confirm
- Single venue per installation vs. multi-venue/multi-tenant (affects data model: add `Venue` FK everywhere).
- On-premise vs. cloud hosting (cloud would need a local print bridge agent, since printers are in the LAN).
- Printer models (ESC/POS network assumed).
- Waiter login: PIN on shared phones vs. personal devices.
- Does the order system need to report anything to the external payment system (e.g. open amount per table)?

## Verification of the proposal
- Walk through the flow with a prototype slice: one article, one station, `POST /orders` via curl →
  ticket appears on `/station/1` and prints on a real or simulated (netcat on port 9100) printer.
- Kill the printer mid-test → order still visible on display, print job retried when printer returns.
- Re-send the same `POST /orders` → no duplicate ticket.
