# Backup and restore

BonFlow keeps **everything in PostgreSQL**: master data, accounts (password hashes), waiters,
orders, tickets, print jobs, settings and the Flyway history. One consistent database dump is
therefore a complete backup of the application state.

## What to back up

| What | How | Why |
|---|---|---|
| Database | `backup` service of the repo's `docker-compose.yml` (daily dump) | All business data |
| `.env` | by hand, stored securely and **separate from the dumps** | Secrets: database password, `BONFLOW_JWT_SECRET`, admin password. Losing the JWT secret only signs everybody out; the data is unaffected. |
| Caddy volume `caddy-data` | optional | TLS certificates, they are re-issued automatically |
| Master data JSON (admin → *Stammdaten importieren / exportieren*) | optional, by hand | Readable copy of the menu and layout, importable into another installation. **Not** a backup of orders or accounts. |

## Automatic dumps

The `backup` service starts with the stack, writes a compressed dump to `./backups/` (next to the
compose file) at start and then every 24 hours, and deletes dumps older than 14 days:

```
backups/bonflow-20261127-031500.dump
```

Settings in `.env` (all optional): `BONFLOW_BACKUP_INTERVAL_SECONDS` (default `86400`) and
`BONFLOW_BACKUP_KEEP_DAYS` (default `14`). Check that it works:

```bash
docker compose logs backup
ls -lh backups/
```

A dump on the same machine does not help if the machine is lost. **Copy `./backups/` off the
machine regularly**, for example with a cron job on the server or a NAS pulling it:

```bash
rsync -a /opt/bonflow/backups/ nas:/volume1/bonflow-backups/
```

Without the compose file (the minimal example in the README) add the same `backup` service, or
take a dump by hand:

```bash
docker compose exec -T db pg_dump -U bonflow --format=custom bonflow > bonflow-$(date +%F).dump
```

## Restore

Restoring replaces the current database content with the dump. Stop the app first so nothing
writes during the restore:

```bash
docker compose stop app
docker compose exec -T db pg_restore -U bonflow -d bonflow --clean --if-exists --no-owner \
  < backups/bonflow-20261127-031500.dump
docker compose start app
```

`--clean --if-exists` drops the existing objects before recreating them. On start the app checks
the Flyway history stored in the dump: a dump from an older version is migrated forward
automatically, a dump from a *newer* version than the running image is refused (use the image
version the dump was made with).

### Restore into an empty database (new server)

```bash
docker compose up -d db
docker compose exec -T db pg_restore -U bonflow -d bonflow --no-owner < bonflow-20261127-031500.dump
docker compose up -d
```

Use the same `BONFLOW_DB_PASSWORD` in `.env` or set the new one: the password is not part of the dump.

## Test the restore

A backup that was never restored is only a hope. Once after setup, and after major upgrades, restore
a dump into a scratch stack (`docker compose -p bonflow-test ...` on another machine or with other
ports), log in and check that orders and the menu are there.

## Notes

- Dumps contain personal data (waiter names, order history) and password hashes: protect the
  backup location like the server itself and encrypt copies that leave the venue.
- Orders are never deleted or archived by BonFlow, so the database and the dumps grow slowly over
  time; a busy venue is a few MB per month.
