# BonFlow — Architecture

Architecture of the BonFlow restaurant & venue ordering system **as built** (server v0.1.x, app 0.1).
The design started as a proposal; this document now describes what exists, and the last section lists
what is not built yet.

## Context
Servers on the floor take orders on a phone (Flutter app). Once an order is submitted it is split by
station (drinks, kitchen, …), printed on receipt printers at those stations, and shown live on station
displays. Payment is handled outside this system and there is no interface to a payment system. An admin
UI manages master data (tables, articles, categories, users). Stack: Java/Spring backend, Vaadin for admin
and station displays, REST API for Flutter. Two repos: `bonflow-server` (Spring + Vaadin) and `bonflow-app`
(Flutter).

**Decisions:** one venue per installation (no multi-tenancy), on-premise deployment in the venue LAN,
Epson TM / ESC/POS printers over the network, waiter login by PIN on shared phones.

**Stack:** Java 25 (LTS), Spring Boot 4.1, Vaadin 25, Spring Modulith 2, PostgreSQL 18 + Flyway, Spring
Security, springdoc-openapi, escpos-coffee, Maven. The base package is `ch.bonflow` (domain bonflow.ch).

---

## 1. System overview

```
 ┌──────────────┐  HTTPS/REST (JSON, JWT)   ┌──────────────────────────────────────────────┐
 │ Flutter app  │ ────────────────────────► │ bonflow-server (Spring Boot, one deployable) │
 │ (servers'    │ ◄────── polling ──────── │                                              │
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

**A modular monolith.** One Spring Boot application hosts REST API, Vaadin admin and
Vaadin station displays. A venue's load (tens of phones, a handful of stations) does not justify
microservices; one deployable is simpler to run on-premise (a small server/NUC in the venue — printers
are on the local network anyway). Internal module boundaries keep the option to split later.

## 2. Repositories

### `bonflow-server` (Java 25, Spring Boot 4.1, Vaadin 25, Maven)
Package-by-feature, enforced by **Spring Modulith** (`ModularityTests` verify the module boundaries; the
JDBC event publication registry is the transactional outbox):

```
ch.bonflow
├── masterdata/      Area, DiningTable, ArticleCategory, Article, ModifierGroup/Modifier, Station, Printer;
│                    MasterDataService + MasterDataSnapshot (served by the API)
├── users/           AppUser (waiters: PIN, lockout), AdminUser (web UI accounts incl. station devices)
├── ordering/        CustomerOrder, OrderItem, OrderService, events (OrderSubmitted), OrderFilter/OrderSummary
├── routing/         Article → station mapping, StationTicket (NEW/ADDITION/STORNO), TicketService, TicketStatusChanged
├── printing/        TicketRenderer + EscPosBuilder, PrintJob outbox, PrintDispatcher (worker),
│                    TcpPrinterGateway, PrintAdminService (test print, reprint), PrintMonitor
├── display/         DisplayService, StationBroadcaster (Vaadin push), StationBoard/TicketCard
├── api/             REST controllers + DTOs (/api/v1), exception handler (RFC 9457), OpenAPI config
├── ui/admin/        Vaadin admin views
├── ui/station/      StationView (station display)
└── security/        Spring Security: JWT for /api, session login for Vaadin; TokenService, JwtKeys
```

### `bonflow-app` (Flutter)
- Feature-first (`auth`, `login`, `masterdata`, `tables`, `order`, `menu`), state with **Riverpod 3**
  (Notifier), navigation with go_router (redirect by session state), HTTP with Dio.
- **API access through hand-written repositories**, not a generated client. The contract with the server
  is guarded by a **contract test** (`test/contract`): the real repositories run against a snapshot of the
  server's OpenAPI spec (published with each release as `openapi-vX.Y.Z.json`); a weekly workflow checks the
  latest release.
- **Master data cache** on the device with the server's `ETag` (`If-None-Match` → 304), so the menu works
  fast and survives Wi-Fi drops.
- **Offline queue for new orders:** if the server is unreachable, a new order is stored on the device and
  sent later (every 20 s, on pull-to-refresh, after restart). The client-generated order UUID makes retries
  idempotent. Follow-up orders, storno and completion need the server.
- Tokens are kept in secure storage; a 401 triggers one refresh and a retry, otherwise the waiter is logged out.

## 3. Domain model (core)

Tables of the baseline schema (`V1__baseline.sql`, later migrations add `done_at` on tickets and the
`STATION` role):

| Entity (table) | Key fields |
|---|---|
| `Area` (`area`) | name (e.g. Terrace, Hall) |
| `DiningTable` (`dining_table`) | label, area, active |
| `ArticleCategory` | name, sort order, **default station**, parent (optional) |
| `Article` | name, short name (for printing), category, price (informational), active, **station override (optional)**, modifier groups |
| `ModifierGroup` / `Modifier` | name, single/multi select; modifiers of a group |
| `Station` | name (Kitchen, Bar, …), has display, printers (`station_printer`) |
| `Printer` | name, host, port, protocol (ESC/POS), paper width, enabled |
| `AppUser` (`app_user`) | display name, login, PIN hash, active, role WAITER |
| `AdminUser` (`admin_user`) | username, password hash, role ADMIN / MANAGER / STATION |
| `CustomerOrder` | id (UUID, client-generated → idempotent), table, waiter, status, createdAt, submittedAt, note, `@Version` |
| `OrderItem` | article, quantity, modifiers (`order_item_modifier`), note, course, submission no., status ACTIVE / CANCELLED |
| `StationTicket` | order, station, submission no., kind NEW / ADDITION / STORNO, status NEW → IN_PROGRESS → DONE, printedAt, doneAt, items (`station_ticket_item`), `@Version` |
| `PrintJob` | ticket, printer, payload (ESC/POS bytes), status PENDING / SENT / FAILED, attempts, lastError, nextAttemptAt |

**Routing rule:** an item's station = `article.stationOverride ?? category.defaultStation`. On submit, an order
is split into one `StationTicket` per station involved.

**Order lifecycle:** `SUBMITTED` → `COMPLETED` (explicit completion by the waiter) or `CANCELLED`. A follow-up
order for the same table is a **new submission** (`submissionNo` + 1, tickets of kind `ADDITION`), never an
edit of already printed tickets; the client sends the number it expects and gets `409` if another submission
came first. Cancelling an item marks it `CANCELLED` and creates a `STORNO` ticket at the station.

## 4. REST API (Flutter ↔ server)

Base `/api/v1`, JSON, JWT bearer tokens (except `/auth/**`). Errors as RFC 9457 Problem Details (`404`
unknown, `409` conflict, `422` business rule, `401`/`429` for login). Documented with springdoc-openapi at
`/v3/api-docs` (Swagger UI at `/swagger-ui.html`); the spec is exported by CI and attached to every release.

| Method | Path | Purpose |
|---|---|---|
| GET | `/auth/waiters` | active waiters for the login list (public) |
| POST | `/auth/login` | waiter id + PIN → access + refresh token (`429` + `Retry-After` while locked) |
| POST | `/auth/refresh` | refresh token → new token pair |
| GET | `/masterdata` | areas, tables, stations, categories, articles, modifier groups in one payload, with `ETag` |
| GET | `/tables/{id}/orders` | open orders of a table (items, tickets with per-station status) |
| POST | `/orders` | submit an order; body has the client UUID → **idempotent** (`201` created, `200` already known) |
| POST | `/orders/{id}/items` | follow-up submission (`submissionNo` must be the next one, else `409`) |
| POST | `/orders/{id}/cancellations` | cancel items → storno ticket |
| POST | `/orders/{id}/completion` | complete the order |
| GET | `/orders/{id}` | order + per-station ticket status |

Waiters poll `GET /tables/{id}/orders` for the station status; there is no push channel to the phones.

## 5. Order processing flow

1. Phone `POST /orders` (with client UUID).
2. `OrderService` validates (active articles and table), persists the order, `routing` creates the
   `StationTicket`s and the `OrderSubmitted` event is published — **all in one transaction**.
3. Spring Modulith's event publication registry (JDBC, republished on restart) runs the listeners after
   commit and retries failed ones:
   - `printing` renders each ticket with `TicketRenderer`/`EscPosBuilder` and stores a `PrintJob` per
     station printer. `PrintDispatcher` polls due jobs (every 2 s) and sends them over TCP (`TcpPrinterGateway`,
     connect timeout 3 s, write timeout 5 s). A failed attempt keeps the job `PENDING` and retries with
     exponential backoff capped at one minute — **an order is never lost because a printer is down**.
   - `display` pushes the new ticket to all open station views (`StationBroadcaster`).
4. The phone receives the order with its tickets.

Ticket status changes on the station (`TicketStatusChanged`) update the displays and show up on the phone
at its next poll.

## 6. Vaadin UIs

All UI texts are currently German (no `I18NProvider`).

### Admin (`/admin`, roles ADMIN / MANAGER)
CRUD views for areas, tables, categories, articles (station override, modifier groups, active flag),
modifier groups, stations (with printers), printers (**test print** button) and waiters (write-only PIN).
Further views:
- **Orders** (`/admin/orders`): history with filters and **reprint** of tickets.
- **Print status** (`/admin/printing`): per printer the waiting and failed jobs, waiting since, last error; a printer counts as offline when delivery attempts failed.
- **Accounts** (`/admin/accounts`, ADMIN only): admins, managers and one login per station device.

### Station display (`/station/{stationId}`)
- Full-screen kiosk view with one card per `StationTicket`: table, waiter, time since submission (colour
  turns amber/red), items with modifiers and notes. A ticket whose print attempts failed is flagged.
- Tap to `IN_PROGRESS`, then `DONE` (bump); **recall** of recently done tickets.
- **Live updates with Vaadin `@Push`** (WebSocket): `StationBroadcaster` keeps the registered UIs per
  station and updates them via `ui.access(...)`. On reconnect the view reloads the open tickets from the
  database, so it never depends on the push stream alone.
- Access: roles STATION, ADMIN, MANAGER. Each display device gets its own STATION account.

## 7. Cross-cutting decisions

| Topic | Decision |
|---|---|
| DB | PostgreSQL 18; schema migrations with Flyway (`V1` baseline, `V2` ticket `done_at`, `V3` STATION role) |
| Persistence | Spring Data JPA, `ddl-auto: validate`; optimistic locking (`@Version`) on orders and tickets |
| Security (API) | `/api/**` stateless; JWT (HS256, own key from `BONFLOW_JWT_SECRET`), access token 15 min, refresh token 30 days; only access tokens authorize calls |
| Security (UI) | session-based form login; accounts from `admin_user` (database first), bootstrap accounts from the configuration as fallback (`BONFLOW_ADMIN_*`, optional shared `BONFLOW_STATION_*`) |
| Waiter PIN | 4–8 digits, BCrypt; after 5 wrong PINs the waiter is locked for 1 minute (in memory — one instance per venue) |
| Idempotency | client-generated order UUID as primary key; repeated `POST /orders` returns the stored order |
| Printing | ESC/POS over raw TCP 9100 (Epson TM / compatible) behind the `PrinterGateway` interface; outbox table + polling worker |
| Real-time | Vaadin Push for the displays; polling for phones |
| Transport | HTTPS on the LAN through a reverse proxy (Caddy in the compose file) |
| Deployment | Docker Compose (app + PostgreSQL + Caddy) on a box in the venue LAN; image `ghcr.io/bonflow/bonflow-server` built by the release workflow on `vX.Y.Z` tags |
| Observability | Actuator `health` (with probes) and `info`; printer state in the admin print-status view |
| i18n | App: ARB files (DE/EN) with `intl`; Vaadin views: German only |
| CI | GitHub Actions pinned to commit SHAs; `mvn verify` (server), `flutter analyze` + `flutter test` (app) |
| Testing | JUnit 5 + Testcontainers (PostgreSQL), Spring Modulith boundary tests, a fake TCP printer for print tests, order-flow/display/API tests; app: widget tests with fake repositories, contract test against the OpenAPI spec |

## 8. Status and not built yet

Built: master data admin, waiter login, order flow (submit, follow-up, storno, completion), routing,
ESC/POS printing with retry, station displays with bump/recall, test print, print status, order history with
reprint, accounts incl. per-device station logins, Flutter app with offline queue for new orders.

Not built (candidates for later):
- Notifications to waiters ("table 12 food ready") over SSE or push instead of polling.
- Courses: the course is stored per item, but there is no "fire main course" step.
- Sold-out synchronisation to the phones beyond the regular master data refresh.
- Reporting / export.
- A custom printer health indicator in Actuator (the admin view exists).
- Internationalisation of the Vaadin UIs.
- Offline handling of follow-up orders and storno in the app.

## 9. Open points
- Cloud hosting would need a local print bridge agent, because the printers are in the LAN.
- Multi-venue installations would add a `Venue` reference to the data model.
- A payment interface (e.g. open amount per table) is out of scope for now.
