# Self-hosted deployment

PlaylistBridge runs as two containers: a Spring Boot API on port `8080` and a
Next.js server on port `3000`. Compose starts the frontend only after the API
health endpoint reports ready. The containers run as non-root users, drop Linux
capabilities, disallow privilege escalation, and use read-only root filesystems
with a temporary `/tmp` mount.

## Prerequisites

- Docker Engine with Docker Compose v2.
- A Spotify application with its redirect URI configured.
- A Google Cloud OAuth client with YouTube Data API v3 enabled.
- Public HTTPS hostnames and a reverse proxy for production use.

## Local deployment

From repository root:

```powershell
Copy-Item .env.example .env
# Edit .env and add provider credentials.
docker compose --env-file .env up --build -d
docker compose --env-file .env ps
```

Open `http://localhost:3000`. Verify API liveness with:

```powershell
Invoke-WebRequest http://localhost:8080/api/health
```

Stop services with `docker compose --env-file .env down`. Add `--volumes` only
when intentionally removing Compose-managed data; this deployment does not
create a database volume by default.

## Environment and API URL

Copy `.env.example` to `.env` and set at least:

- `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET`.
- `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
- Exact provider callback values in `SPOTIFY_REDIRECT_URI` and
  `GOOGLE_REDIRECT_URI`.
- `BACKEND_PUBLIC_URL`, `FRONTEND_PUBLIC_URL`, and
  `CORS_ALLOWED_ORIGINS` for the deployment origins.
- `NEXT_PUBLIC_API_BASE_URL` to an absolute URL reachable by users' browsers.

`NEXT_PUBLIC_API_BASE_URL` is a public Next.js variable. Its value is embedded
in browser JavaScript during `docker compose build` / `docker compose up
--build`; changing only the frontend container's runtime environment does not
change an already-built browser bundle. Rebuild the frontend after changing
it:

```powershell
docker compose --env-file .env build --no-cache frontend
docker compose --env-file .env up -d frontend
```

Use `http://localhost:8080` for a local browser. Do not use
`http://backend:8080`: that Compose service name resolves only inside the
Compose network, not in a user's browser. For a reverse-proxy deployment,
point it at the public API origin, such as `https://api.example.com`.

The frontend runtime also receives this variable so server-side Next.js code
has a consistent process environment, but browser behavior still follows the
build-time value above.

## Production reverse proxy

Expose only the reverse proxy to the public internet. Route the frontend host
to `frontend:3000` and the API host to `backend:8080` on the internal Compose
network. Terminate TLS at the proxy and forward `Host`, `X-Forwarded-Proto`,
and `X-Forwarded-For` headers. Set:

```dotenv
BACKEND_PUBLIC_URL=https://api.example.com
FRONTEND_PUBLIC_URL=https://app.example.com
CORS_ALLOWED_ORIGINS=https://app.example.com
NEXT_PUBLIC_API_BASE_URL=https://api.example.com
SPOTIFY_REDIRECT_URI=https://api.example.com/login/oauth2/code/spotify
GOOGLE_REDIRECT_URI=https://api.example.com/login/oauth2/code/google
```

Register those exact HTTPS callback URLs with Spotify and Google. Keep
`8080:8080` and `3000:3000` unpublished or bind them to loopback when the
reverse proxy runs on the host. The sample Compose file keeps the mappings for
simple local use; production operators should override or remove them in a
deployment-specific Compose override.

## Health and operations

Compose reports `backend` healthy only when `GET /api/health` returns success.
The frontend probe requests `/` on port `3000`. Inspect status and logs with:

```powershell
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=100 backend frontend
```

Provider credentials are passed as environment variables. Do not put populated
`.env` files in source control or expose environment dumps in support logs. Use
a secret manager in production, restrict host access to Docker, and rotate
provider credentials if disclosure is suspected.

The containers do not persist application data. If a later deployment adds
token or conversion storage, attach an encrypted, access-controlled volume and
document backup, restore, retention, and deletion procedures before enabling
it. Back up only what policy requires.

## Updates and troubleshooting

Pull the new source, review configuration changes, and rebuild both images:

```powershell
git pull
docker compose --env-file .env build --pull
docker compose --env-file .env up -d
```

If the frontend calls an old API host, check `.env` and rebuild the frontend;
runtime-only environment changes cannot rewrite the browser bundle. If the
frontend waits on `backend`, inspect backend logs and call
`http://localhost:8080/api/health` directly. If OAuth fails, compare callback
URLs character-for-character, including scheme, host, port, path, and trailing
slash.

Validate configuration before deployment:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\validate-config.tests.ps1
docker compose --env-file .env.example config --quiet
```
