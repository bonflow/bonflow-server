# bonflow-server

Backend of the BonFlow restaurant/venue ordering system: REST API for the waiters' Flutter app
([bonflow-app](https://github.com/bonflow/bonflow-app)), Vaadin admin UI, Vaadin station
displays and receipt-printer output. One Spring Boot application (modular monolith).

The design is in [docs/architecture.md](docs/architecture.md).

## Stack

Java 25 (LTS), Spring Boot 4.1, Vaadin 25, Spring Modulith 2, PostgreSQL 18 + Flyway, Spring Security
(JWT for `/api`, session login for Vaadin), springdoc-openapi, escpos-coffee. Build: Maven.

## Modules (`ch.famfrehner.bonflow.*`)

`masterdata`, `users`, `ordering`, `routing`, `printing`, `display`, `api`, `ui` (`ui.admin`,
`ui.station`), `security`. Boundaries are verified by `ModularityTests`.

## Run locally

You need Java 25, Docker (PostgreSQL) and Node.js 24+ (Vaadin frontend).

```bash
docker run -d --name bonflow-db -p 5432:5432 \
  -e POSTGRES_DB=bonflow -e POSTGRES_USER=bonflow -e POSTGRES_PASSWORD=bonflow postgres:18-alpine
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- Admin UI: <http://localhost:8080/admin> (dev login `admin` / `admin`, from `application-dev.yml`)
- OpenAPI: <http://localhost:8080/v3/api-docs>, Swagger UI at `/swagger-ui.html`
- Health: <http://localhost:8080/actuator/health>

Without the `dev` profile you must set `BONFLOW_JWT_SECRET` (at least 32 bytes) and
`BONFLOW_ADMIN_PASSWORD`; the app refuses to start without a valid signing key.

## Tests

```bash
mvn verify   # needs Docker for Testcontainers
```

`ApplicationSmokeTest` also writes the OpenAPI spec to `target/openapi.json`. CI publishes it as
the `openapi` artifact, and bonflow-app generates its API client from it.

## Deploy (on-premise)

```bash
cp .env.example .env   # fill in secrets
docker compose up -d --build
```

Starts the app, PostgreSQL and a Caddy reverse proxy with LAN HTTPS (edit
`docker/caddy/Caddyfile` for your hostname).

### Minimal example: server and database

Without the repo, with the published image (see [Release](#release)). Save as
`docker-compose.yml` next to a `.env` file that sets `BONFLOW_DB_PASSWORD`,
`BONFLOW_JWT_SECRET` (at least 32 bytes, e.g. `openssl rand -base64 48`) and
`BONFLOW_ADMIN_PASSWORD`:

```yaml
services:
  db:
    image: postgres:18-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: bonflow
      POSTGRES_USER: bonflow
      POSTGRES_PASSWORD: ${BONFLOW_DB_PASSWORD:?set BONFLOW_DB_PASSWORD}
    volumes:
      - db-data:/var/lib/postgresql   # PostgreSQL 18 keeps version-specific data below this path
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U bonflow -d bonflow"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: ghcr.io/bonflow/bonflow-server:0.1.0
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    ports:
      - "8080:8080"
    environment:
      BONFLOW_DB_URL: jdbc:postgresql://db:5432/bonflow
      BONFLOW_DB_USER: bonflow
      BONFLOW_DB_PASSWORD: ${BONFLOW_DB_PASSWORD}
      BONFLOW_JWT_SECRET: ${BONFLOW_JWT_SECRET:?set BONFLOW_JWT_SECRET (>= 32 bytes)}
      BONFLOW_ADMIN_USERNAME: ${BONFLOW_ADMIN_USERNAME:-admin}
      BONFLOW_ADMIN_PASSWORD: ${BONFLOW_ADMIN_PASSWORD:?set BONFLOW_ADMIN_PASSWORD}
      TZ: Europe/Zurich

volumes:
  db-data:
```

```bash
docker compose up -d
```

The admin UI is then at <http://localhost:8080/admin>. Flyway creates the schema on the first
start. Notes:

- Plain HTTP on port 8080 is fine for a first try; for the venue network put a TLS proxy in
  front (the repo's `docker-compose.yml` includes Caddy) because the apps send PINs and tokens.
- The first login uses `BONFLOW_ADMIN_USERNAME` / `BONFLOW_ADMIN_PASSWORD`. Create permanent
  accounts under *Konten* (one per station display device, role STATION) and waiters under
  *Mitarbeitende*.
- The printers (ESC/POS over TCP, port 9100) must be reachable from the `app` container.
- Back up the `db-data` volume (`docker compose exec db pg_dump -U bonflow bonflow`).

## Status

Functional: PIN login with JWT, master data API with ETag, order flow (submit, follow-up, storno,
completion), routing to stations, ESC/POS printing with outbox and retry, station displays with
live push, order history with reprint, admin UI including accounts (admin, manager, one login
per station device).

## Release

Push a tag `vX.Y.Z`: the `release` workflow runs the tests, creates a GitHub release with the
OpenAPI spec and publishes the image `ghcr.io/bonflow/bonflow-server:X.Y.Z` (and `latest`).

## Versions

| Component | Version | Note |
|---|---|---|
| Java | 25 (LTS) | |
| Spring Boot | 4.1.3 | Vaadin 25.3 is built on Boot 4.1. Move to Boot 4.2 together with Vaadin 25.4 once it is GA. |
| Vaadin | 25.3.6 | |
| Spring Modulith | 2.1.2 | |
| springdoc-openapi | 3.1.1 | 3.x is the Boot 4 line |
| PostgreSQL | 18 | |
| Testcontainers | 2.x (managed by Boot) | artifact and class names differ from 1.x |

Nothing here has been compiled yet (see "Status"); expect to fix small API differences on the first
`mvn verify`.
