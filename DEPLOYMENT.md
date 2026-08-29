# Deployment

## First-time setup

1. Copy `build.properties.example` to `build.properties` and fill it in. At minimum, set:
   - `pyx.id_code_salt` — a random secret, and never change it once set (it's used to derive
     player identification codes; changing it invalidates everyone's).
   - `pyx.cookie_domain` — the domain NPM will serve this on (e.g. `.pyx.example.internal`), not
     `.localhost`.
   - `pyx.admin_addrs` — IPs that get admin. This is trusted via `X-Forwarded-For`, taken as-is
     from the request (see the security note below) — keep this list to IPs you actually trust.
   - Set `hibernate.url=jdbc:sqlite:/data/pyx.sqlite` (not the example's relative `pyx.sqlite`) --
     `/data` is the volume the container persists across restarts/recreation. Leave the rest of
     the `hibernate.*` block on its SQLite defaults; the repo already ships a pre-seeded
     `pyx.sqlite` with card data loaded (copied to `/data/pyx.sqlite` on first run), so there's no
     DB import step.
2. `docker compose build`
3. `docker compose up -d`

Config is baked into the image at build time (Maven resource filtering), so **any time you change
`build.properties`, you need to `docker compose build` again**, not just restart the container.

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

The SQLite database persists across this via the `pyx-data` volume; only the application code and
whatever's in `build.properties` gets rebuilt.

## Logs / troubleshooting

```sh
docker compose logs -f pyx
```

Startup takes something like a minute (Maven still recompiles and re-runs Jetty's own lifecycle on
every container start, in offline mode -- see the comment in `Dockerfile` for why). The
`HEALTHCHECK` gives it a 90s grace period before the container is marked unhealthy for that reason.
