# Setup guide: BonFlow server behind Caddy (HTTPS)

The apps send waiter PINs and tokens, so a real installation should use HTTPS. This guide sets up
the server, PostgreSQL and the [Caddy](https://caddyserver.com/) reverse proxy with Docker Compose
on one machine in the venue (a mini PC, NAS or small server).

```
phones / station screens ──HTTPS──▶ Caddy :443 ──HTTP──▶ app :8080 ──▶ PostgreSQL
                                     (only port that is exposed)
```

## 1. Prerequisites

- A Linux machine (or any host with Docker) that is always on, with a **fixed IP** in the venue network
  (reserve it in the router's DHCP settings).
- Docker Engine with the Compose plugin (`docker compose version` works).
- The venue Wi-Fi must let the phones reach this machine (no "client isolation" on the access point).
- Ports 80 and 443 free on the machine.

## 2. Get the files

Either clone the repository (the compose file then builds the image locally) ...

```bash
git clone https://github.com/bonflow/bonflow-server.git
cd bonflow-server
```

... or use the published image and only copy `docker-compose.yml`, `docker/caddy/Caddyfile`,
`docker/backup/backup.sh` and `.env.example`. In that case replace `build: .` in the `app` service by

```yaml
    image: ghcr.io/bonflow/bonflow-server:0.1.4   # use the latest release
```

## 3. Configure secrets

```bash
cp .env.example .env
```

Edit `.env`:

| Variable | Value |
|---|---|
| `BONFLOW_DB_PASSWORD` | a long random password (only used between the containers) |
| `BONFLOW_JWT_SECRET` | at least 32 bytes: `openssl rand -base64 48` |
| `BONFLOW_ADMIN_PASSWORD` | password of the first admin login (user `admin`) |
| `BONFLOW_STATION_PASSWORD` | optional shared login for station screens; better create one account per device later under *Konten* |

Keep `.env` private (`chmod 600 .env`) and store a copy somewhere safe, see [backup.md](backup.md).

## 4. Choose the address and certificate

Caddy needs to know the name the phones use. Pick **one** of the options.

### Option A: internal name on the LAN (`bonflow.lan`), Caddy's own CA

No internet and no domain needed. Caddy creates its own certificate authority and signs the
certificate for `bonflow.lan`. This is what the repository's `docker/caddy/Caddyfile` does:

```caddyfile
{
	local_certs
}

bonflow.lan {
	tls internal
	reverse_proxy app:8080
}
```

1. Make the name resolve to the server's IP on all devices: create a DNS entry in the router
   (often *Local DNS* / *Host names*), or as a fallback add `192.168.1.10 bonflow.lan` to the
   hosts file of each device that can do that.
2. Every device must **trust Caddy's root certificate**, otherwise browsers and apps show a
   certificate error. After the first start (step 5) export it:

   ```bash
   docker compose cp proxy:/data/caddy/pki/authorities/local/root.crt ./bonflow-root.crt
   ```

   Install `bonflow-root.crt` on the PCs, station screens and phones (iOS: open the file, install
   the profile, then enable it under *Settings → General → About → Certificate Trust Settings*;
   Android: *Settings → Security → Install a certificate → CA certificate*).

> **Check the waiter app first.** Android apps do not necessarily trust user-installed CAs, and the
> Dart HTTP client the Flutter app uses may only look at the system certificate store. Test the app
> against the internal CA early. If it refuses the connection, use option B or C.

### Option B: a real domain name (no certificate installation on devices)

Use a domain you own, e.g. `bonflow.example.ch`, and let Caddy get a trusted certificate from
Let's Encrypt. Then every device accepts it without installing anything.

```caddyfile
bonflow.example.ch {
	reverse_proxy app:8080
}
```

(remove the `{ local_certs }` block and `tls internal`)

- **Server reachable from the internet:** point the DNS `A` record at the venue's public IP and
  forward ports 80 and 443 from the router to the server. Caddy obtains and renews the certificate
  by itself.
- **Server only in the LAN:** point the DNS `A` record at the *private* IP (e.g. `192.168.1.10`).
  Let's Encrypt can then not reach the server, so use the DNS challenge, which needs a Caddy build
  with the plugin for your DNS provider (see the
  [Caddy docs on the DNS challenge](https://caddyserver.com/docs/automatic-https#dns-challenge)).

### Option C: plain HTTP for a first test

Only for trying things out in a closed network. Publish the app port directly and skip Caddy:

```yaml
  app:
    ports:
      - "8080:8080"
```

```bash
docker compose up -d db app
```

Never use this for real operation, PINs and tokens travel unencrypted.

## 5. Start

```bash
docker compose up -d --build     # with the published image: docker compose up -d
docker compose ps                # db, app, backup, proxy: "running" / "healthy"
docker compose logs -f app       # wait for "Started BonflowApplication"
```

Flyway creates the database schema on the first start. The app needs about 10 seconds; until then
Caddy answers `502 Bad Gateway`.

## 6. Check

```bash
curl -I https://bonflow.lan/actuator/health     # add -k if the root certificate is not installed yet
```

Open `https://bonflow.lan/admin` and sign in with `admin` and `BONFLOW_ADMIN_PASSWORD`. Then:

1. Create your own admin account under *Konten* and change the password in the top right, the
   `admin` login from `.env` can not change its password in the UI.
2. Enter the master data: areas, tables, printers, stations, categories, articles (or import a
   JSON file from the start page).
3. Create one account with the role `STATION` per station screen. On each screen open
   `https://bonflow.lan/station/<station id>` in a browser in kiosk/fullscreen mode (the link is in
   *Stationen*).
4. In the waiter app enter `https://bonflow.lan` as the server address.

## 7. Operation

| Task | Command |
|---|---|
| Logs | `docker compose logs -f app` (or `proxy`, `db`) |
| Stop / start | `docker compose stop` / `docker compose start` |
| Update | pull the new code or change the image tag, then `docker compose pull && docker compose up -d --build`. **Make a backup first**, the app migrates the database on start. |
| Backup / restore | see [backup.md](backup.md) |

The containers have `restart: unless-stopped`, so the stack comes back after a reboot of the machine
as long as the Docker service starts at boot (`sudo systemctl enable docker`).

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Browser: certificate warning | Option A: the root certificate is not installed on that device, or the name does not match the one in the `Caddyfile`. |
| `502 Bad Gateway` right after start | The app is still starting; wait and check `docker compose logs app`. |
| App does not start, message about the signing key | `BONFLOW_JWT_SECRET` is missing or shorter than 32 bytes. |
| Name does not resolve on the phone | DNS entry / hosts file missing, or the phone uses a different DNS server than the router (private DNS, VPN). |
| Station screen shows "Zugriff verweigert" | The account needs the role `STATION`, `ADMIN` or `MANAGER`. |
| Caddy cannot get a certificate (option B) | Ports 80/443 not forwarded, DNS record wrong, or (LAN only) the DNS challenge is not set up: `docker compose logs proxy`. |
| Live updates on the station screen stop | Something between browser and server blocks WebSockets (some proxies/VPNs). Caddy itself proxies them without extra configuration; the screen falls back to polling every 15 seconds. |
