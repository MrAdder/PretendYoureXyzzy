# Deployment

## First-time setup

This deploys Postgres alongside the app (as a second service in `docker-compose.yml`), seeded on
first boot from the repo's `cah_cards.sql` (schema + the full card catalog) — no manual DB import
step needed.

1. Copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` to a real secret.
2. Copy `build.properties.example` to `build.properties` and fill it in. At minimum, set:
   - `pyx.id_code_salt` — a random secret, and never change it once set (it's used to derive
     player identification codes; changing it invalidates everyone's).
   - `pyx.cookie_domain` — the domain NPM will serve this on (e.g. `.pyx.example.internal`), not
     `.localhost`.
   - `pyx.admin_addrs` — IPs that get admin. This is trusted via `X-Forwarded-For`, taken as-is
     from the request (see the security note below) — keep this list to IPs you actually trust.
   - `hibernate.password` — must be the *same* value as `POSTGRES_PASSWORD` in `.env`. These
     aren't linked automatically: `.env` feeds the postgres container, `build.properties` gets
     baked into the app's image separately.
   - Leave `hibernate.url=jdbc:postgresql://postgres:5432/pyx` as-is — `postgres` is the other
     service's name on the Docker network `docker-compose.yml` puts them both on.
3. `docker compose build`
4. `docker compose up -d`

Config is baked into the image at build time (Maven resource filtering), so **any time you change
`build.properties`, you need to `docker compose build` again**, not just restart the container.

### Using SQLite instead

For a lighter-weight/local setup, comment out the postgres `hibernate.*` block in
`build.properties` and uncomment the SQLite one instead, then remove the `postgres` service and
its `depends_on` entry from `docker-compose.yml`, and mount a volume onto `/data` on the `pyx`
service (the Dockerfile seeds `/data/pyx.sqlite` from the repo's pre-loaded copy on first run, same
as before). Not recommended for a deployment expected to hold real concurrent traffic — SQLite
serializes writes, which Postgres doesn't.

## Nginx Proxy Manager

Point a Proxy Host at this machine's IP, port `8080` (the port `docker-compose.yml` publishes).
If NPM also runs in Docker on the same host, you can skip the published port entirely and instead
add both containers to a shared external Docker network, then point NPM at `http://pyx:8080`.

Enable "Websockets Support" is not required (this app uses long-polling, not WebSockets), but
there's no harm leaving it on.

## Security note on X-Forwarded-For

The app trusts `X-Forwarded-For` verbatim for admin IP checks, bans, and GeoIP -- it doesn't
distinguish "set by my reverse proxy" from "set by whoever's making the request." That's fine as
long as the only way to reach this container is through NPM (bind it to an internal network /
firewall it off from anything else), and NPM overwrites rather than appends to that header. Do not
expose the container's port directly to an untrusted network.

## Updating

```sh
git pull
docker compose build
docker compose up -d
```

The Postgres database persists across this via the `pyx-postgres-data` volume (Postgres itself
isn't rebuilt unless you bump its image tag in `docker-compose.yml`); only the application code and
whatever's in `build.properties` gets rebuilt.

## Logs / troubleshooting

```sh
docker compose logs -f pyx
docker compose logs -f postgres
```

If `pyx` never becomes healthy, check `postgres` first -- `pyx` won't even start until Compose
sees postgres's healthcheck pass. If postgres itself fails on a fresh volume, it's almost always
`cah_cards.sql` failing to apply; check `docker compose logs postgres` for the actual SQL error.

Startup takes something like a minute (Maven still recompiles and re-runs Jetty's own lifecycle on
every container start, in offline mode -- see the comment in `Dockerfile` for why). The
`HEALTHCHECK` gives it a 90s grace period before the container is marked unhealthy for that reason.
