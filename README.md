# bonflow-server

Backend of the BonFlow restaurant/venue ordering system: REST API for the waiters' Flutter app
([bonflow-app](https://github.com/bonflow/bonflow-app)), Vaadin admin UI, Vaadin station
displays and receipt-printer output. One Spring Boot application (modular monolith).

The design is in [docs/architecture.md](docs/architecture.md).

## Stack

Java 21, Spring Boot 3.5, Vaadin 24, Spring Modulith, PostgreSQL + Flyway, Spring Security
(JWT for `/api`, session login for Vaadin), springdoc-openapi, escpos-coffee. Build: Maven.

## Modules (`ch.example.bonflow.*`)

`masterdata`, `users`, `ordering`, `routing`, `printing`, `display`, `api`, `ui` (`ui.admin`,
`ui.station`), `security`. Boundaries are verified by `ModularityTests`.

## Run locally

You need Java 21, Docker (PostgreSQL) and Node.js (Vaadin frontend).

```bash
docker run -d --name bonflow-db -p 5432:5432 \
  -e POSTGRES_DB=bonflow -e POSTGRES_USER=bonflow -e POSTGRES_PASSWORD=bonflow postgres:16-alpine
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

## Status

Skeleton only: build, module layout, baseline schema (`V1__baseline.sql`), security chains,
placeholder Vaadin views, Docker and CI. Domain logic (entities, `OrderService`, routing,
printing, display broadcaster, REST controllers) is the next step, in the order of the phasing
in the architecture document.
